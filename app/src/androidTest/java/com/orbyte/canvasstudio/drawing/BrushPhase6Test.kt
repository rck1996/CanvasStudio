package com.orbyte.canvasstudio.drawing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrushPhase6Test {
    @Test
    fun pressureCurveSupportsSoftAndFirmResponses() {
        val raw = 0.5f
        assertTrue(calibratedPressure(raw, 0.5f) > raw)
        assertTrue(calibratedPressure(raw, 2f) < raw)
        assertEquals(1f, calibratedPressure(2f, 1f), 0.0001f)
    }

    @Test
    fun phaseSixIncludesExpandedBrushLibrary() {
        assertTrue(premiumBrushes.size >= 22)
        assertTrue(premiumBrushes.any { it.name == "Acuarela granulada" })
        assertTrue(premiumBrushes.any { it.name == "Plumilla G" })
    }
}
