@file:Suppress("LongMethod")

package com.orbyte.canvasstudio.ui.tutorial

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orbyte.canvasstudio.model.StudioPalette
import kotlin.math.sin

/**
 * Tutorial interactivo y desacoplado del editor.
 *
 * La pantalla propietaria de la navegación conserva el estado y decide qué hacer al salir o
 * finalizar. [state] puede venir de ViewModel/DataStore; [rememberBrushTutorialController] ofrece
 * persistencia ante rotación y recreación para integraciones simples.
 */
@Composable
fun ProfessionalBrushTutorial(
    state: BrushTutorialState,
    onAction: (BrushTutorialAction) -> Unit,
    onFinish: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(StudioPalette.Background),
    ) {
        val wide = maxWidth >= 960.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                TutorialRail(
                    state = state,
                    onAction = onAction,
                    modifier = Modifier.width(248.dp).fillMaxHeight(),
                )
                TutorialMain(
                    state = state,
                    onAction = onAction,
                    onFinish = onFinish,
                    onExit = onExit,
                    contentPadding = PaddingValues(horizontal = 42.dp, vertical = 28.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                CompactStepRail(state, onAction)
                TutorialMain(
                    state = state,
                    onAction = onAction,
                    onFinish = onFinish,
                    onExit = onExit,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun ProfessionalBrushTutorialHost(
    onFinish: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    controller: BrushTutorialController = rememberBrushTutorialController(),
) {
    ProfessionalBrushTutorial(
        state = controller.state,
        onAction = controller::dispatch,
        onFinish = onFinish,
        onExit = onExit,
        modifier = modifier,
    )
}

@Composable
private fun TutorialRail(
    state: BrushTutorialState,
    onAction: (BrushTutorialAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier, color = StudioPalette.Surface) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = StudioPalette.AccentSoft) {
                    Icon(
                        Icons.AutoMirrored.Outlined.MenuBook,
                        contentDescription = null,
                        tint = StudioPalette.Accent,
                        modifier = Modifier.padding(10.dp).size(24.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("CANVAS STUDIO", color = StudioPalette.Text, fontWeight = FontWeight.Bold)
                    Text("Academia de pinceles", color = StudioPalette.TextMuted, style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(34.dp))
            BrushTutorialStep.entries.forEach { step ->
                StepRailItem(
                    step = step,
                    selected = step == state.currentStep,
                    complete = step in state.completedSteps,
                    enabled = step.ordinal <= state.currentStep.ordinal || step in state.completedSteps,
                    onClick = { onAction(BrushTutorialAction.GoTo(step)) },
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Diseñado para tablet · S Pen",
                color = StudioPalette.TextMuted,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun StepRailItem(
    step: BrushTutorialStep,
    selected: Boolean,
    complete: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val color = when {
        selected -> StudioPalette.Accent
        enabled -> StudioPalette.Text
        else -> StudioPalette.TextMuted.copy(alpha = 0.45f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(
                if (selected) StudioPalette.AccentSoft else Color.Transparent,
                RoundedCornerShape(12.dp),
            )
            .clickable(enabled = enabled, role = Role.Tab, onClick = onClick)
            .semantics {
                stateDescription = when {
                    complete -> "Completado"
                    selected -> "Paso actual"
                    enabled -> "Disponible"
                    else -> "Bloqueado"
                }
            }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = if (selected || complete) StudioPalette.Accent else StudioPalette.SurfaceHover,
        ) {
            Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                if (complete) {
                    Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                } else {
                    Text("${step.ordinal + 1}", color = color, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(step.shortTitle, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun CompactStepRail(
    state: BrushTutorialState,
    onAction: (BrushTutorialAction) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(StudioPalette.Surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BrushTutorialStep.entries.forEach { step ->
            val enabled = step.ordinal <= state.currentStep.ordinal || step in state.completedSteps
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (step == state.currentStep) StudioPalette.AccentSoft else StudioPalette.SurfaceRaised,
                modifier = Modifier.clickable(enabled = enabled) {
                    onAction(BrushTutorialAction.GoTo(step))
                },
            ) {
                Text(
                    "${step.ordinal + 1}  ${step.shortTitle}",
                    color = if (enabled) StudioPalette.Text else StudioPalette.TextMuted.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun TutorialMain(
    state: BrushTutorialState,
    onAction: (BrushTutorialAction) -> Unit,
    onFinish: () -> Unit,
    onExit: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(contentPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    state.currentStep.eyebrow,
                    color = StudioPalette.Accent,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    state.currentStep.title,
                    color = StudioPalette.Text,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
            }
            IconButton(onClick = onExit, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Close, "Cerrar tutorial", tint = StudioPalette.TextMuted)
            }
        }
        Spacer(Modifier.height(14.dp))
        val animatedProgress by animateFloatAsState(state.progress, label = "tutorialProgress")
        LinearProgressIndicator(
            progress = { animatedProgress },
            color = StudioPalette.Accent,
            trackColor = StudioPalette.SurfaceHover,
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(animatedProgress, 0f..1f)
                    contentDescription = "Progreso del tutorial"
                },
        )
        Spacer(Modifier.height(24.dp))
        AnimatedContent(
            targetState = state.currentStep,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "tutorialStep",
        ) { step ->
            TutorialStepContent(step, state, onAction)
        }
        Spacer(Modifier.height(18.dp))
        TutorialFooter(state, onAction, onFinish)
    }
}

@Composable
private fun TutorialStepContent(
    step: BrushTutorialStep,
    state: BrushTutorialState,
    onAction: (BrushTutorialAction) -> Unit,
) {
    when (step) {
        BrushTutorialStep.WELCOME -> WelcomeLesson()
        BrushTutorialStep.LIBRARY -> LibraryLesson(state, onAction)
        BrushTutorialStep.PRESSURE_TILT -> PressureTiltLesson(state, onAction)
        BrushTutorialStep.LIVE_PREVIEW -> PreviewLesson(state, onAction)
        BrushTutorialStep.PARAMETERS -> ParametersLesson(state, onAction)
        BrushTutorialStep.GESTURES -> GesturesLesson(state, onAction)
        BrushTutorialStep.PRACTICE -> PracticeLesson(state, onAction)
    }
}

@Composable
private fun WelcomeLesson() {
    Row(
        Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(0.9f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "Un pincel profesional no es solo una punta.",
                color = StudioPalette.Text,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "Aprenderás a combinar forma, grano, presión, inclinación y opacidad. " +
                    "Cada cambio aparecerá de inmediato en el preview antes de tocar el lienzo.",
                color = StudioPalette.TextMuted,
                style = MaterialTheme.typography.bodyLarge,
            )
            LessonCallout(
                Icons.Outlined.AutoAwesome,
                "7 lecciones · práctica incluida",
                "El progreso se guarda al rotar la tablet o cambiar de aplicación.",
            )
        }
        BrushHero(Modifier.weight(1.1f).aspectRatio(1.45f))
    }
}

@Composable
private fun LibraryLesson(
    state: BrushTutorialState,
    onAction: (BrushTutorialAction) -> Unit,
) {
    val brushes = listOf(
        Triple("Lápiz HB", "Grafito claro y gradual", 0.22f),
        Triple("Lápiz 6B", "Grafito oscuro y blando", 0.38f),
        Triple("Tinta técnica", "Línea uniforme", 0.08f),
        Triple("Plumilla cómic", "Línea expresiva", 0.32f),
    )
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Biblioteca profesional", color = StudioPalette.Text, style = MaterialTheme.typography.titleMedium)
            brushes.forEach { (name, category, weight) ->
                BrushChoice(
                    name = name,
                    category = category,
                    weight = weight,
                    selected = state.selectedBrush == name,
                    onClick = { onAction(BrushTutorialAction.SelectBrush(name)) },
                )
            }
            Text(
                "HB construye tono; 6B deposita más grafito. La tinta técnica mantiene el ancho; " +
                    "la plumilla lo cambia con presión. Elige por tarea, no por cantidad.",
                color = StudioPalette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        PreviewCard(state, Modifier.weight(1.15f).fillMaxHeight())
    }
}

@Composable
private fun PressureTiltLesson(
    state: BrushTutorialState,
    onAction: (BrushTutorialAction) -> Unit,
) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(0.9f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(
                "La presión define energía; la inclinación define superficie.",
                color = StudioPalette.Text,
                style = MaterialTheme.typography.bodyLarge,
            )
            TutorialSlider(
                label = "Presión",
                value = state.pressure,
                valueLabel = "${(state.pressure * 100).toInt()}%",
                description = "Desliza para simular cuánto presionas la punta",
                onValueChange = { onAction(BrushTutorialAction.ChangePressure(it)) },
            )
            TutorialSlider(
                label = "Inclinación",
                value = state.tilt,
                valueLabel = "${(state.tilt * 60).toInt()}°",
                description = "Desliza para simular el ángulo del S Pen",
                onValueChange = { onAction(BrushTutorialAction.ChangeTilt(it)) },
            )
            LessonCallout(
                Icons.Outlined.Brush,
                "Consejo de artista",
                "Presión controla depósito y tamaño. Inclina HB o 6B para sombrear con el lateral; " +
                    "el portaminas se mantiene fino.",
            )
        }
        DynamicStrokeCard(state, Modifier.weight(1.1f).fillMaxHeight())
    }
}

@Composable
private fun PreviewLesson(
    state: BrushTutorialState,
    onAction: (BrushTutorialAction) -> Unit,
) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        PreviewCard(state, Modifier.weight(1.2f).fillMaxHeight())
        Column(Modifier.weight(0.8f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(
                "El preview usa la misma punta, grano, presión, tilt y acumulación que el lienzo. " +
                    "Prueba dos cambios.",
                color = StudioPalette.TextMuted,
                style = MaterialTheme.typography.bodyLarge,
            )
            TutorialSlider(
                "Tamaño",
                state.size,
                "${(state.size * 100).toInt()} px",
                "Cambiar tamaño del preview",
            ) { onAction(BrushTutorialAction.ChangeSize(it)) }
            TutorialSlider(
                "Opacidad",
                state.opacity,
                "${(state.opacity * 100).toInt()}%",
                "Cambiar opacidad del preview",
            ) { onAction(BrushTutorialAction.ChangeOpacity(it)) }
        }
    }
}

@Composable
private fun ParametersLesson(
    state: BrushTutorialState,
    onAction: (BrushTutorialAction) -> Unit,
) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(0.9f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ParameterGroup("FORMA", "Tamaño", state.size, "${(state.size * 100).toInt()} px") {
                onAction(BrushTutorialAction.ChangeSize(it))
            }
            ParameterGroup("PINTURA", "Opacidad", state.opacity, "${(state.opacity * 100).toInt()}%") {
                onAction(BrushTutorialAction.ChangeOpacity(it))
            }
            ParameterGroup("MATERIAL", "Profundidad de grano", state.grain, "${(state.grain * 100).toInt()}%") {
                onAction(BrushTutorialAction.ChangeGrain(it))
            }
        }
        PreviewCard(state, Modifier.weight(1.1f).fillMaxHeight())
    }
}

@Composable
private fun GesturesLesson(
    state: BrushTutorialState,
    onAction: (BrushTutorialAction) -> Unit,
) {
    Row(
        Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GesturePad(
            complete = state.gesturePracticed,
            onComplete = { onAction(BrushTutorialAction.CompleteGesture) },
            modifier = Modifier.weight(1.2f).aspectRatio(1.55f),
        )
        Column(Modifier.weight(0.8f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            LessonCallout(Icons.Outlined.Gesture, "Dos dedos", "Arrastra dentro del panel para mover el lienzo.")
            Text(
                "Pellizca para zoom · gira para rotar · dos dedos para navegar. " +
                    "El S Pen queda reservado para dibujar.",
                color = StudioPalette.TextMuted,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun PracticeLesson(
    state: BrushTutorialState,
    onAction: (BrushTutorialAction) -> Unit,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Técnica para detalle · plumilla para lineart · marcador orientado para masas · " +
                    "HB/6B inclinado para sombrear",
                color = StudioPalette.TextMuted,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { onAction(BrushTutorialAction.ResetPractice) },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Outlined.RestartAlt, "Limpiar práctica", tint = StudioPalette.TextMuted)
            }
        }
        PracticeCanvas(
            state = state,
            onStrokeComplete = { onAction(BrushTutorialAction.CompletePracticeStroke) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

@Composable
private fun TutorialFooter(
    state: BrushTutorialState,
    onAction: (BrushTutorialAction) -> Unit,
    onFinish: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = { onAction(BrushTutorialAction.Previous) },
            enabled = state.currentStep != BrushTutorialStep.WELCOME,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
            Spacer(Modifier.width(8.dp))
            Text("Anterior")
        }
        Spacer(Modifier.width(18.dp))
        AnimatedVisibility(
            visible = !state.canContinue,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.weight(1f),
        ) {
            Text(state.instruction, color = StudioPalette.TextMuted, style = MaterialTheme.typography.bodyMedium)
        }
        if (state.canContinue) Spacer(Modifier.weight(1f))
        Button(
            onClick = {
                if (state.currentStep == BrushTutorialStep.PRACTICE) onFinish()
                else onAction(BrushTutorialAction.Next)
            },
            enabled = state.canContinue,
            colors = ButtonDefaults.buttonColors(
                containerColor = StudioPalette.Accent,
                disabledContainerColor = StudioPalette.SurfaceHover,
            ),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
        ) {
            Text(if (state.currentStep == BrushTutorialStep.PRACTICE) "Terminar" else "Siguiente")
            Spacer(Modifier.width(8.dp))
            Icon(
                if (state.currentStep == BrushTutorialStep.PRACTICE) Icons.Outlined.Check
                else Icons.AutoMirrored.Outlined.ArrowForward,
                null,
            )
        }
    }
}

@Composable
private fun BrushChoice(
    name: String,
    category: String,
    weight: Float,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) StudioPalette.AccentSoft else StudioPalette.Surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) StudioPalette.Accent else StudioPalette.Border,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                stateDescription = if (selected) "Seleccionado" else "No seleccionado"
                contentDescription = "Pincel $name, categoría $category"
            },
    ) {
        Row(
            Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.width(112.dp)) {
                Text(name, color = StudioPalette.Text, style = MaterialTheme.typography.labelLarge)
                Text(category, color = StudioPalette.TextMuted, style = MaterialTheme.typography.labelMedium)
            }
            Canvas(Modifier.weight(1f).height(34.dp)) {
                val path = Path().apply {
                    moveTo(8f, size.height * 0.62f)
                    cubicTo(
                        size.width * 0.28f, size.height * 0.15f,
                        size.width * 0.58f, size.height * 0.85f,
                        size.width - 8f, size.height * 0.35f,
                    )
                }
                drawPath(
                    path,
                    color = if (selected) StudioPalette.Text else StudioPalette.TextMuted,
                    style = Stroke(width = 2f + weight * 14f, cap = StrokeCap.Round),
                )
            }
            if (selected) Icon(Icons.Outlined.Check, null, tint = StudioPalette.Accent)
        }
    }
}

@Composable
private fun PreviewCard(
    state: BrushTutorialState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFF0ECE4),
    ) {
        Box(Modifier.fillMaxSize().padding(20.dp)) {
            Text(
                state.selectedBrush ?: "Preview del pincel",
                color = Color(0xFF55504A),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.TopStart),
            )
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .align(Alignment.Center)
                    .semantics {
                        contentDescription =
                            "Vista previa reactiva, tamaño ${(state.size * 100).toInt()}, " +
                            "opacidad ${(state.opacity * 100).toInt()}"
                    },
            ) {
                val path = Path().apply {
                    moveTo(size.width * 0.05f, size.height * 0.62f)
                    for (i in 1..24) {
                        val x = size.width * (0.05f + i / 27f)
                        val y = size.height * (0.52f + sin(i * 0.58f) * 0.20f)
                        lineTo(x, y)
                    }
                }
                val baseWidth = 5f + state.size * 42f
                drawPath(
                    path,
                    color = Color(0xFF27231F).copy(alpha = state.opacity),
                    style = Stroke(baseWidth, cap = StrokeCap.Round),
                )
                if (state.grain > 0.05f) {
                    val dots = (18 + state.grain * 42).toInt()
                    repeat(dots) { index ->
                        val x = size.width * (0.08f + ((index * 37) % 83) / 100f)
                        val y = size.height * (0.28f + ((index * 19) % 48) / 100f)
                        drawCircle(
                            Color.White.copy(alpha = state.grain * 0.42f),
                            radius = 1f + state.grain * 2.5f,
                            center = Offset(x, y),
                        )
                    }
                }
            }
            Text(
                "Los ajustes se reflejan aquí al instante.",
                color = Color(0xFF6F6860),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}

@Composable
private fun DynamicStrokeCard(
    state: BrushTutorialState,
    modifier: Modifier = Modifier,
) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = StudioPalette.Surface) {
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(24.dp)
                .semantics {
                    contentDescription = "Simulación de respuesta a presión e inclinación"
                },
        ) {
            val width = 5f + state.pressure * 42f + state.tilt * 22f
            val path = Path().apply {
                moveTo(size.width * 0.08f, size.height * 0.68f)
                cubicTo(
                    size.width * 0.30f, size.height * 0.18f,
                    size.width * 0.62f, size.height * 0.82f,
                    size.width * 0.92f, size.height * 0.34f,
                )
            }
            drawPath(
                path,
                StudioPalette.Text.copy(alpha = 0.45f + state.pressure * 0.55f),
                style = Stroke(width, cap = StrokeCap.Round),
            )
            drawCircle(
                StudioPalette.Accent.copy(alpha = 0.8f),
                radius = 9f + state.tilt * 13f,
                center = Offset(size.width * 0.92f, size.height * 0.34f),
            )
        }
    }
}

@Composable
private fun TutorialSlider(
    label: String,
    value: Float,
    valueLabel: String,
    description: String,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row {
            Text(label, color = StudioPalette.Text, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            Text(valueLabel, color = StudioPalette.Accent, style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = description
                    stateDescription = valueLabel
                },
        )
    }
}

@Composable
private fun ParameterGroup(
    eyebrow: String,
    label: String,
    value: Float,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
) {
    Surface(shape = RoundedCornerShape(14.dp), color = StudioPalette.Surface) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(eyebrow, color = StudioPalette.Accent, style = MaterialTheme.typography.labelMedium)
            TutorialSlider(label, value, valueLabel, "Ajustar $label", onValueChange)
        }
    }
}

@Composable
private fun LessonCallout(
    icon: ImageVector,
    title: String,
    body: String,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = StudioPalette.SurfaceRaised,
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioPalette.Border),
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = StudioPalette.Accent, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = StudioPalette.Text, style = MaterialTheme.typography.labelLarge)
                Text(body, color = StudioPalette.TextMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun BrushHero(modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(24.dp), color = StudioPalette.Surface) {
        Canvas(Modifier.fillMaxSize().padding(30.dp)) {
            repeat(3) { row ->
                val path = Path().apply {
                    moveTo(0f, size.height * (0.26f + row * 0.24f))
                    cubicTo(
                        size.width * 0.25f, size.height * (0.08f + row * 0.25f),
                        size.width * 0.65f, size.height * (0.45f + row * 0.17f),
                        size.width, size.height * (0.22f + row * 0.24f),
                    )
                }
                drawPath(
                    path,
                    listOf(StudioPalette.Accent, StudioPalette.Success, StudioPalette.Warning)[row],
                    style = Stroke(7f + row * 7f, cap = StrokeCap.Round),
                )
            }
        }
    }
}

@Composable
private fun GesturePad(
    complete: Boolean,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .pointerInput(Unit) {
                var distance = 0f
                detectDragGestures(
                    onDrag = { change, drag ->
                        change.consume()
                        distance += drag.getDistance()
                        if (distance > 90f) onComplete()
                    },
                )
            }
            .semantics {
                contentDescription = "Área para practicar navegación con dos dedos"
                stateDescription = if (complete) "Gesto completado" else "Gesto pendiente"
            },
        shape = RoundedCornerShape(22.dp),
        color = StudioPalette.Surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (complete) StudioPalette.Success else StudioPalette.Border,
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val gridColor = StudioPalette.Border.copy(alpha = 0.45f)
                for (x in 0..size.width.toInt() step 48) {
                    drawLine(gridColor, Offset(x.toFloat(), 0f), Offset(x.toFloat(), size.height), 1f)
                }
                for (y in 0..size.height.toInt() step 48) {
                    drawLine(gridColor, Offset(0f, y.toFloat()), Offset(size.width, y.toFloat()), 1f)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (complete) Icons.Outlined.Check else Icons.Outlined.Gesture,
                    null,
                    tint = if (complete) StudioPalette.Success else StudioPalette.Accent,
                    modifier = Modifier.size(42.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (complete) "¡Lienzo desplazado!" else "Arrastra para simular dos dedos",
                    color = StudioPalette.Text,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun PracticeCanvas(
    state: BrushTutorialState,
    onStrokeComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paths = remember { mutableStateListOf<List<Offset>>() }
    LaunchedEffect(state.practiceStrokeCount) {
        if (state.practiceStrokeCount == 0) paths.clear()
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFF0ECE4),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(state.practiceStrokeCount) {
                    var active = mutableListOf<Offset>()
                    detectDragGestures(
                        onDragStart = { start -> active = mutableListOf(start) },
                        onDrag = { change, _ ->
                            change.consume()
                            active.add(change.position)
                        },
                        onDragEnd = {
                            if (active.size >= 4) {
                                paths.add(active.toList())
                                onStrokeComplete()
                            }
                        },
                    )
                }
                .semantics {
                    contentDescription = "Lienzo de práctica guiada"
                    stateDescription = "${state.practiceStrokeCount} de 3 trazos completados"
                },
        ) {
            paths.forEachIndexed { index, points ->
                if (points.size > 1) {
                    val path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(
                        path,
                        color = Color(0xFF26221F).copy(alpha = state.opacity),
                        style = Stroke(
                            width = 4f + state.size * 18f + index * 5f,
                            cap = StrokeCap.Round,
                        ),
                    )
                }
            }
            if (paths.isEmpty()) {
                val yValues = listOf(0.28f, 0.5f, 0.72f)
                yValues.forEachIndexed { index, y ->
                    drawLine(
                        Color(0xFF8D857C).copy(alpha = 0.28f),
                        Offset(size.width * 0.12f, size.height * y),
                        Offset(size.width * 0.88f, size.height * y),
                        strokeWidth = 2f + index * 2f,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}
