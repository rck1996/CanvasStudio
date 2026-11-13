package com.orbyte.canvasstudio.drawing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orbyte.canvasstudio.drawing.brush.StrokePigmentTrace
import com.orbyte.canvasstudio.drawing.brush.usesPigmentPickup
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
    fun feltHasSmoothTranslucentCoverageAcrossInputDensitiesAndBuildsOnAnotherPass() {
        for (id in listOf("marker", "flat-marker")) {
            val brush = requireNotNull(resolveBuiltInBrush(id)).toSettings(Color.BLACK).copy(
                sizePx = 58f, stabilization = 0f, scatter = 0f,
            )
            fun render(step: Int, passes: Int): IntArray {
                val bitmap = Bitmap.createBitmap(900, 160, Bitmap.Config.ARGB_8888)
                try {
                    val points = (64..832 step step).map { x ->
                        StrokePoint(x.toFloat(), 80f, .85f, 0f, x * 4L)
                    }
                    BrushRasterSession().use { session ->
                        val painter = StrokeCommandPainter(session)
                        val command = StrokeCommand(points = points, tool = DrawingTool.BRUSH, settings = brush)
                        repeat(passes) { painter.draw(Canvas(bitmap), command, RectF(0f, 0f, 900f, 160f)) }
                    }
                    return (384..640).map { Color.alpha(bitmap.getPixel(it, 80)) }.toIntArray()
                } finally {
                    bitmap.recycle()
                }
            }
            val coarse = render(24, 1)
            val fine = render(3, 1)
            val overlaid = render(3, 2)
            val min = coarse.min()
            val max = coarse.max()
            assertTrue("$id should look like translucent felt, not ink: $min..$max", min > 60 && max < 245)
            assertTrue("$id has visible periodic banding: $min..$max", max - min < 30)
            assertTrue("$id coverage depends on input density", coarse.indices.all {
                kotlin.math.abs(coarse[it] - fine[it]) <= 2
            })
            assertTrue("$id needs to accumulate on a second pass", overlaid.average() > fine.average() + 8)
        }
    }

    @Test
    fun loadedFlatOilHasNoHolesOrRegularCrossStrokeBandsAtLargeSize() {
        val bitmap = Bitmap.createBitmap(1280, 360, Bitmap.Config.ARGB_8888)
        try {
            val brush = requireNotNull(resolveBuiltInBrush("thick-oil")).toSettings(Color.BLACK).copy(
                sizePx = 180f, scatter = 0f,
            )
            val points = (80..1200 step 20).map { x -> StrokePoint(x.toFloat(), 180f, .95f, 0f, x * 4L) }
            BrushRasterSession().use { session ->
                StrokeCommandPainter(session).draw(
                    Canvas(bitmap), StrokeCommand(points = points, tool = DrawingTool.BRUSH, settings = brush),
                    RectF(0f, 0f, 1280f, 360f),
                )
            }
            val columns = (400..799).map { x -> (168..192).map { y -> Color.alpha(bitmap.getPixel(x, y)) }.average() }
            assertTrue("Loaded oil must be dense through the center", columns.min() > 190.0)
            assertTrue("Flat oil should retain texture without bands across its core",
                columns.max() - columns.min() < 40.0)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun dryMaterialsGainBodyAtFirmPressureWithoutBecomingSolidInk() {
        fun render(id: String, pressure: Float): IntArray {
            val bitmap = Bitmap.createBitmap(640, 192, Bitmap.Config.ARGB_8888)
            try {
                val settings = requireNotNull(resolveBuiltInBrush(id)).toSettings(Color.BLACK).copy(sizePx = 84f)
                val points = (0..56).map { index ->
                    StrokePoint(80f + index * 8f, 96f, pressure, 0f, index * 12L)
                }
                BrushRasterSession().use { session ->
                    StrokeCommandPainter(session).draw(Canvas(bitmap),
                        StrokeCommand(points = points, tool = DrawingTool.BRUSH, settings = settings),
                        RectF(0f, 0f, 640f, 192f))
                }
                return IntArray(192 * 84).also { bitmap.getPixels(it, 0, 192, 224, 54, 192, 84) }
            } finally {
                bitmap.recycle()
            }
        }
        for (id in listOf("charcoal", "dry-brush")) {
            val light = render(id, .27f)
            val firm = render(id, 1f)
            val lightlyCovered = light.count { Color.alpha(it) > 30 }
            val firmlyCovered = firm.count { Color.alpha(it) > 90 }
            assertTrue("$id should leave a visible dry trace", lightlyCovered > 45)
            assertTrue("$id should have a connected, loaded body at firm pressure",
                firmlyCovered > maxOf(lightlyCovered, 192 * 8))
            assertTrue("$id needs intermediate textured coverage",
                firm.count { Color.alpha(it) in 35..210 } > 192 * 3)
        }
    }

    @Test
    fun renderFullLibraryWithPressurePatchAndGlaze() {
        val presets = premiumBrushes + experimentalBrushes
        val width = 1440
        val rowHeight = 124
        val bitmap = Bitmap.createBitmap(width, 64 + presets.size * rowHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paper = Paint().apply { color = Color.rgb(250, 247, 240) }
        val divider = Paint().apply { color = Color.rgb(221, 214, 202) }
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(35, 33, 30); textSize = 20f }
        canvas.drawText("Canvas Studio · Materiales", 24f, 36f, label.apply { textSize = 16f })
        label.textSize = 20f
        canvas.drawText("Presión suave → firme", 248f, 36f, label)
        canvas.drawText("Mancha corta", 754f, 36f, label)
        canvas.drawText("Pasadas superpuestas", 1060f, 36f, label)
        val frame = RectF(0f, 0f, width.toFloat(), bitmap.height.toFloat())
        try {
            BrushRasterSession().use { session ->
                val painter = StrokeCommandPainter(session)
                presets.forEachIndexed { index, preset ->
                    val top = 64f + index * rowHeight
                    val middle = top + 62f
                    canvas.drawLine(24f, top, width - 24f, top, divider)
                    canvas.drawText(preset.name, 24f, middle, label)
                    val size = when (preset.kind) {
                        BrushKind.PENCIL -> preset.sizePx.coerceIn(10f, 35f)
                        BrushKind.INK -> preset.sizePx.coerceIn(8f, 48f)
                        else -> preset.sizePx.coerceIn(26f, 82f)
                    }
                    val brush = preset.toSettings(Color.rgb(21, 21, 23)).copy(sizePx = size)
                    fun draw(points: List<StrokePoint>, settings: BrushSettings = brush) {
                        val trace = if (usesPigmentPickup(DrawingTool.BRUSH, settings)) {
                            StrokePigmentTrace.capture(points, settings) { point ->
                                bitmap.getPixel(point.x.toInt(), point.y.toInt())
                            }
                        } else null
                        painter.draw(canvas, StrokeCommand(points = points, tool = DrawingTool.BRUSH,
                            settings = settings, pigmentTrace = trace), frame)
                    }
                    draw((0..38).map { step ->
                        val progress = step / 38f
                        StrokePoint(250f + progress * 465f, middle - sin(progress * 6.28f) * 14f,
                            .19f + .81f * progress, .18f, step * 12L)
                    })
                    draw((0..35).map { step ->
                        val progress = step / 35f
                        StrokePoint(870f + sin(progress * 18.85f) * 87f, middle - 28f + progress * 55f,
                            .48f + progress * .5f, .38f, step * 10L)
                    })
                    val overlapSize = maxOf(19f, size)
                    draw((0..20).map { step ->
                        StrokePoint(1064f + step * 14f, middle - 15f + step * 1.45f, .7f, .16f, step * 12L)
                    }, brush.copy(sizePx = overlapSize, color = Color.rgb(50, 87, 115)))
                    draw((0..20).map { step ->
                        StrokePoint(1064f + step * 14f, middle + 15f - step * 1.45f, .7f, .16f, step * 12L)
                    }, brush.copy(sizePx = overlapSize, color = Color.rgb(147, 61, 43)))
                    assertTrue("Blank pressure sample: ${preset.name}", (255..710 step 5).any { x ->
                        (middle.toInt() - 24..middle.toInt() + 24).any { y -> Color.alpha(bitmap.getPixel(x, y)) > 0 }
                    })
                }
            }
            canvas.drawColor(paper.color, PorterDuff.Mode.DST_OVER)
            val output = File(requireNotNull(context.getExternalFilesDir(null)), "canvasstudio-library-pressure-matrix.png")
            output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun renderProfessionalFamilyComparisonSheet() {
        val view = DrawingView(context)
        val presets = BrushKind.values().map { kind ->
            allBuiltInBrushes.first { it.kind == kind }
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
                val output = File(
                    requireNotNull(context.getExternalFilesDir(null)),
                    "phase9-professional-brush-matrix.png",
                )
                output.outputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                val faint = presets.indices.filterNot { bitmap.rowHasInk(it) }.map { presets[it].name }
                assertTrue("Pinceles sin contraste suficiente en la matriz visual: $faint", faint.isEmpty())
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
