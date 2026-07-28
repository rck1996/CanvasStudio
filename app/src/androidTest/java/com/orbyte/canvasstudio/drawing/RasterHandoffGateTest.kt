package com.orbyte.canvasstudio.drawing

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RasterHandoffGateTest {
    @Test
    fun previewIsRemovedOnlyAfterTwoPresentedRasterFrames() {
        val gate = RasterHandoffGate<String>(presentationFrames = 2)

        gate.enqueue(listOf("stroke-1"))

        assertTrue(gate.hasPending())
        assertNull(gate.onRasterFramePresented())
        assertEquals(setOf("stroke-1"), gate.onRasterFramePresented())
        assertFalse(gate.hasPending())
    }

    @Test
    fun newlyFinishedStrokeRestartsCountdownAndRemovesBurstTogether() {
        val gate = RasterHandoffGate<String>(presentationFrames = 2)

        gate.enqueue(listOf("stroke-1"))
        assertNull(gate.onRasterFramePresented())
        gate.enqueue(listOf("stroke-2"))

        assertNull(gate.onRasterFramePresented())
        assertEquals(setOf("stroke-1", "stroke-2"), gate.onRasterFramePresented())
        assertFalse(gate.hasPending())
    }
}
