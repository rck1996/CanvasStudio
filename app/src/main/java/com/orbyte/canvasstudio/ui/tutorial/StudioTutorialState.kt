package com.orbyte.canvasstudio.ui.tutorial

import android.content.SharedPreferences
import kotlin.math.abs

const val STUDIO_TUTORIAL_VERSION = 2

enum class StudioTutorialModule(
    val title: String,
    val objective: String,
    val lessonVersion: Int = 2,
) {
    NAVIGATION("Navegación del lienzo", "Haz zoom, desplaza, rota y restablece la vista."),
    BRUSH_PEN("Pincel y S Pen", "Compara presión suave y fuerte; observa presión e inclinación."),
    ERASER("Borrador", "Borra una parte visible de la figura y recupérala."),
    COLOR_PICKER("Color y cuentagotas", "Muestrea un color y úsalo en un trazo."),
    LAYERS("Capas y molde inferior", "Crea, dibuja, oculta, usa una capa inferior como molde y ordena una capa temporal.", lessonVersion = 3),
    MASKS("Ocultar sin borrar", "Esconde y recupera partes de una figura manteniendo intacta la capa original.", lessonVersion = 3),
    SELECTION("Selección", "Crea una selección con área suficiente y observa su contorno."),
    TRANSFORMATION("Transformación", "Previsualiza y confirma un cambio geométrico real."),
    SHAPES_FILL("Formas, linea y relleno", "Crea una figura y rellena su region interior."),
    GRADIENT("Degradado", "Define inicio, dirección y longitud de un degradado visible."),
    SYMMETRY_GUIDES("Simetría y guías", "Activa un eje y produce un trazo reflejado."),
    UNDO_REDO("Deshacer y rehacer", "Deshaz un trazo identificable y restaura exactamente el mismo."),
    SAVE_EXPORT("Guardado y exportación", "Previsualiza una exportación segura sin escribir archivos."),
    BRUSH_CUSTOMIZATION("Personalización de pinceles", "Compara antes y después de un parámetro perceptible."),
}

enum class TutorialTrack { QUICK_START, FULL_COURSE }
enum class TutorialProgressStatus { NOT_STARTED, IN_PROGRESS, COMPLETED, SKIPPED }

val QUICK_START_MODULES = listOf(
    StudioTutorialModule.NAVIGATION,
    StudioTutorialModule.BRUSH_PEN,
    StudioTutorialModule.ERASER,
    StudioTutorialModule.LAYERS,
    StudioTutorialModule.UNDO_REDO,
    StudioTutorialModule.SAVE_EXPORT,
)

data class StudioModuleProgress(
    val status: TutorialProgressStatus = TutorialProgressStatus.NOT_STARTED,
    val step: Int = 0,
    val completedAtEpochMs: Long? = null,
    val lessonVersion: Int = STUDIO_TUTORIAL_VERSION,
)

enum class TutorialEvidence {
    ZOOMED, PANNED, ROTATED, VIEW_RESET,
    LIGHT_STROKE, HEAVY_STROKE, PRESSURE_VARIATION,
    ERASED_PIXELS, ERASURE_RESTORED,
    COLOR_SAMPLED, ACTIVE_COLOR_CHANGED, COLOR_STROKE,
    LAYER_CREATED, LAYER_STROKE, LAYER_HIDDEN, LAYER_SHOWN, LAYER_CLIPPING_ENABLED, LAYER_REORDERED,
    MASK_CREATED, MASK_CHANGED, MASK_RESTORED,
    SELECTION_VALID,
    TRANSFORM_PREVIEW, TRANSFORM_COMMITTED,
    SHAPE_CREATED, REGION_FILLED,
    GRADIENT_VISIBLE,
    SYMMETRY_GUIDE_VISIBLE, SYMMETRIC_STROKE,
    HISTORY_STROKE, UNDO_VISUAL_CHANGE, REDO_VISUAL_RESTORE,
    EXPORT_FORMAT_SELECTED, EXPORT_PREVIEW_VISIBLE,
    BRUSH_PARAMETER_CHANGED, BRUSH_BEFORE_AFTER,
}

sealed interface StudioTutorialEvent {
    data class CanvasViewChanged(
        val scale: Float,
        val panDistance: Float,
        val rotationDegrees: Float,
    ) : StudioTutorialEvent
    data object ViewReset : StudioTutorialEvent
    data class StrokeCommitted(
        val lengthPx: Float,
        val maximumPressure: Float,
        val minimumPressure: Float,
        val eraser: Boolean,
        val tiltRadians: Float = 0f,
        val visualChanged: Boolean = true,
    ) : StudioTutorialEvent
    data class ErasureRestored(val changedPixels: Int) : StudioTutorialEvent
    data class ColorSampled(val sampledArgb: Long, val previousArgb: Long) : StudioTutorialEvent
    data class StrokeWithActiveColor(val lengthPx: Float) : StudioTutorialEvent
    data class LayerCreated(val layerId: String) : StudioTutorialEvent
    data class LayerStrokeCommitted(val layerId: String, val lengthPx: Float) : StudioTutorialEvent
    data class LayerVisibilityChanged(val layerId: String, val visible: Boolean, val canvasChanged: Boolean) : StudioTutorialEvent
    data class LayerClippingChanged(val layerId: String, val enabled: Boolean) : StudioTutorialEvent
    data class LayerReordered(val layerId: String, val canvasChanged: Boolean) : StudioTutorialEvent
    data class MaskCreated(val layerId: String) : StudioTutorialEvent
    data class MaskContentChanged(val hiddenAreaPx: Float) : StudioTutorialEvent
    data class MaskContentRestored(val restoredAreaPx: Float) : StudioTutorialEvent
    data class SelectionCreated(val areaPx: Float) : StudioTutorialEvent
    data class TransformPreviewChanged(val distancePx: Float, val scaleDelta: Float, val rotationDegrees: Float) : StudioTutorialEvent
    data class TransformCommitted(val geometryChanged: Boolean) : StudioTutorialEvent
    data class ShapeCommitted(val areaPx: Float) : StudioTutorialEvent
    data class FillCommitted(val changedPixels: Int) : StudioTutorialEvent
    data class GradientCommitted(val lengthPx: Float, val colorDifference: Float) : StudioTutorialEvent
    data class SymmetryEnabled(val guideVisible: Boolean) : StudioTutorialEvent
    data class SymmetricStrokeCommitted(val lengthPx: Float, val copyCount: Int) : StudioTutorialEvent
    data class HistoryStrokeCommitted(val strokeId: String, val lengthPx: Float) : StudioTutorialEvent
    data class UndoPerformed(val strokeId: String, val visualChanged: Boolean) : StudioTutorialEvent
    data class RedoPerformed(val strokeId: String, val visualRestored: Boolean) : StudioTutorialEvent
    data class ExportFormatSelected(val format: String) : StudioTutorialEvent
    data class ExportPreviewGenerated(val format: String, val width: Int, val height: Int) : StudioTutorialEvent
    data class BrushParameterChanged(val parameter: String, val before: Float, val after: Float) : StudioTutorialEvent
    data class BrushComparisonCommitted(val differenceScore: Float) : StudioTutorialEvent
}

data class StudioTutorialState(
    val track: TutorialTrack = TutorialTrack.QUICK_START,
    val current: StudioTutorialModule = StudioTutorialModule.NAVIGATION,
    val progressByModule: Map<StudioTutorialModule, StudioModuleProgress> = emptyMap(),
    val evidence: Set<TutorialEvidence> = emptySet(),
    val paused: Boolean = false,
    val practiceMode: Boolean = false,
    val demoVisible: Boolean = false,
    val hintLevel: Int = 0,
    val confirmation: String? = null,
    val visualRevision: Int = 0,
    val attemptId: Int = 0,
) {
    val modules: List<StudioTutorialModule>
        get() = if (track == TutorialTrack.QUICK_START) QUICK_START_MODULES else StudioTutorialModule.entries
    val currentProgress: StudioModuleProgress get() = progressByModule[current] ?: StudioModuleProgress(lessonVersion = current.lessonVersion)
    val currentComplete: Boolean get() = currentProgress.status == TutorialProgressStatus.COMPLETED
    val completed: Set<StudioTutorialModule> get() = progressByModule.filterValues { it.status == TutorialProgressStatus.COMPLETED }.keys
    val progress: Float get() = modules.count { it in completed } / modules.size.toFloat()
}

sealed interface StudioTutorialAction {
    data class Observe(val event: StudioTutorialEvent) : StudioTutorialAction
    data object Next : StudioTutorialAction
    data object Previous : StudioTutorialAction
    data class Open(val module: StudioTutorialModule) : StudioTutorialAction
    data class SelectTrack(val track: TutorialTrack) : StudioTutorialAction
    data object Pause : StudioTutorialAction
    data object Resume : StudioTutorialAction
    data object Skip : StudioTutorialAction
    data object Restart : StudioTutorialAction
    data class RestartModule(val module: StudioTutorialModule) : StudioTutorialAction
    data object Practice : StudioTutorialAction
    data object EndPractice : StudioTutorialAction
    data object ShowHint : StudioTutorialAction
    data object ShowDemo : StudioTutorialAction
    data object HideDemo : StudioTutorialAction
    data object DismissConfirmation : StudioTutorialAction
}

fun reduceStudioTutorial(
    state: StudioTutorialState,
    action: StudioTutorialAction,
    nowEpochMs: Long = System.currentTimeMillis(),
): StudioTutorialState = when (action) {
    is StudioTutorialAction.Observe -> observeTutorialEvent(state, action.event, nowEpochMs)
    StudioTutorialAction.Next -> move(state, 1)
    StudioTutorialAction.Previous -> move(state, -1)
    is StudioTutorialAction.Open -> openModule(state, action.module)
    is StudioTutorialAction.SelectTrack -> {
        val first = if (action.track == TutorialTrack.QUICK_START) QUICK_START_MODULES.first() else StudioTutorialModule.NAVIGATION
        openModule(state.copy(track = action.track), first)
    }
    StudioTutorialAction.Pause -> state.copy(paused = true)
    StudioTutorialAction.Resume -> state.copy(paused = false)
    StudioTutorialAction.Skip -> state.copy(
        progressByModule = state.progressByModule + (state.current to state.currentProgress.copy(status = TutorialProgressStatus.SKIPPED)),
        confirmation = "Leccion omitida; no se marco como aprendida",
    )
    StudioTutorialAction.Restart -> StudioTutorialState(track = state.track)
    is StudioTutorialAction.RestartModule -> restartModule(state, action.module)
    StudioTutorialAction.Practice -> state.copy(practiceMode = true, confirmation = null)
    StudioTutorialAction.EndPractice -> state.copy(practiceMode = false)
    StudioTutorialAction.ShowHint -> state.copy(hintLevel = (state.hintLevel + 1).coerceAtMost(2))
    StudioTutorialAction.ShowDemo -> state.copy(demoVisible = true)
    StudioTutorialAction.HideDemo -> state.copy(demoVisible = false)
    StudioTutorialAction.DismissConfirmation -> state.copy(confirmation = null)
}

private fun move(state: StudioTutorialState, delta: Int): StudioTutorialState {
    if (delta > 0 && !state.currentComplete) return state
    val modules = state.modules
    val currentIndex = modules.indexOf(state.current).coerceAtLeast(0)
    return openModule(state, modules[(currentIndex + delta).coerceIn(0, modules.lastIndex)])
}

private fun openModule(state: StudioTutorialState, module: StudioTutorialModule): StudioTutorialState {
    val existing = state.progressByModule[module] ?: StudioModuleProgress(lessonVersion = module.lessonVersion)
    val started = if (existing.status == TutorialProgressStatus.NOT_STARTED) existing.copy(status = TutorialProgressStatus.IN_PROGRESS) else existing
    return state.copy(
        current = module,
        progressByModule = state.progressByModule + (module to started),
        evidence = emptySet(),
        confirmation = null,
        practiceMode = false,
        demoVisible = false,
        hintLevel = 0,
        attemptId = state.attemptId + 1,
    )
}

private fun restartModule(state: StudioTutorialState, module: StudioTutorialModule): StudioTutorialState = state.copy(
    current = module,
    progressByModule = state.progressByModule + (module to StudioModuleProgress(TutorialProgressStatus.IN_PROGRESS, lessonVersion = module.lessonVersion)),
    evidence = emptySet(),
    confirmation = null,
    practiceMode = false,
    demoVisible = false,
    hintLevel = 0,
    attemptId = state.attemptId + 1,
)

private fun observeTutorialEvent(state: StudioTutorialState, event: StudioTutorialEvent, nowEpochMs: Long): StudioTutorialState {
    if (state.paused || state.demoVisible) return state
    val additions = evidenceFor(state, event)
    if (additions.isEmpty()) return state
    val evidence = state.evidence + additions
    val visualRevision = state.visualRevision + if (eventHasVisibleConsequence(event)) 1 else 0
    val complete = requiredEvidence(state.current).all(evidence::contains)
    val progress = if (complete) {
        StudioModuleProgress(TutorialProgressStatus.COMPLETED, requiredEvidence(state.current).size, nowEpochMs, state.current.lessonVersion)
    } else {
        StudioModuleProgress(TutorialProgressStatus.IN_PROGRESS, evidence.size, null, state.current.lessonVersion)
    }
    return state.copy(
        evidence = evidence,
        visualRevision = visualRevision,
        progressByModule = state.progressByModule + (state.current to progress),
        confirmation = if (complete && !state.currentComplete) completionMessage(state.current) else state.confirmation,
    )
}

private fun evidenceFor(state: StudioTutorialState, event: StudioTutorialEvent): Set<TutorialEvidence> {
    if (eventModule(event) != state.current) return emptySet()
    return when (event) {
    is StudioTutorialEvent.CanvasViewChanged -> buildSet {
        if (abs(event.scale - 1f) >= .1f) add(TutorialEvidence.ZOOMED)
        if (event.panDistance >= 24f) add(TutorialEvidence.PANNED)
        if (abs(event.rotationDegrees) >= 8f) add(TutorialEvidence.ROTATED)
    }
    StudioTutorialEvent.ViewReset -> setOf(TutorialEvidence.VIEW_RESET)
    is StudioTutorialEvent.StrokeCommitted -> when {
        !event.visualChanged || event.lengthPx < 48f -> emptySet()
        event.eraser && state.current == StudioTutorialModule.ERASER -> setOf(TutorialEvidence.ERASED_PIXELS)
        !event.eraser && state.current == StudioTutorialModule.BRUSH_PEN -> buildSet {
            if (event.minimumPressure <= .35f) add(TutorialEvidence.LIGHT_STROKE)
            if (event.maximumPressure >= .65f) add(TutorialEvidence.HEAVY_STROKE)
            if (event.maximumPressure - event.minimumPressure >= .2f) add(TutorialEvidence.PRESSURE_VARIATION)
        }
        else -> emptySet()
    }
    is StudioTutorialEvent.ErasureRestored -> if (event.changedPixels >= 32) setOf(TutorialEvidence.ERASURE_RESTORED) else emptySet()
    is StudioTutorialEvent.ColorSampled -> if (event.sampledArgb != event.previousArgb) setOf(TutorialEvidence.COLOR_SAMPLED, TutorialEvidence.ACTIVE_COLOR_CHANGED) else emptySet()
    is StudioTutorialEvent.StrokeWithActiveColor -> if (event.lengthPx >= 48f && TutorialEvidence.COLOR_SAMPLED in state.evidence) setOf(TutorialEvidence.COLOR_STROKE) else emptySet()
    is StudioTutorialEvent.LayerCreated -> if (event.layerId.isNotBlank()) setOf(TutorialEvidence.LAYER_CREATED) else emptySet()
    is StudioTutorialEvent.LayerStrokeCommitted -> if (event.lengthPx >= 48f && TutorialEvidence.LAYER_CREATED in state.evidence) setOf(TutorialEvidence.LAYER_STROKE) else emptySet()
    is StudioTutorialEvent.LayerVisibilityChanged -> if (event.canvasChanged && TutorialEvidence.LAYER_STROKE in state.evidence) setOf(if (event.visible) TutorialEvidence.LAYER_SHOWN else TutorialEvidence.LAYER_HIDDEN) else emptySet()
    is StudioTutorialEvent.LayerClippingChanged -> if (event.enabled && TutorialEvidence.LAYER_CREATED in state.evidence) setOf(TutorialEvidence.LAYER_CLIPPING_ENABLED) else emptySet()
    is StudioTutorialEvent.LayerReordered -> if (event.canvasChanged && TutorialEvidence.LAYER_CREATED in state.evidence) setOf(TutorialEvidence.LAYER_REORDERED) else emptySet()
    is StudioTutorialEvent.MaskCreated -> if (event.layerId.isNotBlank()) setOf(TutorialEvidence.MASK_CREATED) else emptySet()
    is StudioTutorialEvent.MaskContentChanged -> if (event.hiddenAreaPx >= 256f && TutorialEvidence.MASK_CREATED in state.evidence) setOf(TutorialEvidence.MASK_CHANGED) else emptySet()
    is StudioTutorialEvent.MaskContentRestored -> if (event.restoredAreaPx >= 128f && TutorialEvidence.MASK_CHANGED in state.evidence) setOf(TutorialEvidence.MASK_RESTORED) else emptySet()
    is StudioTutorialEvent.SelectionCreated -> if (event.areaPx >= 2_500f) setOf(TutorialEvidence.SELECTION_VALID) else emptySet()
    is StudioTutorialEvent.TransformPreviewChanged -> if (event.distancePx >= 24f || abs(event.scaleDelta) >= .08f || abs(event.rotationDegrees) >= 8f) setOf(TutorialEvidence.TRANSFORM_PREVIEW) else emptySet()
    is StudioTutorialEvent.TransformCommitted -> if (event.geometryChanged && TutorialEvidence.TRANSFORM_PREVIEW in state.evidence) setOf(TutorialEvidence.TRANSFORM_COMMITTED) else emptySet()
    is StudioTutorialEvent.ShapeCommitted -> if (event.areaPx >= 2_500f) setOf(TutorialEvidence.SHAPE_CREATED) else emptySet()
    is StudioTutorialEvent.FillCommitted -> if (event.changedPixels >= 500 && TutorialEvidence.SHAPE_CREATED in state.evidence) setOf(TutorialEvidence.REGION_FILLED) else emptySet()
    is StudioTutorialEvent.GradientCommitted -> if (event.lengthPx >= 72f && event.colorDifference >= .15f) setOf(TutorialEvidence.GRADIENT_VISIBLE) else emptySet()
    is StudioTutorialEvent.SymmetryEnabled -> if (event.guideVisible) setOf(TutorialEvidence.SYMMETRY_GUIDE_VISIBLE) else emptySet()
    is StudioTutorialEvent.SymmetricStrokeCommitted -> if (event.lengthPx >= 48f && event.copyCount >= 2 && TutorialEvidence.SYMMETRY_GUIDE_VISIBLE in state.evidence) setOf(TutorialEvidence.SYMMETRIC_STROKE) else emptySet()
    is StudioTutorialEvent.HistoryStrokeCommitted -> if (event.strokeId.isNotBlank() && event.lengthPx >= 48f) setOf(TutorialEvidence.HISTORY_STROKE) else emptySet()
    is StudioTutorialEvent.UndoPerformed -> if (event.visualChanged && TutorialEvidence.HISTORY_STROKE in state.evidence) setOf(TutorialEvidence.UNDO_VISUAL_CHANGE) else emptySet()
    is StudioTutorialEvent.RedoPerformed -> if (event.visualRestored && TutorialEvidence.UNDO_VISUAL_CHANGE in state.evidence) setOf(TutorialEvidence.REDO_VISUAL_RESTORE) else emptySet()
    is StudioTutorialEvent.ExportFormatSelected -> if (event.format in setOf("PNG", "Canvas Studio")) setOf(TutorialEvidence.EXPORT_FORMAT_SELECTED) else emptySet()
    is StudioTutorialEvent.ExportPreviewGenerated -> if (event.width > 0 && event.height > 0 && TutorialEvidence.EXPORT_FORMAT_SELECTED in state.evidence) setOf(TutorialEvidence.EXPORT_PREVIEW_VISIBLE) else emptySet()
    is StudioTutorialEvent.BrushParameterChanged -> if (isPerceptibleParameterChange(event.before, event.after)) setOf(TutorialEvidence.BRUSH_PARAMETER_CHANGED) else emptySet()
    is StudioTutorialEvent.BrushComparisonCommitted -> if (event.differenceScore >= .15f && TutorialEvidence.BRUSH_PARAMETER_CHANGED in state.evidence) setOf(TutorialEvidence.BRUSH_BEFORE_AFTER) else emptySet()
    }
}

internal fun isPerceptibleParameterChange(before: Float, after: Float): Boolean {
    val baseline = abs(before).coerceAtLeast(.01f)
    return abs(after - before) / baseline >= .15f
}

private fun eventModule(event: StudioTutorialEvent): StudioTutorialModule = when (event) {
    is StudioTutorialEvent.CanvasViewChanged, StudioTutorialEvent.ViewReset -> StudioTutorialModule.NAVIGATION
    is StudioTutorialEvent.StrokeCommitted -> if (event.eraser) StudioTutorialModule.ERASER else StudioTutorialModule.BRUSH_PEN
    is StudioTutorialEvent.ErasureRestored -> StudioTutorialModule.ERASER
    is StudioTutorialEvent.ColorSampled, is StudioTutorialEvent.StrokeWithActiveColor -> StudioTutorialModule.COLOR_PICKER
    is StudioTutorialEvent.LayerCreated, is StudioTutorialEvent.LayerStrokeCommitted, is StudioTutorialEvent.LayerVisibilityChanged, is StudioTutorialEvent.LayerClippingChanged, is StudioTutorialEvent.LayerReordered -> StudioTutorialModule.LAYERS
    is StudioTutorialEvent.MaskCreated, is StudioTutorialEvent.MaskContentChanged, is StudioTutorialEvent.MaskContentRestored -> StudioTutorialModule.MASKS
    is StudioTutorialEvent.SelectionCreated -> StudioTutorialModule.SELECTION
    is StudioTutorialEvent.TransformPreviewChanged, is StudioTutorialEvent.TransformCommitted -> StudioTutorialModule.TRANSFORMATION
    is StudioTutorialEvent.ShapeCommitted, is StudioTutorialEvent.FillCommitted -> StudioTutorialModule.SHAPES_FILL
    is StudioTutorialEvent.GradientCommitted -> StudioTutorialModule.GRADIENT
    is StudioTutorialEvent.SymmetryEnabled, is StudioTutorialEvent.SymmetricStrokeCommitted -> StudioTutorialModule.SYMMETRY_GUIDES
    is StudioTutorialEvent.HistoryStrokeCommitted, is StudioTutorialEvent.UndoPerformed, is StudioTutorialEvent.RedoPerformed -> StudioTutorialModule.UNDO_REDO
    is StudioTutorialEvent.ExportFormatSelected, is StudioTutorialEvent.ExportPreviewGenerated -> StudioTutorialModule.SAVE_EXPORT
    is StudioTutorialEvent.BrushParameterChanged, is StudioTutorialEvent.BrushComparisonCommitted -> StudioTutorialModule.BRUSH_CUSTOMIZATION
}

private fun eventHasVisibleConsequence(event: StudioTutorialEvent): Boolean = when (event) {
    is StudioTutorialEvent.LayerCreated,
    is StudioTutorialEvent.MaskCreated,
    is StudioTutorialEvent.ExportFormatSelected -> false
    else -> true
}

fun requiredEvidence(module: StudioTutorialModule): Set<TutorialEvidence> = when (module) {
    StudioTutorialModule.NAVIGATION -> setOf(TutorialEvidence.ZOOMED, TutorialEvidence.PANNED, TutorialEvidence.ROTATED, TutorialEvidence.VIEW_RESET)
    StudioTutorialModule.BRUSH_PEN -> setOf(TutorialEvidence.LIGHT_STROKE, TutorialEvidence.HEAVY_STROKE, TutorialEvidence.PRESSURE_VARIATION)
    StudioTutorialModule.ERASER -> setOf(TutorialEvidence.ERASED_PIXELS, TutorialEvidence.ERASURE_RESTORED)
    StudioTutorialModule.COLOR_PICKER -> setOf(TutorialEvidence.COLOR_SAMPLED, TutorialEvidence.ACTIVE_COLOR_CHANGED, TutorialEvidence.COLOR_STROKE)
    StudioTutorialModule.LAYERS -> setOf(TutorialEvidence.LAYER_CREATED, TutorialEvidence.LAYER_STROKE, TutorialEvidence.LAYER_HIDDEN, TutorialEvidence.LAYER_SHOWN, TutorialEvidence.LAYER_CLIPPING_ENABLED, TutorialEvidence.LAYER_REORDERED)
    StudioTutorialModule.MASKS -> setOf(TutorialEvidence.MASK_CREATED, TutorialEvidence.MASK_CHANGED, TutorialEvidence.MASK_RESTORED)
    StudioTutorialModule.SELECTION -> setOf(TutorialEvidence.SELECTION_VALID)
    StudioTutorialModule.TRANSFORMATION -> setOf(TutorialEvidence.TRANSFORM_PREVIEW, TutorialEvidence.TRANSFORM_COMMITTED)
    StudioTutorialModule.SHAPES_FILL -> setOf(TutorialEvidence.SHAPE_CREATED, TutorialEvidence.REGION_FILLED)
    StudioTutorialModule.GRADIENT -> setOf(TutorialEvidence.GRADIENT_VISIBLE)
    StudioTutorialModule.SYMMETRY_GUIDES -> setOf(TutorialEvidence.SYMMETRY_GUIDE_VISIBLE, TutorialEvidence.SYMMETRIC_STROKE)
    StudioTutorialModule.UNDO_REDO -> setOf(TutorialEvidence.HISTORY_STROKE, TutorialEvidence.UNDO_VISUAL_CHANGE, TutorialEvidence.REDO_VISUAL_RESTORE)
    StudioTutorialModule.SAVE_EXPORT -> setOf(TutorialEvidence.EXPORT_FORMAT_SELECTED, TutorialEvidence.EXPORT_PREVIEW_VISIBLE)
    StudioTutorialModule.BRUSH_CUSTOMIZATION -> setOf(TutorialEvidence.BRUSH_PARAMETER_CHANGED, TutorialEvidence.BRUSH_BEFORE_AFTER)
}

fun completionMessage(module: StudioTutorialModule): String = when (module) {
    StudioTutorialModule.NAVIGATION -> "Vista transformada y restablecida"
    StudioTutorialModule.BRUSH_PEN -> "La presión cambió el grosor del trazo"
    StudioTutorialModule.ERASER -> "Zona borrada y recuperada"
    StudioTutorialModule.COLOR_PICKER -> "Color muestreado y usado en un trazo"
    StudioTutorialModule.LAYERS -> "Capa creada, dibujada, reordenada y comprobada"
    StudioTutorialModule.MASKS -> "Ocultación aplicada sin modificar la figura original"
    StudioTutorialModule.SELECTION -> "Selección válida creada"
    StudioTutorialModule.TRANSFORMATION -> "Transformación previsualizada y confirmada"
    StudioTutorialModule.SHAPES_FILL -> "Figura creada y rellenada"
    StudioTutorialModule.GRADIENT -> "Degradado visible aplicado"
    StudioTutorialModule.SYMMETRY_GUIDES -> "Eje visible y trazo reflejado"
    StudioTutorialModule.UNDO_REDO -> "Trazo deshecho y restaurado"
    StudioTutorialModule.SAVE_EXPORT -> "Vista previa de exportación generada"
    StudioTutorialModule.BRUSH_CUSTOMIZATION -> "Cambio del pincel comparado antes y después"
}

internal object StudioTutorialProgressStore {
    private const val CURRENT = "studio_tutorial_current"
    private const val TRACK = "studio_tutorial_track"
    private const val VERSION = "studio_tutorial_version"
    private const val MODULE_PREFIX = "studio_tutorial_module_"
    private const val LEGACY_COMPLETED = "studio_tutorial_completed_modules"

    fun load(preferences: SharedPreferences): StudioTutorialState = runCatching {
        val savedVersion = preferences.getInt(VERSION, 1)
        val legacyCompleted = preferences.getStringSet(LEGACY_COMPLETED, emptySet()).orEmpty()
        val progress = StudioTutorialModule.entries.associateWith { module ->
            val raw = preferences.getString(MODULE_PREFIX + module.name, null)
            val parsed = raw?.split('|')
            val status = parsed?.getOrNull(0)?.let { runCatching { TutorialProgressStatus.valueOf(it) }.getOrNull() }
                ?: if (module.name in legacyCompleted) TutorialProgressStatus.COMPLETED else TutorialProgressStatus.NOT_STARTED
            val step = parsed?.getOrNull(1)?.toIntOrNull() ?: 0
            val completedAt = parsed?.getOrNull(2)?.toLongOrNull()?.takeIf { it > 0 }
            val lessonVersion = parsed?.getOrNull(3)?.toIntOrNull() ?: savedVersion
            if (lessonVersion == module.lessonVersion) StudioModuleProgress(status, step, completedAt, lessonVersion)
            else StudioModuleProgress(lessonVersion = module.lessonVersion)
        }.filterValues { it.status != TutorialProgressStatus.NOT_STARTED }
        StudioTutorialState(
            track = preferences.getString(TRACK, null)?.let { TutorialTrack.valueOf(it) } ?: TutorialTrack.QUICK_START,
            current = preferences.getString(CURRENT, null)?.let(StudioTutorialModule::valueOf) ?: StudioTutorialModule.NAVIGATION,
            progressByModule = progress,
        )
    }.getOrDefault(StudioTutorialState())

    fun save(preferences: SharedPreferences, state: StudioTutorialState) {
        val editor = preferences.edit().putInt(VERSION, STUDIO_TUTORIAL_VERSION).putString(CURRENT, state.current.name).putString(TRACK, state.track.name)
        StudioTutorialModule.entries.forEach { module ->
            val progress = state.progressByModule[module]
            if (progress == null) editor.remove(MODULE_PREFIX + module.name)
            else editor.putString(MODULE_PREFIX + module.name, listOf(progress.status.name, progress.step, progress.completedAtEpochMs ?: 0L, progress.lessonVersion).joinToString("|"))
        }
        editor.remove(LEGACY_COMPLETED).apply()
    }

    fun clear(preferences: SharedPreferences) {
        val editor = preferences.edit().remove(CURRENT).remove(TRACK).remove(VERSION).remove(LEGACY_COMPLETED)
        StudioTutorialModule.entries.forEach { editor.remove(MODULE_PREFIX + it.name) }
        editor.apply()
    }
}
