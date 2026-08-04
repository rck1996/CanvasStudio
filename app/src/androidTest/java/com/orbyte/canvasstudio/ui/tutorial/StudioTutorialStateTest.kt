package com.orbyte.canvasstudio.ui.tutorial

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudioTutorialStateTest {
    @Test fun allFourteenLessonsRequireDomainResultsAndVisibleConsequences() {
        StudioTutorialModule.entries.forEach { module ->
            val final = completionEvents(module).fold(StudioTutorialState(current = module)) { state, event ->
                reduceStudioTutorial(state, StudioTutorialAction.Observe(event), nowEpochMs = 1234L)
            }
            assertTrue("${module.name} did not complete", final.currentComplete)
            assertTrue("${module.name} had no visible revision", final.visualRevision > 0)
            assertNotNull("${module.name} lacked explanatory confirmation", final.confirmation)
            assertEquals(1234L, final.currentProgress.completedAtEpochMs)
        }
    }

    @Test fun openingPanelsOrPressingSetupButtonsNeverCompletesImportantLessons() {
        val setupOnly = mapOf(
            StudioTutorialModule.COLOR_PICKER to StudioTutorialEvent.ColorSampled(0xFF00FF, 0xFF00FF),
            StudioTutorialModule.LAYERS to StudioTutorialEvent.LayerCreated("layer"),
            StudioTutorialModule.MASKS to StudioTutorialEvent.MaskCreated("layer"),
            StudioTutorialModule.SYMMETRY_GUIDES to StudioTutorialEvent.SymmetryEnabled(true),
            StudioTutorialModule.SAVE_EXPORT to StudioTutorialEvent.ExportFormatSelected("PNG"),
            StudioTutorialModule.BRUSH_CUSTOMIZATION to StudioTutorialEvent.BrushParameterChanged("size", .2f, .5f),
        )
        setupOnly.forEach { (module, event) ->
            val state = reduceStudioTutorial(StudioTutorialState(current = module), StudioTutorialAction.Observe(event))
            assertFalse(module.name, state.currentComplete)
        }
    }

    @Test fun accidentalOrImperceptibleActionsDoNotComplete() {
        val invalid = mapOf(
            StudioTutorialModule.NAVIGATION to StudioTutorialEvent.CanvasViewChanged(1.02f, 4f, 1f),
            StudioTutorialModule.BRUSH_PEN to StudioTutorialEvent.StrokeCommitted(12f, .9f, .1f, false),
            StudioTutorialModule.ERASER to StudioTutorialEvent.StrokeCommitted(10f, .9f, .1f, true),
            StudioTutorialModule.SELECTION to StudioTutorialEvent.SelectionCreated(400f),
            StudioTutorialModule.TRANSFORMATION to StudioTutorialEvent.TransformPreviewChanged(3f, .01f, 1f),
            StudioTutorialModule.GRADIENT to StudioTutorialEvent.GradientCommitted(20f, .03f),
        )
        invalid.forEach { (module, event) ->
            assertFalse(module.name, reduceStudioTutorial(StudioTutorialState(current = module), StudioTutorialAction.Observe(event)).currentComplete)
        }
    }

    @Test fun layerMaskHistoryAndShapeSequencesAreStrictlyOrdered() {
        var mask = StudioTutorialState(current = StudioTutorialModule.MASKS)
        mask = reduceStudioTutorial(mask, StudioTutorialAction.Observe(StudioTutorialEvent.MaskContentChanged(800f)))
        assertTrue(mask.evidence.isEmpty())
        mask = reduceStudioTutorial(mask, StudioTutorialAction.Observe(StudioTutorialEvent.MaskCreated("layer")))
        mask = reduceStudioTutorial(mask, StudioTutorialAction.Observe(StudioTutorialEvent.MaskContentRestored(800f)))
        assertFalse(mask.currentComplete)

        var history = StudioTutorialState(current = StudioTutorialModule.UNDO_REDO)
        history = reduceStudioTutorial(history, StudioTutorialAction.Observe(StudioTutorialEvent.RedoPerformed("s", true)))
        history = reduceStudioTutorial(history, StudioTutorialAction.Observe(StudioTutorialEvent.UndoPerformed("s", true)))
        assertFalse(history.currentComplete)

        var shapes = StudioTutorialState(current = StudioTutorialModule.SHAPES_FILL)
        shapes = reduceStudioTutorial(shapes, StudioTutorialAction.Observe(StudioTutorialEvent.FillCommitted(5_000)))
        assertFalse(shapes.currentComplete)
    }

    @Test fun pauseDemoPracticeRepeatSkipAndRecoveryAreSafe() {
        val layer = StudioTutorialState(current = StudioTutorialModule.LAYERS)
        val paused = reduceStudioTutorial(layer, StudioTutorialAction.Pause)
        assertTrue(reduceStudioTutorial(paused, StudioTutorialAction.Observe(StudioTutorialEvent.LayerCreated("x"))).evidence.isEmpty())
        val demo = reduceStudioTutorial(layer, StudioTutorialAction.ShowDemo)
        assertTrue(reduceStudioTutorial(demo, StudioTutorialAction.Observe(StudioTutorialEvent.LayerCreated("x"))).evidence.isEmpty())
        val hint = reduceStudioTutorial(layer, StudioTutorialAction.ShowHint)
        assertEquals(1, hint.hintLevel)
        assertTrue(reduceStudioTutorial(layer, StudioTutorialAction.Practice).practiceMode)
        val skipped = reduceStudioTutorial(layer, StudioTutorialAction.Skip)
        assertEquals(TutorialProgressStatus.SKIPPED, skipped.currentProgress.status)
        assertFalse(skipped.currentComplete)
        val restarted = reduceStudioTutorial(skipped, StudioTutorialAction.RestartModule(StudioTutorialModule.LAYERS))
        assertEquals(TutorialProgressStatus.IN_PROGRESS, restarted.currentProgress.status)
        assertTrue(restarted.evidence.isEmpty())
        assertTrue(restarted.attemptId > skipped.attemptId)
    }

    @Test fun completionNeverAutoAdvancesAndSupportsExplicitContinue() {
        val module = StudioTutorialModule.SELECTION
        val complete = completionEvents(module).fold(StudioTutorialState(track = TutorialTrack.FULL_COURSE, current = module)) { state, event ->
            reduceStudioTutorial(state, StudioTutorialAction.Observe(event))
        }
        assertEquals(module, complete.current)
        assertNotNull(complete.confirmation)
        val next = reduceStudioTutorial(complete, StudioTutorialAction.Next)
        assertEquals(StudioTutorialModule.TRANSFORMATION, next.current)
    }

    @Test fun quickStartAndFullCourseHaveIndependentExpectedScope() {
        assertEquals(6, StudioTutorialState(track = TutorialTrack.QUICK_START).modules.size)
        assertEquals(14, StudioTutorialState(track = TutorialTrack.FULL_COURSE).modules.size)
        assertTrue(QUICK_START_MODULES.containsAll(listOf(StudioTutorialModule.NAVIGATION, StudioTutorialModule.BRUSH_PEN, StudioTutorialModule.ERASER, StudioTutorialModule.LAYERS, StudioTutorialModule.UNDO_REDO, StudioTutorialModule.SAVE_EXPORT)))
    }

    @Test fun progressPersistsStatusStepDateVersionAndCanResetWithoutDocumentData() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = context.getSharedPreferences("tutorial-state-v2-test", android.content.Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val expected = StudioTutorialState(
            track = TutorialTrack.FULL_COURSE,
            current = StudioTutorialModule.MASKS,
            progressByModule = mapOf(
                StudioTutorialModule.NAVIGATION to StudioModuleProgress(TutorialProgressStatus.COMPLETED, 4, 777L, 2),
                StudioTutorialModule.MASKS to StudioModuleProgress(TutorialProgressStatus.IN_PROGRESS, 1, null, 2),
                StudioTutorialModule.GRADIENT to StudioModuleProgress(TutorialProgressStatus.SKIPPED, 0, null, 2),
            ),
        )
        StudioTutorialProgressStore.save(preferences, expected)
        val loaded = StudioTutorialProgressStore.load(preferences)
        assertEquals(expected.track, loaded.track)
        assertEquals(expected.current, loaded.current)
        assertEquals(expected.progressByModule, loaded.progressByModule)
        StudioTutorialProgressStore.clear(preferences)
        assertEquals(StudioTutorialState(), StudioTutorialProgressStore.load(preferences))
        assertFalse(preferences.contains("unrelated_document_key"))
    }

    @Test fun storedProgressMigratesAndOnlyChangedLessonVersionInvalidates() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = context.getSharedPreferences("tutorial-migration-test", android.content.Context.MODE_PRIVATE)
        preferences.edit().clear()
            .putString("studio_tutorial_module_NAVIGATION", "COMPLETED|4|77|2")
            .putString("studio_tutorial_module_LAYERS", "COMPLETED|5|88|1")
            .commit()
        val loaded = StudioTutorialProgressStore.load(preferences)
        assertEquals(TutorialProgressStatus.COMPLETED, loaded.progressByModule[StudioTutorialModule.NAVIGATION]?.status)
        assertFalse(StudioTutorialModule.LAYERS in loaded.progressByModule)
    }

    @Test fun requirementsCannotBeSatisfiedByEventsFromOtherModules() {
        StudioTutorialModule.entries.forEach { module ->
            val wrong = StudioTutorialEvent.ExportPreviewGenerated("PNG", 100, 100)
            val result = reduceStudioTutorial(StudioTutorialState(current = module), StudioTutorialAction.Observe(wrong))
            if (module != StudioTutorialModule.SAVE_EXPORT) assertTrue(module.name, result.evidence.isEmpty())
        }
    }

    private fun completionEvents(module: StudioTutorialModule): List<StudioTutorialEvent> = when (module) {
        StudioTutorialModule.NAVIGATION -> listOf(StudioTutorialEvent.CanvasViewChanged(1.3f, 50f, 12f), StudioTutorialEvent.ViewReset)
        StudioTutorialModule.BRUSH_PEN -> listOf(StudioTutorialEvent.StrokeCommitted(150f, .9f, .1f, false, .4f))
        StudioTutorialModule.ERASER -> listOf(StudioTutorialEvent.StrokeCommitted(100f, .8f, .4f, true), StudioTutorialEvent.ErasureRestored(500))
        StudioTutorialModule.COLOR_PICKER -> listOf(StudioTutorialEvent.ColorSampled(0xFFFF0000, 0xFF000000), StudioTutorialEvent.StrokeWithActiveColor(100f))
        StudioTutorialModule.LAYERS -> listOf(
            StudioTutorialEvent.LayerCreated("layer"), StudioTutorialEvent.LayerStrokeCommitted("layer", 100f),
            StudioTutorialEvent.LayerVisibilityChanged("layer", false, true), StudioTutorialEvent.LayerVisibilityChanged("layer", true, true),
            StudioTutorialEvent.LayerReordered("layer", true),
        )
        StudioTutorialModule.MASKS -> listOf(StudioTutorialEvent.MaskCreated("layer"), StudioTutorialEvent.MaskContentChanged(600f), StudioTutorialEvent.MaskContentRestored(400f))
        StudioTutorialModule.SELECTION -> listOf(StudioTutorialEvent.SelectionCreated(10_000f))
        StudioTutorialModule.TRANSFORMATION -> listOf(StudioTutorialEvent.TransformPreviewChanged(50f, 0f, 0f), StudioTutorialEvent.TransformCommitted(true))
        StudioTutorialModule.SHAPES_FILL -> listOf(StudioTutorialEvent.ShapeCommitted(10_000f), StudioTutorialEvent.FillCommitted(5_000))
        StudioTutorialModule.GRADIENT -> listOf(StudioTutorialEvent.GradientCommitted(180f, .8f))
        StudioTutorialModule.SYMMETRY_GUIDES -> listOf(StudioTutorialEvent.SymmetryEnabled(true), StudioTutorialEvent.SymmetricStrokeCommitted(120f, 2))
        StudioTutorialModule.UNDO_REDO -> listOf(StudioTutorialEvent.HistoryStrokeCommitted("stroke", 120f), StudioTutorialEvent.UndoPerformed("stroke", true), StudioTutorialEvent.RedoPerformed("stroke", true))
        StudioTutorialModule.SAVE_EXPORT -> listOf(StudioTutorialEvent.ExportFormatSelected("PNG"), StudioTutorialEvent.ExportPreviewGenerated("PNG", 4096, 2732))
        StudioTutorialModule.BRUSH_CUSTOMIZATION -> listOf(StudioTutorialEvent.BrushParameterChanged("size", .2f, .6f), StudioTutorialEvent.BrushComparisonCommitted(.4f))
    }
}
