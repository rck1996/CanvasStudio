package com.orbyte.canvasstudio.drawing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import com.orbyte.canvasstudio.drawing.brush.BrushEvaluator
import com.orbyte.canvasstudio.drawing.performance.DrawingPerformanceMetrics
import java.io.Closeable
import java.io.File
import java.util.LinkedHashMap
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Authoritative CPU brush painter, independent of DrawingView.
 *
 * A session exclusively owns every mutable Paint, shader matrix, Bitmap cache
 * and BlurMaskFilter. The caller must confine one session to exactly one thread.
 * Use one instance for UI and create independent instances for background tile
 * workers; never share this session between dispatchers.
 *
 * Algorithms are intentionally moved byte-for-byte from DrawingView to keep
 * project history and existing brush pixels compatible in the first extraction.
 * This is a seam for asynchronous rasterization, not an async executor itself.
 */
internal class BrushRasterSession(
    private val performanceMetrics: DrawingPerformanceMetrics = DrawingPerformanceMetrics(false),
) : Closeable {
    internal val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val clearXfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    private val sourceOverXfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
    private val multiplyXfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
    private val screenXfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    private val tipBitmapCache = object : LinkedHashMap<String, Bitmap>(8, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
            val remove = size > 8
            if (remove) eldest?.value?.recycle()
            return remove
        }
    }
    private data class GrainTextureEntry(
        val bitmap: Bitmap,
        val shader: BitmapShader,
        var stationaryScale: Float? = null,
    )
    private val grainTextureCache =
        object : LinkedHashMap<BrushTextureKey, GrainTextureEntry>(12, .75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<BrushTextureKey, GrainTextureEntry>?,
            ): Boolean {
                val remove = size > 12
                if (remove) eldest?.value?.bitmap?.recycle()
                return remove
            }
        }
    private val grainShaderMatrix = Matrix()
    private var lastTintColor = 0
    private var lastTintFilter: PorterDuffColorFilter? = null
    private var cachedMaskFilterKey: Int = Int.MIN_VALUE
    private var cachedMaskFilter: BlurMaskFilter? = null

    internal fun isStampBrush(kind: BrushKind): Boolean = when (kind) {
        BrushKind.PENCIL,
        BrushKind.INK,
        BrushKind.MARKER,
        BrushKind.PAINT,
        BrushKind.AIRBRUSH,
        BrushKind.CHARCOAL,
        BrushKind.CHALK,
        BrushKind.DRY_BRUSH,
        BrushKind.BRISTLE,
        BrushKind.WATERCOLOR,
        BrushKind.OIL,
        -> true
    }

    internal fun stampSpacing(
        settings: BrushSettings,
        pressure: Float,
        speedFactor: Float,
        tool: DrawingTool,
    ): Float {
        val requested = settings.spacing.coerceIn(0.025f, 0.5f)
        val floor = when (settings.kind) {
            BrushKind.CHARCOAL, BrushKind.CHALK, BrushKind.DRY_BRUSH -> 0.13f
            BrushKind.BRISTLE -> 0.1f
            BrushKind.PAINT, BrushKind.WATERCOLOR, BrushKind.OIL -> 0.09f
            BrushKind.AIRBRUSH -> 0.1f
            BrushKind.MARKER -> 0.08f
            else -> requested
        }
        val largeTipFloor = when (settings.kind) {
            BrushKind.BRISTLE, BrushKind.WATERCOLOR, BrushKind.OIL -> .20f
            else -> floor
        }
        if (settings.sizePx <= 96f || largeTipFloor == floor || tool == DrawingTool.ERASER) {
            return max(requested, floor)
        }
        val minimum = settings.minSize.coerceIn(.02f, 1f)
        val pressureWidth = if (settings.pressureSize) {
            val curved = applyInputCurve(
                calibratedPressure(pressure, settings.pressureCurve),
                settings.dynamicsProfile.sizePressure,
            )
            minimum + curved * (1f - minimum)
        } else {
            1f
        }
        val velocityWidth = 1f - max(settings.velocitySize, settings.dynamicsProfile.velocitySize)
            .coerceIn(0f, 1f) * speedFactor * .62f
        val widthFactor = pressureWidth * velocityWidth
        // A nominally large brush can still produce a thin dab at light pressure.
        val fullTipWeight = ((widthFactor - .55f) / .30f).coerceIn(0f, 1f)
        // At large sizes these tips already overlap heavily. Keep their full
        // geometry and grain, while limiting repeated overdraw of the same area.
        val largeTipWeight = ((settings.sizePx - 96f) / 84f).coerceIn(0f, 1f)
        return max(requested, floor + (largeTipFloor - floor) * largeTipWeight * fullTipWeight)
    }

    internal fun drawStrokeSegment(
        canvas: Canvas,
        from: StrokePoint,
        to: StrokePoint,
        drawingTool: DrawingTool,
        settings: BrushSettings,
        progress: Float = 1f,
        segmentIndex: Int = 0,
        sampledColor: Int? = null,
    ) {
        val deltaX = to.x - from.x
        val deltaY = to.y - from.y
        val distance = hypot(deltaX, deltaY)
        val elapsed = (to.timestampMillis - from.timestampMillis).coerceAtLeast(1L).toFloat()
        val speed = distance / elapsed
        val speedFactor = (speed / 2.4f).coerceIn(0f, 1f)
        val taper = taperFactor(settings, progress)
        val effectivePressure = ((from.pressure + to.pressure) / 2f) * taper
        val angleRadians = atan2(deltaY.toDouble(), deltaX.toDouble()).toFloat()
        val stampBased = isStampBrush(settings.kind)
        val pigmentColor = mixPigmentColor(
            settings.color,
            sampledColor,
            settings.renderProfile.colorPickup,
        )

        if (stampBased) {
            val minimumPressure = min(from.pressure, to.pressure) * taper
            val step = max(1.5f, settings.sizePx * stampSpacing(settings, minimumPressure, speedFactor, drawingTool))
            val stampCount = max(1, ceil(distance / step).toInt()).coerceAtMost(MAX_STAMPS_PER_SEGMENT)
            repeat(stampCount) { index ->
                val stampProgress = (index + 1f) / stampCount.toFloat()
                drawBrushStamp(
                    canvas = canvas,
                    x = from.x + deltaX * stampProgress,
                    y = from.y + deltaY * stampProgress,
                    pressure = (from.pressure + (to.pressure - from.pressure) * stampProgress) * taper,
                    tilt = from.tilt + (to.tilt - from.tilt) * stampProgress,
                    drawingTool = drawingTool,
                    settings = settings,
                    stampIndex = segmentIndex.coerceAtLeast(0) * MAX_STAMPS_PER_SEGMENT + index,
                    angleRadians = angleRadians,
                    orientation = interpolateCircularRadians(
                        from.orientation,
                        to.orientation,
                        stampProgress,
                    ),
                    speedFactor = speedFactor,
                    resolvedColor = pigmentColor,
                    strokeProgress = progress,
                )
            }
            return
        }

        val tilt = (from.tilt + to.tilt) / 2f
        configurePaint(
            strokePaint,
            drawingTool,
            settings,
            effectivePressure,
            tilt,
            speedFactor = speedFactor,
            resolvedColor = pigmentColor,
        )
        canvas.drawLine(from.x, from.y, to.x, to.y, strokePaint)

        if (settings.kind == BrushKind.PENCIL && drawingTool != DrawingTool.ERASER) {
            val originalAlpha = strokePaint.alpha
            val originalWidth = strokePaint.strokeWidth
            strokePaint.alpha = (originalAlpha * (0.18f + settings.grain * 0.28f)).toInt().coerceIn(1, 255)
            strokePaint.strokeWidth = max(0.7f, originalWidth * 0.28f)
            val offset = max(0.45f, originalWidth * 0.09f)
            canvas.drawLine(from.x + offset, from.y - offset, to.x + offset, to.y - offset, strokePaint)
            strokePaint.alpha = originalAlpha
            strokePaint.strokeWidth = originalWidth
        }
    }

    internal fun drawBrushStamp(
        canvas: Canvas,
        x: Float,
        y: Float,
        pressure: Float,
        tilt: Float,
        drawingTool: DrawingTool,
        settings: BrushSettings,
        stampIndex: Int,
        angleRadians: Float,
        orientation: Float = 0f,
        speedFactor: Float = 0f,
        resolvedColor: Int = settings.color,
        strokeProgress: Float = 0f,
    ) {
        configurePaint(
            strokePaint,
            drawingTool,
            settings,
            pressure,
            tilt,
            speedFactor = speedFactor,
            resolvedColor = resolvedColor,
        )
        if (drawingTool != DrawingTool.ERASER && settings.renderProfile.wetness > .001f) {
            val charge = settings.renderProfile.charge.coerceIn(0f, 1f)
            val attack = settings.renderProfile.attack.coerceIn(0f, 1f)
            val pigmentLoad = charge * (
                1f - strokeProgress.coerceIn(0f, 1f) * (1f - attack) * .72f
                )
            strokePaint.alpha = (strokePaint.alpha * pigmentLoad.coerceIn(.08f, 1f))
                .toInt().coerceIn(1, 255)
        }
        if (drawingTool != DrawingTool.ERASER) {
            strokePaint.alpha = (
                strokePaint.alpha *
                    grainCoverage(settings.grainProfile, x, y, stampIndex)
                ).toInt().coerceIn(1, 255)
        }
        val diameter = strokePaint.strokeWidth
        val radius = max(0.6f, diameter / 2f)
        strokePaint.style = Paint.Style.FILL

        val seed = x.toInt() * 73856093 xor y.toInt() * 19349663 xor stampIndex * 83492791
        val noiseX = ((seed and 0xFFFF) / 32767.5f) - 1f
        val noiseY = (((seed ushr 16) and 0xFFFF) / 32767.5f) - 1f
        val scatterRadius = radius * settings.scatter.coerceIn(0f, 1f)
        val stampX = x + noiseX * scatterRadius
        val stampY = y + noiseY * scatterRadius
        val resolvedAngle = resolveBrushRotation(
            settings = settings,
            strokeAngle = angleRadians,
            stylusOrientation = orientation,
            seed = seed,
        )
        val customTip = settings.tipAssetPath?.let(::loadTipBitmap)
        if (customTip != null) {
            canvas.save()
            canvas.rotate(resolvedAngle * 180f / PI.toFloat(), stampX, stampY)
            val aspect = customTip.width.toFloat() / customTip.height.coerceAtLeast(1)
            val halfWidth = if (aspect >= 1f) radius else radius * aspect
            val halfHeight = if (aspect >= 1f) radius / aspect else radius
            strokePaint.colorFilter = if (drawingTool == DrawingTool.ERASER) {
                null
            } else {
                pigmentTint(resolvedColor)
            }
            canvas.drawBitmap(
                customTip,
                null,
                RectF(
                    stampX - halfWidth,
                    stampY - halfHeight,
                    stampX + halfWidth,
                    stampY + halfHeight,
                ),
                strokePaint,
            )
            strokePaint.colorFilter = null
            canvas.restore()
            drawDualBrushStamp(
                canvas = canvas,
                x = stampX,
                y = stampY,
                radius = radius,
                settings = settings,
                drawingTool = drawingTool,
                stampIndex = stampIndex,
                angleRadians = resolvedAngle,
                seed = seed,
                resolvedColor = resolvedColor,
            )
            strokePaint.style = Paint.Style.STROKE
            return
        }
        applyGrainTexture(
            paint = strokePaint,
            settings = settings,
            drawingTool = drawingTool,
            x = stampX,
            y = stampY,
            stampIndex = stampIndex,
            angleRadians = resolvedAngle,
            resolvedColor = resolvedColor,
        )

        when (settings.kind) {
            BrushKind.PENCIL -> {
                val baseAlpha = strokePaint.alpha
                val roundness = settings.tipProfile.roundness.coerceIn(.08f, 1f)
                val tiltWidth = 1f + tilt * settings.tiltResponse.coerceIn(0f, 1f) * 1.35f
                canvas.save()
                canvas.rotate(resolvedAngle * 180f / PI.toFloat(), stampX, stampY)
                canvas.drawOval(
                    stampX - radius * tiltWidth,
                    stampY - radius * roundness,
                    stampX + radius * tiltWidth,
                    stampY + radius * roundness,
                    strokePaint,
                )
                if (settings.grainProfile.depth > .08f) {
                    strokePaint.alpha = (baseAlpha * .24f).toInt().coerceIn(1, 255)
                    strokePaint.style = Paint.Style.STROKE
                    strokePaint.strokeWidth = max(.55f, diameter * .055f)
                    val toothOffset = radius * roundness * .42f
                    canvas.drawLine(
                        stampX - radius * .74f,
                        stampY - toothOffset,
                        stampX + radius * .74f,
                        stampY - toothOffset,
                        strokePaint,
                    )
                    strokePaint.style = Paint.Style.FILL
                }
                canvas.restore()
                strokePaint.alpha = baseAlpha
            }

            BrushKind.INK -> {
                canvas.save()
                canvas.rotate(resolvedAngle * 180f / PI.toFloat(), stampX, stampY)
                when (settings.tipProfile.shape) {
                    BrushTipShape.ROUND -> canvas.drawCircle(stampX, stampY, radius, strokePaint)
                    BrushTipShape.OVAL, BrushTipShape.CHISEL -> canvas.drawOval(
                        stampX - radius,
                        stampY - radius * settings.tipProfile.roundness.coerceIn(.08f, 1f),
                        stampX + radius,
                        stampY + radius * settings.tipProfile.roundness.coerceIn(.08f, 1f),
                        strokePaint,
                    )
                    BrushTipShape.BRISTLE, BrushTipShape.PARTICLE -> {
                        val count = settings.tipProfile.count.coerceIn(2, 8)
                        repeat(count) { part ->
                            val unit = part / (count - 1f)
                            val offset = (unit - .5f) * radius * 1.2f
                            canvas.drawOval(
                                stampX - radius,
                                stampY + offset - max(.4f, radius * .05f),
                                stampX + radius,
                                stampY + offset + max(.4f, radius * .05f),
                                strokePaint,
                            )
                        }
                    }
                }
                canvas.restore()
            }

            BrushKind.CHARCOAL, BrushKind.CHALK -> {
                val adaptiveCount = when {
                    diameter >= 144f -> 2
                    diameter >= 96f -> 3
                    else -> settings.tipProfile.count
                }
                val countNoise = (((seed ushr 5) and 0xFF) / 255f - .5f) *
                    settings.tipProfile.countJitter.coerceIn(0f, 1f)
                val particles = (adaptiveCount * (1f + countNoise)).toInt().coerceIn(2, 12)
                val baseAlpha = strokePaint.alpha
                // A translucent directional core keeps very large charcoal/chalk strokes
                // continuous; particles alone look like disconnected circles above ~100 px.
                canvas.save()
                canvas.rotate(resolvedAngle * 180f / PI.toFloat(), stampX, stampY)
                strokePaint.alpha = (
                    baseAlpha * if (settings.kind == BrushKind.CHARCOAL) .2f else .14f
                ).toInt().coerceIn(1, 255)
                canvas.drawOval(
                    stampX - radius * .82f,
                    stampY - radius * .2f,
                    stampX + radius * .82f,
                    stampY + radius * .2f,
                    strokePaint,
                )
                canvas.restore()
                strokePaint.alpha = baseAlpha
                val spread = radius * (0.48f + tilt * settings.tiltResponse + settings.scatter)
                repeat(particles) { particle ->
                    val particleSeed = seed xor (particle + 1) * 83492791
                    val xNoise = ((particleSeed and 0xFFFF) / 32767.5f) - 1f
                    val yNoise = (((particleSeed ushr 16) and 0xFFFF) / 32767.5f) - 1f
                    val grain = settings.grain.coerceIn(0f, 1f)
                    val particleRadius = radius * (0.08f + ((particleSeed ushr 8) and 0xFF) / 255f * (0.18f + grain * 0.22f))
                    strokePaint.alpha = (baseAlpha * (0.18f + grain * 0.28f + particle * 0.045f)).toInt().coerceIn(1, 255)
                    val particleX = stampX + xNoise * spread
                    val particleY = stampY + yNoise * spread
                    val aspect = .32f + ((particleSeed ushr 20) and 0xFF) / 255f * .54f
                    canvas.save()
                    canvas.rotate(
                        (((particleSeed ushr 12) and 0xFF) / 255f * 180f) +
                            resolvedAngle * 180f / PI.toFloat(),
                        particleX,
                        particleY,
                    )
                    canvas.drawOval(
                        particleX - max(.55f, particleRadius),
                        particleY - max(.45f, particleRadius * aspect),
                        particleX + max(.55f, particleRadius),
                        particleY + max(.45f, particleRadius * aspect),
                        strokePaint,
                    )
                    canvas.restore()
                }
                strokePaint.alpha = baseAlpha
            }

            BrushKind.MARKER -> {
                canvas.save()
                canvas.rotate(resolvedAngle * 180f / PI.toFloat(), stampX, stampY)
                val roundness = settings.tipProfile.roundness.coerceIn(.12f, 1f)
                canvas.drawOval(
                    stampX - radius,
                    stampY - radius * roundness,
                    stampX + radius,
                    stampY + radius * roundness,
                    strokePaint,
                )
                canvas.restore()
            }

            BrushKind.PAINT -> {
                val baseAlpha = strokePaint.alpha
                canvas.drawCircle(stampX, stampY, radius, strokePaint)
                if (settings.grain > 0f) {
                    val grainParticles = if (diameter >= 120f) 1 else 2
                    repeat(grainParticles) { particle ->
                        val particleSeed = seed xor particle * 265443576
                        val px = (((particleSeed and 0xFFFF) / 32767.5f) - 1f) * radius * .55f
                        val py = ((((particleSeed ushr 16) and 0xFFFF) / 32767.5f) - 1f) * radius * .55f
                        strokePaint.alpha = (baseAlpha * settings.grain * .18f).toInt().coerceIn(1, 255)
                        canvas.drawCircle(stampX + px, stampY + py, max(.8f, radius * .12f), strokePaint)
                    }
                    strokePaint.alpha = baseAlpha
                }
            }

            BrushKind.DRY_BRUSH -> {
                val baseAlpha = strokePaint.alpha
                canvas.save()
                canvas.rotate(resolvedAngle * 180f / PI.toFloat(), stampX, stampY)
                val bristleCount = settings.tipProfile.count.coerceIn(3, 12)
                repeat(bristleCount) { bristle ->
                    if (((seed ushr (bristle * 3)) and 0x3) != 0) {
                        val offset = (bristle - (bristleCount - 1) / 2f) *
                            radius * 1.12f / bristleCount
                        strokePaint.alpha = (
                            baseAlpha * (.3f + bristle * .36f / bristleCount)
                            ).toInt().coerceIn(1, 255)
                        canvas.drawOval(
                            stampX - radius * .7f,
                            stampY + offset - radius * .08f,
                            stampX + radius * .7f,
                            stampY + offset + radius * .08f,
                            strokePaint,
                        )
                    }
                }
                canvas.restore()
                strokePaint.alpha = baseAlpha
            }

            BrushKind.BRISTLE -> {
                val baseAlpha = strokePaint.alpha
                canvas.save()
                canvas.rotate(resolvedAngle * 180f / PI.toFloat(), stampX, stampY)
                val bristleCount = settings.tipProfile.count.coerceIn(3, 14)
                repeat(bristleCount) { bristle ->
                    val offset = (bristle - (bristleCount - 1) / 2f) *
                        radius * 1.05f / bristleCount
                    strokePaint.alpha = (
                        baseAlpha * (.42f + bristle * .4f / bristleCount)
                        ).toInt().coerceIn(1, 255)
                    canvas.drawOval(
                        stampX - radius,
                        stampY + offset - radius * .055f,
                        stampX + radius,
                        stampY + offset + radius * .055f,
                        strokePaint,
                    )
                }
                canvas.restore()
                strokePaint.alpha = baseAlpha
            }

            BrushKind.WATERCOLOR -> {
                val baseAlpha = strokePaint.alpha
                val wetness = settings.renderProfile.wetness.coerceIn(0f, 1f)
                val dilution = settings.renderProfile.dilution.coerceIn(0f, 1f)
                val bleed = settings.renderProfile.bleed.coerceIn(0f, 1f)
                val wetRadius = radius * (1f + bleed * .12f)
                strokePaint.alpha = (
                    baseAlpha * (.64f - dilution * .24f)
                    ).toInt().coerceIn(1, 255)
                canvas.drawOval(
                    stampX - wetRadius,
                    stampY - wetRadius * (.78f + wetness * .18f),
                    stampX + wetRadius,
                    stampY + wetRadius * (.78f + wetness * .18f),
                    strokePaint,
                )
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = max(1f, radius * (.035f + wetness * .055f))
                strokePaint.alpha = (
                    baseAlpha * (.1f + wetness * .18f + settings.grain * .12f)
                    ).toInt().coerceIn(1, 255)
                canvas.drawOval(
                    stampX - radius * .91f + noiseX * radius * .06f,
                    stampY - radius * .77f + noiseY * radius * .06f,
                    stampX + radius * .91f + noiseX * radius * .06f,
                    stampY + radius * .77f + noiseY * radius * .06f,
                    strokePaint,
                )
                strokePaint.style = Paint.Style.FILL
                strokePaint.alpha = baseAlpha
            }

            BrushKind.OIL -> {
                val baseAlpha = strokePaint.alpha
                val bristleCount = settings.tipProfile.count.coerceIn(4, 14)
                val drag = settings.renderProfile.drag.coerceIn(0f, 1f)
                canvas.save()
                canvas.rotate(resolvedAngle * 180f / PI.toFloat(), stampX, stampY)
                repeat(bristleCount) { bristle ->
                    val unit = bristle / (bristleCount - 1f)
                    val offset = (unit - .5f) * radius * 1.1f
                    val bristleSeed = seed xor (bristle + 3) * 1103515245
                    val lengthNoise = .72f + ((bristleSeed ushr 9) and 0xFF) / 255f * .28f
                    val thickness = radius * (.035f + (1f - drag) * .035f)
                    strokePaint.alpha = (
                        baseAlpha * (.48f + ((bristleSeed ushr 18) and 0xFF) / 255f * .42f)
                        ).toInt().coerceIn(1, 255)
                    canvas.drawOval(
                        stampX - radius * lengthNoise,
                        stampY + offset - thickness,
                        stampX + radius,
                        stampY + offset + thickness,
                        strokePaint,
                    )
                }
                canvas.restore()
                strokePaint.alpha = baseAlpha
            }

            BrushKind.AIRBRUSH -> canvas.drawCircle(stampX, stampY, radius, strokePaint)
        }
        drawDualBrushStamp(
            canvas = canvas,
            x = stampX,
            y = stampY,
            radius = radius,
            settings = settings,
            drawingTool = drawingTool,
            stampIndex = stampIndex,
            angleRadians = resolvedAngle,
            seed = seed,
            resolvedColor = resolvedColor,
        )
        strokePaint.shader = null
        strokePaint.colorFilter = null
        strokePaint.style = Paint.Style.STROKE
    }

    private fun drawDualBrushStamp(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        settings: BrushSettings,
        drawingTool: DrawingTool,
        stampIndex: Int,
        angleRadians: Float,
        seed: Int,
        resolvedColor: Int,
    ) {
        val dual = settings.dualBrushProfile
        if (!dual.enabled || dual.opacity <= .001f) return

        val originalAlpha = strokePaint.alpha
        val originalXfermode = strokePaint.xfermode
        val originalStyle = strokePaint.style
        val secondaryRadius = max(.45f, radius * dual.sizeScale.coerceIn(.1f, 2f))
        val noiseX = ((seed ushr 3 and 0xFFFF) / 32767.5f) - 1f
        val noiseY = ((seed ushr 11 and 0xFFFF) / 32767.5f) - 1f
        val normalX = -sin(angleRadians)
        val normalY = cos(angleRadians)
        val offset = radius * dual.offset.coerceIn(-1f, 1f)
        val scatter = radius * dual.scatter.coerceIn(0f, 1f)
        val centerX = x + normalX * offset + noiseX * scatter
        val centerY = y + normalY * offset + noiseY * scatter
        val dualAngle = when (dual.tip.rotationMode) {
            BrushRotationMode.FOLLOW_STROKE -> angleRadians
            BrushRotationMode.FIXED -> 0f
            BrushRotationMode.STYLUS -> angleRadians
            BrushRotationMode.RANDOM -> ((seed ushr 8) and 0xFFFF) / 65535f * 2f * PI.toFloat()
        } + Math.toRadians(dual.tip.angleDegrees.toDouble()).toFloat()

        strokePaint.alpha = (originalAlpha * dual.opacity.coerceIn(0f, 1f))
            .toInt().coerceIn(1, 255)
        if (drawingTool != DrawingTool.ERASER) {
            strokePaint.xfermode = when (dual.blendMode) {
                DualBrushBlendMode.NORMAL -> sourceOverXfermode
                DualBrushBlendMode.MULTIPLY -> multiplyXfermode
                DualBrushBlendMode.SCREEN -> screenXfermode
            }
        }
        strokePaint.style = Paint.Style.FILL
        applyGrainTexture(
            paint = strokePaint,
            settings = settings,
            drawingTool = drawingTool,
            x = centerX,
            y = centerY,
            stampIndex = stampIndex,
            angleRadians = dualAngle,
            profile = dual.grain,
            resolvedColor = resolvedColor,
        )
        canvas.save()
        canvas.rotate(dualAngle * 180f / PI.toFloat(), centerX, centerY)
        val roundness = dual.tip.roundness.coerceIn(.08f, 1f)
        when (dual.tip.shape) {
            BrushTipShape.ROUND -> canvas.drawCircle(centerX, centerY, secondaryRadius, strokePaint)
            BrushTipShape.OVAL, BrushTipShape.CHISEL -> canvas.drawOval(
                centerX - secondaryRadius,
                centerY - secondaryRadius * roundness,
                centerX + secondaryRadius,
                centerY + secondaryRadius * roundness,
                strokePaint,
            )
            BrushTipShape.BRISTLE -> {
                val count = dual.tip.count.coerceIn(2, 8)
                repeat(count) { bristle ->
                    val unit = bristle / (count - 1f)
                    val bristleY = centerY + (unit - .5f) * secondaryRadius * 1.4f
                    canvas.drawOval(
                        centerX - secondaryRadius,
                        bristleY - max(.35f, secondaryRadius * .045f),
                        centerX + secondaryRadius,
                        bristleY + max(.35f, secondaryRadius * .045f),
                        strokePaint,
                    )
                }
            }
            BrushTipShape.PARTICLE -> {
                val count = dual.tip.count.coerceIn(1, 6)
                repeat(count) { particle ->
                    val particleSeed = seed xor (particle + 7) * 1103515245
                    val px = ((particleSeed and 0xFFFF) / 32767.5f - 1f) * secondaryRadius
                    val py = (((particleSeed ushr 16) and 0xFFFF) / 32767.5f - 1f) *
                        secondaryRadius
                    val particleRadius = secondaryRadius * (
                        .12f + ((particleSeed ushr 9) and 0xFF) / 255f * .24f
                        )
                    canvas.drawOval(
                        centerX + px - particleRadius,
                        centerY + py - particleRadius * roundness,
                        centerX + px + particleRadius,
                        centerY + py + particleRadius * roundness,
                        strokePaint,
                    )
                }
            }
        }
        canvas.restore()
        strokePaint.shader = null
        strokePaint.colorFilter = null
        strokePaint.alpha = originalAlpha
        strokePaint.xfermode = originalXfermode
        strokePaint.style = originalStyle
    }

    private fun applyGrainTexture(
        paint: Paint,
        settings: BrushSettings,
        drawingTool: DrawingTool,
        x: Float,
        y: Float,
        stampIndex: Int,
        angleRadians: Float,
        profile: BrushGrainProfile = settings.grainProfile,
        resolvedColor: Int = settings.color,
    ) {
        if (
            drawingTool == DrawingTool.ERASER ||
            profile.depth <= .01f ||
            profile.source == BrushGrainSource.NONE
        ) {
            paint.shader = null
            paint.colorFilter = null
            return
        }
        val key = brushTextureKey(profile)
        val entry = grainTextureCache[key] ?: run {
            val bitmap = createBrushGrainBitmap(key)
            GrainTextureEntry(
                bitmap = bitmap,
                shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT),
            ).also { grainTextureCache[key] = it }
        }
        val textureScale = profile.scale.coerceIn(.15f, 4f)
        if (profile.mode == BrushGrainMode.MOVING) {
            val movement = profile.movement.coerceIn(0f, 1f)
            grainShaderMatrix.setScale(textureScale, textureScale)
            grainShaderMatrix.postRotate(
                angleRadians * 180f / PI.toFloat() * movement,
                x,
                y,
            )
            grainShaderMatrix.postTranslate(
                x * movement + stampIndex * 1.7f,
                y * movement + stampIndex * .9f,
            )
            entry.shader.setLocalMatrix(grainShaderMatrix)
            entry.stationaryScale = null
        } else if (entry.stationaryScale != textureScale) {
            grainShaderMatrix.setScale(textureScale, textureScale)
            entry.shader.setLocalMatrix(grainShaderMatrix)
            entry.stationaryScale = textureScale
        }
        paint.shader = entry.shader
        paint.colorFilter = pigmentTint(resolvedColor)
    }

    private fun pigmentTint(color: Int): PorterDuffColorFilter {
        lastTintFilter?.takeIf { lastTintColor == color }?.let { return it }
        return PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN).also {
            lastTintColor = color
            lastTintFilter = it
        }
    }

    private fun resolveBrushRotation(
        settings: BrushSettings,
        strokeAngle: Float,
        stylusOrientation: Float,
        seed: Int,
    ): Float {
        val profile = settings.tipProfile
        val base = when (profile.rotationMode) {
            BrushRotationMode.FOLLOW_STROKE -> strokeAngle
            BrushRotationMode.FIXED -> 0f
            BrushRotationMode.STYLUS -> stylusOrientation
            BrushRotationMode.RANDOM -> {
                val unit = ((seed ushr 8) and 0xFFFF) / 65535f
                unit * (2f * PI.toFloat())
            }
        }
        val jitterUnit = ((seed and 0xFFFF) / 65535f) - .5f
        return base +
            Math.toRadians(profile.angleDegrees.toDouble()).toFloat() +
            jitterUnit * profile.rotationJitter.coerceIn(0f, 1f) * PI.toFloat()
    }

    private fun loadTipBitmap(path: String): Bitmap? {
        tipBitmapCache[path]?.takeUnless(Bitmap::isRecycled)?.let { return it }
        val file = File(path)
        if (!file.isFile || file.length() > 2L * 1024L * 1024L) return null
        val bitmap = BitmapFactory.decodeFile(path) ?: return null
        tipBitmapCache[path] = bitmap
        return bitmap
    }

    internal fun taperFactor(settings: BrushSettings, progress: Float): Float {
        val safeProgress = progress.coerceIn(0f, 1f)
        val start = settings.taperStart.coerceIn(0f, 0.48f)
        val end = settings.taperEnd.coerceIn(0f, 0.48f)
        val startFactor = if (start <= 0.001f) 1f else (safeProgress / start).coerceIn(0.06f, 1f)
        val endFactor = if (end <= 0.001f) 1f else ((1f - safeProgress) / end).coerceIn(0.06f, 1f)
        return min(startFactor, endFactor)
    }

    private fun maskFilterFor(settings: BrushSettings): BlurMaskFilter? {
        val radius = when (settings.kind) {
            BrushKind.AIRBRUSH -> max(
                2f,
                settings.sizePx * (0.12f + (1f - settings.hardness) * 0.22f),
            )
            BrushKind.PAINT, BrushKind.WATERCOLOR -> if (settings.hardness < 0.62f) {
                max(1f, settings.sizePx * (1f - settings.hardness) * 0.08f)
            } else {
                0f
            }
            else -> 0f
        }
        if (radius <= 0f) return null
        val key = settings.kind.ordinal * 100_000 + (radius * 8f).toInt()
        if (key != cachedMaskFilterKey) {
            cachedMaskFilterKey = key
            cachedMaskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        }
        return cachedMaskFilter
    }

    internal fun configurePaint(
        paint: Paint,
        drawingTool: DrawingTool,
        settings: BrushSettings,
        pressure: Float,
        tilt: Float,
        isPreview: Boolean = false,
        speedFactor: Float = 0f,
        resolvedColor: Int = settings.color,
    ) {
        val evaluationStartedNanos = if (performanceMetrics.isActive) System.nanoTime() else 0L
        val dab = performanceMetrics.trace("BrushEvaluation") {
            BrushEvaluator.evaluate(
                x = 0f,
                y = 0f,
                pressure = pressure,
                tilt = tilt,
                orientation = 0f,
                speedFactor = speedFactor,
                progress = 0f,
                stampIndex = 0,
                settings = settings,
                drawingTool = drawingTool,
                color = resolvedColor,
            )
        }
        if (evaluationStartedNanos != 0L) {
            performanceMetrics.addBrushEvaluation(System.nanoTime() - evaluationStartedNanos)
        }
        val previewFactor = if (isPreview) 0.72f else 1f
        paint.strokeWidth = dab.radiusX * 2f
        paint.alpha = (dab.opacity * previewFactor * 255f).toInt().coerceIn(1, 255)
        paint.color = resolvedColor
        paint.style = Paint.Style.STROKE
        paint.strokeCap = if (settings.kind == BrushKind.MARKER) Paint.Cap.SQUARE else Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.maskFilter = maskFilterFor(settings)
        if (drawingTool == DrawingTool.ERASER) {
            paint.xfermode = clearXfermode
            paint.maskFilter = null
        } else {
            paint.xfermode = null
        }
    }


    override fun close() {
        // An editor may be detached and attached again; safely clear cached
        // resources without invalidating the session's reusable Paint objects.
        strokePaint.shader = null
        strokePaint.colorFilter = null
        tipBitmapCache.values.forEach { if (!it.isRecycled) it.recycle() }
        tipBitmapCache.clear()
        grainTextureCache.values.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
        grainTextureCache.clear()
        cachedMaskFilterKey = Int.MIN_VALUE
        cachedMaskFilter = null
    }

    private companion object {
        const val MAX_STAMPS_PER_SEGMENT = 48
    }
}
