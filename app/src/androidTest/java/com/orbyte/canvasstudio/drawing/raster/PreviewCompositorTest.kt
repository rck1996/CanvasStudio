package com.orbyte.canvasstudio.drawing.raster

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orbyte.canvasstudio.drawing.LayerBlendMode
import com.orbyte.canvasstudio.drawing.SparseTileSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PreviewCompositorTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun previewComposesLayerMasksOffTheMainThread() {
        val root = File(context.cacheDir, "preview-compositor-test").apply {
            deleteRecursively()
            mkdirs()
        }
        val bottom = SparseTileSurface(64, 64, File(root, "bottom"), 2L * 1024 * 1024)
        val top = SparseTileSurface(64, 64, File(root, "top"), 2L * 1024 * 1024)
        val mask = SparseTileSurface(64, 64, File(root, "mask"), 2L * 1024 * 1024)
        val full = RectF(0f, 0f, 64f, 64f)
        val worker = Executors.newSingleThreadExecutor()
        try {
            assertTrue(bottom.draw(full) { it.drawColor(Color.RED) })
            assertTrue(top.draw(full) { it.drawColor(Color.BLUE) })
            assertTrue(mask.draw(RectF(0f, 0f, 32f, 64f)) { canvas ->
                canvas.drawRect(0f, 0f, 32f, 64f, Paint().apply { color = Color.BLACK })
            })
            val rendered = worker.submit<android.graphics.Bitmap> {
                PreviewCompositor().render(
                    64, 64,
                    listOf(
                        PreviewCompositor.Layer(bottom, null, true, 1f, LayerBlendMode.NORMAL, false, false),
                        PreviewCompositor.Layer(top, mask, true, 1f, LayerBlendMode.NORMAL, false, true),
                    ),
                )
            }.get(30, TimeUnit.SECONDS)
            try {
                assertEquals(64, rendered.width)
                assertEquals(Color.RED, rendered.getPixel(10, 20))
                assertEquals(Color.BLUE, rendered.getPixel(50, 20))
            } finally {
                rendered.recycle()
            }
        } finally {
            worker.shutdownNow()
            bottom.recycle()
            top.recycle()
            mask.recycle()
            root.deleteRecursively()
        }
    }

    @Test
    fun maskedMultiplyBlendsWithUnderlyingLayer() {
        val root = File(context.cacheDir, "preview-masked-multiply").apply { deleteRecursively(); mkdirs() }
        val bottom = SparseTileSurface(32, 32, File(root, "bottom"), 2L * 1024 * 1024)
        val top = SparseTileSurface(32, 32, File(root, "top"), 2L * 1024 * 1024)
        val mask = SparseTileSurface(32, 32, File(root, "mask"), 2L * 1024 * 1024)
        try {
            val bounds = RectF(0f, 0f, 32f, 32f)
            assertTrue(bottom.draw(bounds) { it.drawColor(Color.RED) })
            assertTrue(top.draw(bounds) { it.drawColor(Color.BLUE) })
            val bitmap = PreviewCompositor().render(32, 32, listOf(
                PreviewCompositor.Layer(bottom, null, true, 1f, LayerBlendMode.NORMAL, false, false),
                PreviewCompositor.Layer(top, mask, true, 1f, LayerBlendMode.MULTIPLY, false, true),
            ), includePaper = false)
            try { assertEquals(Color.BLACK, bitmap.getPixel(16, 16)) } finally { bitmap.recycle() }
        } finally {
            bottom.recycle(); top.recycle(); mask.recycle(); root.deleteRecursively()
        }
    }

    @Test
    fun clippingExcludesMissingTilesAndHiddenBase() {
        val root = File(context.cacheDir, "preview-clipping-tiles").apply { deleteRecursively(); mkdirs() }
        val base = SparseTileSurface(1024, 64, File(root, "base"), 4L * 1024 * 1024)
        val top = SparseTileSurface(1024, 64, File(root, "top"), 8L * 1024 * 1024)
        try {
            assertTrue(base.draw(RectF(0f, 0f, 512f, 64f)) { it.drawColor(Color.RED) })
            assertTrue(top.draw(RectF(0f, 0f, 1024f, 64f)) { it.drawColor(Color.BLUE) })
            fun render(baseVisible: Boolean) = PreviewCompositor().render(1024, 64, listOf(
                PreviewCompositor.Layer(base, null, baseVisible, 1f, LayerBlendMode.NORMAL, false, false),
                PreviewCompositor.Layer(top, null, true, 1f, LayerBlendMode.NORMAL, true, false),
            ), maxSize = 1024, includePaper = false)
            val visible = render(true)
            try {
                assertEquals(Color.BLUE, visible.getPixel(200, 32))
                assertEquals(Color.TRANSPARENT, visible.getPixel(800, 32))
            } finally { visible.recycle() }
            val hidden = render(false)
            try { assertEquals(Color.TRANSPARENT, hidden.getPixel(200, 32)) }
            finally { hidden.recycle() }
        } finally {
            base.recycle(); top.recycle(); root.deleteRecursively()
        }
    }

    @Test
    fun hiddenLayerIsNotComposited() {
        val root = File(context.cacheDir, "preview-compositor-hidden-test").apply {
            deleteRecursively()
            mkdirs()
        }
        val surface = SparseTileSurface(32, 32, root, 2L * 1024 * 1024)
        try {
            assertTrue(surface.draw(RectF(0f, 0f, 32f, 32f)) {
                it.drawColor(Color.BLACK)
            })
            val preview = PreviewCompositor().render(
                32, 32,
                listOf(
                    PreviewCompositor.Layer(surface, null, false, 1f, LayerBlendMode.NORMAL, false, false),
                ),
            )
            try {
                assertEquals(Color.WHITE, preview.getPixel(12, 12))
            } finally {
                preview.recycle()
            }
        } finally {
            surface.recycle()
            root.deleteRecursively()
        }
    }
}
