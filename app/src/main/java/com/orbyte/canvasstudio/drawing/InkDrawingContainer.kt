package com.orbyte.canvasstudio.drawing

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.MotionEvent
import android.view.KeyEvent
import android.widget.FrameLayout
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.Stroke

/**
 * Hybrid Ink API surface: AndroidX renders the active large stroke in a low-latency front buffer,
 * while [DrawingView] remains the source of truth for layers, undo, tiles and project persistence.
 */
class InkDrawingContainer(context: Context) : FrameLayout(context) {
    val drawingView = DrawingView(context)
    private val inkView = InProgressStrokesView(context)
    private val pressurePenFamily = StockBrushes.pressurePen()
    private val previewBrushCache = object : LinkedHashMap<String, Brush>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Brush>?): Boolean =
            size > 16
    }
    private var activeInkStroke: InProgressStrokeId? = null
    private var activePointerId: Int = -1
    private val rasterHandoff = RasterHandoffGate<InProgressStrokeId>(RASTER_HANDOFF_FRAMES)

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        addView(
            drawingView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        addView(
            inkView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        inkView.addFinishedStrokesListener(object : InProgressStrokesFinishedListener {
            override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
                // The Ink front buffer must remain visible until DrawingView has presented the
                // raster source of truth. Removing it immediately can clear the low-latency
                // surface before the backing tile frame is ready, which looks like older strokes
                // are progressively disappearing during a dense drawing session.
                rasterHandoff.enqueue(strokes.keys)
                drawingView.postInvalidateOnAnimation()
            }
        })
        drawingView.onRasterFramePresented = {
            if (rasterHandoff.hasPending()) {
                val completed = rasterHandoff.onRasterFramePresented()
                if (completed != null) {
                    drawingView.post {
                        inkView.removeFinishedStrokes(completed)
                        drawingView.postInvalidateOnAnimation()
                    }
                } else {
                    drawingView.postInvalidateOnAnimation()
                }
            }
        }
        inkView.eagerInit()
        post { requestFocus() }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // DrawingView reads the event synchronously before handing it to AndroidX Ink.
        val handled = drawingView.onTouchEvent(event)
        val index = event.actionIndex
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                endInk(event, canceled = true)
                startInkIfOwned(event, index)
            }
            MotionEvent.ACTION_POINTER_DOWN -> startInkIfOwned(event, index)
            MotionEvent.ACTION_MOVE -> {
                activeInkStroke?.let { stroke ->
                    if (event.findPointerIndex(activePointerId) >= 0 && drawingView.hasActiveBrushStroke()) {
                        inkView.addToStroke(event, activePointerId, stroke)
                    } else {
                        endInk(event, canceled = true)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(index) == activePointerId) {
                    val canceled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        event.flags and MotionEvent.FLAG_CANCELED != 0
                    endInk(event, canceled)
                }
            }
            MotionEvent.ACTION_CANCEL -> endInk(event, canceled = true)
        }
        return handled
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        drawingView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)

    private fun startInkIfOwned(event: MotionEvent, index: Int) {
        if (activeInkStroke != null || event.getToolType(index) != MotionEvent.TOOL_TYPE_STYLUS) return
        val pointerId = event.getPointerId(index)
        if (!drawingView.ownsBrushPointer(pointerId) || !drawingView.supportsPlatformLowLatencyPreview()) return
        requestUnbufferedDispatch(event)
        activeInkStroke = inkView.startStroke(event, pointerId, previewBrush())
        activePointerId = pointerId
        drawingView.platformInkPreviewActive = true
    }

    private fun endInk(event: MotionEvent, canceled: Boolean) {
        val stroke = activeInkStroke ?: return
        val pointerId = activePointerId
        activeInkStroke = null
        activePointerId = -1
        drawingView.platformInkPreviewActive = false
        if (canceled) inkView.cancelStroke(stroke, event)
        else inkView.finishStroke(event, pointerId, stroke)
    }

    private fun previewBrush(): Brush {
        val settings = drawingView.brushSettings
        val alpha = (settings.opacity * Color.alpha(settings.color)).toInt()
        val color = Color.argb(
            alpha,
            Color.red(settings.color),
            Color.green(settings.color),
            Color.blue(settings.color),
        )
        val size = drawingView.platformPreviewSizePx().coerceAtLeast(1f)
        val cacheKey = "$color:${size.toBits()}"
        return previewBrushCache.getOrPut(cacheKey) {
            Brush.createWithColorIntArgb(
                pressurePenFamily,
                color,
                size,
                0.1f,
            )
        }
    }

    private companion object {
        // One frame commits the tile update and the second safely hands presentation back from
        // AndroidX Ink. This is short enough to be imperceptible and prevents front-buffer holes.
        const val RASTER_HANDOFF_FRAMES = 2
    }
}
