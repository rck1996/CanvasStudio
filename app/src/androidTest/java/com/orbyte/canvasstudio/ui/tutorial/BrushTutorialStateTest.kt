package com.orbyte.canvasstudio.ui.tutorial

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrushTutorialStateTest {

    @Test
    fun next_isBlockedUntilCurrentExerciseIsComplete() {
        val library = BrushTutorialState(currentStep = BrushTutorialStep.LIBRARY)

        assertEquals(
            BrushTutorialStep.LIBRARY,
            reduceBrushTutorial(library, BrushTutorialAction.Next).currentStep,
        )

        val selected = reduceBrushTutorial(
            library,
            BrushTutorialAction.SelectBrush("HB Natural"),
        )
        val advanced = reduceBrushTutorial(selected, BrushTutorialAction.Next)

        assertEquals(BrushTutorialStep.PRESSURE_TILT, advanced.currentStep)
        assertTrue(BrushTutorialStep.LIBRARY in advanced.completedSteps)
    }

    @Test
    fun pressureAndTilt_requireBothSpenInteractions() {
        val initial = BrushTutorialState(currentStep = BrushTutorialStep.PRESSURE_TILT)
        val pressured = reduceBrushTutorial(
            initial,
            BrushTutorialAction.ChangePressure(0.8f),
        )

        assertFalse(pressured.canContinue)

        val tilted = reduceBrushTutorial(
            pressured,
            BrushTutorialAction.ChangeTilt(0.7f),
        )

        assertTrue(tilted.canContinue)
        assertEquals(0.8f, tilted.pressure)
        assertEquals(0.7f, tilted.tilt)
    }

    @Test
    fun inputValues_areClampedToSafeRanges() {
        val initial = BrushTutorialState()
        val oversized = reduceBrushTutorial(
            initial,
            BrushTutorialAction.ChangeSize(9f),
        )
        val transparent = reduceBrushTutorial(
            oversized,
            BrushTutorialAction.ChangeOpacity(-2f),
        )
        val excessiveGrain = reduceBrushTutorial(
            transparent,
            BrushTutorialAction.ChangeGrain(4f),
        )

        assertEquals(1f, excessiveGrain.size)
        assertEquals(0.05f, excessiveGrain.opacity)
        assertEquals(1f, excessiveGrain.grain)
    }

    @Test
    fun practice_requiresThreeValidStrokeCompletions() {
        var state = BrushTutorialState(currentStep = BrushTutorialStep.PRACTICE)

        repeat(2) {
            state = reduceBrushTutorial(state, BrushTutorialAction.CompletePracticeStroke)
        }
        assertFalse(state.canContinue)

        state = reduceBrushTutorial(state, BrushTutorialAction.CompletePracticeStroke)
        assertTrue(state.canContinue)
        assertEquals(3, state.practiceStrokeCount)

        state = reduceBrushTutorial(state, BrushTutorialAction.CompletePracticeStroke)
        assertEquals(3, state.practiceStrokeCount)
    }

    @Test
    fun lockedFutureStep_cannotBeOpenedFromRail() {
        val initial = BrushTutorialState(currentStep = BrushTutorialStep.LIBRARY)
        val result = reduceBrushTutorial(
            initial,
            BrushTutorialAction.GoTo(BrushTutorialStep.PRACTICE),
        )

        assertEquals(BrushTutorialStep.LIBRARY, result.currentStep)
    }
}
