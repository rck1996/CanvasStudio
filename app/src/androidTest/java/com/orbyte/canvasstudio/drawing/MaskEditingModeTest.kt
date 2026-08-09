package com.orbyte.canvasstudio.drawing

import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaskEditingModeTest {
    @Test
    fun maskEditingAlwaysHasAnExplicitExitBackToLayerContent() = onMain {
        var layers = emptyList<LayerUiModel>()
        val view = DrawingView(ApplicationProvider.getApplicationContext()).apply {
            configureDocument(1024, 768)
            onLayersChanged = { layers = it }
            refreshLayerState()
            addMaskToActiveLayer()
        }
        assertTrue(layers.single { it.isActive }.editingMask)
        assertTrue(view.finishMaskEditing())
        assertFalse(layers.single { it.isActive }.editingMask)
        assertFalse(view.finishMaskEditing())
    }

    @Test
    fun incompatibleSelectionToolAutomaticallyLeavesMaskEditing() = onMain {
        var layers = emptyList<LayerUiModel>()
        val view = DrawingView(ApplicationProvider.getApplicationContext()).apply {
            configureDocument(1024, 768)
            layout(0, 0, 1200, 800)
            onLayersChanged = { layers = it }
            refreshLayerState()
            addMaskToActiveLayer()
            tool = DrawingTool.SELECT_RECTANGLE
        }
        assertTrue(layers.single { it.isActive }.editingMask)
        val now = android.os.SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 600f, 400f, 0)
        try {
            view.onTouchEvent(down)
        } finally {
            down.recycle()
        }
        assertFalse(layers.single { it.isActive }.editingMask)
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = runCatching(block)
        }
        return result!!.getOrThrow()
    }
}
