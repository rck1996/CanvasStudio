package com.orbyte.canvasstudio.drawing.brush

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbyte.canvasstudio.drawing.BrushPreset
import com.orbyte.canvasstudio.drawing.resolveBuiltInBrush
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrushPreviewModelTest {
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

    private inline fun withRaster(preset: BrushPreset, check: (Bitmap) -> Unit) {
        val bitmap = BrushPreviewModel.rasterize(preset)
        try {
            check(bitmap)
        } finally {
            bitmap.recycle()
        }
    }
}
