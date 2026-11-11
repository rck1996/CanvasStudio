package com.orbyte.canvasstudio.ui.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorWheelGeometryTest {
    @Test
    fun wheelHueMatchesExpectedCompassPositions() {
        val right = colorWheelSelection(220f, 100f, 200f, 200f) as ColorWheelSelection.Hue
        val top = colorWheelSelection(100f, 0f, 200f, 200f) as ColorWheelSelection.Hue
        val left = colorWheelSelection(-2f, 100f, 200f, 200f) as ColorWheelSelection.Hue
        assertEquals(0f, right.degrees, .001f)
        assertEquals(270f, top.degrees, .001f)
        assertEquals(180f, left.degrees, .001f)
    }

    @Test
    fun squareSelectionClampsSaturationAndValue() {
        val center = colorWheelSelection(100f, 100f, 200f, 200f) as ColorWheelSelection.SaturationValue
        assertEquals(.5f, center.saturation, .001f)
        assertEquals(.5f, center.value, .001f)
        val white = colorWheelSelection(60f, 60f, 200f, 200f) as ColorWheelSelection.SaturationValue
        assertTrue(white.saturation < .05f && white.value > .95f)
        assertNull(colorWheelSelection(50f, 50f, 0f, 0f))
    }
}
