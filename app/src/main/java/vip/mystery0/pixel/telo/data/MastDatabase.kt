package vip.mystery0.pixel.telo.data

import androidx.room.Database
import androidx.room.RoomDatabase
import vip.mystery0.pixel.telo.data.dao.MastDao
import vip.mystery0.pixel.telo.data.dao.SpamNumberDao
import vip.mystery0.pixel.telo.data.entity.MetadataEntity
import vip.mystery0.pixel.telo.data.entity.SpamNumberEntity

@Database(
    entities = [MetadataEntity::class, SpamNumberEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MastDatabase : RoomDatabase() {
    abstract fun mastDao(): MastDao
    abstract fun spamNumberDao(): SpamNumberDao
}
