package com.orbyte.canvasstudio.ui.tutorial

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Stable
internal class TutorialFocusRegistry {
    private var expectedTarget by mutableStateOf<String?>(null)
    private var measuredTarget by mutableStateOf<String?>(null)
    private var targetInRoot by mutableStateOf<Rect?>(null)
    private var overlayInRoot by mutableStateOf<Rect?>(null)

    val target: Rect?
        get() {
            if (expectedTarget != measuredTarget) return null
            val target = targetInRoot ?: return null
            val overlay = overlayInRoot ?: return target
            return relativeFocusBounds(target, overlay)
        }

    fun expectTarget(id: String?) {
        if (expectedTarget == id) return
        expectedTarget = id
        measuredTarget = null
        targetInRoot = null
    }

    fun updateTarget(id: String, bounds: Rect) {
        if (expectedTarget != id) return
        measuredTarget = id
        targetInRoot = bounds
    }
    fun updateOverlay(bounds: Rect) { overlayInRoot = bounds }
}

internal fun relativeFocusBounds(targetInRoot: Rect, overlayInRoot: Rect): Rect = Rect(
    left = targetInRoot.left - overlayInRoot.left,
    top = targetInRoot.top - overlayInRoot.top,
    right = targetInRoot.right - overlayInRoot.left,
    bottom = targetInRoot.bottom - overlayInRoot.top,
)

@Composable
internal fun rememberTutorialFocusRegistry(): TutorialFocusRegistry = remember { TutorialFocusRegistry() }

internal fun Modifier.tutorialAnchor(
    registry: TutorialFocusRegistry,
    semanticId: String,
): Modifier = this
    .onGloballyPositioned { registry.updateTarget(semanticId, it.boundsInRoot()) }
    .semantics { contentDescription = "Objetivo tutorial: $semanticId" }
    .testTag("tutorial_anchor_$semanticId")

private val LocalTutorialFocusRegistry = compositionLocalOf<TutorialFocusRegistry?> { null }
private val LocalTutorialTarget = compositionLocalOf<String?> { null }

@Composable
internal fun EditorTutorialFocusProvider(
    registry: TutorialFocusRegistry,
    target: String?,
    content: @Composable () -> Unit,
) {
    SideEffect { registry.expectTarget(target) }
    CompositionLocalProvider(
        LocalTutorialFocusRegistry provides registry,
        LocalTutorialTarget provides target,
        content = content,
    )
}

internal fun Modifier.editorTutorialAnchor(id: String): Modifier = composed {
    val registry = LocalTutorialFocusRegistry.current
    val target = LocalTutorialTarget.current
    if (registry != null && target == id) tutorialAnchor(registry, id) else this
}

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
            .onGloballyPositioned { registry.updateOverlay(it.boundsInRoot()) }
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
