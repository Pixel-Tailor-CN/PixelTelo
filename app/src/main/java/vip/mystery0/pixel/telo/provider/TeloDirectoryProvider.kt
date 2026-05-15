package vip.mystery0.pixel.telo.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.Directory
import android.provider.ContactsContract.PhoneLookup
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.BuildConfig
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.repository.SpamNumberRepository

class TeloDirectoryProvider : ContentProvider(), KoinComponent {
    companion object {
        private const val TAG = "TeloDirectoryProvider"
        const val MATCH_DIRECTORIES = 1
        const val MATCH_PHONE_LOOKUP = 2
        const val MATCH_CONTACT_LOOKUP = 3
        private const val AUTHORITY = "${BuildConfig.APPLICATION_ID}.provider"

        private val DIRECTORY_COLUMNS = arrayOf(
            Directory.ACCOUNT_NAME,
            Directory.ACCOUNT_TYPE,
            Directory.DISPLAY_NAME,
            Directory.PACKAGE_NAME,
            Directory.TYPE_RESOURCE_ID,
            Directory.EXPORT_SUPPORT,
            Directory.SHORTCUT_SUPPORT,
            Directory.PHOTO_SUPPORT,
            Directory.DIRECTORY_AUTHORITY
        )

        private val PHONE_COLUMNS = arrayOf(
            Data._ID,
            Data.MIMETYPE,
            Data.CONTACT_ID,
            Contacts.LOOKUP_KEY,
            Contacts.DISPLAY_NAME,
            PhoneLookup.DISPLAY_NAME,
            PhoneLookup.NUMBER,
            PhoneLookup.TYPE,
            PhoneLookup.LABEL,
            Phone.NUMBER,
            Phone.TYPE,
            Phone.LABEL
        )
    }

    private val spamNumberRepository: SpamNumberRepository by inject()

    private val MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(AUTHORITY, "directories", MATCH_DIRECTORIES)
        addURI(AUTHORITY, "phone_lookup/*", MATCH_PHONE_LOOKUP)
        addURI(AUTHORITY, "data/phones/filter/*", MATCH_PHONE_LOOKUP)
        addURI(AUTHORITY, "contacts/lookup/*", MATCH_CONTACT_LOOKUP)
        addURI(AUTHORITY, "contacts/lookup/*/#", MATCH_CONTACT_LOOKUP)
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
        Log.d(TAG, "Query received: uri=$uri, match=$match")
        return when (match) {
            MATCH_DIRECTORIES -> handleDirectoriesQuery(projection)
            MATCH_PHONE_LOOKUP -> handlePhoneLookup(uri, projection)
            MATCH_CONTACT_LOOKUP -> handleContactLookup(uri, projection)
            else -> {
                Log.w(TAG, "Unsupported query uri: $uri")
                null
            }
        }
    }

    private fun handleDirectoriesQuery(projection: Array<out String>?): Cursor {
        val columns = projection ?: DIRECTORY_COLUMNS
        val cursor = MatrixCursor(columns)
        val values = mapOf(
            Directory.ACCOUNT_NAME to "TeloLocal",
            Directory.ACCOUNT_TYPE to BuildConfig.APPLICATION_ID,
            Directory.DISPLAY_NAME to "Pixel Telo",
            Directory.PACKAGE_NAME to BuildConfig.APPLICATION_ID,
            Directory.TYPE_RESOURCE_ID to R.string.app_name,
            Directory.EXPORT_SUPPORT to Directory.EXPORT_SUPPORT_NONE,
            Directory.SHORTCUT_SUPPORT to Directory.SHORTCUT_SUPPORT_NONE,
            Directory.PHOTO_SUPPORT to Directory.PHOTO_SUPPORT_NONE,
            Directory.DIRECTORY_AUTHORITY to AUTHORITY
        )
        cursor.addProjectionAwareRow(columns, values)
        return cursor
    }

    // 号码查询逻辑：同时兼容 PhoneLookup 和 CommonDataKinds.Phone.CONTENT_FILTER_URI 转发路径。
    private fun handlePhoneLookup(uri: Uri, projection: Array<out String>?): Cursor {
        val phoneNumber = uri.lastPathSegment ?: return emptyCursor(projection)
        return queryPhoneNumber(phoneNumber, projection)
    }

    // 联系人详情回查逻辑：Dialer 会使用我们返回的 LOOKUP_KEY 再次查询联系人详情。
    private fun handleContactLookup(uri: Uri, projection: Array<out String>?): Cursor {
        val lookupKey = uri.pathSegments.getOrNull(2)
        val phoneNumber = lookupKey?.removePrefix("telo:").takeUnless { it == lookupKey }
            ?: return emptyCursor(projection)
        return queryPhoneNumber(phoneNumber, projection)
    }

    private fun queryPhoneNumber(filter: String, projection: Array<out String>?): Cursor {
        val columns = projection ?: PHONE_COLUMNS
        val emptyCursor = MatrixCursor(columns)
        Log.d(TAG, "Looking up number: $filter")

        return runBlocking(Dispatchers.IO) {
            val (shouldFilter, spamLabel) = spamNumberRepository.checkSpam(filter)
            if (shouldFilter) {
                Log.i(TAG, "Found spam number: $filter")
            } else {
                Log.i(TAG, "Not spam number: $filter")
                return@runBlocking emptyCursor
            }

            val rowId = stablePositiveId(filter)
            val values = mapOf(
                Data._ID to rowId,
                Data.MIMETYPE to Phone.CONTENT_ITEM_TYPE,
                Data.CONTACT_ID to rowId,
                Contacts._ID to rowId,
                Contacts.LOOKUP_KEY to "telo:$filter",
                Contacts.DISPLAY_NAME to spamLabel,
                PhoneLookup._ID to rowId,
                PhoneLookup.DISPLAY_NAME to spamLabel,
                PhoneLookup.NUMBER to filter,
                PhoneLookup.TYPE to Phone.TYPE_CUSTOM,
                PhoneLookup.LABEL to spamLabel,
                Phone.NUMBER to filter,
                Phone.TYPE to Phone.TYPE_CUSTOM,
                Phone.LABEL to spamLabel
            )

            val cursor = MatrixCursor(columns)
            cursor.addProjectionAwareRow(columns, values)
            return@runBlocking cursor
        }
    }

    private fun emptyCursor(projection: Array<out String>?): Cursor {
        return MatrixCursor(projection ?: PHONE_COLUMNS)
    }

    private fun MatrixCursor.addProjectionAwareRow(
        columns: Array<out String>,
        values: Map<String, Any?>
    ) {
        addRow(columns.map { values[it] }.toTypedArray())
    }

    private fun stablePositiveId(phoneNumber: String): Long {
        return (phoneNumber.hashCode().toLong() and 0x7fffffffL) + 1L
    }

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
