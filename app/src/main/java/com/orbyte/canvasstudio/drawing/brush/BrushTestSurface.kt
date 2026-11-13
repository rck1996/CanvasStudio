package com.orbyte.canvasstudio.drawing.brush

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.orbyte.canvasstudio.drawing.BrushRasterSession
import com.orbyte.canvasstudio.drawing.BrushSettings
import com.orbyte.canvasstudio.drawing.DrawingTool
import com.orbyte.canvasstudio.drawing.StrokeCommand
import com.orbyte.canvasstudio.drawing.StrokeCommandPainter
import com.orbyte.canvasstudio.drawing.StrokePoint
import com.orbyte.canvasstudio.drawing.input.StrokeSampler
import com.orbyte.canvasstudio.drawing.input.StylusInputController
import com.orbyte.canvasstudio.drawing.sanitized

/**
 * Bounded, in-memory paper. It shares pen sampling and Canvas raster code with
 * the editor, but never constructs a DrawingView, document, tile cache or history.
 */
internal class BrushTestSurface(context: Context) : View(context) {
    private val raster = BrushRasterSession()
    private val painter = StrokeCommandPainter(raster)
    private val committed = Bitmap.createBitmap(PAPER_WIDTH, PAPER_HEIGHT, Bitmap.Config.ARGB_8888)
    private val live = Bitmap.createBitmap(PAPER_WIDTH, PAPER_HEIGHT, Bitmap.Config.ARGB_8888)
    private val committedCanvas = Canvas(committed)
    private val liveCanvas = Canvas(live)
    private val paperBounds = RectF(0f, 0f, PAPER_WIDTH.toFloat(), PAPER_HEIGHT.toFloat())
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val screenBounds = RectF()
    private val points = ArrayList<StrokePoint>()
    private var paperScale = 1f
    private var pointerId = INVALID_POINTER
    private var strokeSettings: BrushSettings? = null
    private var wetReservoir: WetPigmentReservoir? = null
    private var distanceDabs: DistanceDabCursor? = null
    private var released = false

    var settings = BrushSettings()
    var darkPaper = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        paperScale = minOf(w.toFloat() / PAPER_WIDTH, h.toFloat() / PAPER_HEIGHT)
        val left = (w - PAPER_WIDTH * paperScale) / 2f
        val top = (h - PAPER_HEIGHT * paperScale) / 2f
        screenBounds.set(left, top, w - left, h - top)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(if (darkPaper) 0xFF2C2E34.toInt() else 0xFFE6DFD4.toInt())
        if (!released) canvas.drawBitmap(if (strokeSettings != null) live else committed, null, screenBounds, bitmapPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (released) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val index = event.actionIndex
                if (!screenBounds.contains(event.getX(index), event.getY(index))) return false
                if (StylusInputController.isEraser(event, index)) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                requestUnbufferedDispatch(event)
                pointerId = event.getPointerId(index)
                val brush = settings.sanitized()
                strokeSettings = brush
                wetReservoir = if (usesWetPigment(DrawingTool.BRUSH, brush)) WetPigmentReservoir(brush) else null
                distanceDabs = if (usesDistanceDabs(brush, DrawingTool.BRUSH)) DistanceDabCursor() else null
                points.clear()
                live.eraseColor(Color.TRANSPARENT)
                liveCanvas.drawBitmap(committed, 0f, 0f, null)
                val first = map(StylusInputController.currentSample(event, index))
                points.add(first)
                raster.drawBrushStamp(
                    canvas = liveCanvas, x = first.x, y = first.y,
                    pressure = first.pressure * raster.taperFactor(brush, 0f),
                    tilt = first.tilt, drawingTool = DrawingTool.BRUSH, settings = brush,
                    stampIndex = 0, angleRadians = 0f, orientation = first.orientation,
                    resolvedColor = wetReservoir?.advance(first, pigmentAt(first)) ?: brush.color,
                )
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointerId)
                if (index < 0) return true
                repeat(event.historySize) { append(StylusInputController.historicalSample(event, index, it)) }
                append(StylusInputController.currentSample(event, index))
                postInvalidateOnAnimation()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == pointerId) {
                    if (event.flags and MotionEvent.FLAG_CANCELED != 0) endStroke(commit = false)
                    else {
                        append(StylusInputController.currentSample(event, event.actionIndex))
                        endStroke(commit = true)
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> endStroke(commit = false)
        }
        return true
    }

    private fun map(sample: StylusInputController.Sample) = StrokePoint(
        x = (sample.x - screenBounds.left) / paperScale,
        y = (sample.y - screenBounds.top) / paperScale,
        pressure = sample.pressure,
        tilt = sample.tilt,
        timestampMillis = sample.eventTimeMillis,
        orientation = sample.orientation,
    )

    private fun append(sample: StylusInputController.Sample) {
        val brush = strokeSettings ?: return
        if (points.size >= MAX_STROKE_POINTS) return
        val raw = map(sample)
        val previous = points.lastOrNull() ?: return
        val point = StrokeSampler.sample(
            previous, raw.x, raw.y, raw.pressure, raw.tilt, raw.orientation, raw.timestampMillis,
            brush, stampBased = raster.isStampBrush(brush.kind),
        ) ?: return
        val index = points.size - 1
        points.add(point)
        val sampled = if (brush.renderProfile.colorPickup > .001f) pigmentAt(point) else null
        val pigment = wetReservoir?.advance(point, sampled)
        val cursor = distanceDabs
        if (cursor != null) {
            val start = ((index + 1f) / 28f).coerceAtMost(.5f)
            val end = ((index + 2f) / 28f).coerceAtMost(.5f)
            raster.nextDistanceDabs(cursor, previous, point, brush, start, end)?.let {
                raster.drawDistanceDabs(liveCanvas, previous, point, brush, it, sampled, pigment)
            }
            return
        }
        raster.drawStrokeSegment(
            liveCanvas, previous, point, DrawingTool.BRUSH, brush,
            progress = ((index + 2f) / 28f).coerceAtMost(.5f),
            segmentIndex = index,
            sampledColor = sampled,
            pigmentColorOverride = pigment,
        )
    }

    private fun pigmentAt(point: StrokePoint): Int? =
        if (paperBounds.contains(point.x, point.y)) committed.getPixel(point.x.toInt(), point.y.toInt()) else null

    private fun endStroke(commit: Boolean) {
        val brush = strokeSettings
        if (commit && brush != null && points.isNotEmpty()) {
            val trace = if (usesPigmentPickup(DrawingTool.BRUSH, brush)) {
                StrokePigmentTrace.capture(points, brush, ::pigmentAt)
            } else null
            painter.draw(
                committedCanvas,
                StrokeCommand(points = points, tool = DrawingTool.BRUSH, settings = brush, pigmentTrace = trace),
                paperBounds,
            )
        }
        strokeSettings = null
        wetReservoir = null
        distanceDabs = null
        pointerId = INVALID_POINTER
        points.clear()
        parent?.requestDisallowInterceptTouchEvent(false)
        invalidate()
    }

    fun clearPaper() {
        if (released) return
        endStroke(commit = false)
        committed.eraseColor(Color.TRANSPARENT)
    }

    fun release() {
        if (released) return
        endStroke(commit = false)
        released = true
        raster.close()
        live.recycle()
        committed.recycle()
    }

    internal fun inkAt(x: Int, y: Int): Int = committed.getPixel(x, y)

    private companion object {
        const val PAPER_WIDTH = 560
        const val PAPER_HEIGHT = 220
        const val MAX_STROKE_POINTS = 3072
        const val INVALID_POINTER = -1
    }
}
