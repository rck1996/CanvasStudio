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
    fun productionLibraryIsSmallAndExperimentalMediaAreSeparated() {
        assertTrue(premiumBrushes.size in 10..16)
        assertTrue(premiumBrushes.none { it.kind == BrushKind.WATERCOLOR || it.kind == BrushKind.OIL })
        assertTrue(experimentalBrushes.any { it.name == "Acuarela granulada" })
        assertTrue(experimentalBrushes.all { it.category == "Experimental" })
    }

    @Test
    fun largeStampBrushesUseAdaptiveInputSampling() {
        val small = BrushSettings(sizePx = 24f, spacing = .1f, kind = BrushKind.CHALK)
        val large = BrushSettings(sizePx = 180f, spacing = .13f, kind = BrushKind.CHALK)
        assertTrue(inputSamplingDistance(large, true) > inputSamplingDistance(small, true) * 5f)
        assertTrue(inputSamplingDistance(large, true) <= 28f)
    }

    @Test
    fun materialBrushNamesUseDedicatedRenderingFamilies() {
        val expected = mapOf(
            "Pincel seco" to BrushKind.DRY_BRUSH,
            "Pincel de cerdas" to BrushKind.BRISTLE,
            "Acuarela granulada" to BrushKind.WATERCOLOR,
            "Óleo espeso" to BrushKind.OIL,
        )
        expected.forEach { (name, kind) ->
            assertEquals(kind, (premiumBrushes + experimentalBrushes).single { it.name == name }.kind)
        }
        assertEquals(expected.size, expected.values.distinct().size)
    }

    @Test
    fun platformPreviewIsLimitedToVisuallyCompatibleBrushFamilies() {
        assertTrue(platformInkPreviewCompatible(BrushKind.MARKER))
        assertTrue(platformInkPreviewCompatible(BrushKind.PAINT))
        assertTrue(platformInkPreviewCompatible(BrushKind.OIL))
        assertTrue(!platformInkPreviewCompatible(BrushKind.AIRBRUSH))
        assertTrue(!platformInkPreviewCompatible(BrushKind.CHARCOAL))
        assertTrue(!platformInkPreviewCompatible(BrushKind.WATERCOLOR))
    }
}
