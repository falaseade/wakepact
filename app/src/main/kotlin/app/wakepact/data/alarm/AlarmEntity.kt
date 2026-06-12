package app.wakepact.data.alarm

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val hour: Int,
    val minute: Int,
    val daysMask: Int,
    val label: String,
    val enabled: Boolean,
    val stepGoal: Int,
    val graceSec: Int,
    val maxRingSec: Int,
)
