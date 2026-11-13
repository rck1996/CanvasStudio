package com.orbyte.canvasstudio.ui.workspace

/**
 * The artwork takes priority at any width. The inspector opens on demand
 * and stays hidden in canvas mode.
 */
internal fun shouldShowInspector(
    expanded: Boolean,
    zenMode: Boolean,
): Boolean = expanded && !zenMode
