package vip.mystery0.pixel.telo.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import vip.mystery0.pixel.telo.data.dao.BlockedCallDao
import vip.mystery0.pixel.telo.data.dao.LocalNumberLabelDao
import vip.mystery0.pixel.telo.data.dao.UserListDao
import vip.mystery0.pixel.telo.data.entity.BlockedCall
import vip.mystery0.pixel.telo.data.entity.LocalNumberLabel
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
                `addedAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS `index_user_list_phoneNumber_listType`
            ON `user_list` (`phoneNumber`, `listType`)
            """.trimIndent()
        )
    }
}

/** 从 v2 升级到 v3：user_list 表新增 tagMatch 字段 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `user_list` ADD COLUMN `tagMatch` INTEGER NOT NULL DEFAULT 0")
    }
}

/** 从 v3 升级到 v4：blocked_calls 表新增 label 字段 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `blocked_calls` ADD COLUMN `label` TEXT")
    }
}

/** 从 v4 升级到 v5：user_list 表新增 locationMatch 字段 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `user_list` ADD COLUMN `locationMatch` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("DROP INDEX IF EXISTS `index_user_list_phoneNumber_listType`")
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS `index_user_list_phoneNumber_listType_tagMatch_locationMatch`
            ON `user_list` (`phoneNumber`, `listType`, `tagMatch`, `locationMatch`)
            """.trimIndent()
        )
    }
}

/** 从 v5 升级到 v6：blocked_calls 表新增联网查询反馈字段 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `blocked_calls` ADD COLUMN `querySource` TEXT")
        db.execSQL("ALTER TABLE `blocked_calls` ADD COLUMN `feedbackToken` TEXT")
        db.execSQL(
            "ALTER TABLE `blocked_calls` " +
                "ADD COLUMN `feedbackStatus` TEXT NOT NULL DEFAULT 'UNAVAILABLE'"
        )
    }
}

/**
 * 从 v6 升级到 v7：user_list 表新增 forceBlock 字段。
 * 既有标签/归属地黑名单规则此前始终强制拦截，迁移为 true 保持行为不变；
 * 既有号码黑名单规则此前遵循“仅提示不拦截”，保持 false 不改变行为。
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `user_list` ADD COLUMN `forceBlock` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "UPDATE `user_list` SET `forceBlock` = 1 " +
                "WHERE `listType` = 'BLACK' AND (`tagMatch` = 1 OR `locationMatch` = 1)"
        )
    }
}

/** 从 v7 升级到 v8：拦截记录新增省份和城市字段 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `blocked_calls` ADD COLUMN `province` TEXT")
        db.execSQL("ALTER TABLE `blocked_calls` ADD COLUMN `city` TEXT")
    }
}

/**
 * 从 v8 升级到 v9：拦截记录新增实时查询 Backend 归属。
 *
 * 旧版本只有官方查询会签发反馈 Token，因此仅将非空 Token 的旧记录归属到官方；
 * 没有反馈凭据的纯本地或其他旧记录无法可靠推断来源，继续保持 null。
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `blocked_calls` ADD COLUMN `queryBackendId` TEXT")
        db.execSQL(
            "UPDATE `blocked_calls` SET `queryBackendId` = 'official' " +
                "WHERE `feedbackToken` IS NOT NULL AND TRIM(`feedbackToken`) <> ''"
        )
    }
}

/** 从 v9 升级到 v10：新增独立的持久化本地号码标签表。 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `local_number_labels` (
                `normalizedPhoneNumber` TEXT NOT NULL,
                `label` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`normalizedPhoneNumber`)
            )
            """.trimIndent()
        )
    }
}

@Database(
    entities = [BlockedCall::class, UserListEntry::class, LocalNumberLabel::class],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blockedCallDao(): BlockedCallDao

    /** 用户自定义黑白名单 Dao */
    abstract fun userListDao(): UserListDao

    /** 用户为具体号码设置的持久化本地标签 Dao */
    abstract fun localNumberLabelDao(): LocalNumberLabelDao
}
