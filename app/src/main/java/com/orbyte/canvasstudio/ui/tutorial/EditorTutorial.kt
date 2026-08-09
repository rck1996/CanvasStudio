package com.orbyte.canvasstudio.ui.tutorial

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.orbyte.canvasstudio.drawing.DrawingInteractionEvent
import com.orbyte.canvasstudio.drawing.DrawingTool
import com.orbyte.canvasstudio.model.StudioPalette
import kotlin.math.abs
import kotlin.math.roundToInt

@Stable
internal class EditorTutorialSession(
    initial: StudioTutorialState,
    private val preferences: SharedPreferences,
) {
    var state by mutableStateOf(initial)
        private set

    var activeTool by mutableStateOf(DrawingTool.BRUSH)
    var layersPanelActive by mutableStateOf(false)
    var brushesPanelActive by mutableStateOf(false)
    var selectionActive by mutableStateOf(false)
    var quickMenuOpen by mutableStateOf(false)
    var maskEditingActive by mutableStateOf(false)
    private var historyStrokeId: String? = null

    val guide: EditorTutorialGuide get() = guideFor(
        state,
        activeTool,
        layersPanelActive,
        brushesPanelActive,
        selectionActive,
        quickMenuOpen,
        maskEditingActive,
    )

    fun dispatch(action: StudioTutorialAction) {
        state = reduceStudioTutorial(state, action)
        StudioTutorialProgressStore.save(preferences, state)
    }

    fun observe(event: StudioTutorialEvent) = dispatch(StudioTutorialAction.Observe(event))

    fun observeDrawing(event: DrawingInteractionEvent) {
        when (event) {
            is DrawingInteractionEvent.ViewChanged -> observe(
                StudioTutorialEvent.CanvasViewChanged(event.scale, event.panDistancePx, event.rotationDegrees),
            )
            DrawingInteractionEvent.ViewReset -> observe(StudioTutorialEvent.ViewReset)
            is DrawingInteractionEvent.StrokeCommitted -> when (state.current) {
                StudioTutorialModule.BRUSH_PEN -> observe(
                    StudioTutorialEvent.StrokeCommitted(
                        event.lengthPx, event.maximumPressure, event.minimumPressure,
                        event.eraser, event.maximumTiltRadians,
                    ),
                )
                StudioTutorialModule.ERASER -> observe(
                    StudioTutorialEvent.StrokeCommitted(
                        event.lengthPx, event.maximumPressure, event.minimumPressure,
                        event.eraser, event.maximumTiltRadians,
                    ),
                )
                StudioTutorialModule.COLOR_PICKER -> if (!event.eraser) {
                    observe(StudioTutorialEvent.StrokeWithActiveColor(event.lengthPx))
                }
                StudioTutorialModule.LAYERS -> if (!event.eraser && !event.editingMask) {
                    observe(StudioTutorialEvent.LayerStrokeCommitted(event.layerId, event.lengthPx))
                }
                StudioTutorialModule.MASKS -> if (event.editingMask) {
                    if (event.eraser) observe(StudioTutorialEvent.MaskContentRestored(event.lengthPx * 12f))
                    else observe(StudioTutorialEvent.MaskContentChanged(event.lengthPx * 12f))
                }
                StudioTutorialModule.SYMMETRY_GUIDES -> if (!event.eraser) {
                    observe(StudioTutorialEvent.SymmetricStrokeCommitted(event.lengthPx, event.symmetryCopies))
                }
                StudioTutorialModule.UNDO_REDO -> if (!event.eraser) {
                    historyStrokeId = "stroke-${System.nanoTime()}"
                    observe(StudioTutorialEvent.HistoryStrokeCommitted(historyStrokeId!!, event.lengthPx))
                }
                StudioTutorialModule.BRUSH_CUSTOMIZATION -> if (!event.eraser) {
                    observe(StudioTutorialEvent.BrushComparisonCommitted(.5f))
                }
                else -> Unit
            }
            is DrawingInteractionEvent.ShapeCommitted -> observe(
                StudioTutorialEvent.ShapeCommitted(event.widthPx * event.heightPx),
            )
            is DrawingInteractionEvent.GradientCommitted -> observe(
                StudioTutorialEvent.GradientCommitted(event.lengthPx, 1f),
            )
            is DrawingInteractionEvent.SelectionCreated -> observe(StudioTutorialEvent.SelectionCreated(event.areaPx))
            is DrawingInteractionEvent.TransformPreviewChanged -> observe(
                StudioTutorialEvent.TransformPreviewChanged(event.distancePx, event.scaleDelta, event.rotationDegrees),
            )
            DrawingInteractionEvent.TransformCommitted -> observe(StudioTutorialEvent.TransformCommitted(true))
            is DrawingInteractionEvent.FillCommitted -> observe(StudioTutorialEvent.FillCommitted(event.changedPixels))
            is DrawingInteractionEvent.HistoryChanged -> when (event.action) {
                DrawingInteractionEvent.HistoryChanged.Action.UNDO -> {
                    if (state.current == StudioTutorialModule.ERASER && TutorialEvidence.ERASED_PIXELS in state.evidence) {
                        observe(StudioTutorialEvent.ErasureRestored(128))
                    } else {
                        observe(StudioTutorialEvent.UndoPerformed(historyStrokeId.orEmpty(), true))
                    }
                }
                DrawingInteractionEvent.HistoryChanged.Action.REDO ->
                    observe(StudioTutorialEvent.RedoPerformed(historyStrokeId.orEmpty(), true))
            }
        }
    }
}

internal data class EditorTutorialGuide(
    val target: String,
    val instruction: String,
    val expectedOutcome: String = expectedOutcomeFor(target),
)

private fun expectedOutcomeFor(target: String): String = when {
    target == "quick_layer_add" -> "Verás una capa nueva seleccionada y lista para dibujar."
    target == "quick_layer_visibility" -> "El contenido de esa capa cambia sin afectar las demás."
    target == "quick_layer_mask" -> "Entrarás a Ocultación; la capa original seguirá intacta."
    target == "quick_menu_trigger" -> "La rueda aparece sobre el lienzo y muestra seis acciones configurables."
    target == "quick_menu_gesture" -> "La rueda aparece bajo tu dedo; puedes deslizar hacia una acción y soltar."
    target == "layer_clipping" -> "La pintura de esta capa solo se verá dentro del contenido de la capa inferior."
    target == "quick_access" -> "Tus acciones frecuentes quedan disponibles sin cambiar de panel."
    target == "undo" -> "El último cambio visible desaparece."
    target == "redo" -> "El mismo cambio vuelve exactamente a su posición."
    target == "view_reset" -> "El lienzo vuelve centrado, derecho y al zoom inicial."
    target == "export_png" -> "El tutorial valida una vista previa sin guardar archivos."
    target.startsWith("tool_") -> "La herramienta elegida queda resaltada en la barra izquierda."
    target.startsWith("dock_") -> "El panel solicitado aparece a la derecha sin salir del lienzo."
    target == "canvas" -> "El resultado debe verse inmediatamente sobre el lienzo."
    else -> "El cambio debe verse de inmediato y podrás deshacerlo si lo necesitas."
}

internal enum class TutorialCardPlacement { TOP_CENTER, BOTTOM_CENTER, TOP_START, BOTTOM_START }

/**
 * Places the instruction card close to the highlighted control without covering it.
 * Coordinates are local to the tutorial overlay, which also fixes status/navigation inset drift.
 */
internal fun tutorialCardOffset(
    target: Rect?,
    viewport: IntSize,
    card: IntSize,
    margin: Float = 18f,
): IntOffset {
    if (viewport.width <= 0 || viewport.height <= 0 || card.width <= 0 || card.height <= 0) {
        return IntOffset(margin.roundToInt(), margin.roundToInt())
    }
    val maxX = (viewport.width - card.width - margin).coerceAtLeast(margin).toFloat()
    val maxY = (viewport.height - card.height - margin).coerceAtLeast(margin).toFloat()
    fun clamp(point: Offset) = Offset(
        point.x.coerceIn(margin, maxX),
        point.y.coerceIn(margin, maxY),
    )
    if (target == null) return IntOffset(maxX.roundToInt(), margin.roundToInt())

    val candidates = listOf(
        Offset(target.left - card.width - margin, target.center.y - card.height / 2f),
        Offset(target.right + margin, target.center.y - card.height / 2f),
        Offset(target.center.x - card.width / 2f, target.top - card.height - margin),
        Offset(target.center.x - card.width / 2f, target.bottom + margin),
        Offset(maxX, margin),
        Offset(margin, margin),
        Offset(maxX, maxY),
        Offset(margin, maxY),
    ).map(::clamp).distinct()
    val protectedTarget = Rect(
        target.left - margin,
        target.top - margin,
        target.right + margin,
        target.bottom + margin,
    )
    fun overlapArea(point: Offset): Float {
        val candidate = Rect(point, androidx.compose.ui.geometry.Size(card.width.toFloat(), card.height.toFloat()))
        val overlapWidth = (minOf(candidate.right, protectedTarget.right) - maxOf(candidate.left, protectedTarget.left)).coerceAtLeast(0f)
        val overlapHeight = (minOf(candidate.bottom, protectedTarget.bottom) - maxOf(candidate.top, protectedTarget.top)).coerceAtLeast(0f)
        return overlapWidth * overlapHeight
    }
    val chosen = candidates.minByOrNull(::overlapArea) ?: Offset(maxX, margin)
    return IntOffset(chosen.x.roundToInt(), chosen.y.roundToInt())
}

internal fun tutorialCardPlacement(target: Rect?, width: Float, height: Float): TutorialCardPlacement {
    if (target == null || width <= 0f || height <= 0f) return TutorialCardPlacement.BOTTOM_CENTER
    val targetOnRightDock = target.center.x >= width * .68f
    val targetLow = target.center.y >= height * .52f
    return when {
        targetOnRightDock && targetLow -> TutorialCardPlacement.TOP_START
        targetOnRightDock -> TutorialCardPlacement.BOTTOM_START
        target.bottom >= height * .62f -> TutorialCardPlacement.TOP_CENTER
        else -> TutorialCardPlacement.BOTTOM_CENTER
    }
}

private fun TutorialCardPlacement.alignment(): Alignment = when (this) {
    TutorialCardPlacement.TOP_CENTER -> Alignment.TopCenter
    TutorialCardPlacement.BOTTOM_CENTER -> Alignment.BottomCenter
    TutorialCardPlacement.TOP_START -> Alignment.TopStart
    TutorialCardPlacement.BOTTOM_START -> Alignment.BottomStart
}

internal fun freshEditorTutorialState(saved: StudioTutorialState): StudioTutorialState =
    StudioTutorialState(track = TutorialTrack.FULL_COURSE, current = StudioTutorialModule.NAVIGATION)

internal fun tutorialShouldExitMaskEditing(state: StudioTutorialState): Boolean =
    state.current != StudioTutorialModule.MASKS || state.currentComplete

internal data class TutorialRuntimePolicy(
    val exitMaskEditing: Boolean,
    val clearSelection: Boolean,
    val resetSymmetry: Boolean,
    val closeTransientUi: Boolean = true,
)

internal fun tutorialRuntimePolicy(state: StudioTutorialState): TutorialRuntimePolicy = TutorialRuntimePolicy(
    exitMaskEditing = tutorialShouldExitMaskEditing(state),
    clearSelection = state.current != StudioTutorialModule.TRANSFORMATION,
    resetSymmetry = state.current != StudioTutorialModule.SYMMETRY_GUIDES,
)

internal fun contextualTutorialHint(
    state: StudioTutorialState,
    guide: EditorTutorialGuide,
    level: Int,
): String {
    if (level <= 1) return "Busca el borde turquesa. ${guide.expectedOutcome}"
    return when (state.current) {
        StudioTutorialModule.NAVIGATION -> "Apoya dos dedos dentro del papel, no sobre la tarjeta. Puedes minimizar la guía mientras haces el gesto."
        StudioTutorialModule.BRUSH_PEN -> "Usa el S Pen: empieza suave, aumenta la presión en el centro y termina suave. Haz un solo trazo largo."
        StudioTutorialModule.ERASER -> "Borra una zona que tenga pintura visible. Después usa Deshacer, no vuelvas a pintarla manualmente."
        StudioTutorialModule.COLOR_PICKER -> "Toca una zona de color distinta con el cuentagotas y luego vuelve a Pincel para comprobarla."
        StudioTutorialModule.LAYERS -> "Sigue la rueda y luego el panel Capas. La capa temporal debe contener un trazo para que ocultar y ordenar tengan un resultado visible."
        StudioTutorialModule.MASKS -> "Mientras diga Ocultando ahora, Pincel esconde y Borrador recupera. Toca Salir ocultación para volver a pintar la capa normal."
        StudioTutorialModule.SELECTION -> "La ocultación anterior ya se cerró. Elige Selección rectangular y encierra una zona amplia del dibujo."
        StudioTutorialModule.TRANSFORMATION -> "Con el contorno de selección visible, elige Transformar y arrastra dentro de la zona seleccionada."
        StudioTutorialModule.SHAPES_FILL -> "Crea primero un rectángulo cerrado y grande; luego elige Relleno y toca claramente dentro de él."
        StudioTutorialModule.GRADIENT -> "Arrastra de un extremo al otro de una zona amplia; una pulsación corta no alcanza el mínimo."
        StudioTutorialModule.SYMMETRY_GUIDES -> "En Más opciones, pulsa Simetría hasta ver un eje. Luego dibuja a un solo lado."
        StudioTutorialModule.UNDO_REDO -> "Dibuja una marca reconocible, deshaz una vez y reházala una vez."
        StudioTutorialModule.SAVE_EXPORT -> "Exportar PNG solo crea una vista previa durante el tutorial; no modifica tus proyectos."
        StudioTutorialModule.BRUSH_CUSTOMIZATION -> "Abre Pinceles, cambia Tamaño de forma evidente y dibuja un segundo trazo para comparar."
    }
}

internal fun guideFor(
    state: StudioTutorialState,
    activeTool: DrawingTool,
    layersPanelActive: Boolean,
    brushesPanelActive: Boolean,
    selectionActive: Boolean = true,
    quickMenuOpen: Boolean = true,
    maskEditingActive: Boolean = true,
): EditorTutorialGuide {
    val evidence = state.evidence
    fun missing(item: TutorialEvidence) = item !in evidence
    return when (state.current) {
        StudioTutorialModule.NAVIGATION -> when {
            missing(TutorialEvidence.ZOOMED) -> EditorTutorialGuide("canvas", "Pon dos dedos sobre el lienzo y sepáralos para hacer zoom.")
            missing(TutorialEvidence.PANNED) -> EditorTutorialGuide("canvas", "Sin levantar los dos dedos, desplaza el lienzo.")
            missing(TutorialEvidence.ROTATED) -> EditorTutorialGuide("canvas", "Gira los dos dedos hasta inclinar el lienzo.")
            else -> EditorTutorialGuide("view_reset", "Pulsa Restablecer vista para volver al encuadre inicial.")
        }
        StudioTutorialModule.BRUSH_PEN -> if (activeTool != DrawingTool.BRUSH) {
            EditorTutorialGuide("tool_brush", "Selecciona el pincel real.")
        } else EditorTutorialGuide("canvas", "Dibuja un trazo largo variando claramente la presión del S Pen.")
        StudioTutorialModule.ERASER -> when {
            missing(TutorialEvidence.ERASED_PIXELS) && activeTool != DrawingTool.ERASER -> EditorTutorialGuide("tool_eraser", "Selecciona el borrador.")
            missing(TutorialEvidence.ERASED_PIXELS) -> EditorTutorialGuide("canvas", "Borra una parte visible del dibujo.")
            else -> EditorTutorialGuide("undo", "Pulsa Deshacer para recuperar exactamente lo borrado.")
        }
        StudioTutorialModule.COLOR_PICKER -> when {
            missing(TutorialEvidence.COLOR_SAMPLED) && activeTool != DrawingTool.EYEDROPPER -> EditorTutorialGuide("tool_eyedropper", "Selecciona el cuentagotas.")
            missing(TutorialEvidence.COLOR_SAMPLED) -> EditorTutorialGuide("canvas", "Toca un color visible del dibujo.")
            activeTool != DrawingTool.BRUSH -> EditorTutorialGuide("tool_brush", "Vuelve al pincel.")
            else -> EditorTutorialGuide("canvas", "Dibuja un trazo con el color que acabas de muestrear.")
        }
        StudioTutorialModule.LAYERS -> when {
            missing(TutorialEvidence.LAYER_CREATED) && !quickMenuOpen -> EditorTutorialGuide("quick_menu_gesture", "Mantén un dedo quieto sobre la zona indicada para abrir la rueda. La estrella es el acceso alternativo.")
            missing(TutorialEvidence.LAYER_CREATED) -> EditorTutorialGuide("quick_layer_add", "Crea una capa desde Acceso rápido, sin abandonar el lienzo.")
            missing(TutorialEvidence.LAYER_STROKE) -> EditorTutorialGuide("canvas", "Dibuja un trazo visible en la capa nueva.")
            missing(TutorialEvidence.LAYER_HIDDEN) && !quickMenuOpen -> EditorTutorialGuide("quick_menu_gesture", "Mantén un dedo sobre la zona indicada para volver a abrir la rueda.")
            missing(TutorialEvidence.LAYER_HIDDEN) -> EditorTutorialGuide("quick_layer_visibility", "Oculta la capa activa desde Acceso rápido y observa el cambio.")
            missing(TutorialEvidence.LAYER_SHOWN) && !quickMenuOpen -> EditorTutorialGuide("quick_menu_gesture", "Abre otra vez la rueda bajo tu dedo para recuperar la visibilidad.")
            missing(TutorialEvidence.LAYER_SHOWN) -> EditorTutorialGuide("quick_layer_visibility", "Vuelve a mostrar la misma capa desde Acceso rápido.")
            !layersPanelActive -> EditorTutorialGuide("dock_layers", "Abre Capas para ver el orden completo y sus ajustes.")
            missing(TutorialEvidence.LAYER_CLIPPING_ENABLED) -> EditorTutorialGuide("layer_clipping", "Activa Molde inferior. Así esta capa solo pinta dentro de la capa que está debajo.")
            else -> EditorTutorialGuide("layer_down", "Baja la capa una posición y observa el orden.")
        }
        StudioTutorialModule.MASKS -> when {
            missing(TutorialEvidence.MASK_CREATED) && !quickMenuOpen -> EditorTutorialGuide("quick_menu_gesture", "Mantén un dedo quieto sobre la zona indicada. También puedes tocar la estrella.")
            missing(TutorialEvidence.MASK_CREATED) -> EditorTutorialGuide("quick_layer_mask", "Pulsa Ocultar sin borrar en Acceso rápido. La imagen original quedará intacta.")
            !maskEditingActive && !quickMenuOpen -> EditorTutorialGuide("quick_menu_gesture", "La ocultación está en pausa. Abre la rueda para volver a editarla sin borrar la capa.")
            !maskEditingActive -> EditorTutorialGuide("quick_layer_mask", "Pulsa Editar ocultación para continuar exactamente donde estabas.")
            missing(TutorialEvidence.MASK_CHANGED) && activeTool != DrawingTool.BRUSH -> EditorTutorialGuide("tool_brush", "Elige Pincel: en este modo sirve para ocultar.")
            missing(TutorialEvidence.MASK_CHANGED) -> EditorTutorialGuide("canvas", "Pinta sobre una parte de la figura para ocultarla sin borrarla.")
            activeTool != DrawingTool.ERASER -> EditorTutorialGuide("tool_eraser", "Elige Borrador: en este modo sirve para recuperar.")
            else -> EditorTutorialGuide("canvas", "Pasa el borrador por la zona oculta para recuperarla.")
        }
        StudioTutorialModule.SELECTION -> if (activeTool != DrawingTool.SELECT_RECTANGLE) {
            EditorTutorialGuide("tool_select_rectangle", "Selecciona la herramienta Selección rectangular.")
        } else EditorTutorialGuide("canvas", "Arrastra un rectángulo amplio sobre el dibujo.")
        StudioTutorialModule.TRANSFORMATION -> when {
            !selectionActive && activeTool != DrawingTool.SELECT_RECTANGLE -> EditorTutorialGuide("tool_select_rectangle", "Primero selecciona Seleccion rectangular.")
            !selectionActive -> EditorTutorialGuide("canvas", "Crea una selección amplia sobre el dibujo.")
            activeTool != DrawingTool.TRANSFORM -> EditorTutorialGuide("tool_transform", "Con la selección activa, elige Transformar.")
            else -> EditorTutorialGuide("canvas", "Arrastra la selección; al soltar se confirmará el cambio real.")
        }
        StudioTutorialModule.SHAPES_FILL -> when {
            missing(TutorialEvidence.SHAPE_CREATED) && activeTool != DrawingTool.RECTANGLE -> EditorTutorialGuide("tool_rectangle", "Selecciona Rectangulo.")
            missing(TutorialEvidence.SHAPE_CREATED) -> EditorTutorialGuide("canvas", "Arrastra para crear un rectángulo grande.")
            activeTool != DrawingTool.FILL -> EditorTutorialGuide("tool_fill", "Selecciona Relleno.")
            else -> EditorTutorialGuide("canvas", "Toca el interior del rectangulo para rellenarlo.")
        }
        StudioTutorialModule.GRADIENT -> if (activeTool != DrawingTool.GRADIENT) {
            EditorTutorialGuide("tool_gradient", "Selecciona Degradado.")
        } else EditorTutorialGuide("canvas", "Arrastra una línea larga para definir dirección y longitud.")
        StudioTutorialModule.SYMMETRY_GUIDES -> when {
            missing(TutorialEvidence.SYMMETRY_GUIDE_VISIBLE) -> EditorTutorialGuide("more_menu", "Abre Más opciones y activa una simetría.")
            activeTool != DrawingTool.BRUSH -> EditorTutorialGuide("tool_brush", "Selecciona el pincel.")
            else -> EditorTutorialGuide("canvas", "Dibuja a un lado del eje y observa su copia reflejada.")
        }
        StudioTutorialModule.UNDO_REDO -> when {
            missing(TutorialEvidence.HISTORY_STROKE) -> EditorTutorialGuide("canvas", "Dibuja un trazo largo y fácil de reconocer.")
            missing(TutorialEvidence.UNDO_VISUAL_CHANGE) -> EditorTutorialGuide("undo", "Pulsa Deshacer y comprueba que desaparece.")
            else -> EditorTutorialGuide("redo", "Pulsa Rehacer y comprueba que vuelve igual.")
        }
        StudioTutorialModule.SAVE_EXPORT -> EditorTutorialGuide("export_png", "Pulsa Exportar PNG; en el tutorial se genera solo una vista previa segura.")
        StudioTutorialModule.BRUSH_CUSTOMIZATION -> when {
            !brushesPanelActive -> EditorTutorialGuide("dock_brushes", "Abre el panel Pinceles.")
            missing(TutorialEvidence.BRUSH_PARAMETER_CHANGED) -> EditorTutorialGuide("brush_size", "Cambia el tamaño al menos un 15%.")
            else -> EditorTutorialGuide("canvas", "Dibuja otro trazo y compara visualmente el resultado.")
        }
    }
}

@Composable
internal fun EditorTutorialOverlay(
    session: EditorTutorialSession,
    focusRegistry: TutorialFocusRegistry,
    onFinish: () -> Unit,
    onExit: () -> Unit,
) {
    val state = session.state
    val guide = session.guide
    var minimized by remember { mutableStateOf(false) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(guide.target, guide.instruction) { minimized = false }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { focusRegistry.updateOverlay(it.boundsInRoot()) }
            .testTag("editor_tutorial_overlay"),
    ) {
        TutorialFocusOverlay(focusRegistry, guide.target)
        val placement = tutorialCardPlacement(
            focusRegistry.target,
            constraints.maxWidth.toFloat(),
            constraints.maxHeight.toFloat(),
        )
        if (minimized) {
            Surface(
                modifier = Modifier
                    .align(placement.alignment())
                    .padding(18.dp)
                    .clickable { minimized = false }
                    .testTag("editor_tutorial_restore"),
                color = Color(0xF21B2026),
                shape = RoundedCornerShape(14.dp),
                shadowElevation = 10.dp,
            ) {
                Text("Mostrar guía", modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp), color = StudioPalette.Accent)
            }
            return@BoxWithConstraints
        }
        val cardOffset = tutorialCardOffset(
            target = focusRegistry.target,
            viewport = IntSize(constraints.maxWidth, constraints.maxHeight),
            card = cardSize,
        )
        Surface(
            modifier = Modifier
                .offset { cardOffset }
                .widthIn(max = 620.dp)
                .heightIn(max = maxHeight - 36.dp)
                .onGloballyPositioned { cardSize = it.size }
                .semantics { liveRegion = LiveRegionMode.Polite; contentDescription = guide.instruction }
                .testTag("editor_tutorial_card"),
            color = Color(0xF21B2026),
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 12.dp,
        ) {
            Column(
                Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${state.modules.indexOf(state.current) + 1}/${state.modules.size} · ${state.current.title}", color = StudioPalette.Text, style = MaterialTheme.typography.titleMedium)
                        Text("Documento temporal · no se guarda", color = StudioPalette.TextMuted, style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = { minimized = true }) { Text("Minimizar") }
                    IconButton(onClick = onExit) { Icon(Icons.Outlined.Close, "Salir del tutorial", tint = StudioPalette.TextMuted) }
                }
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                Text("Acción guiada", color = StudioPalette.Accent, style = MaterialTheme.typography.labelMedium)
                Text(guide.instruction, color = StudioPalette.Text, style = MaterialTheme.typography.bodyLarge)
                Surface(
                    color = StudioPalette.SurfaceRaised,
                    shape = RoundedCornerShape(11.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp)) {
                        Text("Comprueba", color = StudioPalette.TextMuted, style = MaterialTheme.typography.labelMedium)
                        Text(guide.expectedOutcome, color = StudioPalette.Text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                val required = requiredEvidence(state.current)
                val verified = required.count(state.evidence::contains)
                Text("Resultados verificados: $verified/${required.size}", color = StudioPalette.Accent)
                if (state.currentComplete) {
                    Surface(color = StudioPalette.AccentSoft, shape = RoundedCornerShape(11.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CheckCircle, null, tint = StudioPalette.Accent)
                            Text(
                                state.confirmation ?: completionMessage(state.current),
                                modifier = Modifier.padding(start = 9.dp),
                                color = StudioPalette.Text,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { session.dispatch(StudioTutorialAction.ShowHint) },
                        modifier = Modifier.testTag("editor_tutorial_hint"),
                    ) {
                        Icon(Icons.Outlined.Lightbulb, null); Text(" Pista")
                    }
                    OutlinedButton(
                        onClick = { session.dispatch(StudioTutorialAction.RestartModule(state.current)) },
                        modifier = Modifier.testTag("editor_tutorial_restart"),
                    ) {
                        Icon(Icons.Outlined.Refresh, null); Text(" Repetir")
                    }
                    Spacer(Modifier.weight(1f))
                    if (state.currentComplete) {
                        Button(onClick = {
                            if (state.current == state.modules.last()) onFinish()
                            else session.dispatch(StudioTutorialAction.Next)
                        }, modifier = Modifier.testTag("editor_tutorial_continue")) {
                            Icon(Icons.Outlined.CheckCircle, null)
                            Text(if (state.current == state.modules.last()) " Finalizar" else " Continuar")
                        }
                    }
                }
                if (state.hintLevel > 0) {
                    Text(contextualTutorialHint(state, guide, state.hintLevel), color = StudioPalette.TextMuted)
                }
            }
        }
    }
}
