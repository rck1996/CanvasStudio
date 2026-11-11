package com.orbyte.canvasstudio.drawing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterHandoffGateTest {
    @Test
    fun itemsRemainVisibleUntilRequiredFramesHaveBeenPresented() {
        val gate = RasterHandoffGate<String>(3)
        gate.enqueue(listOf("stroke-a"))
        assertTrue(gate.hasPending())
        assertNull(gate.onRasterFramePresented())
        assertNull(gate.onRasterFramePresented())
        assertEquals(setOf("stroke-a"), gate.onRasterFramePresented())
        assertFalse(gate.hasPending())
        assertNull(gate.onRasterFramePresented())
    }

    @Test
    fun laterCompletionRestartsCountdownWithoutDroppingOlderStrokes() {
        val gate = RasterHandoffGate<String>(2)
        gate.enqueue(listOf("first"))
        assertNull(gate.onRasterFramePresented())
        gate.enqueue(listOf("second", "first"))
        assertNull(gate.onRasterFramePresented())
        assertEquals(setOf("first", "second"), gate.onRasterFramePresented())
        assertFalse(gate.hasPending())
    }

    @Test
    fun emptyCompletionDoesNotResetPresentationCountdown() {
        val gate = RasterHandoffGate<String>(2)
        gate.enqueue(listOf("stroke"))
        assertNull(gate.onRasterFramePresented())
        gate.enqueue(emptyList())
        assertEquals(setOf("stroke"), gate.onRasterFramePresented())
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonPositivePresentationWindowIsRejected() {
        RasterHandoffGate<String>(0)
    }
}
