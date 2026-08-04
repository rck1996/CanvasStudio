package com.orbyte.canvasstudio.ui.tutorial

import android.content.SharedPreferences

enum class StudioTutorialModule(val title: String, val objective: String) {
    NAVIGATION("Navegación del lienzo", "Haz zoom y mueve el lienzo con dos dedos."),
    BRUSH_PEN("Pincel y S Pen", "Traza variando la presión del S Pen."),
    ERASER("Borrador", "Elimina parte del trazo de práctica."),
    COLOR_PICKER("Color y cuentagotas", "Muestrea un color del lienzo."),
    LAYERS("Capas", "Crea una capa nueva sin tocar tu proyecto real."),
    MASKS("Máscaras", "Añade una máscara raster a la capa de práctica."),
    SELECTION("Selección", "Delimita una región dentro del documento temporal."),
    TRANSFORMATION("Transformación", "Desplaza la selección temporal y confirma el cambio."),
    SHAPES_FILL("Formas, línea y relleno", "Crea una forma y aplica relleno."),
    GRADIENT("Degradado", "Arrastra para definir un degradado."),
    SYMMETRY_GUIDES("Simetría y guías", "Activa una guía de simetría."),
    UNDO_REDO("Undo y redo", "Deshaz y recupera una acción real."),
    SAVE_EXPORT("Guardado y exportación", "Completa una exportación segura de práctica."),
    BRUSH_CUSTOMIZATION("Personalización de pinceles", "Cambia tamaño, flujo y grano."),
}

sealed interface StudioTutorialEvent {
    data class CanvasZoomChanged(val scale: Float, val panDistance: Float) : StudioTutorialEvent
    data class StrokeCommitted(val maximumPressure: Float, val minimumPressure: Float, val eraser: Boolean) : StudioTutorialEvent
    data object ColorPicked : StudioTutorialEvent
    data object LayerCreated : StudioTutorialEvent
    data object MaskCreated : StudioTutorialEvent
    data object SelectionCommitted : StudioTutorialEvent
    data object TransformCommitted : StudioTutorialEvent
    data object ShapeCommitted : StudioTutorialEvent
    data object FillCommitted : StudioTutorialEvent
    data object GradientCommitted : StudioTutorialEvent
    data object SymmetryEnabled : StudioTutorialEvent
    data object UndoPerformed : StudioTutorialEvent
    data object RedoPerformed : StudioTutorialEvent
    data object ExportCompleted : StudioTutorialEvent
    data class BrushCustomized(val changedParameters: Int) : StudioTutorialEvent
}

data class StudioTutorialState(
    val current: StudioTutorialModule = StudioTutorialModule.NAVIGATION,
    val completed: Set<StudioTutorialModule> = emptySet(),
    val paused: Boolean = false,
    val skipped: Boolean = false,
    val navigationZoomed: Boolean = false,
    val navigationPanned: Boolean = false,
    val pressureStroke: Boolean = false,
    val selectionMade: Boolean = false,
    val shapeMade: Boolean = false,
    val undoMade: Boolean = false,
) {
    val currentComplete: Boolean get() = current in completed
    val progress: Float get() = completed.size / StudioTutorialModule.entries.size.toFloat()
}

sealed interface StudioTutorialAction {
    data class Observe(val event: StudioTutorialEvent) : StudioTutorialAction
    data object Next : StudioTutorialAction
    data object Previous : StudioTutorialAction
    data class Open(val module: StudioTutorialModule) : StudioTutorialAction
    data object Pause : StudioTutorialAction
    data object Resume : StudioTutorialAction
    data object Skip : StudioTutorialAction
    data object Restart : StudioTutorialAction
    data class RestartModule(val module: StudioTutorialModule) : StudioTutorialAction
}

fun reduceStudioTutorial(state: StudioTutorialState, action: StudioTutorialAction): StudioTutorialState {
    return when (action) {
        is StudioTutorialAction.Observe -> observeTutorialEvent(state, action.event)
        StudioTutorialAction.Next -> if (!state.currentComplete) state else state.copy(
            current = StudioTutorialModule.entries[
                (state.current.ordinal + 1).coerceAtMost(StudioTutorialModule.entries.lastIndex)
            ],
        )
        StudioTutorialAction.Previous -> state.copy(
            current = StudioTutorialModule.entries[(state.current.ordinal - 1).coerceAtLeast(0)],
        )
        is StudioTutorialAction.Open -> state.copy(current = action.module)
        StudioTutorialAction.Pause -> state.copy(paused = true)
        StudioTutorialAction.Resume -> state.copy(paused = false)
        StudioTutorialAction.Skip -> state.copy(skipped = true)
        StudioTutorialAction.Restart -> StudioTutorialState()
        is StudioTutorialAction.RestartModule -> state.copy(
            current = action.module,
            completed = state.completed - action.module,
            navigationZoomed = if (action.module == StudioTutorialModule.NAVIGATION) false else state.navigationZoomed,
            navigationPanned = if (action.module == StudioTutorialModule.NAVIGATION) false else state.navigationPanned,
            pressureStroke = if (action.module == StudioTutorialModule.BRUSH_PEN) false else state.pressureStroke,
            selectionMade = if (action.module == StudioTutorialModule.SELECTION) false else state.selectionMade,
            shapeMade = if (action.module == StudioTutorialModule.SHAPES_FILL) false else state.shapeMade,
            undoMade = if (action.module == StudioTutorialModule.UNDO_REDO) false else state.undoMade,
        )
    }
}

private fun observeTutorialEvent(state: StudioTutorialState, event: StudioTutorialEvent): StudioTutorialState {
    if (state.paused || state.skipped) return state
    var next = state
    val valid = when (state.current) {
        StudioTutorialModule.NAVIGATION -> when (event) {
            is StudioTutorialEvent.CanvasZoomChanged -> {
                next = next.copy(
                    navigationZoomed = next.navigationZoomed || kotlin.math.abs(event.scale - 1f) >= .08f,
                    navigationPanned = next.navigationPanned || event.panDistance >= 12f,
                )
                next.navigationZoomed && next.navigationPanned
            }
            else -> false
        }
        StudioTutorialModule.BRUSH_PEN -> when (event) {
            is StudioTutorialEvent.StrokeCommitted -> {
                val pressureRange = event.maximumPressure - event.minimumPressure
                next = next.copy(pressureStroke = !event.eraser && event.maximumPressure >= .45f && pressureRange >= .18f)
                next.pressureStroke
            }
            else -> false
        }
        StudioTutorialModule.ERASER -> (event as? StudioTutorialEvent.StrokeCommitted)?.eraser == true
        StudioTutorialModule.COLOR_PICKER -> event is StudioTutorialEvent.ColorPicked
        StudioTutorialModule.LAYERS -> event is StudioTutorialEvent.LayerCreated
        StudioTutorialModule.MASKS -> event is StudioTutorialEvent.MaskCreated
        StudioTutorialModule.SELECTION -> event is StudioTutorialEvent.SelectionCommitted
        StudioTutorialModule.TRANSFORMATION -> event is StudioTutorialEvent.TransformCommitted
        StudioTutorialModule.SHAPES_FILL -> when (event) {
            StudioTutorialEvent.ShapeCommitted -> { next = next.copy(shapeMade = true); false }
            StudioTutorialEvent.FillCommitted -> next.shapeMade
            else -> false
        }
        StudioTutorialModule.GRADIENT -> event is StudioTutorialEvent.GradientCommitted
        StudioTutorialModule.SYMMETRY_GUIDES -> event is StudioTutorialEvent.SymmetryEnabled
        StudioTutorialModule.UNDO_REDO -> when (event) {
            StudioTutorialEvent.UndoPerformed -> { next = next.copy(undoMade = true); false }
            StudioTutorialEvent.RedoPerformed -> next.undoMade
            else -> false
        }
        StudioTutorialModule.SAVE_EXPORT -> event is StudioTutorialEvent.ExportCompleted
        StudioTutorialModule.BRUSH_CUSTOMIZATION ->
            (event as? StudioTutorialEvent.BrushCustomized)?.changedParameters?.let { it >= 3 } == true
    }
    return if (valid) next.copy(completed = next.completed + next.current) else next
}

internal object StudioTutorialProgressStore {
    private const val CURRENT = "studio_tutorial_current"
    private const val COMPLETED = "studio_tutorial_completed_modules"

    fun load(preferences: SharedPreferences): StudioTutorialState = runCatching {
        StudioTutorialState(
            current = preferences.getString(CURRENT, null)?.let(StudioTutorialModule::valueOf)
                ?: StudioTutorialModule.NAVIGATION,
            completed = preferences.getStringSet(COMPLETED, emptySet()).orEmpty()
                .mapTo(mutableSetOf(), StudioTutorialModule::valueOf),
        )
    }.getOrDefault(StudioTutorialState())

    fun save(preferences: SharedPreferences, state: StudioTutorialState) {
        preferences.edit()
            .putString(CURRENT, state.current.name)
            .putStringSet(COMPLETED, state.completed.mapTo(mutableSetOf()) { it.name })
            .apply()
    }

    fun clear(preferences: SharedPreferences) {
        preferences.edit().remove(CURRENT).remove(COMPLETED).apply()
    }
}
