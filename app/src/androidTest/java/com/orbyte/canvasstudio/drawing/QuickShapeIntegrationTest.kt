package com.orbyte.canvasstudio.drawing

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orbyte.canvasstudio.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class QuickShapeIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun heldStraightLineBecomesShapeInsteadOfRegularStroke() = onMain {
        val view = DrawingView(context).apply {
            configureDocument(1024, 1024)
            quickShapeEnabled = true
            quickMenuTouchHoldEnabled = false
        }
        var shapes = 0
        var freehand = 0
        view.onInteraction = {
            if (it is DrawingInteractionEvent.ShapeCommitted) {
                assertEquals(DrawingTool.LINE, it.tool)
                shapes++
            }
            if (it is DrawingInteractionEvent.StrokeCommitted) freehand++
        }
        drawStroke(view, held = true, curved = false)
        assertEquals(1, shapes)
        assertEquals(0, freehand)
    }

    @Test
    fun ordinaryReleaseRemainsAStrokeWithQuickShapeEnabled() = onMain {
        val view = DrawingView(context).apply {
            configureDocument(1024, 1024)
            quickShapeEnabled = true
            quickMenuTouchHoldEnabled = false
        }
        var shapes = 0
        var freehand = 0
        view.onInteraction = {
            if (it is DrawingInteractionEvent.ShapeCommitted) shapes++
            if (it is DrawingInteractionEvent.StrokeCommitted) freehand++
        }
        drawStroke(view, held = false, curved = false)
        assertEquals(0, shapes)
        assertEquals(1, freehand)
    }

    @Test
    fun curvedHeldGestureRemainsFreehand() = onMain {
        val view = DrawingView(context).apply {
            configureDocument(1024, 1024)
            quickShapeEnabled = true
            quickMenuTouchHoldEnabled = false
        }
        var shapes = 0
        var freehand = 0
        view.onInteraction = {
            if (it is DrawingInteractionEvent.ShapeCommitted) shapes++
            if (it is DrawingInteractionEvent.StrokeCommitted) freehand++
        }
        drawStroke(view, held = true, curved = true)
        assertEquals(0, shapes)
        assertEquals(1, freehand)
    }

    @Test
    fun heldOvalIsCommittedAndRestoredAsOneUndoOperation() = onMain {
        val view = DrawingView(context).apply {
            configureDocument(1024, 1024)
            quickShapeEnabled = true
            quickMenuTouchHoldEnabled = false
        }
        val detected = mutableListOf<DrawingTool>()
        view.onInteraction = { if (it is DrawingInteractionEvent.ShapeCommitted) detected += it.tool }
        val initial = view.exportCompositeBitmap(false)
        try {
            drawOval(view, held = true)
            assertEquals(listOf(DrawingTool.ELLIPSE), detected)
            val oval = view.exportCompositeBitmap(false)
            try {
                assertTrue(!initial.sameAs(oval))
                view.undo()
                val undone = view.exportCompositeBitmap(false)
                try { assertTrue(initial.sameAs(undone)) } finally { undone.recycle() }
                view.redo()
                val restored = view.exportCompositeBitmap(false)
                try { assertTrue(oval.sameAs(restored)) } finally { restored.recycle() }
            } finally {
                oval.recycle()
            }
        } finally {
            initial.recycle()
            view.configureDocument(256, 256)
        }
    }

    @Test
    fun closedOvalReleasedNormallyRemainsFreehand() = onMain {
        val view = DrawingView(context).apply {
            configureDocument(1024, 1024)
            quickShapeEnabled = true
            quickMenuTouchHoldEnabled = false
        }
        var shapes = 0
        var freehand = 0
        view.onInteraction = {
            if (it is DrawingInteractionEvent.ShapeCommitted) shapes++
            if (it is DrawingInteractionEvent.StrokeCommitted) freehand++
        }
        try {
            drawOval(view, held = false)
            assertEquals(0, shapes)
            assertEquals(1, freehand)
        } finally {
            view.configureDocument(256, 256)
        }
    }

    @Test
    fun noisyTiltedOvalWithStabilizedStylusShowsPreviewBeforeLift() = onMain {
        val view = DrawingView(context).apply {
            configureDocument(1024, 1024)
            quickShapeEnabled = true
            brushSettings = brushSettings.copy(stabilization = .92f)
        }
        val detected = mutableListOf<DrawingTool>()
        view.onInteraction = { if (it is DrawingInteractionEvent.ShapeCommitted) detected += it.tool }
        val start = 10_000L
        val angle = .53
        val points = (0..39).map { i ->
            val t = i * (2 * PI - .15) / 39
            val r = 1 + .04 * sin(3 * t)
            val x = 146 * cos(t) * r
            val y = 64 * sin(t) * r
            (420 + x * cos(angle) - y * sin(angle)).toFloat() to
                (440 + x * sin(angle) + y * cos(angle)).toFloat()
        }
        try {
            points.forEachIndexed { i, (x, y) ->
                dispatch(view, start, start + i * 9L,
                    if (i == 0) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_MOVE, x, y, stylus = true)
            }
            val (x, y) = points.last()
            dispatch(view, start, start + 580L, MotionEvent.ACTION_MOVE, x + 1f, y + 1f, stylus = true)
            assertEquals(DrawingTool.ELLIPSE, view.refreshQuickShapePreview(start + 690L))
            assertTrue(detected.isEmpty())
            dispatch(view, start, start + 700L, MotionEvent.ACTION_UP, x + 3f, y - 1f, stylus = true)
            assertEquals(listOf(DrawingTool.ELLIPSE), detected)
        } finally {
            view.configureDocument(256, 256)
        }
    }

    @Test
    fun rectangleAndTriangleCrossTilesAndReplayWithIdenticalPixels() = onMain {
        val view = DrawingView(context).apply {
            configureDocument(1024, 1024)
            quickShapeEnabled = true
        }
        val detected = mutableListOf<DrawingTool>()
        view.onInteraction = { if (it is DrawingInteractionEvent.ShapeCommitted) detected += it.tool }
        val initial = view.exportCompositeBitmap(false)
        try {
            drawPolygon(view, 10_000L, 402f to 320f, 690f to 377f, 644f to 610f, 356f to 550f)
            drawPolygon(view, 12_000L, 280f to 700f, 485f to 432f, 699f to 760f)
            assertEquals(listOf(DrawingTool.RECTANGLE, DrawingTool.TRIANGLE), detected)
            val rendered = view.exportCompositeBitmap(false)
            try {
                view.undo()
                view.undo()
                val cleared = view.exportCompositeBitmap(false)
                try {
                    assertTrue(!initial.sameAs(rendered))
                    assertTrue(initial.sameAs(cleared))
                } finally { cleared.recycle() }
                view.redo()
                view.redo()
                val restored = view.exportCompositeBitmap(false)
                try { assertTrue(rendered.sameAs(restored)) } finally { restored.recycle() }
            } finally { rendered.recycle() }
        } finally {
            initial.recycle()
            view.configureDocument(256, 256)
        }
    }

    @Test
    fun attachedEditorKeepsVisibleShapeThroughLiftDriftAndCoordinateJump() {
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        val view = onMain {
            DrawingView(activity).apply {
                configureDocument(1024, 1024)
                quickShapeEnabled = true
                activity.addContentView(this, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                ))
            }
        }
        try {
            instrumentation.waitForIdleSync()
            var events = 0
            val (x, y, downTime) = onMain {
                assertTrue(view.isAttachedToWindow && view.width > 0 && view.height > 0)
                view.onInteraction = { if (it is DrawingInteractionEvent.ShapeCommitted) events++ }
                val x = view.width * .5f
                val y = view.height * .5f
                val length = minOf(view.width, view.height) * .18f
                val now = SystemClock.uptimeMillis()
                val down = now - 128L
                dispatch(view, down, down, MotionEvent.ACTION_DOWN, x - length, y, stylus = true)
                for (i in 1..8) {
                    dispatch(view, down, down + i * 16L, MotionEvent.ACTION_MOVE,
                        x - length + i * length / 8f, y, stylus = true)
                }
                assertEquals(null, view.activeQuickShapePreviewTool)
                Triple(x, y, down)
            }
            val deadline = SystemClock.uptimeMillis() + 2_000L
            while (onMain { view.activeQuickShapePreviewTool } == null &&
                SystemClock.uptimeMillis() < deadline
            ) SystemClock.sleep(24L)
            onMain {
                assertEquals(DrawingTool.LINE, view.activeQuickShapePreviewTool)
                assertEquals(0, events)
                val density = view.resources.displayMetrics.density
                dispatch(view, downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE,
                    x + 7f * density, y, stylus = true)
                assertEquals(DrawingTool.LINE, view.activeQuickShapePreviewTool)
                dispatch(view, downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP,
                    x + 28f * density, y + 18f * density, stylus = true)
                assertEquals(1, events)
                assertEquals(null, view.activeQuickShapePreviewTool)
            }
        } finally {
            onMain {
                (view.parent as? ViewGroup)?.removeView(view)
                activity.finish()
            }
        }
    }

    @Test
    fun roundedImperfectPolygonsPreviewAndCommitWithoutClosingExactly() = onMain {
        val view = DrawingView(context).apply {
            configureDocument(1024, 1024)
            quickShapeEnabled = true
            brushSettings = brushSettings.copy(stabilization = .92f)
        }
        val events = mutableListOf<DrawingTool>()
        view.onInteraction = { if (it is DrawingInteractionEvent.ShapeCommitted) events += it.tool }
        val contours = listOf(
            DrawingTool.RECTANGLE to listOf(400f to 320f, 514f to 337f, 650f to 368f,
                679f to 388f, 673f to 446f, 651f to 570f, 632f to 595f,
                570f to 587f, 420f to 557f, 365f to 539f, 361f to 519f,
                378f to 411f, 390f to 359f),
            DrawingTool.TRIANGLE to listOf(310f to 673f, 385f to 571f, 469f to 454f,
                487f to 442f, 510f to 472f, 621f to 632f, 688f to 738f,
                679f to 754f, 610f to 744f, 448f to 723f, 315f to 707f, 292f to 697f),
        )
        try {
            contours.forEachIndexed { index, (tool, points) ->
                val start = 20_000L + index * 2_000L
                dispatch(view, start, start, MotionEvent.ACTION_DOWN,
                    points.first().first, points.first().second, stylus = true)
                var time = start
                for ((a, b) in points.zipWithNext()) {
                    for (sample in 1..5) {
                        time += 7L
                        val t = sample / 5f
                        dispatch(view, start, time, MotionEvent.ACTION_MOVE, a.first + (b.first - a.first) * t,
                            a.second + (b.second - a.second) * t, stylus = true)
                    }
                }
                assertEquals(tool, view.refreshQuickShapePreview(time + 350L))
                val (x, y) = points.last()
                dispatch(view, start, time + 360L, MotionEvent.ACTION_UP, x + 28f, y + 24f, stylus = true)
                assertEquals(tool, events.lastOrNull())
            }
            assertEquals(listOf(DrawingTool.RECTANGLE, DrawingTool.TRIANGLE), events)
        } finally {
            view.configureDocument(256, 256)
        }
    }

    @Test
    fun preciseRectangleHandlesEditPreviewThenApplyAsOneUndoStep() = onMain {
        val view = DrawingView(context).apply {
            configureDocument(1024, 1024)
            brushSettings = brushSettings.copy(sizePx = 12f, color = Color.BLUE)
        }
        val events = mutableListOf<DrawingTool>()
        view.onInteraction = { if (it is DrawingInteractionEvent.ShapeCommitted) events += it.tool }
        val initial = view.exportCompositeBitmap(false)
        try {
            assertTrue(view.startPreciseShape(DrawingTool.RECTANGLE))
            dragPrecise(view, 3000L, 140f, 180f, 690f, 620f)
            dragPrecise(view, 3100L, 140f, 180f, 192f, 230f)
            // Move the center after resizing; the rectangle keeps its width and height.
            dragPrecise(view, 3200L, 441f, 425f, 466f, 470f)
            val pending = view.exportCompositeBitmap(false)
            try { assertTrue(initial.sameAs(pending)) } finally { pending.recycle() }
            assertTrue(events.isEmpty())
            assertTrue(view.applyPreciseShape())
            val applied = view.exportCompositeBitmap(false)
            try {
                assertEquals(listOf(DrawingTool.RECTANGLE), events)
                assertEquals(0, Color.alpha(applied.getPixel(400, 180)))
                assertEquals(0, Color.alpha(applied.getPixel(400, 230)))
                assertTrue(Color.alpha(applied.getPixel(400, 275)) > 0)
                assertTrue(Color.alpha(applied.getPixel(715, 530)) > 0)
                view.undo()
                val undone = view.exportCompositeBitmap(false)
                try { assertTrue(initial.sameAs(undone)) } finally { undone.recycle() }
                view.redo()
                val restored = view.exportCompositeBitmap(false)
                try { assertTrue(applied.sameAs(restored)) } finally { restored.recycle() }
            } finally { applied.recycle() }
        } finally {
            initial.recycle()
            view.configureDocument(256, 256)
        }
    }

    @Test
    fun numericSelectionTranslationAndRotationUseOneHistoryStep() = onMain {
        val view = DrawingView(context).apply {
            configureDocument(512, 512)
            brushSettings = brushSettings.copy(sizePx = 22f, color = Color.RED, pressureSize = false)
            debugDrawStrokeForTest(listOf(StrokePoint(128f, 256f, 1f, 0f, 1L),
                StrokePoint(150f, 256f, 1f, 0f, 2L)))
            selectAll()
        }
        val before = view.exportCompositeBitmap(false)
        try {
            assertTrue(!view.applyNumericSelectionTransform(Float.NaN, 0f, 100f, 0f))
            assertTrue(!view.applyNumericSelectionTransform(0f, 0f, 100f, 360f))
            assertTrue(view.applyNumericSelectionTransform(24f, 10f, 100f, 90f))
            assertEquals(0, Color.alpha(view.debugPixelForTest(128f, 256f)))
            assertTrue(Color.alpha(view.debugPixelForTest(280f, 138f)) > 0)
            val after = view.exportCompositeBitmap(false)
            try {
                view.undo()
                val undone = view.exportCompositeBitmap(false)
                try { assertTrue(before.sameAs(undone)) } finally { undone.recycle() }
                view.redo()
                val redone = view.exportCompositeBitmap(false)
                try { assertTrue(after.sameAs(redone)) } finally { redone.recycle() }
            } finally { after.recycle() }
        } finally {
            before.recycle()
            view.configureDocument(256, 256)
        }
    }

    @Test
    fun recognizedShapeCanOpenAnEditableDraftWithoutWritingPixels() = onMain {
        val view = DrawingView(context).apply {
            configureDocument(1024, 1024)
            quickShapeEnabled = true
            quickShapeEditBeforeApply = true
            quickMenuTouchHoldEnabled = false
        }
        val initial = view.exportCompositeBitmap(false)
        var draft: DrawingTool? = null
        view.onPreciseShapeChanged = { kind, ready -> draft = kind.takeIf { ready } }
        try {
            drawOval(view, held = true)
            assertEquals(DrawingTool.ELLIPSE, draft)
            val uncommitted = view.exportCompositeBitmap(false)
            try { assertTrue(initial.sameAs(uncommitted)) } finally { uncommitted.recycle() }
            dragPrecise(view, 12_000L, 400f, 380f, 440f, 412f)
            assertTrue(view.applyPreciseShape())
            assertEquals(null, draft)
            val applied = view.exportCompositeBitmap(false)
            try { assertTrue(!initial.sameAs(applied)) } finally { applied.recycle() }
            view.undo()
            val undone = view.exportCompositeBitmap(false)
            try { assertTrue(initial.sameAs(undone)) } finally { undone.recycle() }
        } finally {
            initial.recycle()
            view.configureDocument(256, 256)
        }
    }

    @Test
    fun preciseLineRendersItsActualInclinationAndEllipseUsesCurvedEdges() = onMain {
        val view = DrawingView(context).apply {
            configureDocument(1024, 1024)
            brushSettings = brushSettings.copy(sizePx = 14f, color = Color.BLUE)
        }
        try {
            assertTrue(view.startPreciseShape(DrawingTool.LINE))
            dragPrecise(view, 5000L, 140f, 340f, 340f, 140f)
            assertTrue(view.applyPreciseShape())
            assertTrue(Color.alpha(view.debugPixelForTest(190f, 290f)) > 0)
            assertEquals(0, Color.alpha(view.debugPixelForTest(190f, 190f)))
            assertTrue(view.startPreciseShape(DrawingTool.ELLIPSE))
            dragPrecise(view, 5100L, 400f, 420f, 740f, 660f)
            assertTrue(view.applyPreciseShape())
            assertTrue(Color.alpha(view.debugPixelForTest(570f, 420f)) > 0)
            assertEquals(0, Color.alpha(view.debugPixelForTest(400f, 420f)))
        } finally {
            view.configureDocument(256, 256)
        }
    }

    @Test
    fun preciseTriangleCanMoveAnIndependentPointAndCancelWithoutPainting() = onMain {
        val view = DrawingView(context).apply { configureDocument(1024, 1024) }
        val ready = mutableListOf<Pair<DrawingTool?, Boolean>>()
        view.onPreciseShapeChanged = { tool, valid -> ready += tool to valid }
        val initial = view.exportCompositeBitmap(false)
        try {
            assertTrue(view.startPreciseShape(DrawingTool.TRIANGLE))
            dragPrecise(view, 4000L, 150f, 200f, 560f, 620f)
            dragPrecise(view, 4100L, 355f, 200f, 400f, 260f)
            assertTrue(view.cancelPreciseShape())
            assertTrue(!view.applyPreciseShape())
            assertEquals(listOf(DrawingTool.TRIANGLE to false, DrawingTool.TRIANGLE to true, null to false), ready)
            val after = view.exportCompositeBitmap(false)
            try { assertTrue(initial.sameAs(after)) } finally { after.recycle() }
        } finally {
            initial.recycle()
            view.configureDocument(256, 256)
        }
    }

    private fun dragPrecise(view: DrawingView, start: Long, x: Float, y: Float, endX: Float, endY: Float) {
        dispatch(view, start, start, MotionEvent.ACTION_DOWN, x, y, stylus = true)
        dispatch(view, start, start + 20L, MotionEvent.ACTION_MOVE, endX, endY, stylus = true)
        dispatch(view, start, start + 32L, MotionEvent.ACTION_UP, endX, endY, stylus = true)
    }

    @Test
    fun movingAfterPreviewKeepsOriginalGestureFreehand() = onMain {
        val view = DrawingView(context).apply {
            configureDocument(1024, 1024)
            quickShapeEnabled = true
        }
        var strokes = 0
        view.onInteraction = {
            if (it is DrawingInteractionEvent.ShapeCommitted) error("Unexpected shape after leaving the hold")
            if (it is DrawingInteractionEvent.StrokeCommitted) strokes++
        }
        val start = 10_000L
        try {
            dispatch(view, start, start, MotionEvent.ACTION_DOWN, 120f, 340f, stylus = true)
            (1..12).forEach { i ->
                dispatch(view, start, start + i * 10L, MotionEvent.ACTION_MOVE, 120f + i * 25f,
                    340f, stylus = true)
            }
            assertEquals(DrawingTool.LINE, view.refreshQuickShapePreview(start + 455L))
            dispatch(view, start, start + 461L, MotionEvent.ACTION_MOVE, 419f, 410f, stylus = true)
            assertEquals(null, view.refreshQuickShapePreview(start + 462L))
            dispatch(view, start, start + 470L, MotionEvent.ACTION_UP, 415f, 420f, stylus = true)
            assertEquals(1, strokes)
        } finally {
            view.configureDocument(256, 256)
        }
    }

    private fun drawPolygon(view: DrawingView, start: Long, vararg vertices: Pair<Float, Float>) {
        val count = vertices.size * 14
        for (i in 0..count) {
            val (ax, ay) = vertices[(i / 14) % vertices.size]
            val (bx, by) = vertices[(i / 14 + 1) % vertices.size]
            val t = i % 14 / 14f
            dispatch(view, start, start + i * 9L,
                if (i == 0) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_MOVE,
                ax + (bx - ax) * t, ay + (by - ay) * t, stylus = true)
        }
        dispatch(view, start, start + count * 9L + 360L, MotionEvent.ACTION_UP,
            vertices.first().first, vertices.first().second, stylus = true)
    }

    private fun drawOval(view: DrawingView, held: Boolean) {
        val start = 10_000L
        dispatch(view, start, start, MotionEvent.ACTION_DOWN, 580f, 380f)
        for (index in 1..64) {
            val angle = index / 64.0 * PI * 2
            dispatch(
                view, start, start + index * 12L, MotionEvent.ACTION_MOVE,
                400f + 180f * cos(angle).toFloat(),
                380f + 102f * sin(angle).toFloat(),
            )
        }
        dispatch(view, start, start + if (held) 1350L else 780L, MotionEvent.ACTION_UP, 580f, 380f)
    }

    private fun drawStroke(view: DrawingView, held: Boolean, curved: Boolean) {
        val start = 10_000L
        dispatch(view, start, start, MotionEvent.ACTION_DOWN, 160f, 260f)
        repeat(12) { index ->
            val progress = (index + 1f) / 12f
            val y = if (curved) 260f + 90f * kotlin.math.sin(progress * Math.PI).toFloat()
                    else 260f
            dispatch(
                view,
                start,
                start + 16L * (index + 1),
                MotionEvent.ACTION_MOVE,
                160f + 520f * progress,
                y,
            )
        }
        dispatch(
            view, start,
            if (held) start + 820L else start + 210L,
            MotionEvent.ACTION_UP,
            680f, 260f,
        )
    }

    private fun dispatch(
        view: DrawingView,
        downTime: Long,
        eventTime: Long,
        action: Int,
        x: Float,
        y: Float,
        stylus: Boolean = false,
    ) {
        val event = if (!stylus) MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
        else MotionEvent.obtain(downTime, eventTime, action, 1,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }),
            arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .62f; size = 1f }),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
        event.also {
            try { view.onTouchEvent(it) } finally { it.recycle() }
        }
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return requireNotNull(result).getOrThrow()
    }
}
