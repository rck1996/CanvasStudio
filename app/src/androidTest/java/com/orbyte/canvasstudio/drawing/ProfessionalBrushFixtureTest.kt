package com.orbyte.canvasstudio.drawing

import androidx.test.ext.junit.runners.AndroidJUnit4
import android.util.Log
import com.orbyte.canvasstudio.drawing.brush.BrushFixture
import com.orbyte.canvasstudio.drawing.brush.BrushDabBatchBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfessionalBrushFixtureTest {
    private fun preset(id: String) = requireNotNull(resolveBuiltInBrush(id)).toSettings()

    @Test fun sameSeedAndTrajectoryAreDeterministic() {
        val settings = preset("pencil-hb")
        val first = BrushFixture.evaluate(settings, BrushFixture.Scenario.CURVE)
        val second = BrushFixture.evaluate(settings, BrushFixture.Scenario.CURVE)
        assertEquals(BrushFixture.stableHash(first), BrushFixture.stableHash(second))
    }

    @Test fun hbAnd6bHaveDistinctPressureAndTiltResponses() {
        val hb = BrushFixture.evaluate(preset("pencil-hb"), BrushFixture.Scenario.TILT_SHADING)
        val sixB = BrushFixture.evaluate(preset("pencil-6b"), BrushFixture.Scenario.TILT_SHADING)
        assertNotEquals(BrushFixture.stableHash(hb), BrushFixture.stableHash(sixB))
        assertTrue(sixB.last().radiusX > hb.last().radiusX)
        assertTrue(sixB.map { it.opacity }.average() > hb.map { it.opacity }.average())
    }

    @Test fun technicalInkIsStableAcrossPressureAndSpeed() {
        val settings = preset("technical-ink")
        val slow = BrushFixture.evaluate(settings, BrushFixture.Scenario.SLOW_PRESSURE)
        val fast = BrushFixture.evaluate(settings, BrushFixture.Scenario.FAST_LINE)
        val stableCore = slow.drop(3).dropLast(3)
        assertTrue(stableCore.maxOf { it.radiusX } - stableCore.minOf { it.radiusX } < .05f)
        assertTrue(fast.maxOf { it.opacity } - fast.minOf { it.opacity } < .01f)
    }

    @Test fun allPriorityBrushesStayInsideDefensiveDabBounds() {
        val ids = listOf("pencil-hb", "pencil-6b", "technical-ink", "comic-nib", "flat-marker", "granulated-watercolor")
        ids.forEach { id ->
            val dabs = BrushFixture.evaluate(preset(id), BrushFixture.Scenario.FOUR_TILES)
            assertTrue(id, dabs.size <= 64)
            assertTrue(id, dabs.all { it.radiusX in .1f..512f && it.radiusY in .1f..512f })
        }
    }

    @Test fun comicNibHasPressureVariationAndCleanEndpointTaper() {
        val settings = preset("comic-nib")
        val dabs = BrushDabBatchBuilder.build(
            BrushFixture.points(BrushFixture.Scenario.SLOW_PRESSURE),
            settings,
            DrawingTool.BRUSH,
        )
        val middle = dabs[dabs.size / 2]
        assertTrue(middle.radiusX > dabs.first().radiusX * 2f)
        assertTrue(middle.radiusX > dabs.last().radiusX * 2f)
        assertTrue(dabs.all { it.opacity > .9f })
    }

    @Test fun markerOrientationChangesTipAxisWithoutChangingFlow() {
        val settings = preset("flat-marker")
        val dabs = BrushFixture.evaluate(settings, BrushFixture.Scenario.TILT_SHADING)
        assertTrue(dabs.zipWithNext().any { (a, b) -> kotlin.math.abs(a.rotationRadians - b.rotationRadians) > .01f })
        assertTrue(dabs.maxOf { it.flow } - dabs.minOf { it.flow } < .45f)
        assertTrue(dabs.all { it.radiusX > it.radiusY })
    }

    @Test fun deterministicFixtureMatrixPublishesReferenceHashes() {
        val ids = listOf("pencil-hb", "pencil-6b", "technical-ink", "comic-nib", "flat-marker", "granulated-watercolor")
        ids.forEach { id ->
            val settings = preset(id)
            val hashes = BrushFixture.Scenario.entries.associateWith { scenario ->
                BrushFixture.stableHash(BrushFixture.evaluate(settings, scenario))
            }
            assertEquals(hashes, BrushFixture.Scenario.entries.associateWith { scenario ->
                BrushFixture.stableHash(BrushFixture.evaluate(settings, scenario))
            })
            Log.i("CanvasStudioBrushFixtures", "$id $hashes")
        }
    }
}
