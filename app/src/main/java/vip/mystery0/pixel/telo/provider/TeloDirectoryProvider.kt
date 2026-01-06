package vip.mystery0.pixel.telo.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.Directory
import android.util.Log
import vip.mystery0.pixel.telo.BuildConfig
import vip.mystery0.pixel.telo.R

class TeloDirectoryProvider : ContentProvider() {
    companion object {
        private const val TAG = "TeloDirectoryProvider"
        const val MATCH_DIRECTORIES = 1
        const val MATCH_PHONE_LOOKUP = 2
        private const val AUTHORITY = "${BuildConfig.APPLICATION_ID}.provider"
    }

    private val MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
        // 1. 系统查询有哪些目录 (关键！之前缺了这个)
        addURI(AUTHORITY, "directories", MATCH_DIRECTORIES)
        // 2. 具体的号码查询 (匹配 /filter/123456)
        addURI(AUTHORITY, "phone_lookup/*", MATCH_PHONE_LOOKUP)
        // 3. 通用查询 (匹配 /123456)
        addURI(AUTHORITY, "*", MATCH_PHONE_LOOKUP)
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val match = MATCHER.match(uri)
        Log.d(TAG, "Provider Query: $uri, Match Code: $match")

        return when (match) {
            MATCH_DIRECTORIES -> handleDirectoriesQuery(projection)
            MATCH_PHONE_LOOKUP -> handlePhoneLookup(uri, projection)
            else -> null
        }
    }

    // --- 核心修复：告诉系统我们有一个名为 "Pixel Telo" 的目录 ---
    private fun handleDirectoriesQuery(projection: Array<out String>?): Cursor {
        val columns = projection ?: arrayOf(
            Directory._ID,
            Directory.ACCOUNT_NAME,
            Directory.ACCOUNT_TYPE,
            Directory.DISPLAY_NAME,
            Directory.TYPE_RESOURCE_ID,
            Directory.EXPORT_SUPPORT,
            Directory.SHORTCUT_SUPPORT,
            Directory.PHOTO_SUPPORT
        )

        val cursor = MatrixCursor(columns)
        val row = cursor.newRow()

        // 填充目录信息
        row.add(Directory._ID, 1)
        row.add(Directory.ACCOUNT_NAME, "TeloLocal")
        row.add(Directory.ACCOUNT_TYPE, BuildConfig.APPLICATION_ID)
        row.add(Directory.DISPLAY_NAME, "Pixel Telo")
        row.add(Directory.TYPE_RESOURCE_ID, R.string.app_name) // 确保 strings.xml 里有 app_name
        row.add(Directory.EXPORT_SUPPORT, Directory.EXPORT_SUPPORT_ANY_ACCOUNT)
        row.add(Directory.SHORTCUT_SUPPORT, Directory.SHORTCUT_SUPPORT_NONE)
        row.add(Directory.PHOTO_SUPPORT, Directory.PHOTO_SUPPORT_NONE)

        Log.d(TAG, "Returned directory info")
        return cursor
    }

    // --- 号码查询逻辑 ---
    private fun handlePhoneLookup(uri: Uri, projection: Array<out String>?): Cursor? {
        // 获取查询的号码 (通常在 URL 的最后一段)
        val filter = uri.lastPathSegment ?: return null
        Log.d(TAG, "Looking up number: $filter")

        // 模拟查库逻辑
        // 只有包含 12345 才返回，否则返回空 Cursor (表示没找到)
        if (!filter.contains("12345")) {
            return null
        }

        val columns = projection ?: arrayOf(
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.LABEL
        )

        val cursor = MatrixCursor(columns)
        val row = cursor.newRow()

        row.add(ContactsContract.Contacts.DISPLAY_NAME, "Pixel Telo: 骚扰测试")
        row.add(ContactsContract.CommonDataKinds.Phone.NUMBER, filter)
        row.add(ContactsContract.CommonDataKinds.Phone.LABEL, "广告")

        return cursor
    }

    // 其他方法保持默认
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}