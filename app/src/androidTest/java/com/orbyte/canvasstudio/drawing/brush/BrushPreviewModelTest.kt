package com.orbyte.canvasstudio.drawing.brush

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbyte.canvasstudio.drawing.BrushPreset
import com.orbyte.canvasstudio.drawing.resolveBuiltInBrush
import com.orbyte.canvasstudio.drawing.toSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrushPreviewModelTest {
    @Test fun workspaceSwatchPreservesActualPigmentColor() {
        val pencil = requireNotNull(resolveBuiltInBrush("pencil-hb"))
        val bitmap = BrushPreviewModel.rasterizeSwatch(pencil, color = android.graphics.Color.RED)
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertTrue(pixels.any { it ushr 24 > 20 && android.graphics.Color.red(it) > 200 })
            assertTrue(pixels.none { it ushr 24 > 20 && android.graphics.Color.blue(it) > 5 })
        } finally {
            bitmap.recycle()
        }
    }
    @Test fun previewAcceleratesWithoutReversingTime() {
        val points = BrushFixture.points(BrushFixture.Scenario.PREVIEW)
        val steps = points.zipWithNext { from, to -> to.timestampMillis - from.timestampMillis }

        assertEquals(List(21) { 15L } + List(23) { 5L }, steps)
    }

    @Test fun rasterIsRepeatableAndReflectsTipRoundness() {
        val marker = requireNotNull(resolveBuiltInBrush("marker"))
        val round = marker.copy(tipProfile = marker.tipProfile.copy(roundness = 1f))
        val flat = marker.copy(tipProfile = marker.tipProfile.copy(roundness = .12f))

        withRaster(round) { first ->
            withRaster(round) { second -> assertTrue(first.sameAs(second)) }
            withRaster(flat) { changed -> assertFalse(first.sameAs(changed)) }
        }
    }

    @Test fun stabilizationChangesGeometryWithoutRewritingPenDynamics() {
        val ink = requireNotNull(resolveBuiltInBrush("technical-ink"))
        val responsive = ink.copy(stabilization = 0f)
        val stabilized = ink.copy(stabilization = .92f)
        val raw = BrushFixture.points(BrushFixture.Scenario.PREVIEW)
        val quickPoints = BrushPreviewModel.samplePreviewStroke(responsive.toSettings())
        val stablePoints = BrushPreviewModel.samplePreviewStroke(stabilized.toSettings())
        assertEquals(raw.first(), stablePoints.first())
        assertTrue("The same fixture should visibly lag at high stabilization",
            quickPoints.last().x - stablePoints.last().x > 30f)
        val rawByTime = raw.associateBy { it.timestampMillis }
        stablePoints.forEach { sample ->
            val original = rawByTime.getValue(sample.timestampMillis)
            assertEquals(original, sample.copy(x = original.x, y = original.y))
        }

        val quick = BrushPreviewModel.rasterizeSwatch(responsive)
        val stable = BrushPreviewModel.rasterizeSwatch(stabilized)
        try {
            assertFalse("Stabilization was ignored by the rendered preview", quick.sameAs(stable))
        } finally {
            quick.recycle()
            stable.recycle()
        }
    }

    @Test fun largeSoftBrushFitsWithinRasterWithTransparentMargins() {
        val airbrush = requireNotNull(resolveBuiltInBrush("airbrush"))
        withRaster(airbrush.copy(sizePx = 600f, hardness = 0f)) { bitmap ->
            assertTrue(bitmap.width in 3..BrushPreviewModel.MAX_BITMAP_SIDE)
            assertTrue(bitmap.height in 3..BrushPreviewModel.MAX_BITMAP_SIDE)
            val row = IntArray(bitmap.width)
            val column = IntArray(bitmap.height)
            for (y in listOf(0, bitmap.height - 1)) {
                bitmap.getPixels(row, 0, row.size, 0, y, row.size, 1)
                assertTrue("Visible ink on horizontal edge $y", row.all { it ushr 24 == 0 })
            }
            for (x in listOf(0, bitmap.width - 1)) {
                bitmap.getPixels(column, 0, 1, x, 0, 1, column.size)
                assertTrue("Visible ink on vertical edge $x", column.all { it ushr 24 == 0 })
            }
            bitmap.getPixels(row, 0, row.size, 0, bitmap.height / 2, row.size, 1)
            assertTrue(row.any { it ushr 24 > 0 })
        }
    }

    @Test fun swatchKeepsRelativeStrokeThicknessInAFixedFrame() {
        val marker = requireNotNull(resolveBuiltInBrush("marker"))
        val round = marker.copy(tipProfile = marker.tipProfile.copy(roundness = 1f))

        fun centerColumnInk(sizePx: Float): Int {
            val bitmap = BrushPreviewModel.rasterizeSwatch(round.copy(sizePx = sizePx))
            try {
                assertEquals(768, bitmap.width)
                assertEquals(240, bitmap.height)
                val column = IntArray(bitmap.height)
                bitmap.getPixels(column, 0, 1, bitmap.width / 2, 0, 1, column.size)
                assertEquals(0, column.first() ushr 24)
                assertEquals(0, column.last() ushr 24)
                return column.count { it ushr 24 > 0 }
            } finally {
                bitmap.recycle()
            }
        }

        val thin = centerColumnInk(24f)
        val thick = centerColumnInk(96f)
        assertTrue("The thinner stroke should be visible", thin > 0)
        assertTrue("Expected a thicker silhouette in the same frame: $thin vs $thick", thick > thin * 2)
    }

    @Test fun opacityAndFlowChangeInkCoverageAcrossRepresentativeSwatches() {
        listOf("pencil-hb", "technical-ink", "granulated-watercolor", "bristle", "impasto-bristle")
            .forEach { id ->
                val preset = requireNotNull(resolveBuiltInBrush(id)).copy(sizePx = 56f)
                val solid = swatchCoverage(preset.copy(opacity = 1f, flow = 1f))
                val lowOpacity = swatchCoverage(preset.copy(opacity = .06f, flow = 1f))
                val lowFlow = swatchCoverage(preset.copy(opacity = 1f, flow = .06f))
                assertTrue("$id must remain visible at low opacity: $lowOpacity", lowOpacity > 0)
                assertTrue("$id opacity should change coverage: $lowOpacity vs $solid", solid > lowOpacity)
                assertTrue("$id flow should change coverage: $lowFlow vs $solid", solid > lowFlow)
            }
    }

    @Test fun pressureComparisonKeepsTheFrameAndShowsPencilResponse() {
        val pencil = requireNotNull(resolveBuiltInBrush("pencil-hb")).copy(sizePx = 56f)
        val soft = swatchCoverage(pencil, BrushPreviewModel.Pressure.SOFT)
        val firm = swatchCoverage(pencil, BrushPreviewModel.Pressure.FIRM)
        assertTrue("Firm pressure should leave more graphite: $soft vs $firm", soft > 0 && firm > soft)

        val unresponsive = pencil.copy(pressureSize = false, pressureOpacity = false)
        assertEquals(
            swatchCoverage(unresponsive, BrushPreviewModel.Pressure.SOFT),
            swatchCoverage(unresponsive, BrushPreviewModel.Pressure.FIRM),
        )
    }

    private fun swatchCoverage(
        preset: BrushPreset,
        pressure: BrushPreviewModel.Pressure = BrushPreviewModel.Pressure.NATURAL,
    ): Long {
        val bitmap = BrushPreviewModel.rasterizeSwatch(preset, pressure)
        try {
            assertEquals(768, bitmap.width)
            assertEquals(240, bitmap.height)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            return pixels.sumOf { (it ushr 24).toLong() }
        } finally {
            bitmap.recycle()
        }
    }

    private inline fun withRaster(preset: BrushPreset, check: (Bitmap) -> Unit) {
        val bitmap = BrushPreviewModel.rasterize(preset)
        try {
            check(bitmap)
        } finally {
            bitmap.recycle()
        }
    }
}
