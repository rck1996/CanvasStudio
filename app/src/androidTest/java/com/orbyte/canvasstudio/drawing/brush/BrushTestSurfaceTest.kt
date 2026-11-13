package com.orbyte.canvasstudio.drawing.brush

import android.graphics.Color
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orbyte.canvasstudio.drawing.BrushSettings
import com.orbyte.canvasstudio.drawing.DrawingView
import com.orbyte.canvasstudio.drawing.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrushTestSurfaceTest {
    @Test
    fun drawingAndClearingPaperDoesNotChangeTheDocument() = onMain {
        val view = DrawingView(ApplicationProvider.getApplicationContext()).apply {
            configureDocument(512, 512)
            brushSettings = BrushSettings(color = Color.RED, sizePx = 35f, pressureSize = false)
            debugDrawStrokeForTest(listOf(StrokePoint(100f, 160f, 1f, 0f, 0), StrokePoint(280f, 160f, 1f, 0f, 32)))
        }
        val documentBefore = view.exportCompositeBitmap(false)
        var documentChanges = 0
        view.onDocumentChanged = { documentChanges++ }
        val paper = BrushTestSurface(view.context).apply {
            settings = BrushSettings(color = Color.BLUE, sizePx = 45f, pressureSize = false, stabilization = 0f)
            layout(0, 0, 560, 220)
        }
        try {
            stroke(paper, 110f, MotionEvent.ACTION_UP)
            assertNotEquals(0, Color.alpha(paper.inkAt(240, 110)))
            paper.darkPaper = true
            assertNotEquals(0, Color.alpha(paper.inkAt(240, 110)))
            paper.clearPaper()
            assertEquals(Color.TRANSPARENT, paper.inkAt(240, 110))
            val after = view.exportCompositeBitmap(false)
            try { assertTrue(documentBefore.sameAs(after)) } finally { after.recycle() }
            assertEquals(0, documentChanges)
        } finally {
            paper.release()
            documentBefore.recycle()
            view.configureDocument(256, 256)
        }
    }

    @Test
    fun cancellingAStrokeDiscardsTheTransientMarks() = onMain {
        val paper = BrushTestSurface(ApplicationProvider.getApplicationContext()).apply {
            layout(0, 0, 560, 220)
        }
        try {
            stroke(paper, 110f, MotionEvent.ACTION_CANCEL)
            assertEquals(Color.TRANSPARENT, paper.inkAt(240, 110))
        } finally {
            paper.release()
        }
    }

    private fun stroke(paper: BrushTestSurface, y: Float, end: Int) {
        val time = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, end).forEachIndexed { i, action ->
            val event = MotionEvent.obtain(time, time + 16L * i, action, 90f + 170f * i, y, 0)
            try { paper.onTouchEvent(event) } finally { event.recycle() }
        }
    }

    private fun onMain(block: () -> Unit) {
        var outcome: Result<Unit>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { outcome = runCatching(block) }
        outcome!!.getOrThrow()
    }
}
