package com.orbyte.canvasstudio.drawing.brush

import com.orbyte.canvasstudio.drawing.BrushSettings
import com.orbyte.canvasstudio.drawing.DrawingTool
import com.orbyte.canvasstudio.drawing.StrokePoint
import com.orbyte.canvasstudio.drawing.pipeline.BrushDab
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Deterministic material-response fixtures shared by instrumentation and renderer A/B tests. */
internal object BrushFixture {
    enum class Scenario {
        SLOW_LINE,
        PRESSURE_INCREASING,
        PRESSURE_DECREASING,
        SLOW_PRESSURE,
        FAST_LINE,
        CURVE,
        ZIGZAG,
        CIRCLES,
        TILT_PROGRESSIVE,
        TILT_SHADING,
        OVERLAPPING_PASSES,
        FOUR_TILES,
        PREVIEW,
    }

    fun points(scenario: Scenario): List<StrokePoint> = when (scenario) {
        Scenario.SLOW_LINE -> List(33) { index ->
            val t = index / 32f
            StrokePoint(80f + t * 720f, 110f, .58f, .08f, index * 18L, 0f)
        }
        Scenario.SLOW_PRESSURE, Scenario.PRESSURE_INCREASING -> List(33) { index ->
            val t = index / 32f
            StrokePoint(80f + t * 720f, 180f, .05f + t * .95f, .08f, index * 18L, 0f)
        }
        Scenario.PRESSURE_DECREASING -> List(33) { index ->
            val t = index / 32f
            StrokePoint(80f + t * 720f, 210f, 1f - t * .95f, .08f, index * 18L, 0f)
        }
        Scenario.FAST_LINE -> List(17) { index ->
            val t = index / 16f
            StrokePoint(80f + t * 720f, 250f, .62f, .1f, index * 3L, 0f)
        }
        Scenario.CURVE -> List(41) { index ->
            val t = index / 40f
            StrokePoint(
                90f + t * 700f,
                360f + sin(t * PI * 2.0).toFloat() * 90f,
                .25f + sin(t * PI).toFloat() * .7f,
                .18f,
                index * 12L,
                (t * PI).toFloat(),
            )
        }
        Scenario.ZIGZAG -> List(25) { index ->
            StrokePoint(80f + index * 30f, if (index % 2 == 0) 500f else 610f, .72f, .15f, index * 9L, 0f)
        }
        Scenario.CIRCLES -> List(49) { index ->
            val angle = index / 48f * PI.toFloat() * 2f
            StrokePoint(
                440f + cos(angle) * 170f,
                570f + sin(angle) * 120f,
                .58f,
                .16f,
                index * 11L,
                angle + PI.toFloat() / 2f,
            )
        }
        Scenario.TILT_PROGRESSIVE, Scenario.TILT_SHADING -> List(29) { index ->
            val t = index / 28f
            StrokePoint(100f + t * 680f, 720f, .58f, t, index * 14L, (t * PI * .75).toFloat())
        }
        Scenario.OVERLAPPING_PASSES -> buildList {
            repeat(3) { pass ->
                repeat(21) { index ->
                    val t = index / 20f
                    add(StrokePoint(120f + t * 620f, 840f + pass * 4f, .48f, .24f, (pass * 21 + index) * 11L, 0f))
                }
            }
        }
        Scenario.FOUR_TILES -> listOf(
            StrokePoint(420f, 420f, .2f, .1f, 0L, 0f),
            StrokePoint(604f, 420f, .45f, .25f, 12L, .2f),
            StrokePoint(604f, 604f, .72f, .55f, 24L, .7f),
            StrokePoint(420f, 604f, 1f, .82f, 36L, 1.1f),
        )
        Scenario.PREVIEW -> List(45) { index ->
            val t = index / 44f
            val pressure = (.06f + sin(t * PI).toFloat().coerceAtLeast(0f) * .94f)
            StrokePoint(
                x = 60f + t * 820f,
                y = 160f + sin(t * PI * 2.2).toFloat() * 54f,
                pressure = pressure,
                tilt = (.08f + t * .84f).coerceAtMost(1f),
                timestampMillis = index * if (index < 22) 15L else 5L,
                orientation = (t * PI * .9).toFloat(),
            )
        }
    }

    fun evaluate(
        settings: BrushSettings,
        scenario: Scenario,
        tool: DrawingTool = DrawingTool.BRUSH,
    ): List<BrushDab> {
        val samples = points(scenario)
        return samples.mapIndexed { index, point ->
            val previous = samples.getOrNull(index - 1) ?: point
            val distance = kotlin.math.hypot(point.x - previous.x, point.y - previous.y)
            val elapsed = (point.timestampMillis - previous.timestampMillis).coerceAtLeast(1L)
            BrushEvaluator.evaluate(
                x = point.x,
                y = point.y,
                pressure = point.pressure,
                tilt = point.tilt,
                orientation = point.orientation,
                speedFactor = (distance / elapsed / 2.4f).coerceIn(0f, 1f),
                progress = index / samples.lastIndex.coerceAtLeast(1).toFloat(),
                stampIndex = index,
                settings = settings,
                drawingTool = tool,
            )
        }
    }

    fun stableHash(dabs: List<BrushDab>): Long = dabs.fold(0xcbf29ce484222325UL.toLong()) { hash, dab ->
        listOf(
            dab.x.toRawBits(), dab.y.toRawBits(), dab.radiusX.toRawBits(), dab.radiusY.toRawBits(),
            dab.rotationRadians.toRawBits(), dab.opacity.toRawBits(), dab.flow.toRawBits(), dab.color,
            dab.grainX.toRawBits(), dab.grainY.toRawBits(), dab.randomSeed,
        ).fold(hash) { accumulator, value -> (accumulator xor value.toLong()) * 0x100000001b3L }
    }
}
