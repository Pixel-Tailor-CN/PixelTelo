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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.BuildConfig
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.repository.SpamNumberRepository
import vip.mystery0.pixel.telo.data.repository.SyncRepository

class TeloDirectoryProvider : ContentProvider(), KoinComponent {
    companion object {
        private const val TAG = "TeloDirectoryProvider"
        const val MATCH_DIRECTORIES = 1
        const val MATCH_PHONE_LOOKUP = 2
        private const val AUTHORITY = "${BuildConfig.APPLICATION_ID}.provider"
    }

    private val spamNumberRepository: SpamNumberRepository by inject()
    private val syncRepository: SyncRepository by inject()

    private val MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(AUTHORITY, "directories", MATCH_DIRECTORIES)
        addURI(AUTHORITY, "phone_lookup/*", MATCH_PHONE_LOOKUP)
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
        return when (match) {
            MATCH_DIRECTORIES -> handleDirectoriesQuery(projection)
            MATCH_PHONE_LOOKUP -> handlePhoneLookup(uri, projection)
            else -> null
        }
    }

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
        return cursor
    }

    // --- 号码查询逻辑 ---
    private fun handlePhoneLookup(uri: Uri, projection: Array<out String>?): Cursor? {
        val filter = uri.lastPathSegment ?: return null
        Log.d(TAG, "Looking up number: $filter")

        return runBlocking(Dispatchers.IO) {
            var label: String
            var displayName: String

            val (shouldFilter, spamLabel) = spamNumberRepository.checkSpam(filter)
            if (shouldFilter) {
                Log.i(TAG, "Found spam number: $filter")
                label = spamLabel
                displayName = spamLabel
            } else {
                Log.i(TAG, "Not spam number: $filter")
                return@runBlocking null
            }

            val columns = projection ?: arrayOf(
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.LABEL,
                ContactsContract.CommonDataKinds.Phone.TYPE
            )

            val cursor = MatrixCursor(columns)
            val row = cursor.newRow()

            row.add(ContactsContract.Contacts.DISPLAY_NAME, displayName)
            row.add(ContactsContract.CommonDataKinds.Phone.NUMBER, filter)
            row.add(ContactsContract.CommonDataKinds.Phone.LABEL, label)
            row.add(
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM
            )

            return@runBlocking cursor
        }
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