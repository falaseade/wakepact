package app.wakepact.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StepChainValidatorTest {

    @Test
    fun `AC-2_1 first two steps of a chain are credited retroactively at the third`() {
        val v = StepChainValidator()
        assertEquals(0, v.onStep(0))
        assertEquals(0, v.onStep(500))
        assertEquals(3, v.onStep(1_000))
    }

    @Test
    fun `AC-2_1 chained steps after the third each add one`() {
        val v = StepChainValidator()
        v.onStep(0); v.onStep(500); v.onStep(1_000)
        assertEquals(4, v.onStep(1_500))
        assertEquals(5, v.onStep(2_500)) // 1.0 s interval, still in rhythm
    }

    @Test
    fun `AC-2_2 a long gap restarts the chain and the new chain is provisional again`() {
        val v = StepChainValidator()
        v.onStep(0); v.onStep(500); v.onStep(1_000) // credited: 3
        assertEquals(3, v.onStep(7_000)) // 6 s gap: chain restarts, no credit
        assertEquals(3, v.onStep(7_500)) // second of new chain, provisional
        assertEquals(6, v.onStep(8_000)) // third: +3 retroactive
    }

    @Test
    fun `shake-speed intervals never credit a step`() {
        val v = StepChainValidator()
        var credited = 0
        for (t in 0..2_000L step 100) credited = v.onStep(t) // 100 ms apart, below min interval
        assertEquals(0, credited)
    }

    @Test
    fun `interval bounds are inclusive`() {
        val v = StepChainValidator()
        v.onStep(0)
        v.onStep(250) // exactly min
        assertEquals(3, v.onStep(2_250)) // exactly max (2000 ms later)
    }

    @Test
    fun `AC-2_3 the goal count is reached at the exact validated step`() {
        val goal = 30
        val v = StepChainValidator()
        var t = 0L
        var validated = 0
        var rawSteps = 0
        while (validated < goal) {
            validated = v.onStep(t)
            rawSteps++
            t += 600
        }
        assertEquals(goal, validated) // lands exactly on the goal, no overshoot
        assertEquals(goal, rawSteps)  // perfect rhythm: every raw step eventually counts
    }

    @Test
    fun `reset clears progress and rhythm`() {
        val v = StepChainValidator()
        v.onStep(0); v.onStep(500); v.onStep(1_000)
        v.reset()
        assertEquals(0, v.validatedSteps)
        assertEquals(0, v.onStep(1_500)) // first step of a brand-new chain
    }
}
