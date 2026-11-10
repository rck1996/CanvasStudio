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
            assertEquals(Color.RED, first.strokePaint.color)
            assertEquals(Color.BLUE, second.strokePaint.color)
        } finally {
            first.close()
            second.close()
        }
    }
}
