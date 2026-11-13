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
import com.orbyte.canvasstudio.drawing.LayerBlendMode
import com.orbyte.canvasstudio.drawing.PsdLayerReader
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
    fun layeredOpacityAndBlendModeMatchAcrossPngBands() {
        val view = DrawingView(context)
        lateinit var snapshot: DocumentExportSnapshot
        lateinit var original: android.graphics.Bitmap
        instrumentation.runOnMainSync {
            view.configureDocument(512, 640)
            view.brushSettings = BrushSettings(
                kind = BrushKind.INK, color = Color.RED, sizePx = 100f, opacity = 1f,
            )
            view.debugDrawStrokeForTest(listOf(
                StrokePoint(215f, 512f, 1f, 0f, 1L),
                StrokePoint(310f, 512f, 1f, 0f, 2L),
            ))
            view.addLayer()
            var activeId = ""
            view.onLayersChanged = { models ->
                activeId = models.firstOrNull { it.isActive }?.id.orEmpty()
            }
            view.refreshLayerState()
            view.brushSettings = BrushSettings(
                kind = BrushKind.INK, color = Color.BLUE, sizePx = 100f, opacity = 1f,
            )
            view.debugDrawStrokeForTest(listOf(
                StrokePoint(255f, 462f, 1f, 0f, 3L),
                StrokePoint(255f, 562f, 1f, 0f, 4L),
            ))
            check(activeId.isNotEmpty())
            view.setLayerOpacity(activeId, .65f)
            view.setLayerBlendMode(activeId, LayerBlendMode.MULTIPLY)
            original = view.exportCompositeBitmap(includePaper = false)
            snapshot = view.captureExportSnapshot()
        }
        try {
            val png = ByteArrayOutputStream()
            snapshot.writePng(png, includePaper = false)
            val exported = requireNotNull(
                BitmapFactory.decodeByteArray(png.toByteArray(), 0, png.size()),
            )
            try {
                for (y in 0 until 640) for (x in 0 until 512) {
                    assertEquals(
                        "Composite blend mismatch at $x,$y",
                        original.getPixel(x, y),
                        exported.getPixel(x, y),
                    )
                }
            } finally {
                exported.recycle()
            }
        } finally {
            original.recycle()
        }
    }

    @Test
    fun partialMaskOnClippingBaseMatchesCompositeAndBandedExport() {
        val view = DrawingView(context)
        lateinit var snapshot: DocumentExportSnapshot
        lateinit var original: android.graphics.Bitmap
        instrumentation.runOnMainSync {
            view.configureDocument(512, 640)
            var activeId = ""
            view.onLayersChanged = { activeId = it.single { layer -> layer.isActive }.id }
            view.refreshLayerState()
            view.brushSettings = BrushSettings(kind = BrushKind.INK, color = Color.RED, sizePx = 96f, pressureSize = false)
            val horizontal = listOf(StrokePoint(125f, 512f, 1f, 0f, 0L), StrokePoint(390f, 512f, 1f, 0f, 44L))
            view.debugDrawStrokeForTest(horizontal)
            view.addMaskToActiveLayer()
            view.debugDrawStrokeForTest(horizontal)
            view.setLayerMaskStrength(activeId, 50)
            view.finishMaskEditing()
            view.addLayer()
            view.brushSettings = view.brushSettings.copy(color = Color.BLUE)
            view.debugDrawStrokeForTest(listOf(StrokePoint(256f, 410f, 1f, 0f, 0L), StrokePoint(256f, 600f, 1f, 0f, 44L)))
            view.setLayerClipping(activeId, true)
            original = view.exportCompositeBitmap(false)
            snapshot = view.captureExportSnapshot()
        }
        try {
            val png = ByteArrayOutputStream()
            snapshot.writePng(png, includePaper = false)
            val exported = requireNotNull(BitmapFactory.decodeByteArray(png.toByteArray(), 0, png.size()))
            try {
                assertEquals(Color.TRANSPARENT, exported.getPixel(256, 590))
                assertTrue("Base mask should partially hide the red stroke", Color.alpha(exported.getPixel(145, 512)) in 126..128)
                val overlap = exported.getPixel(256, 512)
                assertTrue("Clipped blue should remain partially visible over the base", Color.blue(overlap) > Color.red(overlap))
                assertTrue(Color.alpha(overlap) in 188..193)
                assertTrue("The banded export changed masked clipping pixels", original.sameAs(exported))
            } finally {
                exported.recycle()
            }
            val psd = ByteArrayOutputStream()
            snapshot.writeLayeredPsd(psd)
            val psdBytes = psd.toByteArray()
            BasicPsdCodec.read(ByteArrayInputStream(psdBytes)).let { composite ->
                try { assertTrue("Layered PSD composite changed masked clipping pixels", original.sameAs(composite)) }
                finally { composite.recycle() }
            }
            requireNotNull(PsdLayerReader.read(ByteArrayInputStream(psdBytes))).use { layers ->
                val base = layers.layers.first()
                assertTrue(Color.alpha(requireNotNull(base.bitmap).getPixel(145 - base.left, 512 - base.top)) in 126..128)
            }
        } finally {
            original.recycle()
            instrumentation.runOnMainSync { view.configureDocument(256, 256) }
        }
    }

    @Test
    fun invertedMaskCoversMissingTilesAndMatchesPngAndLayeredPsd() {
        val view = DrawingView(context)
        lateinit var snapshot: DocumentExportSnapshot
        lateinit var original: android.graphics.Bitmap
        instrumentation.runOnMainSync {
            view.configureDocument(1024, 640)
            var activeId = ""
            view.onLayersChanged = { activeId = it.single { layer -> layer.isActive }.id }
            view.refreshLayerState()
            view.brushSettings = BrushSettings(kind = BrushKind.INK, color = Color.RED, sizePx = 76f, pressureSize = false)
            fun stroke(from: Float, to: Float) = listOf(
                StrokePoint(from, 512f, 1f, 0f, 0L), StrokePoint(to, 512f, 1f, 0f, 60L),
            )
            view.debugDrawStrokeForTest(stroke(100f, 900f))
            view.addMaskToActiveLayer()
            view.debugDrawStrokeForTest(stroke(130f, 320f))
            view.toggleLayerMaskInverted(activeId)
            original = view.exportCompositeBitmap(false)
            snapshot = view.captureExportSnapshot()
        }
        try {
            assertEquals(255, Color.alpha(original.getPixel(210, 512)))
            assertEquals(0, Color.alpha(original.getPixel(800, 512)))
            val png = ByteArrayOutputStream()
            snapshot.writePng(png, includePaper = false)
            val exported = requireNotNull(BitmapFactory.decodeByteArray(png.toByteArray(), 0, png.size()))
            try { assertTrue(original.sameAs(exported)) } finally { exported.recycle() }
            val psd = ByteArrayOutputStream()
            snapshot.writeLayeredPsd(psd)
            requireNotNull(PsdLayerReader.read(ByteArrayInputStream(psd.toByteArray()))).use { stack ->
                val layer = stack.layers.single()
                val pixels = requireNotNull(layer.bitmap)
                assertEquals(255, Color.alpha(pixels.getPixel(210 - layer.left, 512 - layer.top)))
                assertEquals(0, Color.alpha(pixels.getPixel(800 - layer.left, 512 - layer.top)))
            }
        } finally {
            original.recycle()
            instrumentation.runOnMainSync { view.configureDocument(256, 256) }
        }
    }

    @Test
    fun sparseEightKPngKeepsInkInTheLastPartialBand() {
        lateinit var snapshot: DocumentExportSnapshot
        instrumentation.runOnMainSync {
            val view = DrawingView(context)
            view.configureDocument(7680, 4320)
            view.brushSettings = BrushSettings(kind = BrushKind.INK, color = Color.RED, sizePx = 120f)
            view.debugDrawStrokeForTest(listOf(
                StrokePoint(7000f, 4200f, 1f, 0f, 1L),
                StrokePoint(7200f, 4200f, 1f, 0f, 2L),
            ))
            snapshot = view.captureExportSnapshot()
        }
        val png = ByteArrayOutputStream()
        snapshot.writePng(png, includePaper = false)
        val bytes = png.toByteArray()
        val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, dimensions)
        assertEquals(7680, dimensions.outWidth)
        assertEquals(4320, dimensions.outHeight)
        val sampled = requireNotNull(BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = 8 },
        ))
        try {
            assertTrue(Color.alpha(sampled.getPixel(7100 / 8, 4200 / 8)) > 0)
            assertEquals(Color.TRANSPARENT, sampled.getPixel(1, 1))
        } finally {
            sampled.recycle()
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
    fun staleSnapshotDoesNotOpenDestination() {
        lateinit var snapshot: DocumentExportSnapshot
        val view = DrawingView(context)
        instrumentation.runOnMainSync {
            view.configureDocument(512, 512)
            snapshot = view.captureExportSnapshot()
            view.addLayer()
        }
        var opened = false
        assertTrue(runCatching {
            snapshot.writePngTo({ opened = true; ByteArrayOutputStream() })
        }.isFailure)
        assertFalse(opened)
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
