package com.orbyte.canvasstudio.ui.workspace

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectorLayoutPolicyTest {
    @Test
    fun portraitOpensCanvasFirstAndLetsTheUserShowLayers() {
        assertFalse(shouldShowInspector(compact = true, expanded = false, zenMode = false))
        assertTrue(shouldShowInspector(compact = true, expanded = true, zenMode = false))
    }

    @Test
    fun wideTabletsRetainDockAndZenAlwaysHidesIt() {
        assertTrue(shouldShowInspector(compact = false, expanded = false, zenMode = false))
        assertFalse(shouldShowInspector(compact = true, expanded = true, zenMode = true))
        assertFalse(shouldShowInspector(compact = false, expanded = true, zenMode = true))
    }
}
