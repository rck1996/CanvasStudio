package com.orbyte.canvasstudio.drawing

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrushRetentionMatrixTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test
    fun everyBrushFamilyRetainsEarlierThickStrokes() {
        val view = DrawingView(context)
        val presets = BrushKind.values().map { kind ->
            premiumBrushes.first { it.kind == kind }
        }
        val points = List(49) { index ->
            TestPoint(
                x = 170f + (index % 7) * 280f,
                y = 170f + (index / 7) * 280f,
            )
        }

        try {
            presets.forEach { preset ->
                instrumentation.runOnMainSync {
                    view.configureDocument(DOCUMENT_SIZE, DOCUMENT_SIZE)
                    view.tool = DrawingTool.BRUSH
                    view.brushSettings = preset.toStressSettings()
                    points.take(FIRST_BATCH).forEachIndexed { index, point ->
                        drawStroke(view, point, index)
                    }
                }
                val before = instrumentation.runOnMainSyncWithResult {
                    view.exportCompositeBitmap(includePaper = false)
                }
                val retainedBefore = points.take(FIRST_BATCH).count { before.hasInkNear(it) }
                before.recycle()

                instrumentation.runOnMainSync {
                    points.drop(FIRST_BATCH).forEachIndexed { index, point ->
                        drawStroke(view, point, FIRST_BATCH + index)
                    }
                }
                val after = instrumentation.runOnMainSyncWithResult {
                    view.exportCompositeBitmap(includePaper = false)
                }
                val retainedAfter = points.take(FIRST_BATCH).count { after.hasInkNear(it) }
                val secondBatch = points.drop(FIRST_BATCH).count { after.hasInkNear(it) }
                after.recycle()

                assertTrue(
                    "${preset.name} perdió trazos antiguos: $retainedAfter/$retainedBefore",
                    retainedBefore == FIRST_BATCH && retainedAfter == FIRST_BATCH,
                )
                assertTrue(
                    "${preset.name} no rasterizó todos los trazos nuevos: $secondBatch/${points.size - FIRST_BATCH}",
                    secondBatch == points.size - FIRST_BATCH,
                )
            }
        } finally {
            instrumentation.runOnMainSync { view.configureDocument(256, 256) }
        }
    }

    private fun BrushPreset.toStressSettings(): BrushSettings = BrushSettings(
        sizePx = 120f,
        opacity = opacity.coerceAtLeast(.55f),
        color = Color.rgb(24, 28, 34),
        hardness = hardness,
        spacing = spacing,
        stabilization = 0f,
        flow = flow.coerceAtLeast(.55f),
        minSize = minSize,
        pressureSize = false,
        pressureOpacity = false,
        pressureCurve = pressureCurve,
        tiltResponse = tiltResponse,
        taperStart = 0f,
        taperEnd = 0f,
        scatter = scatter,
        grain = grain,
        velocitySize = 0f,
        kind = kind,
    )

    private fun drawStroke(view: DrawingView, point: TestPoint, index: Int) {
        val downTime = 10_000L + index * 100L
        val startX = point.x - 58f
        val endX = point.x + 58f
        dispatch(view, downTime, downTime, MotionEvent.ACTION_DOWN, startX, point.y)
        repeat(8) { step ->
            val progress = (step + 1f) / 8f
            dispatch(
                view = view,
                downTime = downTime,
                eventTime = downTime + (step + 1) * 8L,
                action = MotionEvent.ACTION_MOVE,
                x = startX + (endX - startX) * progress,
                y = point.y + if (step % 2 == 0) 4f else -4f,
            )
        }
        dispatch(view, downTime, downTime + 80L, MotionEvent.ACTION_UP, endX, point.y)
    }

    private fun dispatch(
        view: DrawingView,
        downTime: Long,
        eventTime: Long,
        action: Int,
        x: Float,
        y: Float,
    ) {
        MotionEvent.obtain(downTime, eventTime, action, x, y, 0).also { event ->
            try {
                view.onTouchEvent(event)
            } finally {
                event.recycle()
            }
        }
    }

    private fun Bitmap.hasInkNear(point: TestPoint): Boolean {
        val area = Rect(
            (point.x - 92f).toInt().coerceAtLeast(0),
            (point.y - 92f).toInt().coerceAtLeast(0),
            (point.x + 92f).toInt().coerceAtMost(width),
            (point.y + 92f).toInt().coerceAtMost(height),
        )
        val pixels = IntArray(area.width() * area.height())
        getPixels(pixels, 0, area.width(), area.left, area.top, area.width(), area.height())
        return pixels.count { Color.alpha(it) > 8 } >= 20
    }

    private fun <T> android.app.Instrumentation.runOnMainSyncWithResult(block: () -> T): T {
        var result: Result<T>? = null
        runOnMainSync { result = runCatching(block) }
        return requireNotNull(result).getOrThrow()
    }

    private data class TestPoint(val x: Float, val y: Float)

    private companion object {
        const val DOCUMENT_SIZE = 2048
        const val FIRST_BATCH = 24
    }
}
