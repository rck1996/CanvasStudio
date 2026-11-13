package com.orbyte.canvasstudio.drawing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The extracted painter must generate exactly the same raster pixels when
 * replaying one immutable command as DrawingView's authoritative document.
 */
@RunWith(AndroidJUnit4::class)
class BrushRasterSessionIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun inkBrushPixelsMatchCommittedDocument() =
        assertBrushMatchesDocument(BrushKind.INK)

    @Test
    fun watercolorPixelsMatchCommittedDocument() =
        assertBrushMatchesDocument(BrushKind.WATERCOLOR)

    @Test
    fun charcoalPixelsMatchCommittedDocument() =
        assertBrushMatchesDocument(BrushKind.CHARCOAL)

    @Test
    fun translucentPigmentRetainsCombinedOpacityAndFlowInTheRaster() {
        val settings = BrushSettings(
            kind = BrushKind.INK,
            color = Color.argb(128, 24, 60, 180),
            sizePx = 20f,
            opacity = .5f,
            flow = .5f,
            pressureSize = false,
            pressureOpacity = false,
            renderProfile = BrushRenderProfile(mode = BrushRenderMode.INTENSE_GLAZE, buildup = 1f),
        )
        BrushRasterSession().use { raster ->
            val bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
            try {
                raster.drawBrushStamp(Canvas(bitmap), 20f, 20f, 1f, 0f, DrawingTool.BRUSH, settings, 0, 0f)
                assertEquals(32, Color.alpha(bitmap.getPixel(20, 20)))
                assertEquals(128, Color.alpha(mixPigmentColor(settings.color, Color.RED, .8f)))
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test
    fun graphiteRespondsToPressureWhileTechnicalInkKeepsItsLine() {
        val hb = requireNotNull(resolveBuiltInBrush("pencil-hb")).toSettings(Color.BLACK).copy(sizePx = 46f)
        val technicalInk = requireNotNull(resolveBuiltInBrush("technical-ink")).toSettings(Color.BLACK)
            .copy(sizePx = 46f)
        val softGraphite = dabCoverage(hb, .2f)
        val hardGraphite = dabCoverage(hb, .85f)
        assertTrue("HB soft mark must be visible: $softGraphite", softGraphite > 0L)
        assertTrue("HB must deposit more pigment with pressure: $softGraphite vs $hardGraphite",
            hardGraphite > softGraphite * 2)
        assertEquals(dabCoverage(technicalInk, .2f), dabCoverage(technicalInk, .85f))
    }

    @Test
    fun granulatedWatercolorBuildsCoverageWithoutMakingSoftDabsOpaque() {
        val brush = requireNotNull(resolveBuiltInBrush("granulated-watercolor")).toSettings(Color.BLACK)
        fun mark(pressure: Float): Pair<Long, Int> {
            val bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
            try {
                BrushRasterSession().use {
                    it.drawBrushStamp(Canvas(bitmap), 80f, 80f, pressure, .10f, DrawingTool.BRUSH, brush, 0, 0f)
                }
                val alpha = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(alpha, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                return alpha.sumOf { (it ushr 24).toLong() } to alpha.maxOf { it ushr 24 }
            } finally {
                bitmap.recycle()
            }
        }
        val (soft, softPeak) = mark(.28f)
        val (hard, hardPeak) = mark(.9f)
        assertTrue("Soft watercolor must show pigment without opaque spots", soft > 0 && softPeak in 1..127)
        assertTrue("Pressure must increase translucent coverage", hard > soft && hardPeak in softPeak..254)
    }

    @Test
    fun transparentPigmentDoesNotLeaveAnArtificialWetOrTexturedMark() {
        listOf("pencil-hb", "granulated-watercolor", "bristle", "impasto-bristle").forEach { id ->
            val settings = requireNotNull(resolveBuiltInBrush(id)).toSettings(Color.TRANSPARENT)
            assertEquals(id, 0L, dabCoverage(settings, .85f))
        }
    }

    private fun dabCoverage(settings: BrushSettings, pressure: Float): Long {
        val bitmap = Bitmap.createBitmap(140, 140, Bitmap.Config.ARGB_8888)
        try {
            BrushRasterSession().use { raster ->
                raster.drawBrushStamp(Canvas(bitmap), 70f, 70f, pressure, 0f, DrawingTool.BRUSH, settings, 0, 0f)
            }
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            return pixels.sumOf { (it ushr 24).toLong() }
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun largeTipsKeepOriginalSpacingWhenDynamicsMakeTheMarkNarrow() {
        BrushRasterSession().use { raster ->
            val settings = BrushSettings(
                sizePx = 180f, spacing = .05f, kind = BrushKind.WATERCOLOR,
                pressureSize = true, minSize = .02f, pressureCurve = 1f,
            )
            val legacy = raster.stampSpacing(settings.copy(sizePx = 96f), .10f, 0f, DrawingTool.BRUSH)
            assertEquals(legacy, raster.stampSpacing(settings, .10f, 0f, DrawingTool.BRUSH), .001f)
            assertEquals(legacy, raster.stampSpacing(settings.copy(velocitySize = 1f), 1f, 1f, DrawingTool.BRUSH), .001f)
            assertEquals(legacy, raster.stampSpacing(settings, 1f, 0f, DrawingTool.ERASER), .001f)
            assertEquals(.20f, raster.stampSpacing(settings, 1f, 0f, DrawingTool.BRUSH), .001f)
        }
    }

    private fun assertBrushMatchesDocument(kind: BrushKind) {
        instrumentation.runOnMainSync {
            val view = DrawingView(context).apply {
                configureDocument(256, 256)
                brushSettings = BrushSettings(
                    kind = kind,
                    color = Color.rgb(96, 25, 174),
                    sizePx = 28f,
                    opacity = 0.8f,
                    spacing = 0.14f,
                    pressureSize = true,
                    pressureOpacity = true,
                    grain = 0.35f,
                    scatter = 0.1f,
                )
            }
            val settings = view.brushSettings
            val stroke = listOf(
                StrokePoint(31f, 47f, .38f, .2f, 11L, .18f),
                StrokePoint(84f, 73f, .63f, .35f, 38L, .32f),
                StrokePoint(142f, 92f, .8f, .42f, 65L, .4f),
                StrokePoint(195f, 164f, .52f, .22f, 92L, .2f),
            )
            val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
            val painter = BrushRasterSession()
            try {
                view.debugDrawStrokeForTest(stroke)
                val canvas = Canvas(bitmap)
                val first = stroke.first()
                painter.drawBrushStamp(
                    canvas = canvas,
                    x = first.x,
                    y = first.y,
                    pressure = first.pressure * painter.taperFactor(settings, 0f),
                    tilt = first.tilt,
                    drawingTool = DrawingTool.BRUSH,
                    settings = settings,
                    stampIndex = 0,
                    angleRadians = 0f,
                    orientation = first.orientation,
                )
                for (index in 0 until stroke.lastIndex) {
                    painter.drawStrokeSegment(
                        canvas = canvas,
                        from = stroke[index],
                        to = stroke[index + 1],
                        drawingTool = DrawingTool.BRUSH,
                        settings = settings,
                        progress = (index + 1f) / stroke.lastIndex,
                        segmentIndex = index,
                    )
                }
                // Probe the whole document grid, including boundaries, opaque
                // pixels, transparent pixels, and points near each dab.
                for (y in 0 until 256 step 3) for (x in 0 until 256 step 3) {
                    assertEquals(
                        "$kind raster mismatch at $x,$y",
                        bitmap.getPixel(x, y),
                        view.debugPixelForTest(x.toFloat(), y.toFloat()),
                    )
                }
            } finally {
                painter.close()
                bitmap.recycle()
            }
        }
    }

    @Test
    fun paintersOwnIndependentMutablePaintsAndCaches() {
        val first = BrushRasterSession()
        val second = BrushRasterSession()
        try {
            assertNotSame(first.strokePaint, second.strokePaint)
            first.configurePaint(
                first.strokePaint,
                DrawingTool.BRUSH,
                BrushSettings(color = Color.RED),
                1f,
                0f,
            )
            second.configurePaint(
                second.strokePaint,
                DrawingTool.BRUSH,
                BrushSettings(color = Color.BLUE),
                1f,
                0f,
            )
            // Paint now retains brush coverage as its alpha, independently of RGB.
            assertEquals(Color.RED, first.strokePaint.color or 0xFF000000.toInt())
            assertEquals(Color.BLUE, second.strokePaint.color or 0xFF000000.toInt())
        } finally {
            first.close()
            second.close()
        }
    }
}
