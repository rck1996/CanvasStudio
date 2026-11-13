package com.orbyte.canvasstudio.drawing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.content.Context
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orbyte.canvasstudio.model.ProjectRepository
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    @Test
    fun openingAMaskOnAnInactiveLayerSelectsItsRasterTarget() = onMain {
        var layers = emptyList<LayerUiModel>()
        val view = DrawingView(ApplicationProvider.getApplicationContext()).apply {
            configureDocument(384, 384)
            onLayersChanged = { layers = it }
            addMaskToActiveLayer()
        }
        try {
            val maskLayer = layers.single { it.isActive }.id
            view.addLayer()
            assertNotEquals(maskLayer, layers.single { it.isActive }.id)
            view.setEditingLayerMask(maskLayer, true)
            assertEquals(maskLayer, layers.single { it.isActive && it.editingMask }.id)
        } finally {
            view.configureDocument(256, 256)
        }
    }

    @Test
    fun originalComparisonChangesOnlyViewportAndRejectsStrokes() = onMain {
        var layers = emptyList<LayerUiModel>()
        val view = DrawingView(ApplicationProvider.getApplicationContext()).apply {
            configureDocument(384, 384)
            layout(0, 0, 512, 512)
            onLayersChanged = { layers = it }
            brushSettings = BrushSettings(color = Color.RED, sizePx = 96f, pressureSize = false)
        }
        val stroke = listOf(StrokePoint(90f, 192f, 1f, 0f, 0), StrokePoint(296f, 192f, 1f, 0f, 32))
        fun visiblePixel(): Int {
            val frame = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            return try {
                view.draw(Canvas(frame))
                frame.getPixel(256, 256)
            } finally {
                frame.recycle()
            }
        }
        fun exportPixel(): Int {
            val frame = view.exportCompositeBitmap(false)
            return try { frame.getPixel(192, 192) } finally { frame.recycle() }
        }
        try {
            view.debugDrawStrokeForTest(stroke)
            val original = visiblePixel()
            view.addMaskToActiveLayer()
            view.debugDrawStrokeForTest(stroke)
            val result = visiblePixel()
            val maskedExport = exportPixel()
            assertNotEquals(original, result)

            view.setMaskOriginalPreview(true)
            assertEquals(original, visiblePixel())
            assertEquals(maskedExport, exportPixel())
            val layer = layers.single { it.isActive }
            assertTrue(layer.maskEnabled && layer.maskPreviewOriginal)
            val before = view.debugPixelForTest(192f, 192f)
            view.tool = DrawingTool.ERASER
            val now = android.os.SystemClock.uptimeMillis()
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP).forEachIndexed { i, action ->
                val event = MotionEvent.obtain(now, now + 16L * i, action, 220f + 36f * i, 256f, 0)
                try { view.onTouchEvent(event) } finally { event.recycle() }
            }
            assertEquals(before, view.debugPixelForTest(192f, 192f))
            view.finishMaskEditing()
            assertEquals(result, visiblePixel())
            assertFalse(layers.single { it.isActive }.maskPreviewOriginal)
        } finally {
            view.configureDocument(256, 256)
        }
    }

    @Test
    fun strengthIsReversibleAndDoesNotChangeMaskStrokeHistory() = onMain {
        var activeId = ""
        val view = DrawingView(ApplicationProvider.getApplicationContext()).apply {
            configureDocument(384, 384)
            onLayersChanged = { layers -> activeId = layers.single { it.isActive }.id }
            refreshLayerState()
            brushSettings = BrushSettings(color = Color.RED, sizePx = 80f, pressureSize = false)
        }
        val stroke = listOf(StrokePoint(90f, 192f, 1f, 0f, 0), StrokePoint(296f, 192f, 1f, 0f, 32))
        fun resultAlpha(): Int {
            val frame = view.exportCompositeBitmap(false)
            return try { Color.alpha(frame.getPixel(192, 192)) } finally { frame.recycle() }
        }
        try {
            view.debugDrawStrokeForTest(stroke)
            assertEquals(255, resultAlpha())
            view.addMaskToActiveLayer()
            view.debugDrawStrokeForTest(stroke)
            val maskPixel = view.debugPixelForTest(192f, 192f)
            assertEquals(0, resultAlpha())
            view.setLayerMaskStrength(activeId, 0)
            assertEquals(255, resultAlpha())
            view.setLayerMaskStrength(activeId, 50)
            val half = resultAlpha()
            assertTrue("Unexpected alpha at 50%: $half", half in 126..128)
            assertEquals(maskPixel, view.debugPixelForTest(192f, 192f))
            view.toggleLayerMaskEnabled(activeId)
            assertEquals(255, resultAlpha())
            view.toggleLayerMaskEnabled(activeId)
            assertEquals(half, resultAlpha())
            view.undo()
            assertEquals(255, resultAlpha())
            view.redo()
            assertEquals(half, resultAlpha())
            view.setLayerMaskStrength(activeId, 100)
            assertEquals(0, resultAlpha())
            view.toggleLayerMaskInverted(activeId)
            assertEquals(255, resultAlpha())
            assertEquals(maskPixel, view.debugPixelForTest(192f, 192f))
            view.undo()
            assertEquals(0, resultAlpha())
            view.redo()
            assertEquals(255, resultAlpha())
            view.toggleLayerMaskInverted(activeId)
            assertEquals(0, resultAlpha())
        } finally {
            view.configureDocument(256, 256)
        }
    }

    @Test
    fun strengthInversionAndThumbnailSurviveSavingAndReopening() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = "mask-strength-${UUID.randomUUID()}"
        val saved = CountDownLatch(1)
        var success = false
        var current: LayerUiModel? = null
        val view = onMain {
            DrawingView(context).apply {
                configureDocument(384, 384)
                onLayersChanged = { current = it.singleOrNull { layer -> layer.isActive } }
                brushSettings = BrushSettings(color = Color.RED, sizePx = 80f, pressureSize = false)
                val stroke = listOf(StrokePoint(90f, 192f, 1f, 0f, 0), StrokePoint(296f, 192f, 1f, 0f, 32))
                debugDrawStrokeForTest(stroke)
                addMaskToActiveLayer()
                debugDrawStrokeForTest(stroke)
                setLayerMaskStrength(requireNotNull(current).id, 50)
                toggleLayerMaskInverted(requireNotNull(current).id)
                onProjectSaved = { success = it; saved.countDown() }
                saveProject(id, "Mask strength", 300, includePreview = true)
            }
        }
        try {
            assertTrue("Save did not complete", saved.await(30, TimeUnit.SECONDS) && success)
            val previewFile = File(ProjectRepository.projectDirectory(context, id), "preview.png")
            val preview = requireNotNull(BitmapFactory.decodeFile(previewFile.absolutePath))
            try {
                onMain {
                    assertTrue(view.loadProject(id))
                    assertEquals(50, current?.maskStrength)
                    assertTrue(current?.maskInverted == true)
                    val rendered = view.exportCompositeBitmap()
                    try { assertTrue("Thumbnail differs from the reopened document", rendered.sameAs(preview)) }
                    finally { rendered.recycle() }
                }
            } finally {
                preview.recycle()
            }
        } finally {
            onMain { view.configureDocument(256, 256) }
            ProjectRepository.projectDirectory(context, id).deleteRecursively()
        }
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = runCatching(block)
        }
        return result!!.getOrThrow()
    }
}
