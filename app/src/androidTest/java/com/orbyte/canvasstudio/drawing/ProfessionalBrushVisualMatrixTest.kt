package com.orbyte.canvasstudio.drawing

import android.graphics.Bitmap
import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class ProfessionalBrushVisualMatrixTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test
    fun renderProfessionalFamilyComparisonSheet() {
        val view = DrawingView(context)
        val presets = BrushKind.values().map { kind ->
            premiumBrushes.first { it.kind == kind }
        }
        try {
            instrumentation.runOnMainSync {
                view.configureDocument(SHEET_WIDTH, SHEET_HEIGHT)
                view.tool = DrawingTool.BRUSH
                presets.forEachIndexed { row, preset ->
                    view.brushSettings = preset.toVisualSettings()
                    drawExpressiveStroke(view, row, preset)
                }
            }
            val bitmap = instrumentation.runOnMainSyncWithResult {
                view.exportCompositeBitmap(includePaper = true)
            }
            try {
                presets.indices.forEach { row ->
                    assertTrue(
                        "${presets[row].name} no aparece en la matriz visual",
                        bitmap.rowHasInk(row),
                    )
                }
                val output = File(
                    requireNotNull(context.getExternalFilesDir(null)),
                    "phase9-professional-brush-matrix.png",
                )
                output.outputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally {
                bitmap.recycle()
            }
        } finally {
            instrumentation.runOnMainSync { view.configureDocument(256, 256) }
        }
    }

    private fun BrushPreset.toVisualSettings(): BrushSettings = BrushSettings(
        sizePx = sizePx.coerceIn(26f, 96f),
        opacity = opacity.coerceAtLeast(.58f),
        color = Color.rgb(28, 34, 43),
        hardness = hardness,
        spacing = spacing,
        stabilization = stabilization,
        flow = flow.coerceAtLeast(.55f),
        minSize = minSize,
        pressureSize = pressureSize,
        pressureOpacity = pressureOpacity,
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

    private fun drawExpressiveStroke(view: DrawingView, row: Int, preset: BrushPreset) {
        val downTime = 40_000L + row * 2_000L
        val centerY = ROW_HEIGHT * row + ROW_HEIGHT / 2f
        repeat(3) { pass ->
            val passY = centerY + (pass - 1) * 36f
            dispatch(
                view,
                downTime + pass * 500,
                downTime + pass * 500,
                MotionEvent.ACTION_DOWN,
                START_X,
                passY,
                pressure = .16f + pass * .18f,
                tilt = if (preset.kind == BrushKind.PENCIL) .7f else .2f,
                orientation = -.5f + pass * .5f,
            )
            repeat(54) { step ->
                val progress = (step + 1f) / 54f
                dispatch(
                    view,
                    downTime + pass * 500,
                    downTime + pass * 500 + (step + 1) * 8L,
                    MotionEvent.ACTION_MOVE,
                    START_X + (END_X - START_X) * progress,
                    passY + sin(progress * Math.PI * 2.4).toFloat() * 24f,
                    pressure = (.14f + progress * .78f).coerceIn(.08f, 1f),
                    tilt = if (preset.kind == BrushKind.PENCIL) .72f - progress * .38f else .22f,
                    orientation = -.75f + progress * 1.5f,
                )
            }
            dispatch(
                view,
                downTime + pass * 500,
                downTime + pass * 500 + 460L,
                MotionEvent.ACTION_UP,
                END_X,
                passY,
                pressure = .92f,
                tilt = .2f,
                orientation = .75f,
            )
        }
    }

    private fun dispatch(
        view: DrawingView,
        downTime: Long,
        eventTime: Long,
        action: Int,
        x: Float,
        y: Float,
        pressure: Float,
        tilt: Float,
        orientation: Float,
    ) {
        val properties = arrayOf(
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_STYLUS
            },
        )
        val coordinates = arrayOf(
            MotionEvent.PointerCoords().apply {
                this.x = x
                this.y = y
                this.pressure = pressure
                size = .05f
                setAxisValue(MotionEvent.AXIS_TILT, tilt)
                setAxisValue(MotionEvent.AXIS_ORIENTATION, orientation)
            },
        )
        MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            1,
            properties,
            coordinates,
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_STYLUS,
            0,
        ).also { event ->
            try {
                view.onTouchEvent(event)
            } finally {
                event.recycle()
            }
        }
    }

    private fun Bitmap.rowHasInk(row: Int): Boolean {
        val top = (row * ROW_HEIGHT + 20).coerceAtLeast(0)
        val bottom = ((row + 1) * ROW_HEIGHT - 20).coerceAtMost(height)
        var inkPixels = 0
        for (y in top until bottom step 4) {
            for (x in START_X.toInt() until END_X.toInt() step 4) {
                val color = getPixel(x, y)
                if (Color.red(color) < 180 && Color.green(color) < 180 && Color.blue(color) < 180) {
                    inkPixels++
                    if (inkPixels >= 30) return true
                }
            }
        }
        return false
    }

    private fun <T> android.app.Instrumentation.runOnMainSyncWithResult(block: () -> T): T {
        var result: Result<T>? = null
        runOnMainSync { result = runCatching(block) }
        return requireNotNull(result).getOrThrow()
    }

    private companion object {
        const val SHEET_WIDTH = 2048
        const val ROW_HEIGHT = 176
        const val SHEET_HEIGHT = ROW_HEIGHT * 11
        const val START_X = 110f
        const val END_X = 1938f
    }
}
