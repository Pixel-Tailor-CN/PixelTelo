package vip.mystery0.pixel.telo.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import vip.mystery0.pixel.telo.data.dao.BlockedCallDao
import vip.mystery0.pixel.telo.data.dao.UserListDao
import vip.mystery0.pixel.telo.data.entity.BlockedCall
import vip.mystery0.pixel.telo.data.entity.UserListEntry

/** 从 v1 升级到 v2：新增 user_list 表 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `user_list` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `phoneNumber` TEXT NOT NULL,
                `isPrefix` INTEGER NOT NULL,
                `listType` TEXT NOT NULL,
                `remark` TEXT,
                `addedAt` INTEGER NOT NULL,
                UNIQUE(`phoneNumber`, `listType`)
            )
            """.trimIndent()
        )
    }
}

@Database(
    entities = [BlockedCall::class, UserListEntry::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blockedCallDao(): BlockedCallDao

    /** 用户自定义黑白名单 Dao */
    abstract fun userListDao(): UserListDao
}
