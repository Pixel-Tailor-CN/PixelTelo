package vip.mystery0.pixel.telo.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.CancellationSignal
import android.os.Looper
import android.os.SystemClock
import android.provider.ContactsContract
import android.util.Log
import android.util.LruCache
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import vip.mystery0.pixel.telo.data.PhoneNumberNormalizer
import vip.mystery0.pixel.telo.data.PhoneNumberRuleMatcher
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume

private data class ContactLookupResult(
    val name: String?,
    val expiresAtElapsedMillis: Long,
)

private sealed interface ContactQueryResult {
    data class Found(val name: String) : ContactQueryResult
    data object NotFound : ContactQueryResult
    data object Failed : ContactQueryResult
}

/**
 * 查询系统联系人是否存在，并为页面动态解析联系人姓名。
 *
 * 查询范围由页面当前已加载的 Paging 窗口决定；姓名只保存在有界内存缓存中，
 * 不写入 Room、备份或日志。
 */
class ContactRepository(private val context: Context) {
    companion object {
        private const val TAG = "ContactRepository"
        private const val CACHE_SIZE = 256
        private const val NEGATIVE_CACHE_DURATION_MS = 30_000L
        private const val SCREENING_LOOKUP_TIMEOUT_MS = 200L
    }

    private val resolver = context.contentResolver
    private val cache = LruCache<String, ContactLookupResult>(CACHE_SIZE)
    private val lookupDispatcher = Dispatchers.IO.limitedParallelism(4)
    private val observationGeneration = MutableStateFlow(0L)
    private var cacheGeneration = 0L

    /**
     * 实时识别只查询本地通讯录的号码存在性，不读取姓名，也不使用页面姓名缓存。
     *
     * 独立调度同步 Binder 查询，使超时方无需等待 Provider 返回；取消同时通知 Provider。
     * 未授权、异常、排队或查询超过预算均按未确认联系人处理，继续原识别流程。
     */
    suspend fun isContact(phoneNumber: String): Boolean {
        if (phoneNumber.isBlank() || ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_CONTACTS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return false

        val startedAt = SystemClock.elapsedRealtime()
        val found = withTimeoutOrNull(SCREENING_LOOKUP_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation {
                    // Provider 的取消也可能触发 Binder，不能占用来电超时返回线程。
                    Dispatchers.IO.dispatch(EmptyCoroutineContext) { signal.cancel() }
                }
                lookupDispatcher.dispatch(EmptyCoroutineContext) {
                    if (!continuation.isActive) return@dispatch
                    val exists = try {
                        // 明确国际号码保留国际标记，避免姓名展示用的国内兼容逻辑扩大放行范围。
                        screeningCandidates(phoneNumber).any { candidate ->
                            signal.throwIfCanceled()
                            val uri = Uri.withAppendedPath(
                                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                                Uri.encode(candidate),
                            ).buildUpon().appendQueryParameter(
                                ContactsContract.DIRECTORY_PARAM_KEY,
                                ContactsContract.Directory.DEFAULT.toString(),
                            ).build()
                            resolver.query(
                                uri,
                                arrayOf(ContactsContract.PhoneLookup._ID),
                                null, null, null, signal,
                            )?.use { it.moveToFirst() } == true
                        }
                    } catch (_: Exception) {
                        if (!signal.isCanceled) Log.w(TAG, "Contact screening lookup failed")
                        false
                    }
                    continuation.resume(exists)
                }
            }
        }
        val cost = SystemClock.elapsedRealtime() - startedAt
        if (found == null) {
            Log.w(TAG, "Contact screening lookup timed out: cost=${cost}ms")
        } else if (cost > 100L) {
            Log.w(TAG, "Contact screening lookup too slow: cost=${cost}ms")
        }
        return found == true
    }

    /**
     * 保留原始格式及规则候选；国内查询域额外尝试 +86 格式，固定电话移除长途冠码 0。
     * 非中国地区的 Contacts Provider 不一定将国内号码与已保存的 +86 号码视为相同号码。
     * 明确境外号码（包括和多号前缀处理后暴露的国际标记）不得补充中国国家码。
     */
    private fun screeningCandidates(phoneNumber: String): List<String> {
        val candidates = listOf(phoneNumber.trim()) + PhoneNumberRuleMatcher.matchCandidates(phoneNumber)
        val normalized = PhoneNumberNormalizer.normalizeForLookup(phoneNumber)
        val chinaCandidate = if (
            !PhoneNumberRuleMatcher.isExplicitInternational(phoneNumber) &&
            !PhoneNumberRuleMatcher.isExplicitInternational(normalized) &&
            normalized.isNotBlank() && normalized.all(Char::isDigit)
        ) {
            "+86${normalized.removePrefix("0")}"
        } else null
        return (candidates + listOfNotNull(chinaCandidate)).filter(String::isNotBlank).distinct()
    }

    /** 联系人 Provider 发生变化时清空缓存，并通知当前页面重新解析已加载号码。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val changes: Flow<Unit> = observationGeneration
        .flatMapLatest { observeContactChanges() }
        .conflate()

    private fun observeContactChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                invalidateCache()
                trySend(Unit)
            }
        }
        var registered = false
        try {
            resolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI,
                true,
                observer,
            )
            registered = true
        } catch (_: SecurityException) {
            Log.w(TAG, "Unable to observe contacts")
        }
        awaitClose {
            if (registered) {
                resolver.unregisterContentObserver(observer)
            }
        }
    }

    /**
     * 解析一批原始号码，返回以原始号码为 key 的联系人姓名。
     *
     * 没有联系人权限时直接返回空集合，不缓存权限失败结果，确保授权后可立即重查。
     */
    suspend fun resolveNames(phoneNumbers: Set<String>): Map<String, String> {
        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyMap()
        }
        return coroutineScope {
            phoneNumbers
                .filter { it.isNotBlank() }
                .map { phone ->
                    async(lookupDispatcher) {
                        phone to resolveName(phone)
                    }
                }
                .awaitAll()
                .mapNotNull { (phone, name) -> name?.let { phone to it } }
                .toMap()
        }
    }

    fun invalidateCache() {
        synchronized(cache) {
            cacheGeneration++
            cache.evictAll()
        }
    }

    /**
     * 权限状态可能在页面离开期间变化，重新建立观察器。
     *
     * 某些 ROM 会在没有联系人权限时拒绝注册 ContentObserver；重新授权后必须显式重试。
     */
    fun restartObservation() {
        invalidateCache()
        observationGeneration.update { it + 1 }
    }

    private fun resolveName(phone: String): String? {
        val generation = synchronized(cache) {
            val cached = cache.get(phone)
            if (cached != null) {
                if (cached.expiresAtElapsedMillis > SystemClock.elapsedRealtime()) {
                    return cached.name
                }
                cache.remove(phone)
            }
            cacheGeneration
        }

        val result = queryPhoneCandidates(phone)
        synchronized(cache) {
            // 联系人变化后，旧查询即使稍后返回也不能回灌已失效缓存。
            if (generation == cacheGeneration) {
                when (result) {
                    is ContactQueryResult.Found -> cache.put(
                        phone,
                        ContactLookupResult(result.name, Long.MAX_VALUE),
                    )

                    ContactQueryResult.NotFound -> cache.put(
                        phone,
                        ContactLookupResult(
                            name = null,
                            expiresAtElapsedMillis =
                                SystemClock.elapsedRealtime() + NEGATIVE_CACHE_DURATION_MS,
                        ),
                    )

                    ContactQueryResult.Failed -> Unit
                }
            }
        }
        return (result as? ContactQueryResult.Found)?.name
    }

    /**
     * 同时尝试原始号码、去国家码号码和业务查询标准号码，
     * 兼容联系人中保存 +86 或国内号码，以及中国移动一卡多号前缀来电。
     */
    private fun phoneCandidates(phone: String): List<String> = listOf(
        phone.trim(),
        PhoneNumberNormalizer.normalizeCountryCode(phone),
        PhoneNumberNormalizer.normalizeForLookup(phone),
    ).filter { it.isNotBlank() }.distinct()

    private fun queryPhoneCandidates(phone: String): ContactQueryResult {
        var failed = false
        for (candidate in phoneCandidates(phone)) {
            when (val result = queryName(candidate)) {
                is ContactQueryResult.Found -> return result
                ContactQueryResult.NotFound -> Unit
                ContactQueryResult.Failed -> failed = true
            }
        }
        if (failed) {
            Log.w(TAG, "Contact lookup failed")
            return ContactQueryResult.Failed
        }
        return ContactQueryResult.NotFound
    }

    private fun queryName(phone: String): ContactQueryResult {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phone),
        )
        return try {
            val cursor = resolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null,
            ) ?: return ContactQueryResult.Failed
            cursor.use {
                val name = if (it.moveToFirst()) {
                    it.getString(0)?.trim()?.takeIf(String::isNotEmpty)
                } else null
                name?.let(ContactQueryResult::Found) ?: ContactQueryResult.NotFound
            }
        } catch (_: Exception) {
            ContactQueryResult.Failed
        }
    }
}
