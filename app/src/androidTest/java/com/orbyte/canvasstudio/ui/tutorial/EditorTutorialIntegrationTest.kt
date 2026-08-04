package com.orbyte.canvasstudio.ui.tutorial

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.geometry.Rect
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
        assertEquals("dock_layers", guideFor(state, DrawingTool.BRUSH, false, false).target)
        assertEquals("layer_add", guideFor(state, DrawingTool.BRUSH, true, false).target)

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
        assertEquals("mask_add", guide.target)
        assertTrue(guide.instruction.contains("Ocultar sin borrar"))
        assertTrue(guide.instruction.contains("original"))
        assertFalse(guide.instruction.contains("raster", ignoreCase = true))
    }
}
