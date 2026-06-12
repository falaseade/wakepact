package app.wakepact.data.alarm

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AlarmEntity::class, RingRecordEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class WakePactDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun ringRecordDao(): RingRecordDao
}
