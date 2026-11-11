package com.orbyte.canvasstudio.drawing.raster

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orbyte.canvasstudio.drawing.BasicPsdCodec
import com.orbyte.canvasstudio.drawing.BrushKind
import com.orbyte.canvasstudio.drawing.BrushSettings
import com.orbyte.canvasstudio.drawing.DrawingView
import com.orbyte.canvasstudio.drawing.StrokePoint
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentExportSnapshotTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun testView(): DrawingView = DrawingView(context).apply {
        configureDocument(512, 512)
        brushSettings = BrushSettings(
            kind = BrushKind.INK,
            color = Color.rgb(64, 120, 235),
            sizePx = 46f,
            opacity = 1f,
        )
        debugDrawStrokeForTest(
            listOf(
                StrokePoint(150f, 150f, 1f, 0f, 1L),
                StrokePoint(360f, 300f, 1f, 0f, 2L),
            ),
        )
    }

    @Test
    fun detachedPngPsdOraRemainReadableAndIncludeTheSameStroke() {
        lateinit var snapshot: DocumentExportSnapshot
        instrumentation.runOnMainSync { snapshot = testView().captureExportSnapshot() }

        val png = ByteArrayOutputStream()
        snapshot.writePng(png)
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(png.toByteArray(), 0, png.size()))
        try {
            assertEquals(512, bitmap.width)
            assertEquals(512, bitmap.height)
            assertEquals(Color.WHITE, bitmap.getPixel(5, 5))
            assertTrue(Color.alpha(bitmap.getPixel(150, 150)) > 0)
            assertFalse(bitmap.getPixel(150, 150) == Color.WHITE)
        } finally {
            bitmap.recycle()
        }

        val psd = ByteArrayOutputStream()
        snapshot.writeBasicPsd(psd)
        val decodedPsd = BasicPsdCodec.read(ByteArrayInputStream(psd.toByteArray()))
        try {
            assertEquals(512, decodedPsd.width)
            assertEquals(512, decodedPsd.height)
            assertEquals(0, Color.alpha(decodedPsd.getPixel(5, 5)))
            assertTrue(Color.alpha(decodedPsd.getPixel(150, 150)) > 0)
        } finally {
            decodedPsd.recycle()
        }

        val ora = ByteArrayOutputStream()
        snapshot.writeOpenRaster(ora)
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(ora.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        assertEquals("image/openraster", entries["mimetype"]?.toString(Charsets.US_ASCII))
        assertTrue(entries["stack.xml"]?.toString(Charsets.UTF_8)?.contains("Canvas Studio") == true)
        assertTrue(entries.keys.any { it.startsWith("data/layer") && it.endsWith(".png") })
        val mergedBytes = requireNotNull(entries["mergedimage.png"])
        val merged = requireNotNull(BitmapFactory.decodeByteArray(mergedBytes, 0, mergedBytes.size))
        try {
            assertEquals(0, Color.alpha(merged.getPixel(5, 5)))
            assertTrue(Color.alpha(merged.getPixel(150, 150)) > 0)
        } finally {
            merged.recycle()
        }
    }

    @Test
    fun editingPixelsAfterCaptureRejectsExportWithoutDestinationBytes() {
        lateinit var snapshot: DocumentExportSnapshot
        val view = DrawingView(context)
        instrumentation.runOnMainSync {
            view.configureDocument(512, 512)
            snapshot = view.captureExportSnapshot()
            view.debugDrawStrokeForTest(
                listOf(StrokePoint(250f, 250f, 1f, 0f, 1L)),
            )
        }
        val destination = ByteArrayOutputStream()
        val outcome = runCatching { snapshot.writePng(destination) }
        assertTrue("Version mismatch should reject stale export", outcome.isFailure)
        assertEquals(0, destination.size())
    }

    @Test
    fun layerMetadataEditsInvalidatePendingExports() {
        lateinit var snapshot: DocumentExportSnapshot
        val view = DrawingView(context)
        instrumentation.runOnMainSync {
            view.configureDocument(512, 512)
            snapshot = view.captureExportSnapshot()
            view.addLayer()
        }
        val destination = ByteArrayOutputStream()
        assertTrue(runCatching { snapshot.writeOpenRaster(destination) }.isFailure)
        assertEquals(0, destination.size())
    }
}
