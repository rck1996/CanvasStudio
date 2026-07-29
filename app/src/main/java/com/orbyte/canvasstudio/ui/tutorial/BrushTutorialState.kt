package com.orbyte.canvasstudio.ui.tutorial

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable

enum class BrushTutorialStep(
    val eyebrow: String,
    val title: String,
    val shortTitle: String,
) {
    WELCOME("01 · EMPEZAR", "Conoce tu estudio", "Inicio"),
    LIBRARY("02 · PINCELES", "Elige la herramienta correcta", "Biblioteca"),
    PRESSURE_TILT("03 · S PEN", "Presión e inclinación naturales", "S Pen"),
    LIVE_PREVIEW("04 · PREVIEW", "Mira el pincel antes de dibujar", "Preview"),
    PARAMETERS("05 · AJUSTES", "Construye tu propio pincel", "Parámetros"),
    GESTURES("06 · GESTOS", "Navega sin interrumpir tu trazo", "Gestos"),
    PRACTICE("07 · PRÁCTICA", "Hazlo tuyo", "Práctica"),
}

data class BrushTutorialState(
    val currentStep: BrushTutorialStep = BrushTutorialStep.WELCOME,
    val completedSteps: Set<BrushTutorialStep> = emptySet(),
    val selectedBrush: String? = null,
    val pressure: Float = 0.5f,
    val tilt: Float = 0.25f,
    val size: Float = 0.42f,
    val opacity: Float = 0.9f,
    val grain: Float = 0.35f,
    val pressureSampled: Boolean = false,
    val tiltSampled: Boolean = false,
    val previewChanges: Int = 0,
    val parameterChanges: Int = 0,
    val gesturePracticed: Boolean = false,
    val practiceStrokeCount: Int = 0,
) {
    val progress: Float
        get() = (completedSteps.size + stepCompletionFraction()).coerceAtMost(
            BrushTutorialStep.entries.size.toFloat(),
        ) / BrushTutorialStep.entries.size.toFloat()

    val canContinue: Boolean
        get() = when (currentStep) {
            BrushTutorialStep.WELCOME -> true
            BrushTutorialStep.LIBRARY -> selectedBrush != null
            BrushTutorialStep.PRESSURE_TILT -> pressureSampled && tiltSampled
            BrushTutorialStep.LIVE_PREVIEW -> previewChanges >= 2
            BrushTutorialStep.PARAMETERS -> parameterChanges >= 3
            BrushTutorialStep.GESTURES -> gesturePracticed
            BrushTutorialStep.PRACTICE -> practiceStrokeCount >= 3
        }

    val instruction: String
        get() = when (currentStep) {
            BrushTutorialStep.WELCOME -> "Descubre un flujo pensado para tablet y S Pen."
            BrushTutorialStep.LIBRARY ->
                selectedBrush?.let { "$it seleccionado. Ya puedes continuar." }
                    ?: "Selecciona un pincel para comparar su carácter."
            BrushTutorialStep.PRESSURE_TILT ->
                when {
                    !pressureSampled -> "Prueba el control de presión."
                    !tiltSampled -> "Ahora inclina el S Pen."
                    else -> "La punta responde a tu mano."
                }
            BrushTutorialStep.LIVE_PREVIEW ->
                "Modifica tamaño u opacidad · ${previewChanges.coerceAtMost(2)}/2"
            BrushTutorialStep.PARAMETERS ->
                "Ajusta tamaño, opacidad y grano · ${parameterChanges.coerceAtMost(3)}/3"
            BrushTutorialStep.GESTURES ->
                if (gesturePracticed) "Gesto reconocido." else "Completa la simulación de dos dedos."
            BrushTutorialStep.PRACTICE ->
                "Traza tres líneas con distinta intención · ${practiceStrokeCount.coerceAtMost(3)}/3"
        }

    private fun stepCompletionFraction(): Float = when (currentStep) {
        BrushTutorialStep.WELCOME -> 0f
        BrushTutorialStep.LIBRARY -> if (selectedBrush == null) 0f else 1f
        BrushTutorialStep.PRESSURE_TILT ->
            (listOf(pressureSampled, tiltSampled).count { it } / 2f)
        BrushTutorialStep.LIVE_PREVIEW -> (previewChanges / 2f).coerceIn(0f, 1f)
        BrushTutorialStep.PARAMETERS -> (parameterChanges / 3f).coerceIn(0f, 1f)
        BrushTutorialStep.GESTURES -> if (gesturePracticed) 1f else 0f
        BrushTutorialStep.PRACTICE -> (practiceStrokeCount / 3f).coerceIn(0f, 1f)
    }
}

sealed interface BrushTutorialAction {
    data object Next : BrushTutorialAction
    data object Previous : BrushTutorialAction
    data class GoTo(val step: BrushTutorialStep) : BrushTutorialAction
    data class SelectBrush(val brushName: String) : BrushTutorialAction
    data class ChangePressure(val value: Float) : BrushTutorialAction
    data class ChangeTilt(val value: Float) : BrushTutorialAction
    data class ChangeSize(val value: Float) : BrushTutorialAction
    data class ChangeOpacity(val value: Float) : BrushTutorialAction
    data class ChangeGrain(val value: Float) : BrushTutorialAction
    data object CompleteGesture : BrushTutorialAction
    data object CompletePracticeStroke : BrushTutorialAction
    data object ResetPractice : BrushTutorialAction
}

fun reduceBrushTutorial(
    state: BrushTutorialState,
    action: BrushTutorialAction,
): BrushTutorialState = when (action) {
    BrushTutorialAction.Next -> {
        if (!state.canContinue) {
            state
        } else {
            val nextIndex = (state.currentStep.ordinal + 1)
                .coerceAtMost(BrushTutorialStep.entries.lastIndex)
            state.copy(
                currentStep = BrushTutorialStep.entries[nextIndex],
                completedSteps = state.completedSteps + state.currentStep,
            )
        }
    }
    BrushTutorialAction.Previous -> state.copy(
        currentStep = BrushTutorialStep.entries[
            (state.currentStep.ordinal - 1).coerceAtLeast(0)
        ],
    )
    is BrushTutorialAction.GoTo -> {
        val isAvailable = action.step.ordinal <= state.currentStep.ordinal ||
            action.step in state.completedSteps
        if (isAvailable) state.copy(currentStep = action.step) else state
    }
    is BrushTutorialAction.SelectBrush -> state.copy(selectedBrush = action.brushName.take(40))
    is BrushTutorialAction.ChangePressure -> state.copy(
        pressure = action.value.coerceIn(0f, 1f),
        pressureSampled = true,
    )
    is BrushTutorialAction.ChangeTilt -> state.copy(
        tilt = action.value.coerceIn(0f, 1f),
        tiltSampled = true,
    )
    is BrushTutorialAction.ChangeSize -> state.copy(
        size = action.value.coerceIn(0.05f, 1f),
        previewChanges = state.previewChanges + 1,
        parameterChanges = state.parameterChanges + 1,
    )
    is BrushTutorialAction.ChangeOpacity -> state.copy(
        opacity = action.value.coerceIn(0.05f, 1f),
        previewChanges = state.previewChanges + 1,
        parameterChanges = state.parameterChanges + 1,
    )
    is BrushTutorialAction.ChangeGrain -> state.copy(
        grain = action.value.coerceIn(0f, 1f),
        parameterChanges = state.parameterChanges + 1,
    )
    BrushTutorialAction.CompleteGesture -> state.copy(gesturePracticed = true)
    BrushTutorialAction.CompletePracticeStroke -> state.copy(
        practiceStrokeCount = (state.practiceStrokeCount + 1).coerceAtMost(3),
    )
    BrushTutorialAction.ResetPractice -> state.copy(practiceStrokeCount = 0)
}

@Stable
class BrushTutorialController internal constructor(initialState: BrushTutorialState) {
    var state by mutableStateOf(initialState)
        private set

    fun dispatch(action: BrushTutorialAction) {
        state = reduceBrushTutorial(state, action)
    }

    companion object {
        val Saver: Saver<BrushTutorialController, String> = Saver(
            save = { encodeTutorialState(it.state) },
            restore = { BrushTutorialController(decodeTutorialState(it)) },
        )
    }
}

@Composable
fun rememberBrushTutorialController(
    initialState: BrushTutorialState = BrushTutorialState(),
): BrushTutorialController = rememberSaveable(saver = BrushTutorialController.Saver) {
    BrushTutorialController(initialState)
}

private fun encodeTutorialState(state: BrushTutorialState): String = listOf(
    state.currentStep.name,
    state.completedSteps.joinToString(",") { it.name },
    state.selectedBrush.orEmpty(),
    state.pressure,
    state.tilt,
    state.size,
    state.opacity,
    state.grain,
    state.pressureSampled,
    state.tiltSampled,
    state.previewChanges,
    state.parameterChanges,
    state.gesturePracticed,
    state.practiceStrokeCount,
).joinToString("|")

private fun decodeTutorialState(encoded: String): BrushTutorialState {
    val values = encoded.split("|")
    if (values.size != 14) return BrushTutorialState()
    return runCatching {
        BrushTutorialState(
            currentStep = BrushTutorialStep.valueOf(values[0]),
            completedSteps = values[1].split(",")
                .filter(String::isNotBlank)
                .mapTo(mutableSetOf(), BrushTutorialStep::valueOf),
            selectedBrush = values[2].ifBlank { null },
            pressure = values[3].toFloat().coerceIn(0f, 1f),
            tilt = values[4].toFloat().coerceIn(0f, 1f),
            size = values[5].toFloat().coerceIn(0.05f, 1f),
            opacity = values[6].toFloat().coerceIn(0.05f, 1f),
            grain = values[7].toFloat().coerceIn(0f, 1f),
            pressureSampled = values[8].toBooleanStrict(),
            tiltSampled = values[9].toBooleanStrict(),
            previewChanges = values[10].toInt().coerceAtLeast(0),
            parameterChanges = values[11].toInt().coerceAtLeast(0),
            gesturePracticed = values[12].toBooleanStrict(),
            practiceStrokeCount = values[13].toInt().coerceIn(0, 3),
        )
    }.getOrDefault(BrushTutorialState())
}
