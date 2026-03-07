package vip.mystery0.pixel.telo.data

import androidx.room.Database
import androidx.room.RoomDatabase
import vip.mystery0.pixel.telo.data.dao.BlockedCallDao
import vip.mystery0.pixel.telo.data.dao.UserListDao
import vip.mystery0.pixel.telo.data.entity.BlockedCall
import vip.mystery0.pixel.telo.data.entity.UserListEntry

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
