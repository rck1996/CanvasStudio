package com.orbyte.canvasstudio.drawing

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SparseTileScratchTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun alphaLockKeepsTransparentPixelsUnchangedAcrossRepeatedStamps() {
        val dir = File(context.cacheDir, "alpha-lock-reuse-test").apply { deleteRecursively(); mkdirs() }
        val surface = SparseTileSurface(512, 512, dir, 4L * 1024 * 1024)
        try {
            val base = Paint().apply { color = Color.RED }
            assertTrue(surface.draw(RectF(85f, 85f, 230f, 230f)) {
                it.drawCircle(160f, 160f, 50f, base)
            })
            assertTrue(surface.drawPreservingAlpha(RectF(0f, 0f, 512f, 512f)) {
                it.drawColor(Color.BLUE)
            })
            assertEquals(Color.BLUE, surface.samplePixel(160f, 160f))
            assertEquals(0, Color.alpha(surface.samplePixel(5f, 5f) ?: 0))
            val green = Paint().apply { color = Color.GREEN }
            assertTrue(surface.drawPreservingAlpha(RectF(100f, 100f, 240f, 240f)) {
                it.drawCircle(160f, 160f, 8f, green)
            })
            assertEquals(Color.GREEN, surface.samplePixel(160f, 160f))
            assertEquals(Color.BLUE, surface.samplePixel(195f, 160f))
            assertEquals(0, Color.alpha(surface.samplePixel(5f, 5f) ?: 0))
            assertTrue(surface.flushPending())
        } finally {
            surface.recycle()
            dir.deleteRecursively()
        }
    }

    @Test
    fun rasterPixelArrayIsReusedAndRemainsExactlySizedForJni() {
        val dir = File(context.cacheDir, "jni-buffer-reuse-test").apply { deleteRecursively(); mkdirs() }
        val surface = SparseTileSurface(600, 600, dir, 4L * 1024 * 1024)
        try {
            var previous: IntArray? = null
            val bounds = RectF(8f, 8f, 18f, 18f)
            assertEquals(1, surface.mutateTilePixels(bounds, true) { _, rect, pixels ->
                assertEquals(rect.width() * rect.height(), pixels.size)
                pixels[0] = Color.RED
                previous = pixels
                true
            })
            assertEquals(1, surface.mutateTilePixels(bounds, true) { _, rect, pixels ->
                assertEquals(rect.width() * rect.height(), pixels.size)
                assertSame(previous, pixels)
                pixels[0] = Color.BLUE
                true
            })
            assertEquals(Color.BLUE, surface.samplePixel(0f, 0f))
            assertEquals(1, surface.mutateTilePixels(RectF(550f, 550f, 560f, 560f), true) { _, rect, pixels ->
                assertEquals(rect.width() * rect.height(), pixels.size)
                true
            })
        } finally {
            surface.recycle()
            dir.deleteRecursively()
        }
    }
}
