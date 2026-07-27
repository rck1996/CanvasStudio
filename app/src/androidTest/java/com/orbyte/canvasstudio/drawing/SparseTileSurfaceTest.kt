package com.orbyte.canvasstudio.drawing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SparseTileSurfaceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun twoHundredThickStrokesSurviveFlushEvictionAndReload() {
        val directory = freshDirectory("massive-strokes")
        val points = uniqueStrokePoints(count = 200)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        val surface = SparseTileSurface(
            width = DOCUMENT_WIDTH,
            height = DOCUMENT_HEIGHT,
            workingDirectory = directory,
            cacheBudgetBytes = 1L * 1024L * 1024L,
        )
        points.forEachIndexed { index, point ->
            val radius = 18f + (index % 4) * 7f
            assertTrue(
                surface.draw(RectF(point.x - radius, point.y - radius, point.x + radius, point.y + radius)) {
                    it.drawCircle(point.x, point.y, radius, paint)
                },
            )
        }
        assertTrue(surface.flushPending())
        assertEquals(200, points.size)
        assertTrue(surface.stats().storedTiles > 1)
        surface.recycle()

        val reopened = SparseTileSurface(
            width = DOCUMENT_WIDTH,
            height = DOCUMENT_HEIGHT,
            workingDirectory = directory,
            cacheBudgetBytes = 1L * 1024L * 1024L,
        )
        points.forEachIndexed { index, point ->
            assertTrue("Stroke ${index + 1} disappeared after reload", reopened.isOpaqueAt(point.x, point.y))
        }
        assertTrue("Reload should exercise disk-backed tiles", reopened.stats().diskLoads > 1)
        reopened.recycle()
        directory.deleteRecursively()
    }

    @Test
    fun visibleStoredTileIsReportedMissingUntilPrefetched() {
        val directory = freshDirectory("visible-prefetch")
        val bounds = RectF(700f, 700f, 760f, 760f)
        val surface = SparseTileSurface(2048, 2048, directory, 1L * 1024L * 1024L)
        assertTrue(surface.draw(bounds) { canvas ->
            canvas.drawRect(bounds, Paint().apply { color = Color.MAGENTA })
        })
        assertTrue(surface.flushPending())
        surface.recycle()

        val reopened = SparseTileSurface(2048, 2048, directory, 1L * 1024L * 1024L)
        assertTrue(reopened.hasMissingVisibleTiles(bounds))
        assertEquals(1, reopened.prefetch(bounds))
        assertFalse(reopened.hasMissingVisibleTiles(bounds))
        assertTrue(reopened.isOpaqueAt(730f, 730f))
        reopened.recycle()
        directory.deleteRecursively()
    }

    private fun SparseTileSurface.isOpaqueAt(x: Float, y: Float): Boolean {
        val sample = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        return try {
            drawAtPoint(Canvas(sample), x, y, null)
            Color.alpha(sample.getPixel(0, 0)) > 0
        } finally {
            sample.recycle()
        }
    }

    private fun uniqueStrokePoints(count: Int): List<Point> = buildList(count) {
        repeat(10) { row ->
            repeat(20) { column ->
                add(Point(120f + column * 195f, 120f + row * 270f))
            }
        }
    }.take(count)

    private fun freshDirectory(name: String): File =
        File(context.cacheDir, "sparse-tile-tests/$name").apply {
            deleteRecursively()
            mkdirs()
        }

    private data class Point(val x: Float, val y: Float)

    private companion object {
        const val DOCUMENT_WIDTH = 4096
        const val DOCUMENT_HEIGHT = 2732
    }
}
