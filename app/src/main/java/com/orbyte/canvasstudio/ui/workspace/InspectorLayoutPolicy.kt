package com.orbyte.canvasstudio.ui.workspace

/**
 * Tablet workspace policy: a narrow or portrait layout must prioritize the artwork.
 * A user can still open the inspector manually; zen mode always hides it.
 */
internal fun shouldShowInspector(
    compact: Boolean,
    expanded: Boolean,
    zenMode: Boolean,
): Boolean = !zenMode && (!compact || expanded)
