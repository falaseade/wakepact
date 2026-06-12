package app.wakepact.data.alarm

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    fun alarms(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun alarm(id: Long): AlarmEntity?

    @Query("SELECT * FROM alarms WHERE enabled = 1")
    suspend fun enabledAlarms(): List<AlarmEntity>

    @Upsert
    suspend fun upsert(alarm: AlarmEntity): Long

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE alarms SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
}
