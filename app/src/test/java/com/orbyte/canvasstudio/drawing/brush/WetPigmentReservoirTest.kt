package com.orbyte.canvasstudio.drawing.brush

import com.orbyte.canvasstudio.drawing.BrushRenderProfile
import com.orbyte.canvasstudio.drawing.BrushSettings
import com.orbyte.canvasstudio.drawing.DrawingTool
import com.orbyte.canvasstudio.drawing.StrokePoint
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WetPigmentReservoirTest {
    private val loadedColor = 0x80303030.toInt()
    private val sourceColor = 0xFF3040F0.toInt()
    private val transparentColor = 0x00FF00FF
    private val settings = BrushSettings(
        sizePx = 24f,
        color = loadedColor,
        renderProfile = BrushRenderProfile(wetness = 0.8f, drag = 0.2f, colorPickup = 0.75f),
    )

    @Test
    fun carriedPigmentFadesWithDistanceOnBlankCanvas() {
        val reservoir = WetPigmentReservoir(settings)
        val pickedUp = reservoir.advance(point(0), sourceColor)
        val stationary = reservoir.advance(point(0), null)
        val near = reservoir.advance(point(8), null)
        val farther = reservoir.advance(point(40), transparentColor)
        val settled = reservoir.advance(point(1000), null)

        assertEquals(pickedUp, stationary)
        assertTrue(blue(pickedUp) > blue(near))
        assertTrue(blue(near) > blue(farther))
        assertTrue(blue(farther) > blue(loadedColor))
        assertEquals(loadedColor, settled)
        for (color in listOf(pickedUp, near, farther)) {
            assertEquals(loadedColor ushr 24, color ushr 24)
        }
    }

    @Test
    fun constantSourceProducesEquivalentColorsAtCoarseAndFineSampleDensities() {
        fun capture(step: Int): StrokePigmentTrace =
            StrokePigmentTrace.capture((0..48 step step).map(::point), settings) { sourceColor }

        val coarse = capture(step = 12)
        val fine = capture(step = 2)
        for (distance in 0..48 step 12) {
            val coarseColor = requireNotNull(coarse.colorAt(distance / 12))
            val fineColor = requireNotNull(fine.colorAt(distance / 2))
            assertTrue(blue(coarseColor) > blue(loadedColor))
            assertEquals(loadedColor ushr 24, coarseColor ushr 24)
            assertEquals(loadedColor ushr 24, fineColor ushr 24)
            for (shift in listOf(16, 8, 0)) {
                val coarseChannel = (coarseColor ushr shift) and 0xFF
                val fineChannel = (fineColor ushr shift) and 0xFF
                assertTrue(
                    "RGB channel at shift $shift differs at ${distance}px: $coarseChannel vs $fineChannel",
                    abs(coarseChannel - fineChannel) <= 1,
                )
            }
        }
    }

    @Test
    fun transparentAndAbsentSamplesPreserveUnmixedLoadedArgb() {
        val reservoir = WetPigmentReservoir(settings)

        assertEquals(loadedColor, reservoir.advance(point(0), transparentColor))
        assertEquals(loadedColor, reservoir.advance(point(24), null))
        assertEquals(loadedColor, reservoir.advance(point(48), transparentColor))
    }

    @Test
    fun captureKeepsPointIndicesAndStartsEachStrokeWithFreshPigment() {
        val points = listOf(point(0), point(12), point(24))
        val first = StrokePigmentTrace.capture(points, settings) {
            if (it == points[1]) sourceColor else transparentColor
        }
        val second = StrokePigmentTrace.capture(points, settings) { transparentColor }

        assertEquals(loadedColor, first.colorAt(0))
        val pickup = requireNotNull(first.colorAt(1))
        val carry = requireNotNull(first.colorAt(2))
        assertTrue(blue(pickup) > blue(carry))
        assertTrue(blue(carry) > blue(loadedColor))
        assertNull(first.colorAt(-1))
        assertNull(first.colorAt(points.size))
        assertSame(StrokePigmentTrace.Empty, second)
        assertNull(second.colorAt(0))
    }

    @Test
    fun onlyBrushWithBothWetnessAndPickupUsesWetPigment() {
        assertTrue(usesWetPigment(DrawingTool.BRUSH, settings))
        assertFalse(usesWetPigment(DrawingTool.ERASER, settings))
        assertFalse(
            usesWetPigment(
                DrawingTool.BRUSH,
                settings.copy(renderProfile = settings.renderProfile.copy(wetness = 0f)),
            ),
        )
        assertFalse(
            usesWetPigment(
                DrawingTool.BRUSH,
                settings.copy(renderProfile = settings.renderProfile.copy(colorPickup = 0f)),
            ),
        )
    }

    private fun point(x: Int) =
        StrokePoint(x = x.toFloat(), y = 0f, pressure = 1f, tilt = 0f, timestampMillis = 0L)

    private fun blue(color: Int): Int = color and 0xFF
}
