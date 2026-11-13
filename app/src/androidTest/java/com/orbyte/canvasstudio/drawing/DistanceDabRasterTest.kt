package com.orbyte.canvasstudio.drawing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DistanceDabRasterTest {
    private val bristle = BrushSettings(
        kind = BrushKind.BRISTLE, sizePx = 64f, color = Color.BLACK,
        opacity = .55f, spacing = .125f, pressureSize = false, pressureOpacity = false,
        taperStart = 0f, taperEnd = 0f, scatter = .18f,
        dynamicsProfile = BrushDynamicsProfile(), renderProfile = BrushRenderProfile(),
    )

    @Test
    fun bristleKeepsDistinctFibersAndLoadsMorePaintAtFirmPressure() {
        val preset = requireNotNull(resolveBuiltInBrush("bristle"))
        val brush = preset.toSettings(Color.BLACK).copy(
            sizePx = 180f, scatter = 0f, taperStart = 0f, taperEnd = 0f,
            tipProfile = preset.tipProfile.copy(count = 7, rotationJitter = 0f),
            dualBrushProfile = DualBrushProfile(),
        )
        fun stroke(pressure: Float, settings: BrushSettings = brush): Bitmap =
            Bitmap.createBitmap(900, 300, Bitmap.Config.ARGB_8888).also { bitmap ->
                val points = (100..800 step 20).map { x -> StrokePoint(x.toFloat(), 150f, pressure, 0f, x * 4L) }
                BrushRasterSession().use { session ->
                    StrokeCommandPainter(session).draw(Canvas(bitmap),
                        StrokeCommand(points = points, tool = DrawingTool.BRUSH, settings = settings),
                        RectF(0f, 0f, 900f, 300f))
                }
            }
        fun profile(bitmap: Bitmap) = (0 until bitmap.height).map { y ->
            (360..540).sumOf { x -> Color.alpha(bitmap.getPixel(x, y)) }
        }
        val light = stroke(.38f)
        val firm = stroke(.95f)
        val dense = stroke(.38f, brush.copy(tipProfile = brush.tipProfile.copy(count = 14)))
        val firmAtFullOpacity = stroke(1f, brush.copy(
            sizePx = 88f, opacity = 1f,
            tipProfile = brush.tipProfile.copy(count = 14),
        ))
        try {
            val lightProfile = profile(light)
            val visible = lightProfile.map { it > 181 * 4 }
            val lanes = visible.indices.count { visible[it] && (it == 0 || !visible[it - 1]) }
            assertTrue("Expected distinct fibers, found $lanes", lanes >= 4)
            assertTrue("Firm pressure should deposit more paint", profile(firm).sum() > lightProfile.sum() * 2)
            assertTrue("Density should visibly change the fibers", !light.sameAs(dense))
            val firmCore = profile(firmAtFullOpacity)
            assertTrue("Firm black should have dense fibers at 88 px", firmCore.max() >= 181 * 224)
        } finally {
            light.recycle()
            firm.recycle()
            dense.recycle()
            firmAtFullOpacity.recycle()
        }
    }

    @Test
    fun sampledLineKeepsIdenticalTexturedPixelsAtDifferentPointDensities() {
        val coarse = render(line(32))
        val fine = render(line(4))
        try {
            assertTrue(Color.alpha(coarse.getPixel(512, 220)) > 0)
            assertTrue("Dab placement or texture seeds changed with input density", coarse.sameAs(fine))
        } finally {
            coarse.recycle()
            fine.recycle()
        }
    }

    @Test
    fun skippingOffTileDabsKeepsWetAndBristlePixelsIdentical() {
        val document = RectF(0f, 0f, 1800f, 1400f)
        val tile = RectF(512f, 512f, 1024f, 1024f)
        val points = listOf(
            StrokePoint(36f, 340f, .4f, .5f, 0L),
            StrokePoint(1630f, 830f, .9f, .8f, 440L),
            StrokePoint(36f, 1050f, .7f, .2f, 880L),
        )
        val settingsByPreset = listOf(
            "granulated-watercolor", "bristle", "impasto-bristle", "thick-oil", "marker", "flat-marker",
        ).associateWith {
            requireNotNull(resolveBuiltInBrush(it)).toSettings().copy(sizePx = 180f)
        }
        val impasto = settingsByPreset.getValue("impasto-bristle")
        val customDual = impasto.copy(
            scatter = 1f,
            tiltResponse = 1f,
            pressureSize = false,
            dualBrushProfile = impasto.dualBrushProfile.copy(
                enabled = true, opacity = .8f, scatter = 1f, sizeScale = 2f, offset = 1f,
                tip = impasto.dualBrushProfile.tip.copy(shape = BrushTipShape.PARTICLE),
            ),
        )
        for ((preset, settings) in settingsByPreset + ("custom-dual-particle" to customDual)) {
            val command = StrokeCommand(points = points, tool = DrawingTool.BRUSH, settings = settings)
            fun render(tileCull: Boolean): Bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).also { bitmap ->
                BrushRasterSession().use { raster ->
                    val canvas = Canvas(bitmap).apply { translate(-tile.left, -tile.top); clipRect(tile) }
                    StrokeCommandPainter(raster).draw(canvas, command, document, renderClipBounds = tile.takeIf { tileCull })
                }
            }
            val reference = render(tileCull = false)
            val clipped = render(tileCull = true)
            try {
                assertTrue("No test pigment reached the tile for $preset", (0..511).any { y ->
                    Color.alpha(reference.getPixel(256, y)) > 0
                })
                assertTrue("Off-tile culling changed $preset", reference.sameAs(clipped))
            } finally {
                reference.recycle()
                clipped.recycle()
            }
        }
    }

    @Test
    fun tileClippingAndRegionalUndoKeepTheOriginalDabPhase() {
        var result: Result<Unit>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching {
            val view = DrawingView(ApplicationProvider.getApplicationContext()).apply {
                configureDocument(1024, 512)
                brushSettings = bristle
                debugPerformanceMetricsEnabled = true
            }
            view.debugDrawStrokeForTest(line(32))
            val reference = view.exportCompositeBitmap(false)
            try {
                for (x in 508..516) {
                    assertTrue("No ink at the tile seam column $x", (186..254).any {
                        Color.alpha(reference.getPixel(x, it)) > 0
                    })
                }
                view.brushSettings = bristle.copy(color = Color.RED)
                view.debugDrawStrokeForTest(line(16).filter { it.x >= 576f })
                val withNewPaint = view.exportCompositeBitmap(false)
                try {
                    view.undo()
                    assertDocumentEquals(view, reference)
                    view.redo()
                    assertDocumentEquals(view, withNewPaint)
                    assertEquals(0L, view.debugPerformanceMetrics().indexFallbacks)
                } finally {
                    withNewPaint.recycle()
                }
            } finally {
                reference.recycle()
                view.configureDocument(256, 256)
            }
        } }
        result!!.getOrThrow()
    }

    private fun assertDocumentEquals(view: DrawingView, expected: Bitmap) {
        val actual = view.exportCompositeBitmap(false)
        try { assertTrue("Tiled replay changed the dab raster", actual.sameAs(expected)) }
        finally { actual.recycle() }
    }

    private fun render(points: List<StrokePoint>): Bitmap =
        Bitmap.createBitmap(1024, 512, Bitmap.Config.ARGB_8888).also { bitmap ->
            BrushRasterSession().use { raster ->
                StrokeCommandPainter(raster).draw(
                    Canvas(bitmap), StrokeCommand(points = points, tool = DrawingTool.BRUSH, settings = bristle),
                    RectF(0f, 0f, 1024f, 512f),
                )
            }
        }

    private fun line(step: Int): List<StrokePoint> = (64..896 step step).map { x ->
        StrokePoint(x.toFloat(), 220f, 1f, 0f, x * 2L)
    }
}
