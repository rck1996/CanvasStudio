package com.orbyte.canvasstudio.ui.tutorial

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import com.orbyte.canvasstudio.drawing.DrawingInteractionEvent
import com.orbyte.canvasstudio.drawing.DrawingTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorTutorialIntegrationTest {
    private lateinit var session: EditorTutorialSession

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = context.getSharedPreferences("editor_tutorial_test", android.content.Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        session = EditorTutorialSession(StudioTutorialState(), preferences)
    }

    @Test
    fun guideTargetsRealEditorControlsInActionOrder() {
        var state = StudioTutorialState(current = StudioTutorialModule.LAYERS)
        assertEquals("quick_layer_add", guideFor(state, DrawingTool.BRUSH, false, false).target)

        state = reduceStudioTutorial(state, StudioTutorialAction.Observe(StudioTutorialEvent.LayerCreated("layer-2")))
        assertEquals("canvas", guideFor(state, DrawingTool.BRUSH, true, false).target)
    }

    @Test
    fun transformLessonCreatesARealSelectionPrerequisiteWhenNeeded() {
        val state = StudioTutorialState(current = StudioTutorialModule.TRANSFORMATION)
        assertEquals("tool_select_rectangle", guideFor(state, DrawingTool.BRUSH, true, false, false).target)
        assertEquals("canvas", guideFor(state, DrawingTool.SELECT_RECTANGLE, true, false, false).target)
        assertEquals("tool_transform", guideFor(state, DrawingTool.SELECT_RECTANGLE, true, false, true).target)
    }

    @Test
    fun rendererBackedStrokeCompletesHistoryPrerequisite() {
        session.dispatch(StudioTutorialAction.Open(StudioTutorialModule.UNDO_REDO))
        session.observeDrawing(
            DrawingInteractionEvent.StrokeCommitted(
                layerId = "layer-1",
                lengthPx = 180f,
                minimumPressure = .3f,
                maximumPressure = .8f,
                maximumTiltRadians = .2f,
                eraser = false,
                editingMask = false,
                symmetryCopies = 1,
            ),
        )
        assertTrue(TutorialEvidence.HISTORY_STROKE in session.state.evidence)
        session.observeDrawing(DrawingInteractionEvent.HistoryChanged(DrawingInteractionEvent.HistoryChanged.Action.UNDO))
        session.observeDrawing(DrawingInteractionEvent.HistoryChanged(DrawingInteractionEvent.HistoryChanged.Action.REDO))
        assertTrue(session.state.currentComplete)
    }

    @Test
    fun maskEvidenceRequiresActualMaskRasterStroke() {
        session.dispatch(StudioTutorialAction.Open(StudioTutorialModule.MASKS))
        session.observe(StudioTutorialEvent.MaskCreated("layer-1"))
        session.observeDrawing(
            DrawingInteractionEvent.StrokeCommitted(
                "layer-1", 100f, .4f, .8f, 0f,
                eraser = false, editingMask = false, symmetryCopies = 1,
            ),
        )
        assertFalse(TutorialEvidence.MASK_CHANGED in session.state.evidence)
        session.observeDrawing(
            DrawingInteractionEvent.StrokeCommitted(
                "layer-1", 100f, .4f, .8f, 0f,
                eraser = false, editingMask = true, symmetryCopies = 1,
            ),
        )
        assertTrue(TutorialEvidence.MASK_CHANGED in session.state.evidence)
    }

    @Test
    fun exportTutorialUsesPreviewEvidenceWithoutFileWrite() {
        session.dispatch(StudioTutorialAction.Open(StudioTutorialModule.SAVE_EXPORT))
        session.observe(StudioTutorialEvent.ExportFormatSelected("PNG"))
        session.observe(StudioTutorialEvent.ExportPreviewGenerated("PNG", 2048, 1536))
        assertTrue(session.state.currentComplete)
    }

    @Test
    fun openingEditorTutorialAlwaysStartsAtLessonOneWithoutOldEvidence() {
        val saved = StudioTutorialState(
            track = TutorialTrack.FULL_COURSE,
            current = StudioTutorialModule.SYMMETRY_GUIDES,
            progressByModule = mapOf(
                StudioTutorialModule.NAVIGATION to StudioModuleProgress(TutorialProgressStatus.COMPLETED),
            ),
            evidence = setOf(TutorialEvidence.SYMMETRY_GUIDE_VISIBLE),
        )
        val fresh = freshEditorTutorialState(saved)
        assertEquals(StudioTutorialModule.NAVIGATION, fresh.current)
        assertEquals(TutorialTrack.FULL_COURSE, fresh.track)
        assertTrue(fresh.evidence.isEmpty())
        assertTrue(fresh.progressByModule.isEmpty())
    }

    @Test
    fun realEditorTutorialAlwaysExposesTheCompleteFourteenLessonCourse() {
        val fresh = freshEditorTutorialState(StudioTutorialState(track = TutorialTrack.QUICK_START))
        assertEquals(TutorialTrack.FULL_COURSE, fresh.track)
        assertEquals(14, fresh.modules.size)
        assertEquals(StudioTutorialModule.NAVIGATION, fresh.current)
    }

    @Test
    fun focusBoundsSubtractEditorSafeInsetOrigin() {
        val targetInRoot = Rect(2440f, 78f, 2542f, 180f)
        val overlayInRoot = Rect(0f, 64f, 2560f, 1498f)
        assertEquals(Rect(2440f, 14f, 2542f, 116f), relativeFocusBounds(targetInRoot, overlayInRoot))
    }

    @Test
    fun tutorialCardMovesAwayFromRightDockAndLowTargets() {
        assertEquals(
            TutorialCardPlacement.BOTTOM_START,
            tutorialCardPlacement(Rect(2200f, 100f, 2500f, 220f), 2560f, 1500f),
        )
        assertEquals(
            TutorialCardPlacement.TOP_START,
            tutorialCardPlacement(Rect(2200f, 900f, 2500f, 1100f), 2560f, 1500f),
        )
        assertEquals(
            TutorialCardPlacement.TOP_CENTER,
            tutorialCardPlacement(Rect(700f, 1100f, 1500f, 1400f), 2560f, 1500f),
        )
    }

    @Test
    fun maskGuideUsesOutcomeLanguageAndExplainsReversibility() {
        val state = StudioTutorialState(current = StudioTutorialModule.MASKS)
        val guide = guideFor(state, DrawingTool.BRUSH, layersPanelActive = true, brushesPanelActive = false)
        assertEquals("quick_layer_mask", guide.target)
        assertTrue(guide.instruction.contains("Ocultar sin borrar"))
        assertTrue(guide.instruction.contains("original"))
        assertTrue(guide.expectedOutcome.contains("intacta"))
        assertFalse(guide.instruction.contains("raster", ignoreCase = true))
    }

    @Test
    fun maskGuideRecoversWhenTheUserLeavesMaskEditingMidLesson() {
        val state = reduceStudioTutorial(
            StudioTutorialState(current = StudioTutorialModule.MASKS),
            StudioTutorialAction.Observe(StudioTutorialEvent.MaskCreated("layer")),
        )
        val reopen = guideFor(
            state,
            DrawingTool.BRUSH,
            layersPanelActive = true,
            brushesPanelActive = false,
            quickMenuOpen = false,
            maskEditingActive = false,
        )
        assertEquals("quick_menu_gesture", reopen.target)
        assertTrue(reopen.instruction.contains("pausa"))
        val action = guideFor(
            state,
            DrawingTool.BRUSH,
            layersPanelActive = true,
            brushesPanelActive = false,
            quickMenuOpen = true,
            maskEditingActive = false,
        )
        assertEquals("quick_layer_mask", action.target)
    }

    @Test
    fun tutorialCardDoesNotCoverAReachableTargetAndStaysInsideViewport() {
        val viewport = IntSize(2560, 1500)
        val card = IntSize(620, 360)
        val rightDockTarget = Rect(2250f, 480f, 2520f, 560f)
        val offset = tutorialCardOffset(rightDockTarget, viewport, card)
        val cardRect = Rect(
            offset.x.toFloat(),
            offset.y.toFloat(),
            offset.x + card.width.toFloat(),
            offset.y + card.height.toFloat(),
        )
        assertTrue(cardRect.right <= viewport.width)
        assertTrue(cardRect.bottom <= viewport.height)
        assertTrue(cardRect.right <= rightDockTarget.left || cardRect.left >= rightDockTarget.right ||
            cardRect.bottom <= rightDockTarget.top || cardRect.top >= rightDockTarget.bottom)
    }

    @Test
    fun layerLessonUsesQuickAccessBeforeOpeningTheFullPanel() {
        var state = StudioTutorialState(current = StudioTutorialModule.LAYERS)
        assertEquals("quick_layer_add", guideFor(state, DrawingTool.BRUSH, false, false).target)
        state = reduceStudioTutorial(state, StudioTutorialAction.Observe(StudioTutorialEvent.LayerCreated("layer-2")))
        state = reduceStudioTutorial(state, StudioTutorialAction.Observe(StudioTutorialEvent.LayerStrokeCommitted("layer-2", 180f)))
        assertEquals("quick_layer_visibility", guideFor(state, DrawingTool.BRUSH, false, false).target)
        state = reduceStudioTutorial(state, StudioTutorialAction.Observe(StudioTutorialEvent.LayerVisibilityChanged("layer-2", false, true)))
        state = reduceStudioTutorial(state, StudioTutorialAction.Observe(StudioTutorialEvent.LayerVisibilityChanged("layer-2", true, true)))
        assertEquals("dock_layers", guideFor(state, DrawingTool.BRUSH, false, false).target)
        assertEquals("layer_clipping", guideFor(state, DrawingTool.BRUSH, true, false).target)
        state = reduceStudioTutorial(state, StudioTutorialAction.Observe(StudioTutorialEvent.LayerClippingChanged("layer-2", true)))
        assertEquals("layer_down", guideFor(state, DrawingTool.BRUSH, true, false).target)
    }

    @Test
    fun layerAndMaskLessonsTeachTheRadialMenuGestureBeforeItsActions() {
        val layers = StudioTutorialState(current = StudioTutorialModule.LAYERS)
        assertEquals(
            "quick_menu_gesture",
            guideFor(layers, DrawingTool.BRUSH, false, false, quickMenuOpen = false).target,
        )
        val masks = StudioTutorialState(current = StudioTutorialModule.MASKS)
        val guide = guideFor(masks, DrawingTool.BRUSH, true, false, quickMenuOpen = false)
        assertEquals("quick_menu_gesture", guide.target)
        assertTrue(guide.instruction.contains("estrella"))
    }

    @Test
    fun completingMasksExitsMaskEditingBeforeSelectionStarts() {
        var state = StudioTutorialState(current = StudioTutorialModule.MASKS)
        assertFalse(tutorialShouldExitMaskEditing(state))
        state = reduceStudioTutorial(state, StudioTutorialAction.Observe(StudioTutorialEvent.MaskCreated("layer-2")))
        state = reduceStudioTutorial(state, StudioTutorialAction.Observe(StudioTutorialEvent.MaskContentChanged(600f)))
        state = reduceStudioTutorial(state, StudioTutorialAction.Observe(StudioTutorialEvent.MaskContentRestored(400f)))
        assertTrue(state.currentComplete)
        assertTrue(tutorialShouldExitMaskEditing(state))
        state = reduceStudioTutorial(state.copy(track = TutorialTrack.FULL_COURSE), StudioTutorialAction.Next)
        assertEquals(StudioTutorialModule.SELECTION, state.current)
        assertTrue(tutorialShouldExitMaskEditing(state))
    }

    @Test
    fun selectionEntryPolicyClosesMaskAndClearsAnyStaleSelection() {
        val policy = tutorialRuntimePolicy(
            StudioTutorialState(track = TutorialTrack.FULL_COURSE, current = StudioTutorialModule.SELECTION),
        )
        assertTrue(policy.exitMaskEditing)
        assertTrue(policy.clearSelection)
        assertTrue(policy.resetSymmetry)
        assertTrue(policy.closeTransientUi)
    }

    @Test
    fun transformationKeepsSelectionButLaterLessonsClearItAndResetSymmetry() {
        val transform = tutorialRuntimePolicy(
            StudioTutorialState(track = TutorialTrack.FULL_COURSE, current = StudioTutorialModule.TRANSFORMATION),
        )
        assertFalse(transform.clearSelection)
        val shapes = tutorialRuntimePolicy(
            StudioTutorialState(track = TutorialTrack.FULL_COURSE, current = StudioTutorialModule.SHAPES_FILL),
        )
        assertTrue(shapes.clearSelection)
        assertTrue(shapes.resetSymmetry)
    }

    @Test
    fun allFourteenLessonsCompleteThroughTheEditorEventAdapters() {
        StudioTutorialModule.entries.forEach { module ->
            session.dispatch(StudioTutorialAction.Open(module))
            when (module) {
                StudioTutorialModule.NAVIGATION -> {
                    session.observeDrawing(DrawingInteractionEvent.ViewChanged(1.3f, 60f, 12f))
                    session.observeDrawing(DrawingInteractionEvent.ViewReset)
                }
                StudioTutorialModule.BRUSH_PEN -> session.observeDrawing(stroke(minPressure = .1f, maxPressure = .9f))
                StudioTutorialModule.ERASER -> {
                    session.observeDrawing(stroke(eraser = true))
                    session.observeDrawing(DrawingInteractionEvent.HistoryChanged(DrawingInteractionEvent.HistoryChanged.Action.UNDO))
                }
                StudioTutorialModule.COLOR_PICKER -> {
                    session.observe(StudioTutorialEvent.ColorSampled(0xFFFF776FL, 0xFF58D7D1L))
                    session.observeDrawing(stroke())
                }
                StudioTutorialModule.LAYERS -> listOf(
                    StudioTutorialEvent.LayerCreated("layer-2"),
                    StudioTutorialEvent.LayerStrokeCommitted("layer-2", 140f),
                    StudioTutorialEvent.LayerVisibilityChanged("layer-2", false, true),
                    StudioTutorialEvent.LayerVisibilityChanged("layer-2", true, true),
                    StudioTutorialEvent.LayerClippingChanged("layer-2", true),
                    StudioTutorialEvent.LayerReordered("layer-2", true),
                ).forEach(session::observe)
                StudioTutorialModule.MASKS -> {
                    session.observe(StudioTutorialEvent.MaskCreated("layer-2"))
                    session.observeDrawing(stroke(editingMask = true))
                    session.observeDrawing(stroke(eraser = true, editingMask = true))
                }
                StudioTutorialModule.SELECTION -> session.observeDrawing(DrawingInteractionEvent.SelectionCreated(12_000f))
                StudioTutorialModule.TRANSFORMATION -> {
                    session.observeDrawing(DrawingInteractionEvent.TransformPreviewChanged(60f, 0f, 0f))
                    session.observeDrawing(DrawingInteractionEvent.TransformCommitted)
                }
                StudioTutorialModule.SHAPES_FILL -> {
                    session.observeDrawing(DrawingInteractionEvent.ShapeCommitted(DrawingTool.RECTANGLE, 160f, 120f))
                    session.observeDrawing(DrawingInteractionEvent.FillCommitted(5_000))
                }
                StudioTutorialModule.GRADIENT -> session.observeDrawing(DrawingInteractionEvent.GradientCommitted(180f))
                StudioTutorialModule.SYMMETRY_GUIDES -> {
                    session.observe(StudioTutorialEvent.SymmetryEnabled(true))
                    session.observeDrawing(stroke(symmetryCopies = 2))
                }
                StudioTutorialModule.UNDO_REDO -> {
                    session.observeDrawing(stroke())
                    session.observeDrawing(DrawingInteractionEvent.HistoryChanged(DrawingInteractionEvent.HistoryChanged.Action.UNDO))
                    session.observeDrawing(DrawingInteractionEvent.HistoryChanged(DrawingInteractionEvent.HistoryChanged.Action.REDO))
                }
                StudioTutorialModule.SAVE_EXPORT -> {
                    session.observe(StudioTutorialEvent.ExportFormatSelected("PNG"))
                    session.observe(StudioTutorialEvent.ExportPreviewGenerated("PNG", 2048, 1536))
                }
                StudioTutorialModule.BRUSH_CUSTOMIZATION -> {
                    session.observe(StudioTutorialEvent.BrushParameterChanged("size", .2f, .6f))
                    session.observeDrawing(stroke())
                }
            }
            assertTrue("${module.name} did not complete through EditorTutorialSession", session.state.currentComplete)
        }
    }

    private fun stroke(
        eraser: Boolean = false,
        editingMask: Boolean = false,
        symmetryCopies: Int = 1,
        minPressure: Float = .25f,
        maxPressure: Float = .8f,
    ) = DrawingInteractionEvent.StrokeCommitted(
        layerId = "layer-2",
        lengthPx = 180f,
        minimumPressure = minPressure,
        maximumPressure = maxPressure,
        maximumTiltRadians = .35f,
        eraser = eraser,
        editingMask = editingMask,
        symmetryCopies = symmetryCopies,
    )
}
