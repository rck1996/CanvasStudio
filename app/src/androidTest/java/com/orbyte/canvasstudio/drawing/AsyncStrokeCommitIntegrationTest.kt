package com.orbyte.canvasstudio.drawing

import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orbyte.canvasstudio.model.ProjectRepository
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in experimental path only. Production still uses the synchronous CPU
 * fallback. These tests verify the UI publication boundary and history order.
 */
@RunWith(AndroidJUnit4::class)
class AsyncStrokeCommitIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun backgroundStrokeIsPublishedBeforeHistoryCallbackAndUndoRedoWorks() {
        val committed = CountDownLatch(1)
        val view = DrawingView(context)
        instrumentation.runOnMainSync {
            configure(view)
            view.onInteraction = { if (it is DrawingInteractionEvent.StrokeCommitted) committed.countDown() }
            drawStroke(view, 90f, 110f, 200f, 110f)
        }
        assertTrue("Detached background commit missing", committed.await(45, TimeUnit.SECONDS))
        instrumentation.runOnMainSync {
            assertEquals(0, view.debugPendingStrokeCommits())
            assertTrue("Stroke not present after callback", Color.alpha(view.debugPixelForTest(145f, 110f)) > 0)
            view.undo()
            assertEquals(0, Color.alpha(view.debugPixelForTest(145f, 110f)))
            view.redo()
            assertTrue(Color.alpha(view.debugPixelForTest(145f, 110f)) > 0)
        }
    }

    @Test
    fun consecutiveStrokesCannotOvertakePendingPublication() {
        val view = DrawingView(context)
        var committed = 0
        instrumentation.runOnMainSync {
            configure(view)
            view.onInteraction = { if (it is DrawingInteractionEvent.StrokeCommitted) committed++ }
            drawStroke(view, 70f, 140f, 160f, 140f)
            // ACTION_DOWN imposes a synchronous barrier on the prior stroke.
            drawStroke(view, 240f, 220f, 340f, 220f)
            view.debugFlushPendingStrokeCommits()
            assertEquals(2, committed)
            assertEquals(0, view.debugPendingStrokeCommits())
            assertTrue(Color.alpha(view.debugPixelForTest(115f, 140f)) > 0)
            assertTrue(Color.alpha(view.debugPixelForTest(290f, 220f)) > 0)
            view.undo()
            assertEquals(0, Color.alpha(view.debugPixelForTest(290f, 220f)))
            assertTrue(Color.alpha(view.debugPixelForTest(115f, 140f)) > 0)
        }
    }

    @Test
    fun saveBarriersPublishBeforeCapturingProjectSnapshot() {
        val projectId = "async-save-${UUID.randomUUID()}"
        val saved = CountDownLatch(1)
        val view = DrawingView(context)
        var success = false
        try {
            instrumentation.runOnMainSync {
                configure(view)
                view.onProjectSaved = { result ->
                    success = result
                    saved.countDown()
                }
                drawStroke(view, 130f, 240f, 260f, 240f)
                view.saveProject(projectId, "Trazo asíncrono", 300, includePreview = true)
                assertEquals(0, view.debugPendingStrokeCommits())
            }
            assertTrue("No project-save callback", saved.await(45, TimeUnit.SECONDS))
            assertTrue("Project save failed", success)
            val project = ProjectRepository.projectDirectory(context, projectId)
            assertTrue("Missing thumbnail", java.io.File(project, "preview.png").isFile)
            assertTrue(
                "Asynchronous stroke was not persisted",
                java.io.File(project, "layers").walkTopDown()
                    .any { it.extension == "png" && it.length() > 0L },
            )
        } finally {
            ProjectRepository.projectDirectory(context, projectId).deleteRecursively()
        }
    }

    private fun configure(view: DrawingView) {
        view.configureDocument(512, 512)
        view.quickShapeEnabled = false
        view.quickMenuTouchHoldEnabled = false
        view.debugAsyncStrokeRasterEnabled = true
        view.brushSettings = BrushSettings(
            kind = BrushKind.INK,
            sizePx = 52f,
            color = Color.rgb(20, 90, 215),
            opacity = 1f,
            spacing = .08f,
        )
    }

    private fun drawStroke(view: DrawingView, fromX: Float, fromY: Float, toX: Float, toY: Float) {
        val downTime = 10_000L
        touch(view, downTime, downTime, MotionEvent.ACTION_DOWN, fromX, fromY)
        repeat(6) { index ->
            val fraction = (index + 1f) / 6f
            touch(
                view,
                downTime,
                downTime + 16L * (index + 1),
                MotionEvent.ACTION_MOVE,
                fromX + (toX - fromX) * fraction,
                fromY + (toY - fromY) * fraction,
            )
        }
        touch(view, downTime, downTime + 120L, MotionEvent.ACTION_UP, toX, toY)
    }

    private fun touch(view: DrawingView, down: Long, time: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(down, time, action, x, y, 0)
        try { view.onTouchEvent(event) } finally { event.recycle() }
    }
}
