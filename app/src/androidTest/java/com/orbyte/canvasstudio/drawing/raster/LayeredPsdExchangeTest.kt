package com.orbyte.canvasstudio.drawing.raster

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orbyte.canvasstudio.drawing.BasicPsdCodec
import com.orbyte.canvasstudio.drawing.BrushKind
import com.orbyte.canvasstudio.drawing.BrushSettings
import com.orbyte.canvasstudio.drawing.DrawingView
import com.orbyte.canvasstudio.drawing.LayerBlendMode
import com.orbyte.canvasstudio.drawing.LayerUiModel
import com.orbyte.canvasstudio.drawing.PsdLayerReader
import com.orbyte.canvasstudio.drawing.StrokePoint
import java.io.File
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LayeredPsdExchangeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun importsLayersWrittenByIndependentPsdLibrary() {
        val asset = instrumentation.context.assets
        requireNotNull(asset.open("psd/external-rgb-raster.psd").use(PsdLayerReader::read)).use { external ->
            assertEquals(32, external.width)
            assertEquals(24, external.height)
            assertEquals(listOf("Base externa ñ", "Luz externa 夜"), external.layers.map { it.name })
            val base = external.layers.first()
            val upper = external.layers.last()
            assertEquals(2, base.left)
            assertEquals(3, base.top)
            assertEquals(160, Color.alpha(requireNotNull(base.bitmap).getPixel(0, 0)))
            assertEquals(LayerBlendMode.MULTIPLY, upper.blendMode)
            assertEquals(128f / 255f, upper.opacity, .0001f)
            assertFalse(upper.visible)
        }
    }

    @Test
    fun layerStackRoundTripsNamesOrderAlphaAndMetadataAndCanBeEdited() {
        lateinit var snapshot: DocumentExportSnapshot
        lateinit var source: DrawingView
        lateinit var target: DrawingView
        lateinit var original: android.graphics.Bitmap
        val model = mutableListOf<LayerUiModel>()
        instrumentation.runOnMainSync {
            source = DrawingView(context).apply {
                configureDocument(768, 640)
                renameActiveLayer("Base ñ")
                paint(Color.RED, 120f, 514f, 632f, 514f)
                addMaskToActiveLayer()
                paint(Color.BLACK, 340f, 470f, 340f, 560f)
                finishMaskEditing()
                addLayer()
                renameActiveLayer("Luz 夜")
                paint(Color.BLUE, 521f, 494f, 650f, 494f)
                var active = ""
                onLayersChanged = { active = it.first { layer -> layer.isActive }.id }
                refreshLayerState()
                setLayerOpacity(active, 128f / 255f)
                setLayerAlphaLocked(active, true)
                setLayerBlendMode(active, LayerBlendMode.MULTIPLY)
                setLayerClipping(active, true)
                addLayer()
                renameActiveLayer("Boceto oculto")
                toggleLayerVisibility(active)
            }
            original = source.exportCompositeBitmap(includePaper = false)
            snapshot = source.captureExportSnapshot()
            target = DrawingView(context).apply {
                configureDocument(768, 640)
                onLayersChanged = { model.clear(); model.addAll(it) }
                refreshLayerState()
            }
        }
        // Keep this synthetic file in app cache for a second, independent desktop decoder.
        val file = File(context.cacheDir, "canvasstudio-layered-interop.psd")
        try {
            snapshot.writeLayeredPsdTo { file.outputStream() }
            file.inputStream().use(BasicPsdCodec::read).let { merged ->
                try { assertTrue(original.sameAs(merged)) } finally { merged.recycle() }
            }
            requireNotNull(file.inputStream().use(PsdLayerReader::read)).use { decoded ->
                assertEquals(768, decoded.width)
                assertEquals(640, decoded.height)
                assertEquals(listOf("Base ñ", "Luz 夜", "Boceto oculto"), decoded.layers.map { it.name })
                val base = decoded.layers.first()
                val pixels = requireNotNull(base.bitmap)
                assertTrue(Color.alpha(pixels.getPixel(130 - base.left, 514 - base.top)) > 0)
                assertEquals(0, Color.alpha(pixels.getPixel(340 - base.left, 514 - base.top)))
                val upper = decoded.layers[1]
                assertTrue(upper.alphaLocked)
                assertTrue(upper.clipping)
                assertEquals(LayerBlendMode.MULTIPLY, upper.blendMode)
                assertEquals(128f / 255f, upper.opacity, .0001f)
                assertFalse(decoded.layers.last().visible)
                assertEquals(3, runBlocking(Dispatchers.Main) { target.importLayeredPsd(decoded) })
            }
            instrumentation.runOnMainSync {
                assertEquals(listOf("Boceto oculto", "Luz 夜", "Base ñ"), model.take(3).map { it.name })
                assertTrue(model[1].alphaLocked && model[1].clipping)
                val composite = target.exportCompositeBitmap(includePaper = false)
                try { assertTrue("Imported layers should recompose to the same pixels", original.sameAs(composite)) }
                finally { composite.recycle() }
                val baseLayer = model[2]
                target.setActiveLayer(baseLayer.id)
                target.paint(Color.GREEN, 90f, 95f, 180f, 95f)
                assertTrue(Color.alpha(target.debugPixelForTest(110f, 95f)) > 0)
                target.undo()
                assertEquals(0, Color.alpha(target.debugPixelForTest(110f, 95f)))
                assertTrue(Color.alpha(target.debugPixelForTest(130f, 514f)) > 0)
            }
        } finally {
            original.recycle()
            instrumentation.runOnMainSync {
                source.configureDocument(256, 256)
                target.configureDocument(256, 256)
            }
        }
    }

    @Test
    fun largePsdUsesBandedOutputWithoutAFullBitmap() {
        lateinit var snapshot: DocumentExportSnapshot
        val view = DrawingView(context)
        instrumentation.runOnMainSync {
            view.configureDocument(7680, 4320)
            snapshot = view.captureExportSnapshot()
        }
        try {
            assertEquals(7680, snapshot.width)
            assertEquals(4320, snapshot.height)
            var bytes = 0L
            var signature: ByteArray? = null
            snapshot.writeLayeredPsd(object : OutputStream() {
                override fun write(b: Int) { bytes++ }
                override fun write(b: ByteArray, off: Int, len: Int) {
                    if (signature == null && len >= 4) signature = b.copyOfRange(off, off + 4)
                    bytes += len
                }
            })
            assertNotNull(signature)
            assertEquals("8BPS", signature!!.toString(Charsets.US_ASCII))
            assertTrue(bytes > 7680L * 4320 * 4)
            println("PSD_BANDED_BYTES=$bytes")
        } finally {
            instrumentation.runOnMainSync { view.configureDocument(256, 256) }
        }
    }

    private fun DrawingView.paint(color: Int, x: Float, y: Float, endX: Float, endY: Float) {
        brushSettings = BrushSettings(kind = BrushKind.INK, color = color, sizePx = 76f,
            opacity = 1f, pressureSize = false, stabilization = 0f)
        debugDrawStrokeForTest(listOf(StrokePoint(x, y, 1f, 0f, 1L), StrokePoint(endX, endY, 1f, 0f, 2L)))
    }
}
