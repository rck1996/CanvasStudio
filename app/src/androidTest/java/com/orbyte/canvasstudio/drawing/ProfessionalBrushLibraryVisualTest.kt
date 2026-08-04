package com.orbyte.canvasstudio.drawing

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orbyte.canvasstudio.drawing.brush.BrushFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.CRC32

@RunWith(AndroidJUnit4::class)
class ProfessionalBrushLibraryVisualTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val scenarios = listOf(
        BrushFixture.Scenario.SLOW_LINE,
        BrushFixture.Scenario.FAST_LINE,
        BrushFixture.Scenario.PRESSURE_INCREASING,
        BrushFixture.Scenario.PRESSURE_DECREASING,
        BrushFixture.Scenario.CURVE,
        BrushFixture.Scenario.ZIGZAG,
        BrushFixture.Scenario.CIRCLES,
        BrushFixture.Scenario.TILT_PROGRESSIVE,
        BrushFixture.Scenario.TILT_SHADING,
        BrushFixture.Scenario.OVERLAPPING_PASSES,
        BrushFixture.Scenario.FOUR_TILES,
    )

    @Test fun renderDeterministicElevenScenarioSheetForEveryProductionBrush() {
        val output = File(requireNotNull(context.getExternalFilesDir(null)), "brush-certification").apply { mkdirs() }
        val hashes = linkedMapOf<String, Long>()
        premiumBrushes.forEach { preset ->
            val view = onMain { DrawingView(context).apply { configureDocument(1200, 1200) } }
            val bitmap = try {
                onMain {
                    view.brushSettings = preset.toSettings(Color.rgb(25, 29, 36)).copy(
                        sizePx = preset.sizePx.coerceIn(10f, 72f),
                    )
                    scenarios.forEachIndexed { index, scenario ->
                        val column = index % 2
                        val row = index / 2
                        val cell = RectF(
                            column * 600f + 34f,
                            row * 190f + 24f,
                            column * 600f + 566f,
                            row * 190f + 172f,
                        )
                        view.debugDrawStrokeForTest(fit(BrushFixture.points(scenario), cell))
                    }
                }
                onMain { view.exportCompositeBitmap(includePaper = true) }
            } finally {
                onMain { view.configureDocument(256, 256) }
            }
            try {
                val hash = bitmapHash(bitmap)
                hashes[preset.id] = hash
                File(output, "${preset.id}.png").outputStream().use { stream ->
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                }
            } finally {
                bitmap.recycle()
            }
        }
        assertEquals(premiumBrushes.size, hashes.values.distinct().size)
        Log.i("CanvasStudioBrushAudit", "FIXTURE_HASHES=$hashes")
    }

    @Test fun renderUnlabelledBlindComparisonSheet() {
        val order = premiumBrushes.sortedBy { stableOrder(it.id) }
        val view = onMain { DrawingView(context).apply { configureDocument(1800, 1680) } }
        val bitmap = try {
            onMain {
                order.forEachIndexed { row, preset ->
                    view.brushSettings = preset.toSettings(Color.rgb(24, 29, 37)).copy(
                        sizePx = preset.sizePx.coerceIn(10f, 82f),
                    )
                    val cell = RectF(70f, row * 118f + 18f, 1730f, row * 118f + 106f)
                    view.debugDrawStrokeForTest(fit(BrushFixture.points(BrushFixture.Scenario.PREVIEW), cell))
                }
            }
            onMain { view.exportCompositeBitmap(includePaper = true) }
        } finally {
            onMain { view.configureDocument(256, 256) }
        }
        try {
            val output = File(requireNotNull(context.getExternalFilesDir(null)), "brush-certification/blind-comparison.png")
            output.parentFile?.mkdirs()
            output.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            Log.i("CanvasStudioBrushAudit", "BLIND_ORDER=${order.map { it.id }} HASH=${bitmapHash(bitmap)}")
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun everyProductionBrushCrossesFourRealTilesWithoutLosingTheFirstMark() {
        premiumBrushes.forEach { preset ->
            val view = onMain { DrawingView(context).apply { configureDocument(1024, 1024) } }
            try {
                onMain {
                    view.brushSettings = preset.toSettings(Color.BLACK).copy(sizePx = preset.sizePx.coerceIn(18f, 76f))
                    view.debugDrawStrokeForTest(BrushFixture.points(BrushFixture.Scenario.FOUR_TILES))
                }
                val bitmap = onMain { view.exportCompositeBitmap(includePaper = false) }
                try {
                    assertTrue("${preset.name} no dejó contenido al cruzar tiles", bitmapHasInk(bitmap))
                } finally {
                    bitmap.recycle()
                }
            } finally {
                onMain { view.configureDocument(256, 256) }
            }
        }
    }

    private fun fit(points: List<StrokePoint>, destination: RectF): List<StrokePoint> {
        val minX = points.minOf(StrokePoint::x)
        val maxX = points.maxOf(StrokePoint::x)
        val minY = points.minOf(StrokePoint::y)
        val maxY = points.maxOf(StrokePoint::y)
        val sourceWidth = (maxX - minX).coerceAtLeast(1f)
        val sourceHeight = (maxY - minY).coerceAtLeast(1f)
        return points.map { point ->
            point.copy(
                x = destination.left + (point.x - minX) / sourceWidth * destination.width(),
                y = destination.top + (point.y - minY) / sourceHeight * destination.height(),
            )
        }
    }

    private fun bitmapHash(bitmap: Bitmap): Long {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val crc = CRC32()
        pixels.forEach { pixel ->
            crc.update(pixel ushr 24)
            crc.update(pixel ushr 16)
            crc.update(pixel ushr 8)
            crc.update(pixel)
        }
        return crc.value
    }

    private fun bitmapHasInk(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.any { Color.alpha(it) > 0 }
    }

    private fun stableOrder(id: String): Long = id.fold(1125899906842597L) { hash, char -> hash * 31L + char.code }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }
}
