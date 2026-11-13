package com.orbyte.canvasstudio.drawing

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LargeCanvasExportTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun eightKDocumentWithSixSparseLayersExportsThroughThePngSnapshot() {
        val snapshot = onMain {
            DrawingView(context).apply {
                configureDocument(7680, 4320)
                brushSettings = BrushSettings(sizePx = 96f, pressureSize = false)
                repeat(5) { addLayer() }
                drawDot(this, 800f, 700f)
            }.captureExportSnapshot()
        }
        val output = ByteArrayOutputStream()
        snapshot.writePng(output, includePaper = false)
        val bytes = output.toByteArray()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        assertEquals(7680, bounds.outWidth)
        assertEquals(4320, bounds.outHeight)
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = 8 },
        ))
        try {
            assertTrue(Color.alpha(bitmap.getPixel(800 / 8, 700 / 8)) > 0)
            assertEquals(Color.TRANSPARENT, bitmap.getPixel(0, 0))
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun eightKOpenRasterStreamsEditableLayersAndMergedImage() {
        val snapshot = onMain {
            DrawingView(context).apply {
                configureDocument(7680, 4320)
                brushSettings = BrushSettings(sizePx = 96f, color = Color.BLUE, pressureSize = false)
                drawDot(this, 800f, 700f)
                addLayer()
                brushSettings = brushSettings.copy(color = Color.RED)
                drawDot(this, 7000f, 4200f)
                drawDot(this, 7200f, 4200f)
                drawDot(this, 7400f, 4200f)
                addMaskToActiveLayer()
                brushSettings = BrushSettings(kind = BrushKind.INK, sizePx = 96f, color = Color.BLACK,
                    pressureSize = false, hardness = 1f, taperStart = 0f, taperEnd = 0f,
                    renderProfile = BrushRenderProfile(BrushRenderMode.INTENSE_GLAZE, buildup = 1f))
                drawDot(this, 7200f, 4200f)
                brushSettings = brushSettings.copy(opacity = .5f)
                drawDot(this, 7400f, 4200f)
                finishMaskEditing()
                var layerId = ""
                onLayersChanged = { layers -> layerId = layers.first { it.isActive }.id }
                refreshLayerState()
                setLayerOpacity(layerId, .5f)
                toggleLayerVisibility(layerId)
            }.captureExportSnapshot()
        }
        val output = ByteArrayOutputStream()
        snapshot.writeOpenRaster(output)
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(output.toByteArray().inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }
        val xml = entries.getValue("stack.xml").decodeToString()
        assertTrue(xml.contains("visibility=\"hidden\" opacity=\"0.5\""))
        fun inspectPng(path: String, block: (android.graphics.Bitmap) -> Unit) {
            val bytes = entries.getValue(path)
            val bitmap = requireNotNull(BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = 8 },
            ))
            try {
                assertEquals(960, bitmap.width)
                assertEquals(540, bitmap.height)
                block(bitmap)
            } finally {
                bitmap.recycle()
            }
        }
        inspectPng("data/layer000.png") { bitmap ->
            val unmasked = Color.alpha(bitmap.getPixel(7000 / 8, 4200 / 8))
            assertTrue(unmasked > 127)
            assertEquals(Color.TRANSPARENT, bitmap.getPixel(7200 / 8, 4200 / 8))
            val partial = Color.alpha(bitmap.getPixel(7400 / 8, 4200 / 8))
            assertTrue("A soft mask must not erase all coverage", partial in 1 until unmasked)
        }
        inspectPng("mergedimage.png") { bitmap ->
            assertTrue(Color.alpha(bitmap.getPixel(800 / 8, 700 / 8)) > 0)
            assertEquals(Color.TRANSPARENT, bitmap.getPixel(7000 / 8, 4200 / 8))
        }
        assertTrue(entries.containsKey("Thumbnails/thumbnail.png"))
    }

    private fun drawDot(view: DrawingView, x: Float, y: Float) {
        val downTime = 30_000L
        MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0).also {
            view.onTouchEvent(it)
            it.recycle()
        }
        MotionEvent.obtain(downTime, downTime + 16L, MotionEvent.ACTION_UP, x, y, 0).also {
            view.onTouchEvent(it)
            it.recycle()
        }
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return requireNotNull(result).getOrThrow()
    }
}
