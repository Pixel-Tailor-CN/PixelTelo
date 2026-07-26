package vip.mystery0.pixel.telo.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.util.Log
import android.util.LruCache
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import vip.mystery0.pixel.telo.data.PhoneNumberNormalizer

private data class ContactLookupResult(val name: String?)

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
    }

    private val resolver = context.contentResolver
    private val cache = LruCache<String, ContactLookupResult>(CACHE_SIZE)
    private val lookupDispatcher = Dispatchers.IO.limitedParallelism(4)

    /** 联系人 Provider 发生变化时清空缓存，并通知当前页面重新解析已加载号码。 */
    val changes: Flow<Unit> = callbackFlow {
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
    }.conflate()

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
            cache.evictAll()
        }
    }

    private fun resolveName(phone: String): String? {
        synchronized(cache) {
            cache.get(phone)
        }?.let { return it.name }

        val name = phoneCandidates(phone).firstNotNullOfOrNull(::queryName)
        synchronized(cache) {
            cache.put(phone, ContactLookupResult(name))
        }
        return name
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

    private fun queryName(phone: String): String? {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phone),
        )
        return try {
            resolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.trim()?.takeIf { it.isNotEmpty() }
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            Log.w(TAG, "Contact lookup failed")
            null
        }
    }
}
