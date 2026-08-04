package com.orbyte.canvasstudio.ui.tutorial

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudioTutorialStateTest {
    @Test fun wrongActionDoesNotAdvanceLesson() {
        val state = reduceStudioTutorial(
            StudioTutorialState(),
            StudioTutorialAction.Observe(StudioTutorialEvent.LayerCreated),
        )
        assertFalse(state.currentComplete)
    }

    @Test fun navigationRequiresZoomAndPan() {
        val zoom = reduceStudioTutorial(
            StudioTutorialState(),
            StudioTutorialAction.Observe(StudioTutorialEvent.CanvasZoomChanged(1.2f, 0f)),
        )
        assertFalse(zoom.currentComplete)
        val pan = reduceStudioTutorial(
            zoom,
            StudioTutorialAction.Observe(StudioTutorialEvent.CanvasZoomChanged(1f, 30f)),
        )
        assertTrue(pan.currentComplete)
    }

    @Test fun pressureLessonRequiresDynamicRealStroke() {
        val initial = StudioTutorialState(current = StudioTutorialModule.BRUSH_PEN)
        val flat = reduceStudioTutorial(initial, StudioTutorialAction.Observe(StudioTutorialEvent.StrokeCommitted(.8f, .72f, false)))
        assertFalse(flat.currentComplete)
        val dynamic = reduceStudioTutorial(initial, StudioTutorialAction.Observe(StudioTutorialEvent.StrokeCommitted(.88f, .12f, false)))
        assertTrue(dynamic.currentComplete)
    }

    @Test fun selectionTransformAndUndoRedoRequireOrderedPairs() {
        val selection = reduceStudioTutorial(
            StudioTutorialState(current = StudioTutorialModule.SELECTION),
            StudioTutorialAction.Observe(StudioTutorialEvent.SelectionCommitted),
        )
        assertTrue(selection.currentComplete)
        val transform = reduceStudioTutorial(
            StudioTutorialState(current = StudioTutorialModule.TRANSFORMATION),
            StudioTutorialAction.Observe(StudioTutorialEvent.TransformCommitted),
        )
        assertTrue(transform.currentComplete)

        var history = StudioTutorialState(current = StudioTutorialModule.UNDO_REDO)
        history = reduceStudioTutorial(history, StudioTutorialAction.Observe(StudioTutorialEvent.RedoPerformed))
        assertFalse(history.currentComplete)
        history = reduceStudioTutorial(history, StudioTutorialAction.Observe(StudioTutorialEvent.UndoPerformed))
        history = reduceStudioTutorial(history, StudioTutorialAction.Observe(StudioTutorialEvent.RedoPerformed))
        assertTrue(history.currentComplete)
    }

    @Test fun pauseSkipRestartAndIndividualReplayAreSafe() {
        val paused = reduceStudioTutorial(StudioTutorialState(current = StudioTutorialModule.LAYERS), StudioTutorialAction.Pause)
        assertFalse(reduceStudioTutorial(paused, StudioTutorialAction.Observe(StudioTutorialEvent.LayerCreated)).currentComplete)
        val resumed = reduceStudioTutorial(paused, StudioTutorialAction.Resume)
        val complete = reduceStudioTutorial(resumed, StudioTutorialAction.Observe(StudioTutorialEvent.LayerCreated))
        assertTrue(complete.currentComplete)
        val replay = reduceStudioTutorial(complete, StudioTutorialAction.RestartModule(StudioTutorialModule.LAYERS))
        assertFalse(replay.currentComplete)
        assertEquals(StudioTutorialState(), reduceStudioTutorial(complete, StudioTutorialAction.Restart))
        assertTrue(reduceStudioTutorial(complete, StudioTutorialAction.Skip).skipped)
    }

    @Test fun sevenRequiredInteractiveLessonsCompleteOnlyFromDomainEvents() {
        val sequences = mapOf(
            StudioTutorialModule.NAVIGATION to listOf(StudioTutorialEvent.CanvasZoomChanged(1.2f, 24f)),
            StudioTutorialModule.BRUSH_PEN to listOf(StudioTutorialEvent.StrokeCommitted(.9f, .1f, false)),
            StudioTutorialModule.ERASER to listOf(StudioTutorialEvent.StrokeCommitted(.7f, .4f, true)),
            StudioTutorialModule.LAYERS to listOf(StudioTutorialEvent.LayerCreated),
            StudioTutorialModule.SELECTION to listOf(StudioTutorialEvent.SelectionCommitted),
            StudioTutorialModule.TRANSFORMATION to listOf(StudioTutorialEvent.TransformCommitted),
            StudioTutorialModule.UNDO_REDO to listOf(StudioTutorialEvent.UndoPerformed, StudioTutorialEvent.RedoPerformed),
            StudioTutorialModule.SAVE_EXPORT to listOf(StudioTutorialEvent.ExportCompleted),
        )
        sequences.forEach { (module, events) ->
            val final = events.fold(StudioTutorialState(current = module)) { state, event ->
                reduceStudioTutorial(state, StudioTutorialAction.Observe(event))
            }
            assertTrue(module.name, final.currentComplete)
        }
    }

    @Test fun progressPersistsLocallyAndCanBeResetWithoutDocumentData() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = context.getSharedPreferences("tutorial-state-test", android.content.Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val expected = StudioTutorialState(
            current = StudioTutorialModule.LAYERS,
            completed = setOf(StudioTutorialModule.NAVIGATION, StudioTutorialModule.BRUSH_PEN),
        )
        StudioTutorialProgressStore.save(preferences, expected)
        assertEquals(expected.current, StudioTutorialProgressStore.load(preferences).current)
        assertEquals(expected.completed, StudioTutorialProgressStore.load(preferences).completed)
        StudioTutorialProgressStore.clear(preferences)
        assertEquals(StudioTutorialState(), StudioTutorialProgressStore.load(preferences))
    }
}
