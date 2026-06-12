package app.wakepact.data.alarm

import app.wakepact.domain.model.Alarm
import app.wakepact.domain.model.RingEvent
import app.wakepact.domain.model.RingState
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmMappersTest {

    @Test
    fun `alarm survives a round trip through its entity`() {
        val alarm = Alarm(
            id = 7, hour = 6, minute = 45, daysMask = 0b0011111,
            label = "Gym", enabled = true, stepGoal = 40, graceSec = 240, maxRingSec = 900,
        )
        assertEquals(alarm, alarm.toEntity().toDomain())
    }

    @Test
    fun `ring event survives a round trip through its record`() {
        val event = RingEvent(
            id = "evt-1", ownerUid = "u1", ownerName = "Asha", label = "Gym",
            firedAtMs = 123L, state = RingState.PROOF_DONE, proofAtMs = 456L,
            resolvedAtMs = null, deactivatedByUid = null, deactivatedByName = null,
        )
        assertEquals(event, event.toRecord(alarmId = 7).toEvent())
        assertEquals(7L, event.toRecord(alarmId = 7).alarmId)
    }

    @Test
    fun `unknown persisted state falls back to MISSED instead of crashing`() {
        val record = RingRecordEntity(
            id = "evt-2", alarmId = 1, ownerUid = "u1", ownerName = "Asha", label = "",
            firedAtMs = 1L, state = "FUTURE_STATE_FROM_V2", proofAtMs = null,
            resolvedAtMs = null, deactivatedByUid = null, deactivatedByName = null,
        )
        assertEquals(RingState.MISSED, record.toEvent().state)
    }
}
