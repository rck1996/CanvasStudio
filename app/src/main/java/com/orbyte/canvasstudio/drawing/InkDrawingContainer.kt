package com.orbyte.canvasstudio.drawing

import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import android.view.KeyEvent
import android.view.View
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
    val drawingView = DrawingView(context).apply {
        usePlatformLowLatencyPreview = true
    }
    private val inkView = InProgressStrokesView(context)
    private val pressurePenFamily = StockBrushes.pressurePen()
    private val previewBrushCache = object : LinkedHashMap<String, Brush>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Brush>?): Boolean =
            size > 16
    }
    private var activeInkStroke: InProgressStrokeId? = null
    private var activePointerId: Int = -1

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
                inkView.removeFinishedStrokes(strokes.keys)
            }
        })
        inkView.eagerInit()
        post { requestFocus() }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean = handleTouch(this, event)

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        drawingView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)

    private fun handleTouch(view: View, event: MotionEvent): Boolean {
        val useInk = drawingView.supportsPlatformLowLatencyPreview()
        if (!useInk) {
            activeInkStroke?.let { inkView.cancelStroke(it, event) }
            activeInkStroke = null
            activePointerId = -1
            return drawingView.onTouchEvent(event)
        }

        // Ink may internally transform or batch the event. Give the document engine its own
        // untouched copy first so the persisted stroke can never depend on preview behavior.
        MotionEvent.obtain(event).let { engineEvent ->
            try {
                drawingView.onTouchEvent(engineEvent)
            } finally {
                engineEvent.recycle()
            }
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                view.requestUnbufferedDispatch(event)
                activePointerId = event.getPointerId(event.actionIndex)
                activeInkStroke = inkView.startStroke(
                    event,
                    activePointerId,
                    previewBrush(),
                )
            }
            MotionEvent.ACTION_MOVE -> {
                activeInkStroke?.let { stroke ->
                    inkView.addToStroke(event, activePointerId, stroke)
                }
            }
            MotionEvent.ACTION_UP -> {
                val stroke = activeInkStroke
                if (stroke != null) inkView.finishStroke(event, activePointerId, stroke)
                activeInkStroke = null
                activePointerId = -1
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                activeInkStroke?.let { inkView.cancelStroke(it, event) }
                activeInkStroke = null
                activePointerId = -1
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                activeInkStroke?.let { inkView.cancelStroke(it, event) }
                activeInkStroke = null
                activePointerId = -1
            }
        }
        return true
    }

    private fun previewBrush(): Brush {
        val settings = drawingView.brushSettings
        val alpha = (settings.opacity.coerceIn(0.15f, 1f) * 255f).toInt()
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
}
