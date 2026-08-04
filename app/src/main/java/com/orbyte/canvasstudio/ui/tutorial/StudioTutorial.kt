package com.orbyte.canvasstudio.ui.tutorial

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orbyte.canvasstudio.model.StudioPalette
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.hypot

private val PracticeBackground = Color(0xFF171A20)
private val Paper = Color(0xFFF4F0E8)
private val Cyan = Color(0xFF58D7D1)
private val Coral = Color(0xFFFF776F)
private const val MIN_OBSERVATION_MS = 850L

private data class PracticeStroke(val points: List<Offset>, val width: Float, val color: Color)

@Composable
fun StudioTutorialHost(onFinish: () -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("canvas_studio_tutorial_progress", android.content.Context.MODE_PRIVATE) }
    var state by remember { mutableStateOf(StudioTutorialProgressStore.load(preferences)) }
    fun dispatch(action: StudioTutorialAction) { state = reduceStudioTutorial(state, action) }
    LaunchedEffect(state.current, state.progressByModule, state.track) { StudioTutorialProgressStore.save(preferences, state) }
    StudioTutorialContent(state, ::dispatch, onFinish, onExit)
}

@Composable
internal fun StudioTutorialContent(
    state: StudioTutorialState,
    onAction: (StudioTutorialAction) -> Unit,
    onFinish: () -> Unit,
    onExit: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(StudioPalette.Background).testTag("tutorial_root")) {
        val landscape = maxWidth > maxHeight
        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                ModuleRail(state, onAction, Modifier.width(292.dp).fillMaxHeight())
                LessonPane(state, onAction, onFinish, onExit, Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                CompactHeader(state, onAction)
                LessonPane(state, onAction, onFinish, onExit, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ModuleRail(state: StudioTutorialState, onAction: (StudioTutorialAction) -> Unit, modifier: Modifier) {
    Surface(modifier, color = StudioPalette.Surface) {
        LazyColumn(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Text("Aprende Canvas Studio", color = StudioPalette.Text, fontWeight = FontWeight.Bold)
                Text("Documento temporal: tus proyectos no se modifican", color = StudioPalette.TextMuted)
                TrackSelector(state, onAction)
                Spacer(Modifier.height(8.dp))
            }
            items(state.modules) { module -> ModuleButton(module, state, onAction) }
        }
    }
}

@Composable
private fun CompactHeader(state: StudioTutorialState, onAction: (StudioTutorialAction) -> Unit) {
    Surface(color = StudioPalette.Surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
            TrackSelector(state, onAction)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${state.modules.indexOf(state.current) + 1}/${state.modules.size} - ${state.current.title}", color = StudioPalette.Text, modifier = Modifier.weight(1f))
                IconButton(onClick = { onAction(StudioTutorialAction.RestartModule(state.current)) }) {
                    Icon(Icons.Outlined.Refresh, "Repetir leccion", tint = StudioPalette.TextMuted)
                }
            }
        }
    }
}

@Composable
private fun TrackSelector(state: StudioTutorialState, onAction: (StudioTutorialAction) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
        FilterChip(
            selected = state.track == TutorialTrack.QUICK_START,
            onClick = { onAction(StudioTutorialAction.SelectTrack(TutorialTrack.QUICK_START)) },
            label = { Text("Inicio rapido") },
            modifier = Modifier.testTag("track_quick"),
        )
        FilterChip(
            selected = state.track == TutorialTrack.FULL_COURSE,
            onClick = { onAction(StudioTutorialAction.SelectTrack(TutorialTrack.FULL_COURSE)) },
            label = { Text("Curso completo") },
            modifier = Modifier.testTag("track_full"),
        )
    }
}

@Composable
private fun ModuleButton(module: StudioTutorialModule, state: StudioTutorialState, onAction: (StudioTutorialAction) -> Unit) {
    val selected = module == state.current
    val status = state.progressByModule[module]?.status ?: TutorialProgressStatus.NOT_STARTED
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) StudioPalette.Accent.copy(alpha = .18f) else Color.Transparent, RoundedCornerShape(9.dp))
            .clickable(role = Role.Button) { onAction(StudioTutorialAction.Open(module)) }
            .semantics { contentDescription = "Leccion ${module.ordinal + 1}: ${module.title}, ${status.name}" }
            .testTag("module_${module.name}").padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (status == TutorialProgressStatus.COMPLETED) Icon(Icons.Outlined.CheckCircle, null, tint = StudioPalette.Accent, modifier = Modifier.size(18.dp))
        else Box(Modifier.size(18.dp).border(1.dp, if (status == TutorialProgressStatus.SKIPPED) Coral else StudioPalette.TextMuted, CircleShape))
        Spacer(Modifier.width(9.dp))
        Text(module.title, color = StudioPalette.Text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LessonPane(
    state: StudioTutorialState,
    onAction: (StudioTutorialAction) -> Unit,
    onFinish: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(state.current.title, color = StudioPalette.Text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(state.current.objective, color = StudioPalette.TextMuted)
                }
                IconButton(onClick = onExit) { Icon(Icons.Outlined.Close, "Cerrar tutorial", tint = StudioPalette.TextMuted) }
            }
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Progreso del tutorial" })
            EvidenceStrip(state)
            if (state.paused) PausedPanel(onAction, Modifier.weight(1f))
            else key(state.current, state.attemptId) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    val focus = rememberTutorialFocusRegistry()
                    val semanticTarget = state.current.name.lowercase()
                    PracticeLesson(state, onAction, Modifier.fillMaxSize().tutorialAnchor(focus, semanticTarget))
                    TutorialFocusOverlay(focus, semanticTarget)
                    if (state.demoVisible) DemoOverlay(state.current) { onAction(StudioTutorialAction.HideDemo) }
                }
            }
            if (state.hintLevel > 0) HintPanel(state.current, state.hintLevel)
            Footer(state, onAction, onFinish, onExit)
        }
        state.confirmation?.let { message -> CompletionDialog(message, state, onAction, onFinish, onExit) }
    }
}

@Composable
private fun EvidenceStrip(state: StudioTutorialState) {
    val required = requiredEvidence(state.current)
    Text(
        "Practica: ${state.evidence.count { it in required }}/${required.size} acciones verificadas" + if (state.practiceMode) " - modo libre" else "",
        color = if (state.currentComplete) Cyan else StudioPalette.TextMuted,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.semantics { contentDescription = "Progreso de la leccion ${state.evidence.size} de ${required.size}" },
    )
}

@Composable
private fun Footer(state: StudioTutorialState, onAction: (StudioTutorialAction) -> Unit, onFinish: () -> Unit, onExit: () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("tutorial_footer"), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { onAction(if (state.paused) StudioTutorialAction.Resume else StudioTutorialAction.Pause) }) {
            Icon(if (state.paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, null)
            Text(if (state.paused) " Reanudar" else " Pausar")
        }
        OutlinedButton(onClick = { onAction(StudioTutorialAction.ShowHint) }, modifier = Modifier.testTag("tutorial_hint")) {
            Icon(Icons.Outlined.Lightbulb, null); Text(" Pista")
        }
        OutlinedButton(onClick = { onAction(StudioTutorialAction.ShowDemo) }, modifier = Modifier.testTag("tutorial_demo")) { Text("Muestrame como") }
        OutlinedButton(onClick = { onAction(StudioTutorialAction.Skip) }, modifier = Modifier.testTag("tutorial_skip")) { Text("Omitir") }
        OutlinedButton(onClick = { onAction(StudioTutorialAction.Previous) }, enabled = state.modules.indexOf(state.current) > 0) { Text("Anterior") }
        Button(
            onClick = { if (state.current == state.modules.last()) onFinish() else onAction(StudioTutorialAction.Next) },
            enabled = state.currentComplete,
        ) { Text(if (state.current == state.modules.last()) "Finalizar" else "Continuar") }
    }
}

@Composable
private fun PausedPanel(onAction: (StudioTutorialAction) -> Unit, modifier: Modifier) {
    Surface(modifier.fillMaxWidth(), color = StudioPalette.Surface, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Tutorial en pausa", color = StudioPalette.Text, style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { onAction(StudioTutorialAction.Resume) }) { Text("Reanudar") }
        }
    }
}

@Composable
private fun CompletionDialog(
    message: String,
    state: StudioTutorialState,
    onAction: (StudioTutorialAction) -> Unit,
    onFinish: () -> Unit,
    onExit: () -> Unit,
) {
    var actionsVisible by remember(message) { mutableStateOf(false) }
    LaunchedEffect(message) { delay(MIN_OBSERVATION_MS); actionsVisible = true }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .62f)), contentAlignment = Alignment.Center) {
        Surface(
            color = StudioPalette.Surface,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(.68f).semantics { liveRegion = LiveRegionMode.Polite; contentDescription = message }.testTag("completion_confirmation"),
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.CheckCircle, null, tint = Cyan, modifier = Modifier.size(38.dp))
                Text(message, color = StudioPalette.Text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Observa el resultado: la leccion no avanzara hasta que tu decidas.", color = StudioPalette.TextMuted)
                if (actionsVisible) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        onAction(StudioTutorialAction.DismissConfirmation)
                        if (state.current == state.modules.last()) onFinish() else onAction(StudioTutorialAction.Next)
                    }, modifier = Modifier.testTag("completion_continue")) { Text("Continuar") }
                    OutlinedButton(onClick = { onAction(StudioTutorialAction.RestartModule(state.current)) }) { Text("Repetir") }
                    OutlinedButton(onClick = { onAction(StudioTutorialAction.DismissConfirmation); onAction(StudioTutorialAction.Practice) }) { Text("Practicar") }
                    OutlinedButton(onClick = onExit) { Text("Salir") }
                } else Text("Observando el resultado...", color = Cyan)
            }
        }
    }
}

@Composable
private fun HintPanel(module: StudioTutorialModule, level: Int) {
    val text = if (level == 1) "Pista: ${nextHint(module)}" else "Pista concreta: ${recoveryHint(module)}"
    Surface(color = Color(0xFF27313A), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(text, color = StudioPalette.Text, modifier = Modifier.padding(10.dp))
    }
}

private fun nextHint(module: StudioTutorialModule): String = when (module) {
    StudioTutorialModule.NAVIGATION -> "usa dos dedos y luego pulsa Restablecer vista."
    StudioTutorialModule.BRUSH_PEN -> "haz un trazo largo cambiando la presion."
    StudioTutorialModule.ERASER -> "arrastra sobre la figura y despues recupera la zona."
    StudioTutorialModule.COLOR_PICKER -> "elige una muestra distinta y dibuja con ella."
    StudioTutorialModule.LAYERS -> "sigue las acciones del panel; cada una cambia el lienzo."
    StudioTutorialModule.MASKS -> "crea la mascara, oculta y recupera parte de la figura."
    StudioTutorialModule.SELECTION -> "arrastra un rectangulo grande sobre el objeto."
    StudioTutorialModule.TRANSFORMATION -> "mueve el control y confirma el preview."
    StudioTutorialModule.SHAPES_FILL -> "crea el contorno antes de rellenar."
    StudioTutorialModule.GRADIENT -> "arrastra una distancia amplia entre los colores."
    StudioTutorialModule.SYMMETRY_GUIDES -> "activa el eje y despues dibuja a un lado."
    StudioTutorialModule.UNDO_REDO -> "dibuja, deshaz y recupera el mismo trazo."
    StudioTutorialModule.SAVE_EXPORT -> "elige un formato y genera la vista previa segura."
    StudioTutorialModule.BRUSH_CUSTOMIZATION -> "cambia bastante el tamano y compara los trazos."
}

private fun recoveryHint(module: StudioTutorialModule): String = "Reinicia solo esta leccion si el ejercicio quedo confuso. " + nextHint(module)

@Composable
private fun DemoOverlay(module: StudioTutorialModule, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .82f)).testTag("demo_overlay"), contentAlignment = Alignment.Center) {
        Surface(color = StudioPalette.Surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(.7f)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Demostracion: ${module.title}", color = StudioPalette.Text, fontWeight = FontWeight.Bold)
                Canvas(Modifier.fillMaxWidth().height(130.dp)) {
                    drawCircle(Cyan.copy(alpha = .3f), radius = size.minDimension * .28f, center = center)
                    drawLine(Coral, Offset(size.width * .2f, size.height * .7f), Offset(size.width * .8f, size.height * .3f), 14f, StrokeCap.Round)
                }
                Text("La demostracion no completa la leccion. Cierra e intentalo tu.", color = StudioPalette.TextMuted)
                Button(onClick = onClose) { Text("Ahora lo intento") }
            }
        }
    }
}

@Composable
private fun PracticeLesson(state: StudioTutorialState, onAction: (StudioTutorialAction) -> Unit, modifier: Modifier) {
    Surface(modifier, color = PracticeBackground, shape = RoundedCornerShape(16.dp)) {
        when (state.current) {
            StudioTutorialModule.NAVIGATION -> NavigationPractice(onAction)
            StudioTutorialModule.BRUSH_PEN -> BrushPractice(onAction)
            StudioTutorialModule.ERASER -> EraserPractice(onAction)
            StudioTutorialModule.COLOR_PICKER -> ColorPractice(onAction)
            StudioTutorialModule.LAYERS -> LayersPractice(onAction)
            StudioTutorialModule.MASKS -> MaskPractice(onAction)
            StudioTutorialModule.SELECTION -> SelectionPractice(onAction)
            StudioTutorialModule.TRANSFORMATION -> TransformPractice(onAction)
            StudioTutorialModule.SHAPES_FILL -> ShapeFillPractice(onAction)
            StudioTutorialModule.GRADIENT -> GradientPractice(onAction)
            StudioTutorialModule.SYMMETRY_GUIDES -> SymmetryPractice(onAction)
            StudioTutorialModule.UNDO_REDO -> HistoryPractice(onAction)
            StudioTutorialModule.SAVE_EXPORT -> ExportPractice(onAction)
            StudioTutorialModule.BRUSH_CUSTOMIZATION -> ParameterPractice(onAction)
        }
    }
}

@Composable
private fun NavigationPractice(onAction: (StudioTutorialAction) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }; var pan by remember { mutableStateOf(Offset.Zero) }; var rotation by remember { mutableFloatStateOf(0f) }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Zoom ${(scale * 100).toInt()}%  Desplazamiento ${hypot(pan.x, pan.y).toInt()} px  Rotacion ${rotation.toInt()} grados", color = Cyan)
            OutlinedButton(onClick = { scale = 1f; pan = Offset.Zero; rotation = 0f; onAction(StudioTutorialAction.Observe(StudioTutorialEvent.ViewReset)) }, modifier = Modifier.testTag("focus_navigation_reset")) { Text("Restablecer vista") }
        }
        Box(Modifier.fillMaxSize().pointerInput(Unit) {
            detectTransformGestures { _, panChange, zoom, rotationChange ->
                scale = (scale * zoom).coerceIn(.6f, 2.5f); pan += panChange; rotation += rotationChange
                onAction(StudioTutorialAction.Observe(StudioTutorialEvent.CanvasViewChanged(scale, hypot(pan.x, pan.y), rotation)))
            }
        }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.size((210 * scale).dp)) {
                drawRoundRect(Paper, cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f))
                drawCircle(Coral, size.minDimension * .16f, Offset(size.width * .35f, size.height * .42f))
                drawLine(Cyan, Offset(size.width * .2f, size.height * .72f), Offset(size.width * .8f, size.height * .3f), 12f, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun BrushPractice(onAction: (StudioTutorialAction) -> Unit) {
    val strokes = remember { mutableStateListOf<PracticeStroke>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }; var minP by remember { mutableFloatStateOf(1f) }; var maxP by remember { mutableFloatStateOf(0f) }; var length by remember { mutableFloatStateOf(0f) }; var tilt by remember { mutableFloatStateOf(0f) }
    Column(Modifier.fillMaxSize()) {
        Text("Presion ${(maxP * 100).toInt()}%  Inclinacion ${(tilt * 57.3f).toInt()} grados", color = Cyan, modifier = Modifier.padding(12.dp))
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Lienzo temporal con indicador de presion" }.testTag("practice_canvas").pointerInteropFilter { event ->
            val p = Offset(event.x, event.y); val pressure = event.pressure.coerceIn(.02f, 1f)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { current = listOf(p); minP = pressure; maxP = pressure; length = 0f; tilt = event.getAxisValue(MotionEvent.AXIS_TILT); true }
                MotionEvent.ACTION_MOVE -> { val last = current.lastOrNull(); if (last != null) length += hypot(p.x - last.x, p.y - last.y); current = current + p; minP = minOf(minP, pressure); maxP = maxOf(maxP, pressure); tilt = event.getAxisValue(MotionEvent.AXIS_TILT); true }
                MotionEvent.ACTION_UP -> { strokes += PracticeStroke(current + p, 3f + maxP * 24f, Cyan); onAction(StudioTutorialAction.Observe(StudioTutorialEvent.StrokeCommitted(length, maxP, minP, false, tilt))); current = emptyList(); true }
                else -> true
            }
        }) {
            drawLine(Color.White.copy(.12f), Offset(30f, size.height * .5f), Offset(size.width - 30f, size.height * .5f), 2f)
            (strokes + if (current.size > 1) listOf(PracticeStroke(current, 3f + maxP * 24f, Cyan)) else emptyList()).forEach { drawStroke(it) }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(stroke: PracticeStroke) {
    stroke.points.zipWithNext().forEach { (a, b) -> drawLine(stroke.color, a, b, stroke.width, StrokeCap.Round) }
}

@Composable
private fun EraserPractice(onAction: (StudioTutorialAction) -> Unit) {
    var erased by remember { mutableFloatStateOf(0f) }; var restored by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Arrastra el borrador sobre la franja coral. Luego recupera la zona.", color = StudioPalette.Text)
        Canvas(Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) { detectDragGestures { _, drag -> erased = (erased + hypot(drag.x, drag.y)).coerceAtMost(220f); restored = false; if (erased >= 52f) onAction(StudioTutorialAction.Observe(StudioTutorialEvent.StrokeCommitted(erased, .8f, .4f, true))) } }) {
            drawRoundRect(Coral, topLeft = Offset(size.width * .15f, size.height * .35f), size = androidx.compose.ui.geometry.Size(size.width * .7f, size.height * .3f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(30f))
            if (!restored && erased > 0f) drawCircle(PracticeBackground, radius = erased.coerceAtMost(size.height * .2f), center = center)
        }
        Button(onClick = { if (erased >= 52f) { restored = true; onAction(StudioTutorialAction.Observe(StudioTutorialEvent.ErasureRestored(512))) } }, enabled = erased >= 52f) { Text("Recuperar sin destruir") }
    }
}

@Composable
private fun ColorPractice(onAction: (StudioTutorialAction) -> Unit) {
    var active by remember { mutableStateOf(Color.White) }; var drawn by remember { mutableStateOf(false) }
    val swatches = listOf(Coral, Cyan, Color(0xFFFFD166), Color(0xFF8D7CFF))
    Column(Modifier.fillMaxSize().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { swatches.forEach { color -> Box(Modifier.size(54.dp).background(color, CircleShape).border(if (active == color) 4.dp else 1.dp, Color.White, CircleShape).clickable { val previous = active; active = color; onAction(StudioTutorialAction.Observe(StudioTutorialEvent.ColorSampled(color.value.toLong(), previous.value.toLong()))) }.semantics { contentDescription = "Muestra de color" }) } }
        Text("Color activo", color = StudioPalette.Text); Box(Modifier.size(34.dp).background(active, CircleShape))
        Canvas(Modifier.fillMaxWidth().weight(1f).pointerInput(active) { detectDragGestures(onDragStart = { drawn = true }, onDrag = { _, _ -> }, onDragEnd = { if (drawn && active != Color.White) onAction(StudioTutorialAction.Observe(StudioTutorialEvent.StrokeWithActiveColor(120f))) }) }) {
            drawLine(if (drawn) active else Color.White.copy(.15f), Offset(size.width * .2f, size.height * .65f), Offset(size.width * .8f, size.height * .35f), 24f, StrokeCap.Round)
        }
    }
}

@Composable
private fun LayersPractice(onAction: (StudioTutorialAction) -> Unit) {
    var created by remember { mutableStateOf(false) }; var drawn by remember { mutableStateOf(false) }; var visible by remember { mutableStateOf(true) }; var reordered by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { created = true; onAction(StudioTutorialAction.Observe(StudioTutorialEvent.LayerCreated("practice-layer"))) }) { Text("Nueva capa") }
            OutlinedButton(onClick = { visible = !visible; onAction(StudioTutorialAction.Observe(StudioTutorialEvent.LayerVisibilityChanged("practice-layer", visible, drawn))) }, enabled = drawn) { Text(if (visible) "Ocultar" else "Mostrar") }
            OutlinedButton(onClick = { reordered = !reordered; onAction(StudioTutorialAction.Observe(StudioTutorialEvent.LayerReordered("practice-layer", true))) }, enabled = created) { Text("Cambiar orden") }
        }
        Canvas(Modifier.fillMaxWidth().weight(1f).pointerInput(created) { detectDragGestures(onDragStart = { if (created) drawn = true }, onDrag = { _, _ -> }, onDragEnd = { if (created) onAction(StudioTutorialAction.Observe(StudioTutorialEvent.LayerStrokeCommitted("practice-layer", 130f))) }) }) {
            drawCircle(if (reordered) Cyan else Coral, size.minDimension * .23f, center)
            if (created && drawn && visible) drawLine(Color.White, Offset(size.width * .2f, size.height * .7f), Offset(size.width * .8f, size.height * .3f), 22f, StrokeCap.Round)
        }
        Text(if (!created) "Crea una capa vacia" else if (!drawn) "Capa activa y vacia: dibuja en el lienzo" else "Miniatura: capa de practica ${if (visible) "visible" else "oculta"}", color = Cyan)
    }
}

@Composable
private fun MaskPractice(onAction: (StudioTutorialAction) -> Unit) {
    var mask by remember { mutableStateOf(false) }; var hidden by remember { mutableStateOf(false) }; var restored by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { mask = true; onAction(StudioTutorialAction.Observe(StudioTutorialEvent.MaskCreated("practice-layer"))) }) { Text("Anadir mascara") }
            OutlinedButton(onClick = { restored = true; hidden = false; onAction(StudioTutorialAction.Observe(StudioTutorialEvent.MaskContentRestored(512f))) }, enabled = hidden) { Text("Pintar blanco: recuperar") }
        }
        Canvas(Modifier.fillMaxWidth().weight(1f).pointerInput(mask) { detectDragGestures(onDragEnd = { if (mask) { hidden = true; restored = false; onAction(StudioTutorialAction.Observe(StudioTutorialEvent.MaskContentChanged(700f))) } }, onDrag = { _, _ -> }) }) {
            drawCircle(Coral, size.minDimension * .28f, center)
            if (hidden && !restored) drawRect(PracticeBackground, Offset(center.x, center.y - size.minDimension * .3f), androidx.compose.ui.geometry.Size(size.width * .35f, size.minDimension * .6f))
        }
        Text(if (!mask) "Figura original intacta" else "Miniatura de mascara ${if (hidden) "con zona negra" else "blanca"}", color = Cyan)
    }
}

@Composable
private fun SelectionPractice(onAction: (StudioTutorialAction) -> Unit) {
    var start by remember { mutableStateOf(Offset.Unspecified) }; var end by remember { mutableStateOf(Offset.Unspecified) }
    Canvas(Modifier.fillMaxSize().pointerInput(Unit) { detectDragGestures(onDragStart = { start = it; end = it }, onDrag = { change, _ -> end = change.position }, onDragEnd = { if (start != Offset.Unspecified && end != Offset.Unspecified) onAction(StudioTutorialAction.Observe(StudioTutorialEvent.SelectionCreated(abs(end.x - start.x) * abs(end.y - start.y)))) }) }) {
        drawCircle(Coral, size.minDimension * .22f, center)
        if (start != Offset.Unspecified && end != Offset.Unspecified) drawRect(Cyan, topLeft = Offset(minOf(start.x, end.x), minOf(start.y, end.y)), size = androidx.compose.ui.geometry.Size(abs(end.x - start.x), abs(end.y - start.y)), style = Stroke(4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))))
    }
}

@Composable
private fun TransformPractice(onAction: (StudioTutorialAction) -> Unit) {
    var amount by remember { mutableFloatStateOf(0f) }; var committed by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("Preview: mover objeto ${amount.toInt()} px", color = Cyan)
        Slider(amount, { amount = it; committed = false; onAction(StudioTutorialAction.Observe(StudioTutorialEvent.TransformPreviewChanged(it, 0f, 0f))) }, valueRange = 0f..160f)
        Canvas(Modifier.fillMaxWidth().weight(1f)) { drawRoundRect(if (committed) Cyan else Coral.copy(.75f), Offset(size.width * .22f + amount, size.height * .32f), androidx.compose.ui.geometry.Size(150f, 150f), androidx.compose.ui.geometry.CornerRadius(22f)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { committed = true; onAction(StudioTutorialAction.Observe(StudioTutorialEvent.TransformCommitted(amount >= 24f))) }, enabled = amount >= 24f) { Text("Confirmar") }
            OutlinedButton(onClick = { amount = 0f; committed = false }) { Text("Cancelar preview") }
        }
    }
}

@Composable
private fun ShapeFillPractice(onAction: (StudioTutorialAction) -> Unit) {
    var shape by remember { mutableStateOf(false) }; var filled by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { shape = true; onAction(StudioTutorialAction.Observe(StudioTutorialEvent.ShapeCommitted(18_000f))) }) { Text("Trazar rectangulo") }
            Button(onClick = { filled = true; onAction(StudioTutorialAction.Observe(StudioTutorialEvent.FillCommitted(18_000))) }, enabled = shape) { Text("Rellenar region") }
        }
        Canvas(Modifier.fillMaxSize()) { if (shape) drawRoundRect(if (filled) Cyan else Color.Transparent, Offset(size.width * .22f, size.height * .25f), androidx.compose.ui.geometry.Size(size.width * .56f, size.height * .5f), androidx.compose.ui.geometry.CornerRadius(20f), style = if (filled) androidx.compose.ui.graphics.drawscope.Fill else Stroke(8f)) }
    }
}

@Composable
private fun GradientPractice(onAction: (StudioTutorialAction) -> Unit) {
    var start by remember { mutableStateOf(Offset.Unspecified) }; var end by remember { mutableStateOf(Offset.Unspecified) }
    Canvas(Modifier.fillMaxSize().pointerInput(Unit) { detectDragGestures(onDragStart = { start = it; end = it }, onDrag = { change, _ -> end = change.position }, onDragEnd = { if (start != Offset.Unspecified && end != Offset.Unspecified) onAction(StudioTutorialAction.Observe(StudioTutorialEvent.GradientCommitted(hypot(end.x - start.x, end.y - start.y), .8f))) }) }) {
        if (start != Offset.Unspecified && end != Offset.Unspecified) {
            drawLine(Coral, start, end, 72f, StrokeCap.Round); drawCircle(Color.White, 10f, start); drawCircle(Cyan, 10f, end)
        } else drawLine(Color.White.copy(.12f), Offset(size.width * .2f, size.height * .7f), Offset(size.width * .8f, size.height * .3f), 72f, StrokeCap.Round)
    }
}

@Composable
private fun SymmetryPractice(onAction: (StudioTutorialAction) -> Unit) {
    var enabled by remember { mutableStateOf(false) }; var stroke by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Button(onClick = { enabled = true; onAction(StudioTutorialAction.Observe(StudioTutorialEvent.SymmetryEnabled(true))) }) { Text("Activar simetria vertical") }
        Canvas(Modifier.fillMaxWidth().weight(1f).pointerInput(enabled) { detectDragGestures(onDragEnd = { if (enabled) { stroke = true; onAction(StudioTutorialAction.Observe(StudioTutorialEvent.SymmetricStrokeCommitted(120f, 2))) } }, onDrag = { _, _ -> }) }) {
            if (enabled) drawLine(Cyan, Offset(center.x, 0f), Offset(center.x, size.height), 3f)
            if (stroke) { drawArc(Coral, 210f, 120f, false, Offset(center.x - 170f, center.y - 100f), androidx.compose.ui.geometry.Size(140f, 220f), style = Stroke(18f)); drawArc(Coral, -30f, 120f, false, Offset(center.x + 30f, center.y - 100f), androidx.compose.ui.geometry.Size(140f, 220f), style = Stroke(18f)) }
        }
    }
}

@Composable
private fun HistoryPractice(onAction: (StudioTutorialAction) -> Unit) {
    var drawn by remember { mutableStateOf(false) }; var undone by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { undone = true; onAction(StudioTutorialAction.Observe(StudioTutorialEvent.UndoPerformed("tutorial-stroke", drawn))) }, enabled = drawn && !undone) { Text("Undo") }
            Button(onClick = { undone = false; onAction(StudioTutorialAction.Observe(StudioTutorialEvent.RedoPerformed("tutorial-stroke", drawn))) }, enabled = drawn && undone) { Text("Redo") }
        }
        Canvas(Modifier.fillMaxWidth().weight(1f).pointerInput(Unit) { detectDragGestures(onDragEnd = { drawn = true; undone = false; onAction(StudioTutorialAction.Observe(StudioTutorialEvent.HistoryStrokeCommitted("tutorial-stroke", 140f))) }, onDrag = { _, _ -> }) }) {
            drawCircle(Cyan.copy(.24f), size.minDimension * .22f, center)
            if (drawn && !undone) drawLine(Coral, Offset(size.width * .2f, size.height * .7f), Offset(size.width * .8f, size.height * .3f), 28f, StrokeCap.Round)
        }
    }
}

@Composable
private fun ExportPractice(onAction: (StudioTutorialAction) -> Unit) {
    var format by remember { mutableStateOf<String?>(null) }; var preview by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("El marco punteado indica el area que se exportara.", color = StudioPalette.Text)
        Canvas(Modifier.fillMaxWidth().weight(1f)) { drawRoundRect(Paper, Offset(size.width * .18f, size.height * .12f), androidx.compose.ui.geometry.Size(size.width * .64f, size.height * .76f), androidx.compose.ui.geometry.CornerRadius(16f)); drawCircle(Coral, size.minDimension * .18f, center); drawRect(Cyan.copy(.4f), Offset(size.width * .25f, size.height * .2f), androidx.compose.ui.geometry.Size(size.width * .5f, size.height * .6f), style = Stroke(4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("PNG", "Canvas Studio").forEach { f -> FilterChip(selected = format == f, onClick = { format = f; preview = false; onAction(StudioTutorialAction.Observe(StudioTutorialEvent.ExportFormatSelected(f))) }, label = { Text(f) }) } }
        Button(onClick = { preview = true; onAction(StudioTutorialAction.Observe(StudioTutorialEvent.ExportPreviewGenerated(format!!, 4096, 2732))) }, enabled = format != null) { Text("Generar vista previa segura") }
        if (preview) Text("Vista previa 4096 x 2732 - ${format}; no se escribio ningun archivo", color = Cyan, modifier = Modifier.testTag("export_preview"))
    }
}

@Composable
private fun ParameterPractice(onAction: (StudioTutorialAction) -> Unit) {
    var brushSize by remember { mutableFloatStateOf(.28f) }; var compared by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("Tamano ${(brushSize * 100).toInt()}% - el preview responde en tiempo real", color = StudioPalette.Text)
        Slider(brushSize, { val before = brushSize; brushSize = it; compared = false; onAction(StudioTutorialAction.Observe(StudioTutorialEvent.BrushParameterChanged("size", before, it))) })
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            drawLine(Color.White.copy(.35f), Offset(size.width * .15f, size.height * .35f), Offset(size.width * .85f, size.height * .35f), 10f, StrokeCap.Round)
            drawLine(Cyan, Offset(size.width * .15f, size.height * .65f), Offset(size.width * .85f, size.height * .65f), 4f + brushSize * 54f, StrokeCap.Round)
        }
        Button(onClick = { compared = true; onAction(StudioTutorialAction.Observe(StudioTutorialEvent.BrushComparisonCommitted(abs(brushSize - .28f)))) }, enabled = abs(brushSize - .28f) >= .15f) { Text("Comparar antes y despues") }
        if (compared) Text("El trazo inferior refleja el nuevo tamano; el superior conserva el original.", color = Cyan)
    }
}
