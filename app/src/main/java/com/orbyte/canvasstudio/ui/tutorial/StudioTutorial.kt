package com.orbyte.canvasstudio.ui.tutorial

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orbyte.canvasstudio.model.StudioPalette
import kotlin.math.hypot

private data class PracticeStroke(val points: List<Offset>, val eraser: Boolean, val pressure: Float)

@Composable
fun StudioTutorialHost(onFinish: () -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences("canvas_studio_tutorial_progress", android.content.Context.MODE_PRIVATE)
    }
    var state by remember { mutableStateOf(StudioTutorialProgressStore.load(preferences)) }
    fun dispatch(action: StudioTutorialAction) {
        state = reduceStudioTutorial(state, action)
    }
    LaunchedEffect(state.current, state.completed) {
        StudioTutorialProgressStore.save(preferences, state)
    }
    StudioTutorial(
        state = state,
        onAction = ::dispatch,
        onFinish = {
            StudioTutorialProgressStore.save(preferences, state)
            onFinish()
        },
        onExit = onExit,
    )
}

@Composable
private fun StudioTutorial(
    state: StudioTutorialState,
    onAction: (StudioTutorialAction) -> Unit,
    onFinish: () -> Unit,
    onExit: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(StudioPalette.Background)) {
        val landscape = maxWidth > maxHeight
        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                ModuleRail(state, onAction, Modifier.width(286.dp).fillMaxHeight())
                LessonPane(state, onAction, onFinish, onExit, Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                CompactModuleStrip(state, onAction)
                LessonPane(state, onAction, onFinish, onExit, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ModuleRail(
    state: StudioTutorialState,
    onAction: (StudioTutorialAction) -> Unit,
    modifier: Modifier,
) {
    Surface(modifier, color = StudioPalette.Surface) {
        LazyColumn(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Text("Aprende Canvas Studio", color = StudioPalette.Text, fontWeight = FontWeight.Bold)
                Text("Documento temporal de práctica", color = StudioPalette.TextMuted)
                Spacer(Modifier.height(10.dp))
            }
            items(StudioTutorialModule.entries) { module ->
                ModuleButton(module, state, onAction)
            }
        }
    }
}

@Composable
private fun CompactModuleStrip(state: StudioTutorialState, onAction: (StudioTutorialAction) -> Unit) {
    Surface(color = StudioPalette.Surface) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${state.current.ordinal + 1}/${StudioTutorialModule.entries.size} · ${state.current.title}",
                color = StudioPalette.Text,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onAction(StudioTutorialAction.RestartModule(state.current)) }) {
                Icon(Icons.Outlined.Refresh, "Repetir lección", tint = StudioPalette.TextMuted)
            }
        }
    }
}

@Composable
private fun ModuleButton(
    module: StudioTutorialModule,
    state: StudioTutorialState,
    onAction: (StudioTutorialAction) -> Unit,
) {
    val selected = module == state.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) StudioPalette.Accent.copy(alpha = .18f) else Color.Transparent, RoundedCornerShape(9.dp))
            .clickable(role = Role.Button) { onAction(StudioTutorialAction.Open(module)) }
            .semantics { contentDescription = "Lección ${module.ordinal + 1}: ${module.title}" }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (module in state.completed) {
            Icon(Icons.Outlined.CheckCircle, null, tint = StudioPalette.Accent, modifier = Modifier.size(18.dp))
        } else {
            Box(Modifier.size(18.dp).border(1.dp, StudioPalette.TextMuted, CircleShape))
        }
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
    Column(modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(state.current.title, color = StudioPalette.Text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(state.current.objective, color = StudioPalette.TextMuted)
            }
            IconButton(onClick = onExit) { Icon(Icons.Outlined.Close, "Cerrar tutorial", tint = StudioPalette.TextMuted) }
        }
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Progreso del tutorial" },
        )
        if (state.paused) {
            Surface(color = StudioPalette.Surface, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("Tutorial en pausa", color = StudioPalette.Text, style = MaterialTheme.typography.headlineSmall)
                    Button(onClick = { onAction(StudioTutorialAction.Resume) }) {
                        Icon(Icons.Outlined.PlayArrow, null)
                        Text(" Reanudar")
                    }
                }
            }
        } else {
            PracticeLesson(state.current, onAction, Modifier.fillMaxWidth().weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { onAction(if (state.paused) StudioTutorialAction.Resume else StudioTutorialAction.Pause) }) {
                Icon(if (state.paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, null)
                Text(if (state.paused) " Reanudar" else " Pausar")
            }
            OutlinedButton(onClick = { onAction(StudioTutorialAction.RestartModule(state.current)) }) { Text("Repetir") }
            OutlinedButton(onClick = { onAction(StudioTutorialAction.Skip); onExit() }) { Text("Omitir") }
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = { onAction(StudioTutorialAction.Previous) },
                enabled = state.current != StudioTutorialModule.NAVIGATION,
            ) { Text("Anterior") }
            Button(
                onClick = {
                    if (state.current == StudioTutorialModule.entries.last()) onFinish()
                    else onAction(StudioTutorialAction.Next)
                },
                enabled = state.currentComplete,
            ) { Text(if (state.current == StudioTutorialModule.entries.last()) "Finalizar" else "Siguiente") }
        }
    }
}

@Composable
private fun PracticeLesson(
    module: StudioTutorialModule,
    onAction: (StudioTutorialAction) -> Unit,
    modifier: Modifier,
) {
    Surface(modifier, color = Color(0xFF171A20), shape = RoundedCornerShape(16.dp)) {
        when (module) {
            StudioTutorialModule.NAVIGATION -> NavigationPractice(onAction)
            StudioTutorialModule.BRUSH_PEN -> StrokePractice(eraser = false, onAction)
            StudioTutorialModule.ERASER -> StrokePractice(eraser = true, onAction)
            StudioTutorialModule.COLOR_PICKER -> ActionPractice("Mantén pulsado el círculo de color.", "Usar cuentagotas") {
                onAction(StudioTutorialAction.Observe(StudioTutorialEvent.ColorPicked))
            }
            StudioTutorialModule.LAYERS -> ActionPractice("Crea una capa dentro del documento temporal.", "Crear capa") {
                onAction(StudioTutorialAction.Observe(StudioTutorialEvent.LayerCreated))
            }
            StudioTutorialModule.MASKS -> ActionPractice("Añade una máscara raster a la capa temporal.", "Añadir máscara") {
                onAction(StudioTutorialAction.Observe(StudioTutorialEvent.MaskCreated))
            }
            StudioTutorialModule.SELECTION -> ActionPractice("Delimita una selección rectangular temporal.", "Crear selección") {
                onAction(StudioTutorialAction.Observe(StudioTutorialEvent.SelectionCommitted))
            }
            StudioTutorialModule.TRANSFORMATION -> ActionPractice("La selección temporal está preparada. Desplázala y confirma.", "Confirmar transformación") {
                onAction(StudioTutorialAction.Observe(StudioTutorialEvent.TransformCommitted))
            }
            StudioTutorialModule.SHAPES_FILL -> OrderedPractice(
                "Crea una forma y rellénala.", "Crear rectángulo", "Aplicar relleno",
                { onAction(StudioTutorialAction.Observe(StudioTutorialEvent.ShapeCommitted)) },
                { onAction(StudioTutorialAction.Observe(StudioTutorialEvent.FillCommitted)) },
            )
            StudioTutorialModule.GRADIENT -> ActionPractice("Arrastra los extremos del degradado temporal.", "Aplicar degradado") {
                onAction(StudioTutorialAction.Observe(StudioTutorialEvent.GradientCommitted))
            }
            StudioTutorialModule.SYMMETRY_GUIDES -> ActionPractice("Activa la simetría vertical de práctica.", "Activar simetría") {
                onAction(StudioTutorialAction.Observe(StudioTutorialEvent.SymmetryEnabled))
            }
            StudioTutorialModule.UNDO_REDO -> OrderedPractice(
                "Deshaz el último cambio y recupéralo.", "Undo", "Redo",
                { onAction(StudioTutorialAction.Observe(StudioTutorialEvent.UndoPerformed)) },
                { onAction(StudioTutorialAction.Observe(StudioTutorialEvent.RedoPerformed)) },
            )
            StudioTutorialModule.SAVE_EXPORT -> ActionPractice("Genera una exportación simulada sin escribir sobre tus proyectos.", "Probar exportación") {
                onAction(StudioTutorialAction.Observe(StudioTutorialEvent.ExportCompleted))
            }
            StudioTutorialModule.BRUSH_CUSTOMIZATION -> ParameterPractice(onAction)
        }
    }
}

@Composable
private fun NavigationPractice(onAction: (StudioTutorialAction) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectTransformGestures { _, panChange, zoom, _ ->
                scale = (scale * zoom).coerceIn(.6f, 2.5f)
                pan += panChange
                onAction(
                    StudioTutorialAction.Observe(
                        StudioTutorialEvent.CanvasZoomChanged(scale, hypot(pan.x, pan.y)),
                    ),
                )
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size((210 * scale).dp)
                .background(Color(0xFFF2EEE5), RoundedCornerShape(10.dp))
                .border(2.dp, StudioPalette.Accent, RoundedCornerShape(10.dp)),
        )
        Text("Pellizca y desplaza con dos dedos", color = StudioPalette.Text, modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp))
    }
}

@Composable
private fun StrokePractice(eraser: Boolean, onAction: (StudioTutorialAction) -> Unit) {
    val strokes = remember { mutableStateListOf<PracticeStroke>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var minimumPressure by remember { mutableFloatStateOf(1f) }
    var maximumPressure by remember { mutableFloatStateOf(0f) }
    Canvas(
        Modifier.fillMaxSize()
            .semantics {
                contentDescription = if (eraser) "Lienzo temporal para practicar borrador" else "Lienzo temporal sensible a presión"
            }
            .pointerInteropFilter { event ->
                val point = Offset(event.x, event.y)
                val pressure = event.pressure.coerceIn(.02f, 1f)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        current = listOf(point); minimumPressure = pressure; maximumPressure = pressure; true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        current = current + point
                        minimumPressure = minOf(minimumPressure, pressure)
                        maximumPressure = maxOf(maximumPressure, pressure)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (current.isNotEmpty()) strokes += PracticeStroke(current + point, eraser, maximumPressure)
                        onAction(
                            StudioTutorialAction.Observe(
                                StudioTutorialEvent.StrokeCommitted(maximumPressure, minimumPressure, eraser),
                            ),
                        )
                        current = emptyList(); true
                    }
                    else -> true
                }
            },
    ) {
        val all = strokes + if (current.isEmpty()) emptyList() else listOf(PracticeStroke(current, eraser, maximumPressure))
        all.filterNot { it.eraser }.forEach { stroke ->
            stroke.points.zipWithNext().forEach { (from, to) ->
                drawLine(StudioPalette.Accent, from, to, 3f + stroke.pressure * 18f, StrokeCap.Round)
            }
        }
        all.filter { it.eraser }.forEach { stroke ->
            stroke.points.zipWithNext().forEach { (from, to) ->
                drawLine(Color(0xFF171A20), from, to, 12f + stroke.pressure * 24f, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun ActionPractice(instruction: String, button: String, action: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(instruction, color = StudioPalette.Text)
        Spacer(Modifier.height(18.dp))
        Button(onClick = action, modifier = Modifier.semantics { contentDescription = button }) { Text(button) }
    }
}

@Composable
private fun OrderedPractice(
    instruction: String,
    firstLabel: String,
    secondLabel: String,
    first: () -> Unit,
    second: () -> Unit,
) {
    var firstDone by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(instruction, color = StudioPalette.Text)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { firstDone = true; first() }) { Text(firstLabel) }
            Button(onClick = second, enabled = firstDone) { Text(secondLabel) }
        }
    }
}

@Composable
private fun ParameterPractice(onAction: (StudioTutorialAction) -> Unit) {
    var size by remember { mutableFloatStateOf(.35f) }
    var flow by remember { mutableFloatStateOf(.65f) }
    var grain by remember { mutableFloatStateOf(.2f) }
    var changed by remember { mutableIntStateOf(0) }
    fun changed(value: Float, setter: (Float) -> Unit) {
        setter(value); changed += 1
        onAction(StudioTutorialAction.Observe(StudioTutorialEvent.BrushCustomized(changed)))
    }
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
        Text("Tamaño", color = StudioPalette.Text); Slider(size, { changed(it) { value -> size = value } })
        Text("Flujo", color = StudioPalette.Text); Slider(flow, { changed(it) { value -> flow = value } })
        Text("Grano", color = StudioPalette.Text); Slider(grain, { changed(it) { value -> grain = value } })
    }
}
