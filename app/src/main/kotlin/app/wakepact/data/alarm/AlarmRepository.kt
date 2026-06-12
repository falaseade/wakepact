package app.wakepact.data.alarm

import app.wakepact.domain.model.Alarm
import app.wakepact.domain.model.RingEvent
import app.wakepact.domain.model.RingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** An unresolved ring found in the local store (used to resume after process death). */
data class ActiveRing(val event: RingEvent, val alarmId: Long)

@Singleton
class AlarmRepository @Inject constructor(
    private val alarmDao: AlarmDao,
    private val ringRecordDao: RingRecordDao,
) {

    // --- Alarms ---

    fun alarms(): Flow<List<Alarm>> = alarmDao.alarms().map { list -> list.map { it.toDomain() } }

    suspend fun alarm(id: Long): Alarm? = alarmDao.alarm(id)?.toDomain()

    suspend fun enabledAlarms(): List<Alarm> = alarmDao.enabledAlarms().map { it.toDomain() }

    /** Inserts or updates; returns the alarm's id (fresh row id for inserts). */
    suspend fun save(alarm: Alarm): Long {
        val rowId = alarmDao.upsert(alarm.toEntity())
        return if (alarm.id != 0L) alarm.id else rowId
    }

    suspend fun delete(id: Long) = alarmDao.delete(id)

    suspend fun setEnabled(id: Long, enabled: Boolean) = alarmDao.setEnabled(id, enabled)

    // --- Ring records ---

    suspend fun insertRingRecord(event: RingEvent, alarmId: Long) =
        ringRecordDao.insert(event.toRecord(alarmId))

    suspend fun updateRingRecord(
        id: String,
        state: RingState,
        proofAtMs: Long?,
        resolvedAtMs: Long?,
        byUid: String?,
        byName: String?,
    ) = ringRecordDao.update(id, state.name, proofAtMs, resolvedAtMs, byUid, byName)

    suspend fun activeRing(): ActiveRing? =
        ringRecordDao.activeRecord()?.let { ActiveRing(it.toEvent(), it.alarmId) }

    fun recentRingEvents(limit: Int = 50): Flow<List<RingEvent>> =
        ringRecordDao.recent(limit).map { list -> list.map { it.toEvent() } }
}

// --- Mappers (pure; covered by unit tests) ---

fun AlarmEntity.toDomain(): Alarm = Alarm(
    id = id,
    hour = hour,
    minute = minute,
    daysMask = daysMask,
    label = label,
    enabled = enabled,
    stepGoal = stepGoal,
    graceSec = graceSec,
    maxRingSec = maxRingSec,
)

fun Alarm.toEntity(): AlarmEntity = AlarmEntity(
    id = id,
    hour = hour,
    minute = minute,
    daysMask = daysMask,
    label = label,
    enabled = enabled,
    stepGoal = stepGoal,
    graceSec = graceSec,
    maxRingSec = maxRingSec,
)

fun RingEvent.toRecord(alarmId: Long): RingRecordEntity = RingRecordEntity(
    id = id,
    alarmId = alarmId,
    ownerUid = ownerUid,
    ownerName = ownerName,
    label = label,
    firedAtMs = firedAtMs,
    state = state.name,
    proofAtMs = proofAtMs,
    resolvedAtMs = resolvedAtMs,
    deactivatedByUid = deactivatedByUid,
    deactivatedByName = deactivatedByName,
)

fun RingRecordEntity.toEvent(): RingEvent = RingEvent(
    id = id,
    ownerUid = ownerUid,
    ownerName = ownerName,
    label = label,
    firedAtMs = firedAtMs,
    state = RingState.entries.firstOrNull { it.name == state } ?: RingState.MISSED,
    proofAtMs = proofAtMs,
    resolvedAtMs = resolvedAtMs,
    deactivatedByUid = deactivatedByUid,
    deactivatedByName = deactivatedByName,
)
