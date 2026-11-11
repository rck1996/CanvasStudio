package com.orbyte.canvasstudio.drawing.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeFrameBudgetTest {
    @Test
    fun startsBoundedAndNeverConsumesBeyondPending() {
        val budget = StrokeFrameBudget()
        assertEquals(0, budget.nextSegments(0))
        assertEquals(2, budget.nextSegments(2))
        assertEquals(12, budget.nextSegments(1000))
    }

    @Test
    fun expensiveFrameReducesWorkAndCheapFrameRestoresThroughput() {
        val budget = StrokeFrameBudget()
        budget.record(12, 12_000_000L)
        assertEquals(3, budget.nextSegments(100))
        repeat(24) { budget.record(16, 160_000L) }
        assertTrue(budget.nextSegments(100) > 3)
        assertTrue(budget.nextSegments(100) <= 32)
    }

    @Test
    fun ignoresInvalidMeasurementsAndCapsPathologicalLatency() {
        val budget = StrokeFrameBudget()
        budget.record(0, 100_000_000L)
        budget.record(12, -1L)
        assertEquals(12, budget.nextSegments(50))
        budget.record(1, Long.MAX_VALUE)
        assertEquals(1, budget.nextSegments(50))
    }
}
