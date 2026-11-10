package com.orbyte.canvasstudio.ui.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickBrushSizingTest {
    @Test
    fun sizeSliderRoundTripsEveryRepresentativeSize() {
        listOf(2f, 3f, 5f, 12f, 24f, 48f, 96f, 180f).forEach { size ->
            assertEquals(size, QuickBrushSizing.sizeFromProgress(QuickBrushSizing.sizeProgress(size)), 0.002f)
        }
        assertEquals(0f, QuickBrushSizing.sizeProgress(-1f), 0f)
        assertEquals(180f, QuickBrushSizing.sizeFromProgress(4f), 0f)
    }

    @Test
    fun precisionIsConcentratedAtSmallBrushSizes() {
        val smallStep = QuickBrushSizing.sizeFromProgress(.2f) - QuickBrushSizing.sizeFromProgress(.1f)
        val largeStep = QuickBrushSizing.sizeFromProgress(.9f) - QuickBrushSizing.sizeFromProgress(.8f)
        assertTrue(largeStep > smallStep * 10f)
        assertTrue(QuickBrushSizing.sizeFromProgress(.5f) < 24f)
    }

    @Test
    fun opacitySliderHandlesActualFivePercentMinimum() {
        listOf(.05f, .2f, .5f, .75f, 1f).forEach { opacity ->
            assertEquals(opacity, QuickBrushSizing.opacityFromProgress(QuickBrushSizing.opacityProgress(opacity)), 0.00001f)
        }
        assertEquals(.05f, QuickBrushSizing.opacityFromProgress(-1f), 0f)
        assertEquals(1f, QuickBrushSizing.opacityFromProgress(2f), 0f)
    }
}
