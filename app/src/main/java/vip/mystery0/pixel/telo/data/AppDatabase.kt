package vip.mystery0.pixel.telo.data

import androidx.room.Database
import androidx.room.RoomDatabase
import vip.mystery0.pixel.telo.data.dao.BlockedCallDao
import vip.mystery0.pixel.telo.data.entity.BlockedCall

@Database(entities = [BlockedCall::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blockedCallDao(): BlockedCallDao
}
