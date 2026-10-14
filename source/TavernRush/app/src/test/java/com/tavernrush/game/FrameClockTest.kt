package com.tavernrush.game

import com.tavernrush.game.core.FrameClock
import com.tavernrush.game.core.LoopGeneration
import org.junit.Assert.*
import org.junit.Test

class FrameClockTest {
    @Test fun fastSoftwareFramesNeverAccelerateGameTime() {
        val clock = FrameClock()
        var simulated = 0.0
        repeat(2000) { i ->
            simulated += clock.advance(0.001, 60f)
            assertTrue("Логика не должна обгонять часы", simulated <= (i + 1) * 0.001 + 0.000002)
        }
        assertEquals(2.0, simulated, 0.00001)
    }

    @Test fun supportedRefreshRatesPreserveElapsedTime() {
        for (hz in floatArrayOf(30f, 60f, 90f, 120f, 144f, 240f)) {
            val clock = FrameClock()
            var simulated = 0.0
            repeat(6000) { simulated += clock.advance(1.0 / 6000.0, hz) }
            assertEquals("Дисплей $hz Гц", 1.0, simulated, 0.00001)
        }
    }

    @Test fun aLongDriverStallDoesNotCreateCatchupDebt() {
        val clock = FrameClock()
        assertEquals(0.1f, clock.advance(20.0, 60f), 0.000001f)
        assertEquals(0f, clock.advance(0.001, 60f), 0f)
    }

    @Test fun invalidClockInputCannotPoisonAccumulator() {
        val clock = FrameClock()
        for (v in doubleArrayOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0, 0.0))
            assertEquals(0f, clock.advance(v, 60f), 0f)
        assertEquals(1f / 60f, clock.advance(1.0 / 60.0, 60f), 0.000001f)
    }

    @Test fun resetDropsOnlyUnconsumedTime() {
        val clock = FrameClock()
        assertEquals(0f, clock.advance(0.01, 60f), 0f)
        clock.reset()
        assertEquals(0f, clock.advance(0.01, 60f), 0f)
        assertEquals(1f / 60f, clock.advance(0.007, 60f), 0.000001f)
    }

    @Test fun invalidRefreshRateUsesSafeSixtyHz() {
        for (hz in floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, 1000f)) {
            val clock = FrameClock()
            assertEquals(1f / 60f, clock.advance(1.0 / 60.0, hz), 0.000001f)
            assertTrue(clock.waitMillis(hz) in 1L..8L)
        }
    }

    @Test fun retiredThreadTokenNeverBecomesCurrentAfterRestart() {
        val guard = LoopGeneration()
        val old = guard.next()
        assertTrue(guard.isCurrent(old))
        guard.invalidate()
        assertFalse(guard.isCurrent(old))
        val fresh = guard.next()
        assertTrue(guard.isCurrent(fresh))
        assertFalse(guard.isCurrent(old))
    }
}