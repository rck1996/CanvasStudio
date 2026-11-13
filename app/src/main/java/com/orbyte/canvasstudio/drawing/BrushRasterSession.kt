package com.orbyte.canvasstudio.drawing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ComposeShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import com.orbyte.canvasstudio.drawing.brush.BrushEvaluator
import com.orbyte.canvasstudio.drawing.brush.DabSpan
import com.orbyte.canvasstudio.drawing.brush.DistanceDabCursor
import com.orbyte.canvasstudio.drawing.brush.usesDistanceDabs
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
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin

internal data class DistanceDabSegment(
    val span: DabSpan,
    val speedFactor: Float,
    val startProgress: Float,
    val endProgress: Float,
)

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
    private val featheredTipMatrix = Matrix()
    private data class FeatheredTipEntry(val bitmap: Bitmap, val shader: BitmapShader)
    private val featheredTips = mutableMapOf<FeatheredBrushTip, FeatheredTipEntry>()
    private val featheredComposites = mutableMapOf<FeatheredBrushTip, Pair<Shader, Shader>>()
    private val materialTips = mutableMapOf<MaterialTip, Bitmap>()
    private val materialBounds = RectF()
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
        val featheredTip = if (tool == DrawingTool.BRUSH) FeatheredBrushTip.forSettings(settings) else null
        val requested = settings.spacing.coerceIn(0.025f, 0.5f)
        val floor = when (settings.kind) {
            BrushKind.CHARCOAL, BrushKind.CHALK, BrushKind.DRY_BRUSH -> 0.13f
            BrushKind.BRISTLE -> 0.1f
            BrushKind.PAINT, BrushKind.WATERCOLOR, BrushKind.OIL -> 0.09f
            BrushKind.AIRBRUSH -> 0.1f
            BrushKind.MARKER -> 0.055f
            else -> requested
        }
        val largeTipFloor = when {
            featheredTip == FeatheredBrushTip.LOADED_OIL -> .13f
            settings.kind == BrushKind.BRISTLE || settings.kind == BrushKind.WATERCOLOR ||
                settings.kind == BrushKind.OIL -> .20f
            else -> floor
        }
        if (settings.sizePx <= 96f || largeTipFloor == floor || tool == DrawingTool.ERASER) {
            return max(requested, floor)
        }
        val widthFactor = tipWidthFactor(settings, pressure, speedFactor)
        // A nominally large brush can still produce a thin dab at light pressure.
        val fullTipWeight = ((widthFactor - .55f) / .30f).coerceIn(0f, 1f)
        // At large sizes these tips already overlap heavily. Keep their full
        // geometry and grain, while limiting repeated overdraw of the same area.
        val largeTipWeight = ((settings.sizePx - 96f) / 84f).coerceIn(0f, 1f)
        return max(requested, floor + (largeTipFloor - floor) * largeTipWeight * fullTipWeight)
    }

    private fun tipWidthFactor(settings: BrushSettings, pressure: Float, speedFactor: Float): Float {
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
        return pressureWidth * velocityWidth
    }

    internal fun planDistanceDabs(command: StrokeCommand): Array<DistanceDabSegment?>? {
        if (!usesDistanceDabs(command.settings, command.tool)) return null
        val points = command.points
        val lengths = FloatArray((points.size - 1).coerceAtLeast(0)) { index ->
            hypot(points[index + 1].x - points[index].x, points[index + 1].y - points[index].y)
        }
        val total = lengths.sum().coerceAtLeast(1f)
        val cursor = DistanceDabCursor()
        var traveled = 0f
        return Array(lengths.size) { index ->
            val length = lengths[index]
            val start = traveled / total
            traveled += length
            nextDistanceDabs(cursor, points[index], points[index + 1], command.settings, start, traveled / total, length)
        }
    }

    internal fun nextDistanceDabs(
        cursor: DistanceDabCursor,
        from: StrokePoint,
        to: StrokePoint,
        settings: BrushSettings,
        startProgress: Float,
        endProgress: Float,
        length: Float = hypot(to.x - from.x, to.y - from.y),
    ): DistanceDabSegment? {
        if (!length.isFinite() || length <= 0f) return null
        val elapsed = (to.timestampMillis - from.timestampMillis).coerceAtLeast(1L).toFloat()
        val speed = (length / elapsed / 2.4f).coerceIn(0f, 1f)
        val pressure = min(from.pressure, to.pressure)
        val nominal = settings.sizePx * stampSpacing(settings, pressure, speed, DrawingTool.BRUSH)
        // These three painters build their own envelope. The roundness of the
        // individual tip profile is not the width of the combined brush mark.
        val aspect = when (settings.kind) {
            BrushKind.WATERCOLOR -> .78f
            BrushKind.BRISTLE -> .73f
            BrushKind.OIL -> if (settings.tipProfile.shape == BrushTipShape.CHISEL) .90f else .56f
            BrushKind.MARKER -> settings.tipProfile.roundness.coerceIn(.12f, 1f)
            else -> .56f
        }
        // Limit the gap by the narrow footprint under light pressure, not the
        // selected size. Short/stationary segments no longer force an extra dab.
        val footprint = settings.sizePx * tipWidthFactor(settings, pressure, speed) * aspect
        val step = min(nominal.coerceAtLeast(1.5f), footprint * .52f).coerceAtLeast(.75f)
        return cursor.advance(length, step)?.let { DistanceDabSegment(it, speed, startProgress, endProgress) }
    }

    internal fun drawDistanceDabs(
        canvas: Canvas,
        from: StrokePoint,
        to: StrokePoint,
        settings: BrushSettings,
        segment: DistanceDabSegment,
        sampledColor: Int? = null,
        pigmentColorOverride: Int? = null,
        dabCenterBounds: RectF? = null,
    ) {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val angle = atan2(dy.toDouble(), dx.toDouble()).toFloat()
        val pigment = pigmentColorOverride ?: mixPigmentColor(settings.color, sampledColor, settings.renderProfile.colorPickup)
        repeat(segment.span.count) { index ->
            val t = segment.span.fractionAt(index)
            val x = snapDabCoordinate(from.x + dx * t)
            val y = snapDabCoordinate(from.y + dy * t)
            // The distance plan still consumes the same phase and seed. Avoid sending
            // dabs with no tile coverage through texture, grain and blur setup.
            if (dabCenterBounds != null && !dabCenterBounds.contains(x, y)) return@repeat
            val progress = segment.startProgress + (segment.endProgress - segment.startProgress) * t
            drawBrushStamp(
                canvas = canvas,
                x = x,
                y = y,
                pressure = (from.pressure + (to.pressure - from.pressure) * t) * taperFactor(settings, progress),
                tilt = from.tilt + (to.tilt - from.tilt) * t,
                drawingTool = DrawingTool.BRUSH, settings = settings,
                stampIndex = segment.span.firstSeed + index, angleRadians = angle,
                orientation = interpolateCircularRadians(from.orientation, to.orientation, t),
                speedFactor = segment.speedFactor, resolvedColor = pigment, strokeProgress = progress,
            )
        }
    }

    // Equivalent interpolation across event partitions can differ by an ULP.
    // Canonicalize well below a visible pixel before position-based grain and jitter.
    private fun snapDabCoordinate(value: Float): Float = round(value * 256f) / 256f

    internal fun drawStrokeSegment(
        canvas: Canvas,
        from: StrokePoint,
        to: StrokePoint,
        drawingTool: DrawingTool,
        settings: BrushSettings,
        progress: Float = 1f,
        segmentIndex: Int = 0,
        sampledColor: Int? = null,
        pigmentColorOverride: Int? = null,
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
        val pigmentColor = pigmentColorOverride ?: mixPigmentColor(
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
        // Several textured branches keep a minimum alpha for visible pigment.
        // A fully transparent color must not be lifted back to that minimum.
        if (drawingTool != DrawingTool.ERASER && strokePaint.alpha == 0) return
        if (drawingTool != DrawingTool.ERASER && settings.renderProfile.wetness > .001f) {
            val charge = settings.renderProfile.charge.coerceIn(0f, 1f)
            val attack = settings.renderProfile.attack.coerceIn(0f, 1f)
            val pigmentLoad = charge * (
                1f - strokeProgress.coerceIn(0f, 1f) * (1f - attack) * .72f
                )
            strokePaint.alpha = (strokePaint.alpha * pigmentLoad.coerceIn(.08f, 1f))
                .toInt().coerceIn(1, 255)
        }
        val featheredTip = if (drawingTool == DrawingTool.BRUSH) FeatheredBrushTip.forSettings(settings) else null
        if (featheredTip == FeatheredBrushTip.ROUND_FELT || featheredTip == FeatheredBrushTip.FLAT_FELT) {
            // Keep felt translucent at close spacing: spread the pigment over
            // the overlapping dabs instead of effectively turning it into ink.
            val dose = .40f * (settings.spacing.coerceIn(.055f, .50f) / .055f)
            val coverage = 1f - (1f - strokePaint.alpha / 255f).pow(dose)
            strokePaint.alpha = (coverage * 255f).toInt().coerceIn(1, 255)
        }
        // The document-space shader carries all the tooth in these tips. Sampling
        // a second scalar at each dab would print stripes along their length.
        if (drawingTool != DrawingTool.ERASER && featheredTip == null) {
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

        if (featheredTip != null) {
            val contact = if (settings.pressureSize) pressure.coerceIn(0f, 1f) else .8f
            val height = when (featheredTip) {
                FeatheredBrushTip.LOADED_OIL -> settings.tipProfile.roundness.coerceIn(.22f, .74f) *
                    (.80f + contact * .20f)
                else -> settings.tipProfile.roundness.coerceIn(.12f, 1f)
            }
            drawFeatheredTip(canvas, featheredTip, stampX, stampY, radius, radius * height, resolvedAngle, resolvedColor)
        } else when (settings.kind) {
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

            BrushKind.CHARCOAL -> {
                val contact = if (settings.pressureOpacity) pressure.coerceIn(0f, 1f) else 1f
                drawMaterialTip(
                    canvas, MaterialTip.CHARCOAL, stampX, stampY, radius * .94f,
                    radius * (.48f + contact * .37f), resolvedAngle, resolvedColor,
                    load = .76f + contact * .84f, drawingTool = drawingTool,
                )
            }

            BrushKind.CHALK -> {
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
                    baseAlpha * .14f
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
                if (settings.tipProfile.shape == BrushTipShape.CHISEL) {
                    // Keep the corner radius small enough to read as a flat nib.
                    val halfHeight = radius * roundness
                    val corner = halfHeight * .24f
                    canvas.drawRoundRect(
                        stampX - radius * .92f, stampY - halfHeight,
                        stampX + radius * .92f, stampY + halfHeight,
                        corner, corner, strokePaint,
                    )
                } else canvas.drawOval(
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
                if (settings.tipProfile.shape == BrushTipShape.OVAL) {
                    val halfHeight = radius * settings.tipProfile.roundness.coerceIn(.12f, 1f)
                    canvas.save()
                    canvas.rotate(resolvedAngle * 180f / PI.toFloat(), stampX, stampY)
                    canvas.drawOval(stampX - radius, stampY - halfHeight,
                        stampX + radius, stampY + halfHeight, strokePaint)
                    canvas.restore()
                } else canvas.drawCircle(stampX, stampY, radius, strokePaint)
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
                val contact = if (settings.pressureOpacity) pressure.coerceIn(0f, 1f) else 1f
                if (contact > .28f && drawingTool != DrawingTool.ERASER) {
                    val body = ((contact - .28f) / .72f).coerceIn(0f, 1f)
                    drawMaterialTip(canvas, MaterialTip.DRY_PAINT, stampX, stampY,
                        radius * .85f, radius * (.26f + body * .31f), resolvedAngle, resolvedColor,
                        load = body * 1.35f, drawingTool = drawingTool)
                }
                canvas.save()
                canvas.rotate(resolvedAngle * 180f / PI.toFloat(), stampX, stampY)
                val bristleCount = settings.tipProfile.count.coerceIn(3, 12)
                repeat(bristleCount) { bristle ->
                    if (((seed ushr (bristle * 3)) and 0x3) != 0) {
                        val offset = (bristle - (bristleCount - 1) / 2f) *
                            radius * 1.12f / bristleCount
                        strokePaint.alpha = (
                            baseAlpha * (.38f + bristle * .36f / bristleCount)
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
                val contact = if (settings.pressureSize) pressure.coerceIn(0f, 1f) else .7f
                val load = if (drawingTool == DrawingTool.ERASER) 0f else {
                    val input = if (settings.pressureOpacity) pressure else 1f
                    ((input - .35f) / .65f).coerceIn(0f, 1f)
                }
                val variation = settings.tipProfile.countJitter.coerceIn(0f, 1f)
                val spread = radius * (.83f + contact * .14f)
                val laneSpacing = spread / (bristleCount - 1)
                repeat(bristleCount) { bristle ->
                    // Give each tuft a persistent profile. Do not re-roll its position
                    // per dab: that makes a clean fiber look like noise as the pen moves.
                    val laneSeed = (bristle + 1) * 1103515245 + 12345
                    val placement = ((laneSeed ushr 12) and 255) / 255f
                    val thickness = ((laneSeed ushr 21) and 255) / 255f
                    val length = ((laneSeed ushr 4) and 255) / 255f
                    val across = bristle / (bristleCount - 1f)
                    val edge = kotlin.math.abs(across * 2f - 1f)
                    // Neighboring central tufts lean toward their bundle at firm
                    // contact, leaving wider gaps between bundles rather than
                    // making a regular comb of independent lines.
                    val bundle = (if (bristle % 2 == 0) 1f else -1f) * load * (1f - edge) * .22f
                    val offset = (across - .5f) * spread +
                        ((placement - .5f) * (.20f + variation * .24f) + bundle) * laneSpacing
                    val halfWidth = laneSpacing * (.20f + contact * .29f) * (.76f + thickness * .30f)
                    val halfLength = radius * (.72f + length * .23f - edge * .16f)
                    val lead = radius * (placement - .5f) * (.12f + variation * .16f)
                    // Strong contact loads the central tufts; keep the edge tufts
                    // drier and leave the geometric gaps between fibers intact.
                    val coreLoad = 1f + load * load * (1f - edge * .72f) * 1.25f
                    strokePaint.alpha = (baseAlpha * (.60f + thickness * .32f) * (1f - edge * .18f) * coreLoad)
                        .toInt().coerceIn(1, 255)
                    canvas.drawOval(
                        stampX + lead - halfLength,
                        stampY + offset - halfWidth,
                        stampX + lead + halfLength,
                        stampY + offset + halfWidth,
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
                val granular = settings.grainProfile.source == BrushGrainSource.WATERCOLOR &&
                    settings.grainProfile.mode == BrushGrainMode.TEXTURIZED && settings.grainProfile.depth >= .7f
                strokePaint.alpha = (
                    baseAlpha * if (granular) .38f + bleed * .14f else {
                        .74f + settings.renderProfile.buildup * .22f - dilution * .18f
                    }
                    ).toInt().coerceIn(1, 255)
                canvas.drawOval(
                    stampX - wetRadius,
                    stampY - wetRadius * (.78f + wetness * .18f),
                    stampX + wetRadius,
                    stampY + wetRadius * (.78f + wetness * .18f),
                    strokePaint,
                )
                // The granulating wash uses a second, denser deposit instead
                // of outlining every dab, which made a row of round blooms.
                if (!granular) {
                    strokePaint.style = Paint.Style.STROKE
                    strokePaint.strokeWidth = max(1f, radius * (.035f + wetness * .055f))
                }
                val deposit = if (granular) .68f + settings.renderProfile.buildup * .20f
                    else .15f + wetness * .28f + settings.grain * .19f
                strokePaint.alpha = (baseAlpha * deposit).toInt().coerceIn(1, 255)
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
                if (settings.tipProfile.shape == BrushTipShape.CHISEL) {
                    val contact = if (settings.pressureSize) pressure.coerceIn(0f, 1f) else .8f
                    val halfHeight = radius * settings.tipProfile.roundness.coerceIn(.22f, .74f) *
                        (.80f + contact * .20f)
                    val corner = halfHeight * .22f
                    canvas.drawRoundRect(
                        stampX - radius * .86f, stampY - halfHeight,
                        stampX + radius * .86f, stampY + halfHeight,
                        corner, corner, strokePaint,
                    )
                } else {
                    // Loaded oil binds fibers together with a textured body. Keep the
                    // outer tufts separate so its edge does not turn into a round stamp.
                    val contact = if (settings.pressureSize) pressure.coerceIn(0f, 1f) else .8f
                    val body = (settings.renderProfile.charge * drag).coerceIn(0f, 1f)
                    val spread = radius * (.62f + .78f * contact)
                    val lane = spread / (bristleCount - 1)
                    repeat(bristleCount) { bristle ->
                        val unit = bristle / (bristleCount - 1f)
                        val edge = kotlin.math.abs(unit * 2f - 1f)
                        // Loaded central tufts merge as pressure rises, while
                        // long, drier edge fibers keep visible grooves.
                        val tuft = (bristle + 3) * 1103515245
                        val width = ((tuft ushr 18) and 0xFF) / 255f
                        val length = ((tuft ushr 9) and 0xFF) / 255f
                        val offset = (unit - .5f) * spread + (width - .5f) * lane * .34f
                        val merge = contact * contact * body * (1f - edge * .90f)
                        val thickness = lane * (.18f + width * .30f + merge * 1.05f)
                        strokePaint.alpha = (
                            baseAlpha * (.54f + width * .28f + merge * .36f)
                            ).toInt().coerceIn(1, 255)
                        canvas.drawOval(
                            stampX - radius * (.78f + length * .23f + drag * .13f),
                            stampY + offset - thickness,
                            stampX + radius * (.74f + length * .22f),
                            stampY + offset + thickness,
                            strokePaint,
                        )
                    }
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

    private fun drawFeatheredTip(
        canvas: Canvas,
        tip: FeatheredBrushTip,
        x: Float,
        y: Float,
        radius: Float,
        halfHeight: Float,
        angle: Float,
        pigment: Int,
    ) {
        val entry = featheredTips.getOrPut(tip) {
            val bitmap = tip.createMask()
            FeatheredTipEntry(bitmap, BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
        }
        val sx = radius * tip.length / FeatheredBrushTip.PIXEL_RADIUS
        val sy = halfHeight / FeatheredBrushTip.PIXEL_RADIUS
        val halfMask = FeatheredBrushTip.SIZE * .5f
        featheredTipMatrix.setScale(sx, sy)
        featheredTipMatrix.postTranslate(x - sx * halfMask, y - sy * halfMask)
        featheredTipMatrix.postRotate(angle * 180f / PI.toFloat(), x, y)
        entry.shader.setLocalMatrix(featheredTipMatrix)
        val grain = strokePaint.shader
        // Both shaders are evaluated in document coordinates, not the rotated
        // canvas. Paper tooth stays in place when the tip turns or crosses a tile.
        strokePaint.shader = if (grain == null) entry.shader else {
            val cached = featheredComposites[tip]
            if (cached?.first === grain) cached.second else {
                ComposeShader(entry.shader, grain, PorterDuff.Mode.DST_IN).also {
                    featheredComposites[tip] = grain to it
                }
            }
        }
        strokePaint.colorFilter = pigmentTint(pigment)
        val wasFiltering = strokePaint.isFilterBitmap
        strokePaint.isFilterBitmap = true
        // The mask is transparent outside the oriented tip. Use its exact
        // axis-aligned extent to avoid shading the corners of a large square.
        val rx = sx * halfMask
        val ry = sy * halfMask
        val c = kotlin.math.abs(cos(angle))
        val s = kotlin.math.abs(sin(angle))
        val dx = rx * c + ry * s
        val dy = rx * s + ry * c
        canvas.drawRect(x - dx, y - dy, x + dx, y + dy, strokePaint)
        strokePaint.isFilterBitmap = wasFiltering
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

    private fun drawMaterialTip(
        canvas: Canvas,
        material: MaterialTip,
        x: Float,
        y: Float,
        halfWidth: Float,
        halfHeight: Float,
        angle: Float,
        pigment: Int,
        load: Float,
        drawingTool: DrawingTool,
    ) {
        val alpha = strokePaint.alpha
        val shader = strokePaint.shader
        val colorFilter = strokePaint.colorFilter
        val filterBitmap = strokePaint.isFilterBitmap
        val bitmap = materialTips.getOrPut(material) { createMaterialTip(material) }
        val saved = canvas.save()
        try {
            canvas.rotate(angle * 180f / PI.toFloat(), x, y)
            materialBounds.set(x - halfWidth, y - halfHeight, x + halfWidth, y + halfHeight)
            strokePaint.shader = null
            strokePaint.isFilterBitmap = true
            strokePaint.colorFilter = if (drawingTool == DrawingTool.ERASER) null else pigmentTint(pigment)
            strokePaint.alpha = (alpha * load).toInt().coerceIn(0, 255)
            canvas.drawBitmap(bitmap, null, materialBounds, strokePaint)
        } finally {
            strokePaint.alpha = alpha
            strokePaint.shader = shader
            strokePaint.colorFilter = colorFilter
            strokePaint.isFilterBitmap = filterBitmap
            canvas.restoreToCount(saved)
        }
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
        // The paint already carries the pigment alpha. Tint only the RGB so
        // that textured and bitmap tips don't apply the color alpha twice.
        val tint = color or 0xFF000000.toInt()
        lastTintFilter?.takeIf { lastTintColor == tint }?.let { return it }
        return PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN).also {
            lastTintColor = tint
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
        // Paint.setColor sets the alpha channel as well. Apply evaluated
        // coverage after assigning the pigment, or pressure/flow/opacity are lost.
        paint.color = resolvedColor
        if (drawingTool != DrawingTool.ERASER) {
            val colorAlpha = resolvedColor ushr 24
            paint.alpha = if (colorAlpha == 0) 0 else {
                (dab.opacity * previewFactor * colorAlpha).toInt().coerceIn(1, colorAlpha)
            }
        }
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
        featheredComposites.clear()
        featheredTips.values.forEach { it.bitmap.recycle() }
        featheredTips.clear()
        materialTips.values.forEach { it.recycle() }
        materialTips.clear()
        cachedMaskFilterKey = Int.MIN_VALUE
        cachedMaskFilter = null
    }

    private companion object {
        const val MAX_STAMPS_PER_SEGMENT = 48
    }
}
