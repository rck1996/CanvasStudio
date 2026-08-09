package com.orbyte.canvasstudio.ui.workspace

/**
 * Stable action identities for the editor's configurable radial quick menu.
 * Keeping this model independent from Compose makes availability and ordering
 * deterministic and easy to regression-test.
 */
internal enum class QuickAccessAction {
    BRUSH_LIBRARY,
    EYEDROPPER,
    ADD_LAYER,
    TOGGLE_LAYER_VISIBILITY,
    DUPLICATE_LAYER,
    TOGGLE_ALPHA_LOCK,
    MASK_WORKFLOW,
    OPEN_LAYERS,
}

internal enum class QuickAccessProfile(val label: String) {
    DRAWING("Dibujo"),
    COLOR("Color"),
    LAYERS("Capas"),
    CUSTOM("Personalizado"),
}

internal data class QuickMenuPosition(val x: Float, val y: Float)

internal data class QuickAccessContext(
    val hasActiveLayer: Boolean,
    val layerVisible: Boolean = true,
    val alphaLocked: Boolean = false,
    val hasMask: Boolean = false,
    val editingMask: Boolean = false,
)

internal data class QuickAccessItem(
    val action: QuickAccessAction,
    val enabled: Boolean,
    val selected: Boolean = false,
)

internal val defaultQuickAccessActions = listOf(
    QuickAccessAction.BRUSH_LIBRARY,
    QuickAccessAction.EYEDROPPER,
    QuickAccessAction.ADD_LAYER,
    QuickAccessAction.TOGGLE_LAYER_VISIBILITY,
    QuickAccessAction.MASK_WORKFLOW,
    QuickAccessAction.OPEN_LAYERS,
)

internal fun quickAccessActionsFor(profile: QuickAccessProfile): List<QuickAccessAction> = when (profile) {
    QuickAccessProfile.DRAWING -> defaultQuickAccessActions
    QuickAccessProfile.COLOR -> listOf(
        QuickAccessAction.BRUSH_LIBRARY,
        QuickAccessAction.EYEDROPPER,
        QuickAccessAction.TOGGLE_ALPHA_LOCK,
        QuickAccessAction.MASK_WORKFLOW,
        QuickAccessAction.DUPLICATE_LAYER,
        QuickAccessAction.OPEN_LAYERS,
    )
    QuickAccessProfile.LAYERS -> listOf(
        QuickAccessAction.ADD_LAYER,
        QuickAccessAction.DUPLICATE_LAYER,
        QuickAccessAction.TOGGLE_LAYER_VISIBILITY,
        QuickAccessAction.TOGGLE_ALPHA_LOCK,
        QuickAccessAction.MASK_WORKFLOW,
        QuickAccessAction.OPEN_LAYERS,
    )
    QuickAccessProfile.CUSTOM -> defaultQuickAccessActions
}

internal fun matchingQuickAccessProfile(actions: List<QuickAccessAction>): QuickAccessProfile =
    QuickAccessProfile.entries
        .filterNot { it == QuickAccessProfile.CUSTOM }
        .firstOrNull { quickAccessActionsFor(it) == actions }
        ?: QuickAccessProfile.CUSTOM

internal fun quickMenuTopLeft(
    anchorX: Float?,
    anchorY: Float?,
    viewportWidth: Float,
    viewportHeight: Float,
    wheelSize: Float,
): QuickMenuPosition {
    val maximumX = (viewportWidth - wheelSize).coerceAtLeast(0f)
    val maximumY = (viewportHeight - wheelSize).coerceAtLeast(0f)
    val centeredX = ((viewportWidth - wheelSize) / 2f).coerceAtLeast(0f)
    val centeredY = ((viewportHeight - wheelSize) / 2f).coerceAtLeast(0f)
    return QuickMenuPosition(
        x = if (viewportWidth >= wheelSize) ((anchorX ?: viewportWidth / 2f) - wheelSize / 2f).coerceIn(0f, maximumX) else centeredX,
        y = if (viewportHeight >= wheelSize) ((anchorY ?: viewportHeight / 2f) - wheelSize / 2f).coerceIn(0f, maximumY) else centeredY,
    )
}

internal fun normalizedQuickAccessActions(saved: List<String>): List<QuickAccessAction> {
    val parsed = saved.mapNotNull { name -> QuickAccessAction.entries.firstOrNull { it.name == name } }.distinct()
    return (parsed + defaultQuickAccessActions + QuickAccessAction.entries)
        .distinct()
        .take(defaultQuickAccessActions.size)
}

internal fun reassignQuickAccessAction(
    current: List<QuickAccessAction>,
    slot: Int,
    replacement: QuickAccessAction,
): List<QuickAccessAction> {
    if (slot !in current.indices) return current
    val updated = current.toMutableList()
    val previousSlot = updated.indexOf(replacement)
    if (previousSlot >= 0) updated[previousSlot] = updated[slot]
    updated[slot] = replacement
    return updated
}

internal fun quickAccessItems(
    context: QuickAccessContext,
    actions: List<QuickAccessAction> = defaultQuickAccessActions,
): List<QuickAccessItem> = actions.map { action ->
    when (action) {
        QuickAccessAction.BRUSH_LIBRARY,
        QuickAccessAction.EYEDROPPER,
        QuickAccessAction.ADD_LAYER,
        QuickAccessAction.OPEN_LAYERS -> QuickAccessItem(action, enabled = true)
        QuickAccessAction.TOGGLE_LAYER_VISIBILITY -> QuickAccessItem(
            action,
            enabled = context.hasActiveLayer,
            selected = context.hasActiveLayer && context.layerVisible,
        )
        QuickAccessAction.DUPLICATE_LAYER -> QuickAccessItem(action, enabled = context.hasActiveLayer)
        QuickAccessAction.TOGGLE_ALPHA_LOCK -> QuickAccessItem(
            action,
            enabled = context.hasActiveLayer,
            selected = context.hasActiveLayer && context.alphaLocked,
        )
        QuickAccessAction.MASK_WORKFLOW -> QuickAccessItem(
            action,
            enabled = context.hasActiveLayer,
            selected = context.hasActiveLayer && context.editingMask,
        )
    }
}
