package app.wakepact.data.alarm

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local record of one ring. Doubles as the source of truth for the owner's
 * own rings (the same id is used as the Firestore event document id) and as
 * the feed in solo mode.
 */
@Entity(tableName = "ring_records")
data class RingRecordEntity(
    @PrimaryKey val id: String,
    val alarmId: Long,
    val ownerUid: String,
    val ownerName: String,
    val label: String,
    val firedAtMs: Long,
    val state: String,
    val proofAtMs: Long?,
    val resolvedAtMs: Long?,
    val deactivatedByUid: String?,
    val deactivatedByName: String?,
)
