package com.orbyte.canvasstudio.drawing

import android.content.Context
import android.graphics.Color
import android.graphics.BitmapFactory
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orbyte.canvasstudio.model.ProjectRepository
import com.orbyte.canvasstudio.drawing.raster.DocumentExportSnapshot
import java.io.ByteArrayOutputStream
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
            assertEquals(2, view.debugAsyncStrokeEnqueueCount)
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
    fun secondPenDownDoesNotFlushBlockedFirstRasterWorker() {
        val enteredWorker = CountDownLatch(1)
        val releaseWorker = CountDownLatch(1)
        val twoCommits = CountDownLatch(2)
        val view = DrawingView(context)
        try {
            instrumentation.runOnMainSync {
                configure(view)
                view.debugBeforeAsyncStrokePrepare = {
                    enteredWorker.countDown()
                    check(releaseWorker.await(25, TimeUnit.SECONDS)) {
                        "Experimental worker release timed out"
                    }
                }
                view.onInteraction = {
                    if (it is DrawingInteractionEvent.StrokeCommitted) twoCommits.countDown()
                }
                drawStroke(view, 65f, 85f, 145f, 85f)
            }
            assertTrue("Raster worker never started", enteredWorker.await(10, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                // Worker #1 is deliberately blocked. Gesture #2 must still
                // complete on UI, queue after #1 and remain visible, rather
                // than force a synchronous replay of #1.
                drawStroke(view, 280f, 315f, 390f, 315f)
                assertEquals(2, view.debugAsyncStrokeEnqueueCount)
                assertEquals(2, view.debugPendingStrokeCommits())
                assertEquals(0, view.debugAsyncStrokeFallbackCount)
                assertEquals(0, view.debugAsyncStrokePublishedCount)
            }
            releaseWorker.countDown()
            assertTrue("Both ordered raster commits were not delivered",
                twoCommits.await(55, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                assertEquals(0, view.debugPendingStrokeCommits())
                assertEquals(2, view.debugAsyncStrokePublishedCount)
                assertEquals(0, view.debugAsyncStrokeFallbackCount)
                assertTrue(Color.alpha(view.debugPixelForTest(104f, 85f)) > 0)
                assertTrue(Color.alpha(view.debugPixelForTest(329f, 315f)) > 0)
            }
        } finally {
            releaseWorker.countDown()
            instrumentation.runOnMainSync {
                view.debugBeforeAsyncStrokePrepare = null
                view.debugFlushPendingStrokeCommits()
            }
        }
    }

    @Test
    fun exportSnapshotBarrierContainsPendingDetachedStroke() {
        val view = DrawingView(context)
        lateinit var snapshot: DocumentExportSnapshot
        instrumentation.runOnMainSync {
            configure(view)
            drawStroke(view, 95f, 245f, 260f, 245f)
            snapshot = view.captureExportSnapshot()
            // Safe owner-thread export barrier must publish pending history
            // before it gives a worker access to detached raster data.
            assertEquals(0, view.debugPendingStrokeCommits())
        }
        val out = ByteArrayOutputStream()
        snapshot.writePng(out, includePaper = false)
        val bytes = out.toByteArray()
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        try {
            assertEquals(512, bitmap.width)
            assertEquals(512, bitmap.height)
            assertTrue(Color.alpha(bitmap.getPixel(177, 245)) > 0)
            assertEquals(0, Color.alpha(bitmap.getPixel(25, 25)))
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun debouncedAutosaveDoesNotBlockUiWhileRasterWorkerIsPending() {
        val projectId = "idle-autosave-${UUID.randomUUID()}"
        val enteredWorker = CountDownLatch(1)
        val releaseWorker = CountDownLatch(1)
        val saved = CountDownLatch(1)
        val view = DrawingView(context)
        var saveSucceeded = false
        try {
            instrumentation.runOnMainSync {
                configure(view)
                view.debugBeforeAsyncStrokePrepare = {
                    enteredWorker.countDown()
                    check(releaseWorker.await(25, TimeUnit.SECONDS))
                }
                view.onProjectSaved = { success ->
                    saveSucceeded = success
                    saved.countDown()
                }
                drawStroke(view, 105f, 205f, 265f, 205f)
            }
            assertTrue("Worker did not begin", enteredWorker.await(10, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                // This must only enqueue the autosave; it must not flush the
                // blocked stroke or encode while holding the pen UI thread.
                view.saveProjectWhenIdle(projectId, "Sin bloqueo", 300, includePreview = false)
                assertEquals(1, view.debugPendingStrokeCommits())
                assertFalse(ProjectRepository.metadataFile(context, projectId).isFile)
            }
            releaseWorker.countDown()
            assertTrue("Idle autosave never completed", saved.await(55, TimeUnit.SECONDS))
            assertTrue("Idle autosave failed", saveSucceeded)
            assertTrue(ProjectRepository.metadataFile(context, projectId).isFile)
        } finally {
            releaseWorker.countDown()
            instrumentation.runOnMainSync {
                view.debugBeforeAsyncStrokePrepare = null
                view.debugFlushPendingStrokeCommits()
            }
            ProjectRepository.projectDirectory(context, projectId).deleteRecursively()
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
