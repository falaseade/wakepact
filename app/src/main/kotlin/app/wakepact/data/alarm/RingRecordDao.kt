package app.wakepact.data.alarm

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RingRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: RingRecordEntity)

    @Query(
        "UPDATE ring_records SET state = :state, proofAtMs = :proofAtMs, resolvedAtMs = :resolvedAtMs, " +
            "deactivatedByUid = :byUid, deactivatedByName = :byName WHERE id = :id",
    )
    suspend fun update(
        id: String,
        state: String,
        proofAtMs: Long?,
        resolvedAtMs: Long?,
        byUid: String?,
        byName: String?,
    )

    @Query("SELECT * FROM ring_records WHERE state IN ('RINGING', 'PROOF_DONE') ORDER BY firedAtMs DESC LIMIT 1")
    suspend fun activeRecord(): RingRecordEntity?

    @Query("SELECT * FROM ring_records ORDER BY firedAtMs DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<RingRecordEntity>>
}
