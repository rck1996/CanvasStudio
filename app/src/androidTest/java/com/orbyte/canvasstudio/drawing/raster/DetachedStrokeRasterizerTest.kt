package com.orbyte.canvasstudio.drawing.raster

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orbyte.canvasstudio.drawing.BrushKind
import com.orbyte.canvasstudio.drawing.BrushSettings
import com.orbyte.canvasstudio.drawing.DrawingTool
import com.orbyte.canvasstudio.drawing.DrawingView
import com.orbyte.canvasstudio.drawing.SparseTileSurface
import com.orbyte.canvasstudio.drawing.StrokeCommand
import com.orbyte.canvasstudio.drawing.StrokePoint
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DetachedStrokeRasterizerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun offThreadPreparedStrokePublishesSamePixelsAsDocumentReplay() {
        val dir = directory("pixel-parity")
        val surface = SparseTileSurface(768, 768, dir, 4L * 1024L * 1024L)
        val settings = BrushSettings(
            kind = BrushKind.INK, sizePx = 32f,
            color = Color.rgb(24, 68, 191), pressureSize = true,
            pressureOpacity = true, opacity = 0.85f,
            scatter = 0.12f, grain = 0.35f,
        )
        val points = listOf(
            StrokePoint(200f, 170f, .45f, .15f, 11L, .2f),
            StrokePoint(390f, 310f, .72f, .35f, 31L, .34f),
            StrokePoint(545f, 490f, .62f, .5f, 54L, .4f),
        )
        val command = StrokeCommand(points = points, tool = DrawingTool.BRUSH, settings = settings)
        val worker = Executors.newSingleThreadExecutor()
        try {
            val prepared = worker.submit<DetachedStrokeRasterizer.PreparedBatch?> {
                DetachedStrokeRasterizer().use { it.prepare(surface, command) }
            }.get(40, TimeUnit.SECONDS)
            assertNotNull(prepared)
            val batch = requireNotNull(prepared)
            batch.use {
                assertTrue("Revision must be unchanged", it.publish())
                assertTrue(it.tileCount > 1)
            }
            instrumentation.runOnMainSync {
                val view = DrawingView(context)
                view.configureDocument(768, 768)
                view.brushSettings = settings
                view.debugDrawStrokeForTest(points)
                for (y in 0 until 768 step 7) for (x in 0 until 768 step 7) {
                    assertEquals(
                        "Raster pixel mismatch at $x,$y",
                        view.debugPixelForTest(x.toFloat(), y.toFloat()),
                        surface.samplePixel(x.toFloat(), y.toFloat()) ?: Color.TRANSPARENT,
                    )
                }
            }
        } finally {
            worker.shutdownNow()
            surface.recycle()
            dir.deleteRecursively()
        }
    }

    @Test
    fun interveningRevisionPreventsPartialPublication() {
        val dir = directory("stale")
        val surface = SparseTileSurface(512, 512, dir, 2L * 1024L * 1024L)
        val command = StrokeCommand(
            points = listOf(StrokePoint(170f, 170f, 1f, 0f, 12L)),
            tool = DrawingTool.BRUSH,
            settings = BrushSettings(kind = BrushKind.INK, sizePx = 40f, color = Color.BLUE),
        )
        try {
            DetachedStrokeRasterizer().use { renderer ->
                val batch = requireNotNull(renderer.prepare(surface, command))
                batch.use {
                    val newer = RectF(0f, 0f, 18f, 18f)
                    assertTrue(surface.draw(newer) { canvas ->
                        canvas.drawRect(newer, Paint().apply { color = Color.RED })
                    })
                    assertFalse("Old result must not overwrite a newer paint", it.publish())
                    assertEquals(Color.RED, surface.samplePixel(10f, 10f))
                    assertEquals(Color.TRANSPARENT, surface.samplePixel(170f, 170f))
                }
            }
        } finally {
            surface.recycle()
            dir.deleteRecursively()
        }
    }

    @Test
    fun alphaLockedBatchLeavesTransparentRegionsUnchanged() {
        val dir = directory("alpha-lock")
        val surface = SparseTileSurface(512, 512, dir, 2L * 1024L * 1024L)
        val anchor = RectF(160f, 160f, 260f, 260f)
        val command = StrokeCommand(
            points = listOf(StrokePoint(200f, 200f, 1f, 0f, 1L)),
            tool = DrawingTool.BRUSH,
            settings = BrushSettings(kind = BrushKind.INK, color = Color.BLUE, sizePx = 38f),
        )
        try {
            assertTrue(surface.draw(anchor) {
                it.drawRect(anchor, Paint().apply { color = Color.RED })
            })
            DetachedStrokeRasterizer().use { renderer ->
                requireNotNull(renderer.prepare(surface, command, preserveAlpha = true)).use {
                    assertTrue(it.publish())
                }
            }
            assertEquals(0, Color.alpha(surface.samplePixel(100f, 100f) ?: 0))
            assertEquals(255, Color.alpha(surface.samplePixel(200f, 200f) ?: 0))
            assertEquals(Color.RED, surface.samplePixel(160f, 160f))
        } finally {
            surface.recycle()
            dir.deleteRecursively()
        }
    }

    private fun directory(name: String): File =
        File(context.cacheDir, "detached-raster-$name-${UUID.randomUUID()}").apply { mkdirs() }
}
