package com.orbyte.canvasstudio.ui.workspace

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectorLayoutPolicyTest {
    @Test
    fun editorOpensCanvasFirstAndLetsTheUserShowLayers() {
        assertFalse(shouldShowInspector(expanded = false, zenMode = false))
        assertTrue(shouldShowInspector(expanded = true, zenMode = false))
    }

    @Test
    fun zenAlwaysHidesTheDock() {
        assertFalse(shouldShowInspector(expanded = true, zenMode = true))
    }
}
