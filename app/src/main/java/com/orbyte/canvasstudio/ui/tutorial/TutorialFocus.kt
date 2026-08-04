package com.orbyte.canvasstudio.ui.tutorial

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Stable
internal class TutorialFocusRegistry {
    var target by mutableStateOf<Rect?>(null)
        private set

    fun update(bounds: Rect) { target = bounds }
}

@Composable
internal fun rememberTutorialFocusRegistry(): TutorialFocusRegistry = remember { TutorialFocusRegistry() }

internal fun Modifier.tutorialAnchor(
    registry: TutorialFocusRegistry,
    semanticId: String,
): Modifier = this
    .onGloballyPositioned { registry.update(it.boundsInParent()) }
    .semantics { contentDescription = "Objetivo tutorial: $semanticId" }
    .testTag("tutorial_anchor_$semanticId")

@Composable
internal fun TutorialFocusOverlay(registry: TutorialFocusRegistry, semanticId: String) {
    val bounds = registry.target ?: return
    val transition = rememberInfiniteTransition(label = "tutorialFocus")
    val alpha by transition.animateFloat(
        initialValue = .55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "focusPulse",
    )
    Canvas(
        Modifier
            .fillMaxSize()
            .testTag("tutorial_focus_$semanticId")
            .semantics { contentDescription = "Foco visual sobre $semanticId" },
    ) {
        val scrim = Color.Black.copy(alpha = .22f)
        drawRect(scrim, size = androidx.compose.ui.geometry.Size(size.width, bounds.top.coerceAtLeast(0f)))
        drawRect(scrim, topLeft = androidx.compose.ui.geometry.Offset(0f, bounds.bottom), size = androidx.compose.ui.geometry.Size(size.width, (size.height - bounds.bottom).coerceAtLeast(0f)))
        drawRect(scrim, topLeft = androidx.compose.ui.geometry.Offset(0f, bounds.top), size = androidx.compose.ui.geometry.Size(bounds.left.coerceAtLeast(0f), bounds.height))
        drawRect(scrim, topLeft = androidx.compose.ui.geometry.Offset(bounds.right, bounds.top), size = androidx.compose.ui.geometry.Size((size.width - bounds.right).coerceAtLeast(0f), bounds.height))
        drawRoundRect(
            color = Color(0xFF58D7D1).copy(alpha = alpha),
            topLeft = bounds.topLeft,
            size = bounds.size,
            cornerRadius = CornerRadius(24f, 24f),
            style = Stroke(width = 5f),
        )
    }
}
