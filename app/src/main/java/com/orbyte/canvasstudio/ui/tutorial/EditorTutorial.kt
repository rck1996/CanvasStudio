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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.orbyte.canvasstudio.drawing.DrawingInteractionEvent
import com.orbyte.canvasstudio.drawing.DrawingTool
import com.orbyte.canvasstudio.model.StudioPalette
import kotlin.math.abs

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
    private var historyStrokeId: String? = null

    val guide: EditorTutorialGuide get() = guideFor(state, activeTool, layersPanelActive, brushesPanelActive, selectionActive)

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

internal data class EditorTutorialGuide(val target: String, val instruction: String)

internal enum class TutorialCardPlacement { TOP_CENTER, BOTTOM_CENTER, TOP_START, BOTTOM_START }

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
    StudioTutorialState(track = saved.track, current = StudioTutorialModule.NAVIGATION)

internal fun guideFor(
    state: StudioTutorialState,
    activeTool: DrawingTool,
    layersPanelActive: Boolean,
    brushesPanelActive: Boolean,
    selectionActive: Boolean = true,
): EditorTutorialGuide {
    val evidence = state.evidence
    fun missing(item: TutorialEvidence) = item !in evidence
    return when (state.current) {
        StudioTutorialModule.NAVIGATION -> when {
            missing(TutorialEvidence.ZOOMED) -> EditorTutorialGuide("canvas", "Pon dos dedos sobre el lienzo y separalos para hacer zoom.")
            missing(TutorialEvidence.PANNED) -> EditorTutorialGuide("canvas", "Sin levantar los dos dedos, desplaza el lienzo.")
            missing(TutorialEvidence.ROTATED) -> EditorTutorialGuide("canvas", "Gira los dos dedos hasta inclinar el lienzo.")
            else -> EditorTutorialGuide("view_reset", "Pulsa Restablecer vista para volver al encuadre inicial.")
        }
        StudioTutorialModule.BRUSH_PEN -> if (activeTool != DrawingTool.BRUSH) {
            EditorTutorialGuide("tool_brush", "Selecciona el pincel real.")
        } else EditorTutorialGuide("canvas", "Dibuja un trazo largo variando claramente la presion del S Pen.")
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
            !layersPanelActive -> EditorTutorialGuide("dock_layers", "Abre el panel Capas.")
            missing(TutorialEvidence.LAYER_CREATED) -> EditorTutorialGuide("layer_add", "Crea una capa nueva con el boton +.")
            missing(TutorialEvidence.LAYER_STROKE) -> EditorTutorialGuide("canvas", "Dibuja un trazo visible en la capa nueva.")
            missing(TutorialEvidence.LAYER_HIDDEN) -> EditorTutorialGuide("layer_visibility", "Oculta la capa activa y observa el cambio.")
            missing(TutorialEvidence.LAYER_SHOWN) -> EditorTutorialGuide("layer_visibility", "Vuelve a mostrar la capa.")
            else -> EditorTutorialGuide("layer_down", "Baja la capa una posicion y observa el orden.")
        }
        StudioTutorialModule.MASKS -> when {
            !layersPanelActive -> EditorTutorialGuide("dock_layers", "Abre el panel Capas.")
            missing(TutorialEvidence.MASK_CREATED) -> EditorTutorialGuide("mask_add", "Pulsa Ocultar sin borrar. La imagen original quedará intacta.")
            missing(TutorialEvidence.MASK_CHANGED) && activeTool != DrawingTool.BRUSH -> EditorTutorialGuide("tool_brush", "Elige Pincel: en este modo sirve para ocultar.")
            missing(TutorialEvidence.MASK_CHANGED) -> EditorTutorialGuide("canvas", "Pinta sobre una parte de la figura para ocultarla sin borrarla.")
            activeTool != DrawingTool.ERASER -> EditorTutorialGuide("tool_eraser", "Elige Borrador: en este modo sirve para recuperar.")
            else -> EditorTutorialGuide("canvas", "Pasa el borrador por la zona oculta para recuperarla.")
        }
        StudioTutorialModule.SELECTION -> if (activeTool != DrawingTool.SELECT_RECTANGLE) {
            EditorTutorialGuide("tool_select_rectangle", "Selecciona la herramienta Seleccion rectangular.")
        } else EditorTutorialGuide("canvas", "Arrastra un rectangulo amplio sobre el dibujo.")
        StudioTutorialModule.TRANSFORMATION -> when {
            !selectionActive && activeTool != DrawingTool.SELECT_RECTANGLE -> EditorTutorialGuide("tool_select_rectangle", "Primero selecciona Seleccion rectangular.")
            !selectionActive -> EditorTutorialGuide("canvas", "Crea una seleccion amplia sobre el dibujo.")
            activeTool != DrawingTool.TRANSFORM -> EditorTutorialGuide("tool_transform", "Con la seleccion activa, elige Transformar.")
            else -> EditorTutorialGuide("canvas", "Arrastra la seleccion; al soltar se confirmara el cambio real.")
        }
        StudioTutorialModule.SHAPES_FILL -> when {
            missing(TutorialEvidence.SHAPE_CREATED) && activeTool != DrawingTool.RECTANGLE -> EditorTutorialGuide("tool_rectangle", "Selecciona Rectangulo.")
            missing(TutorialEvidence.SHAPE_CREATED) -> EditorTutorialGuide("canvas", "Arrastra para crear un rectangulo grande.")
            activeTool != DrawingTool.FILL -> EditorTutorialGuide("tool_fill", "Selecciona Relleno.")
            else -> EditorTutorialGuide("canvas", "Toca el interior del rectangulo para rellenarlo.")
        }
        StudioTutorialModule.GRADIENT -> if (activeTool != DrawingTool.GRADIENT) {
            EditorTutorialGuide("tool_gradient", "Selecciona Degradado.")
        } else EditorTutorialGuide("canvas", "Arrastra una linea larga para definir direccion y longitud.")
        StudioTutorialModule.SYMMETRY_GUIDES -> when {
            missing(TutorialEvidence.SYMMETRY_GUIDE_VISIBLE) -> EditorTutorialGuide("more_menu", "Abre Mas opciones y activa una simetria.")
            activeTool != DrawingTool.BRUSH -> EditorTutorialGuide("tool_brush", "Selecciona el pincel.")
            else -> EditorTutorialGuide("canvas", "Dibuja a un lado del eje y observa su copia reflejada.")
        }
        StudioTutorialModule.UNDO_REDO -> when {
            missing(TutorialEvidence.HISTORY_STROKE) -> EditorTutorialGuide("canvas", "Dibuja un trazo largo y facil de reconocer.")
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
    LaunchedEffect(guide.target, guide.instruction) { minimized = false }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("editor_tutorial_overlay")) {
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
        Surface(
            modifier = Modifier.align(placement.alignment()).padding(18.dp).widthIn(max = 680.dp)
                .semantics { liveRegion = LiveRegionMode.Polite; contentDescription = guide.instruction }
                .testTag("editor_tutorial_card"),
            color = Color(0xF21B2026),
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 12.dp,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${state.modules.indexOf(state.current) + 1}/${state.modules.size} · ${state.current.title}", color = StudioPalette.Text, style = MaterialTheme.typography.titleMedium)
                        Text("Documento temporal · no se guarda", color = StudioPalette.TextMuted, style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = { minimized = true }) { Text("Minimizar") }
                    IconButton(onClick = onExit) { Icon(Icons.Outlined.Close, "Salir del tutorial", tint = StudioPalette.TextMuted) }
                }
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                Text(guide.instruction, color = StudioPalette.Text, style = MaterialTheme.typography.bodyLarge)
                Text("${state.evidence.size}/${requiredEvidence(state.current).size} acciones verificadas", color = StudioPalette.Accent)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { session.dispatch(StudioTutorialAction.ShowHint) }) {
                        Icon(Icons.Outlined.Lightbulb, null); Text(" Pista")
                    }
                    OutlinedButton(onClick = { session.dispatch(StudioTutorialAction.RestartModule(state.current)) }) {
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
                    Text(if (state.hintLevel == 1) nextHint(state.current) else recoveryHint(state.current), color = StudioPalette.TextMuted)
                }
            }
        }
    }
}
