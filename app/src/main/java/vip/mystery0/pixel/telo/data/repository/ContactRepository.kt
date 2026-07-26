package vip.mystery0.pixel.telo.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
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
import vip.mystery0.pixel.telo.data.PhoneNumberNormalizer

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
 * 动态解析系统联系人姓名。
 *
 * 查询范围由页面当前已加载的 Paging 窗口决定；姓名只保存在有界内存缓存中，
 * 不写入 Room、备份或日志。
 */
class ContactRepository(private val context: Context) {
    companion object {
        private const val TAG = "ContactRepository"
        private const val CACHE_SIZE = 256
        private const val NEGATIVE_CACHE_DURATION_MS = 30_000L
    }

    private val resolver = context.contentResolver
    private val cache = LruCache<String, ContactLookupResult>(CACHE_SIZE)
    private val lookupDispatcher = Dispatchers.IO.limitedParallelism(4)
    private val observationGeneration = MutableStateFlow(0L)
    private var cacheGeneration = 0L

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
