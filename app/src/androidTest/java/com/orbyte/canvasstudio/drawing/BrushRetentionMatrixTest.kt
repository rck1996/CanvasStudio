package com.orbyte.canvasstudio.drawing

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
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

    @Test
    fun modifiedHbLongStrokeRetainsOlderStrokesInEveryTouchedTile() {
        val view = DrawingView(context)
        val hb = premiumBrushes.first { it.id == "pencil-hb" }
        val sentinels = listOf(
            TestPoint(180f, 56f),
            TestPoint(700f, 56f),
            TestPoint(1_220f, 56f),
            TestPoint(1_740f, 56f),
        )

        try {
            instrumentation.runOnMainSync {
                view.configureDocument(DOCUMENT_SIZE, DOCUMENT_SIZE)
                view.tool = DrawingTool.BRUSH
                view.brushSettings = hb.toModifiedSettings(sizePx = 24f)
                sentinels.forEachIndexed { index, point -> drawStroke(view, point, index) }
            }
            val before = instrumentation.runOnMainSyncWithResult {
                view.exportCompositeBitmap(includePaper = false)
            }
            assertTrue(
                "La preparación no conservó todos los trazos HB iniciales",
                sentinels.all { before.hasInkNear(it) },
            )
            before.recycle()

            instrumentation.runOnMainSync {
                view.brushSettings = hb.toModifiedSettings(sizePx = 180f)
                repeat(LONG_HB_STROKES) { stroke ->
                    drawLongStroke(
                        view = view,
                        y = 300f + (stroke % 4) * 32f,
                        index = sentinels.size + stroke,
                    )
                }
            }
            val after = instrumentation.runOnMainSyncWithResult {
                view.exportCompositeBitmap(includePaper = false)
            }
            val retained = sentinels.count { after.hasInkNear(it) }
            after.recycle()

            assertTrue(
                "El HB grande y largo borró trazos antiguos en tiles reconstruidos: $retained/${sentinels.size}",
                retained == sentinels.size,
            )
        } finally {
            instrumentation.runOnMainSync { view.configureDocument(256, 256) }
        }
    }

    @Test
    @LargeTest
    fun professionalDualAndWetBrushesRetainSentinelsAfter500LongStrokes() {
        val view = DrawingView(context)
        val presets = listOf(
            "pencil-6b",
            "charcoal",
            "bristle",
            "granulated-watercolor",
            "impasto-bristle",
        ).map { id -> premiumBrushes.first { it.id == id } }
        val sentinels = listOf(
            TestPoint(180f, 72f),
            TestPoint(700f, 72f),
            TestPoint(1_220f, 72f),
            TestPoint(1_740f, 72f),
        )
        val start = android.os.SystemClock.elapsedRealtime()

        try {
            instrumentation.runOnMainSync {
                view.configureDocument(DOCUMENT_SIZE, DOCUMENT_SIZE)
                view.tool = DrawingTool.BRUSH
                view.brushSettings = premiumBrushes.first { it.id == "technical-ink" }
                    .toStressSettings().copy(sizePx = 28f, color = Color.BLACK)
                sentinels.forEachIndexed { index, point -> drawStroke(view, point, index) }

                repeat(500) { stroke ->
                    val preset = presets[stroke % presets.size]
                    view.brushSettings = preset.toStressSettings().copy(
                        sizePx = 180f,
                        color = Color.rgb(82, 92, 108),
                    )
                    drawLongStroke(
                        view = view,
                        y = 300f + (stroke % 45) * 35f,
                        index = sentinels.size + stroke,
                    )
                }
            }
            val result = instrumentation.runOnMainSyncWithResult {
                view.exportCompositeBitmap(includePaper = false)
            }
            try {
                val retained = sentinels.count { result.hasDarkInkNear(it) }
                assertTrue(
                    "La carga mixta de 500 trazos perdió centinelas antiguos: $retained/${sentinels.size}",
                    retained == sentinels.size,
                )
            } finally {
                result.recycle()
            }
            val elapsed = android.os.SystemClock.elapsedRealtime() - start
            assertTrue("La prueba masiva tardó demasiado: ${elapsed}ms", elapsed < 120_000L)
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
        tipProfile = tipProfile,
        grainProfile = grainProfile,
        renderProfile = renderProfile,
        dynamicsProfile = dynamicsProfile,
        dualBrushProfile = dualBrushProfile,
    )

    private fun BrushPreset.toModifiedSettings(sizePx: Float): BrushSettings = BrushSettings(
        sizePx = sizePx,
        opacity = opacity,
        color = Color.rgb(24, 28, 34),
        hardness = hardness,
        spacing = spacing,
        stabilization = 0f,
        flow = flow,
        minSize = minSize,
        pressureSize = false,
        pressureOpacity = false,
        pressureCurve = pressureCurve,
        tiltResponse = tiltResponse,
        taperStart = taperStart,
        taperEnd = taperEnd,
        scatter = scatter,
        grain = grain,
        velocitySize = velocitySize,
        kind = kind,
        tipProfile = tipProfile,
        grainProfile = grainProfile,
        renderProfile = renderProfile,
        dynamicsProfile = dynamicsProfile,
        dualBrushProfile = dualBrushProfile,
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

    private fun drawLongStroke(view: DrawingView, y: Float, index: Int) {
        val downTime = 10_000L + index * 100L
        val startX = 80f
        val endX = DOCUMENT_SIZE - 80f
        dispatch(view, downTime, downTime, MotionEvent.ACTION_DOWN, startX, y)
        repeat(48) { step ->
            val progress = (step + 1f) / 48f
            dispatch(
                view = view,
                downTime = downTime,
                eventTime = downTime + (step + 1) * 8L,
                action = MotionEvent.ACTION_MOVE,
                x = startX + (endX - startX) * progress,
                y = y + if (step % 2 == 0) 12f else -12f,
            )
        }
        dispatch(view, downTime, downTime + 400L, MotionEvent.ACTION_UP, endX, y)
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

    private fun Bitmap.hasDarkInkNear(point: TestPoint): Boolean {
        val area = Rect(
            (point.x - 92f).toInt().coerceAtLeast(0),
            (point.y - 60f).toInt().coerceAtLeast(0),
            (point.x + 92f).toInt().coerceAtMost(width),
            (point.y + 60f).toInt().coerceAtMost(height),
        )
        val pixels = IntArray(area.width() * area.height())
        getPixels(pixels, 0, area.width(), area.left, area.top, area.width(), area.height())
        return pixels.count {
            Color.alpha(it) > 16 &&
                Color.red(it) < 45 &&
                Color.green(it) < 45 &&
                Color.blue(it) < 45
        } >= 12
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
        const val LONG_HB_STROKES = 64
    }
}
