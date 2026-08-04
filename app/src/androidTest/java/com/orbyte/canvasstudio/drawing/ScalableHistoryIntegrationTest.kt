package com.orbyte.canvasstudio.drawing

import android.content.Context
import android.graphics.Bitmap
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orbyte.canvasstudio.drawing.history.TileCheckpointPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScalableHistoryIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun distributedHistoryExaminesOnlyIndexedTileCommandsAndCheckpointRestoresPixels() {
        val view = onMain {
            DrawingView(context).apply {
                configureDocument(1536, 1024)
                tool = DrawingTool.BRUSH
                brushSettings = BrushSettings(sizePx = 42f, pressureSize = false, stabilization = 0f)
                debugPerformanceMetricsEnabled = true
                tileCheckpointPolicy = TileCheckpointPolicy(1, 1, 0)
            }
        }
        try {
            onMain {
                stroke(view, 120f, 180f, 300f, 220f, 1_000L) // tile 0
                stroke(view, 720f, 180f, 900f, 220f, 2_000L) // unrelated tile 1
                stroke(view, 140f, 320f, 320f, 350f, 3_000L) // tile 0
            }
            val expected = onMain { view.exportCompositeBitmap(false) }
            onMain { view.resetDebugPerformanceMetrics(); view.undo() }
            val indexedUndo = view.debugPerformanceMetrics()
            assertTrue(indexedUndo.indexQueries > 0)
            assertEquals(0, indexedUndo.indexFallbacks)
            assertEquals(1L, indexedUndo.commandsExamined)
            assertEquals(1L, indexedUndo.commandsReplayed)

            onMain { view.redo() } // creates a checkpoint at the full cursor
            assertPixelEqual(expected, onMain { view.exportCompositeBitmap(false) })
            onMain { view.undo(); view.resetDebugPerformanceMetrics(); view.redo() }
            val checkpointRedo = view.debugPerformanceMetrics()
            assertTrue(checkpointRedo.checkpointHits > 0)
            assertEquals(0, checkpointRedo.indexFallbacks)
            assertEquals(0L, checkpointRedo.commandsReplayed)
            assertPixelEqual(expected, onMain { view.exportCompositeBitmap(false) })
            android.util.Log.i(
                "CanvasStudioPhase2",
                "PHASE2_METRICS historyTotal=3 " +
                    "indexedUndoQueries=${indexedUndo.indexQueries} " +
                    "indexedUndoExamined=${indexedUndo.commandsExamined} " +
                    "indexedUndoReplayed=${indexedUndo.commandsReplayed} " +
                    "checkpointHits=${checkpointRedo.checkpointHits} " +
                    "commandsAfterCheckpoint=${checkpointRedo.commandsAfterCheckpoint} " +
                    "checkpointBytes=${checkpointRedo.checkpointBytes} " +
                    "checkpointBudgetBytes=${checkpointRedo.checkpointBudgetBytes} " +
                    "fallbacks=${checkpointRedo.indexFallbacks}",
            )
            expected.recycle()
        } finally {
            onMain { view.configureDocument(256, 256) }
        }
    }

    @Test fun maskHistoryUndoRedoStaysIsolatedFromLayerContent() {
        val view = onMain {
            DrawingView(context).apply {
                configureDocument(1024, 1024)
                tool = DrawingTool.BRUSH
                brushSettings = BrushSettings(sizePx = 90f, pressureSize = false, stabilization = 0f)
                debugPerformanceMetricsEnabled = true
            }
        }
        try {
            onMain { stroke(view, 100f, 240f, 760f, 240f, 10_000L) }
            val content = onMain { view.exportCompositeBitmap(false) }
            onMain {
                view.addMaskToActiveLayer()
                stroke(view, 300f, 210f, 520f, 270f, 11_000L)
            }
            val masked = onMain { view.exportCompositeBitmap(false) }
            assertTrue("La máscara debe modificar el compuesto", !content.sameAs(masked))
            onMain { view.resetDebugPerformanceMetrics(); view.undo() }
            assertPixelEqual(content, onMain { view.exportCompositeBitmap(false) })
            onMain { view.redo() }
            assertPixelEqual(masked, onMain { view.exportCompositeBitmap(false) })
            assertEquals(0, view.debugPerformanceMetrics().indexFallbacks)
            content.recycle(); masked.recycle()
        } finally {
            onMain { view.configureDocument(256, 256) }
        }
    }

    private fun stroke(view: DrawingView, x1: Float, y1: Float, x2: Float, y2: Float, time: Long) {
        event(view, MotionEvent.ACTION_DOWN, x1, y1, time, time)
        event(view, MotionEvent.ACTION_MOVE, x2, y2, time, time + 16)
        event(view, MotionEvent.ACTION_UP, x2, y2, time, time + 32)
    }

    private fun event(view: DrawingView, action: Int, x: Float, y: Float, down: Long, at: Long) {
        MotionEvent.obtain(down, at, action, x, y, 0).also { try { view.onTouchEvent(it) } finally { it.recycle() } }
    }

    private fun assertPixelEqual(expected: Bitmap, actual: Bitmap) {
        try { assertTrue("El replay indexado/checkpoint cambió píxeles", expected.sameAs(actual)) }
        finally { actual.recycle() }
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return requireNotNull(result).getOrThrow()
    }
}
