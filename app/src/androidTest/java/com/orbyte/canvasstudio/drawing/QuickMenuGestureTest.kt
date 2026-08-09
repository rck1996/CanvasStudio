package com.orbyte.canvasstudio.drawing

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickMenuGestureTest {
    @Test
    fun flickDirectionsMapToTheSixClockwiseSlots() {
        val threshold = 40f
        assertEquals(0, radialQuickMenuSlot(0f, -100f, threshold))
        assertEquals(1, radialQuickMenuSlot(87f, -50f, threshold))
        assertEquals(2, radialQuickMenuSlot(87f, 50f, threshold))
        assertEquals(3, radialQuickMenuSlot(0f, 100f, threshold))
        assertEquals(4, radialQuickMenuSlot(-87f, 50f, threshold))
        assertEquals(5, radialQuickMenuSlot(-87f, -50f, threshold))
        assertNull(radialQuickMenuSlot(10f, 10f, threshold))
    }
}
