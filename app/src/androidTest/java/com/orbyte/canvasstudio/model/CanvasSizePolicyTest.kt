package com.orbyte.canvasstudio.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanvasSizePolicyTest {
    @Test
    fun newCanvasLimitsMatchDeviceMemoryAndExportCost() {
        assertEquals(40_000_000L, recommendedNewCanvasPixels(512))
        assertEquals(26_000_000L, recommendedNewCanvasPixels(256))
        assertEquals(12_000_000L, recommendedNewCanvasPixels(128))

        val highEnd8k = constrainCanvasSize(
            7680,
            4320,
            recommendedNewCanvasPixels(512),
            MAX_NEW_CANVAS_DIMENSION,
        )
        assertEquals(7680 to 4320, highEnd8k)

        val constrained = constrainCanvasSize(
            16_384,
            16_384,
            recommendedNewCanvasPixels(256),
            MAX_NEW_CANVAS_DIMENSION,
        )
        assertTrue(constrained.first <= MAX_NEW_CANVAS_DIMENSION)
        assertTrue(constrained.second <= MAX_NEW_CANVAS_DIMENSION)
        assertTrue(constrained.first.toLong() * constrained.second <= 26_000_000L)

        val footprint = estimateCanvasFootprint(7680, 4320)
        assertEquals(135, footprint.tileCount)
        assertTrue(footprint.flattenedRgbaMiB in 126..127)
        assertEquals("Exigente", footprint.level)
    }
}
