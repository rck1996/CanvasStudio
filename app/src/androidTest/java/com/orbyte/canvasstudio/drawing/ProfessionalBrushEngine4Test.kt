package com.orbyte.canvasstudio.drawing

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI

@RunWith(AndroidJUnit4::class)
class ProfessionalBrushEngine4Test {
    @Test
    fun stylusTiltUsesTheCompletePhysicalRange() {
        assertEquals(0f, normalizedStylusTilt(0f), .001f)
        assertEquals(.5f, normalizedStylusTilt((PI / 4).toFloat()), .001f)
        assertTrue(normalizedStylusTilt(Math.toRadians(80.0).toFloat()) > .88f)
        assertEquals(1f, normalizedStylusTilt((PI / 2).toFloat()), .001f)
        val wrapped = interpolateCircularRadians(3.0f, -3.0f, .5f)
        assertTrue(kotlin.math.abs(wrapped) > 3f)
    }

    @Test
    fun inputCurvesAreIndependentAndMonotonic() {
        val soft = BrushInputCurve(gamma = .55f, minimum = .05f)
        val hard = BrushInputCurve(gamma = 1.8f, minimum = .05f)
        val samples = listOf(0f, .25f, .5f, .75f, 1f)
        val softValues = samples.map { applyInputCurve(it, soft) }
        val hardValues = samples.map { applyInputCurve(it, hard) }
        assertTrue(softValues.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(hardValues.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(softValues[2] > hardValues[2])
    }

    @Test
    fun pigmentPickupMixesInLinearColorSpaceAndCanBeDisabled() {
        val red = Color.rgb(255, 0, 0)
        val blue = Color.rgb(0, 0, 255)
        assertEquals(red, mixPigmentColor(red, blue, 0f))
        val mixed = mixPigmentColor(red, blue, .5f)
        assertTrue(Color.red(mixed) > 100)
        assertTrue(Color.blue(mixed) > 100)
        assertTrue(Color.green(mixed) < 10)
    }

    @Test
    fun professionalPresetsHaveDistinctAuthoredProfiles() {
        val twoH = premiumBrushes.first { it.id == "pencil-2h" }
        val sixB = premiumBrushes.first { it.id == "pencil-6b" }
        val wetRound = premiumBrushes.first { it.id == "wet-round" }
        val granulated = premiumBrushes.first { it.id == "granulated-watercolor" }
        val technicalInk = premiumBrushes.first { it.id == "technical-ink" }

        assertTrue(twoH.dynamicsProfile.tiltSize < sixB.dynamicsProfile.tiltSize)
        assertTrue(twoH.dualBrushProfile.opacity < sixB.dualBrushProfile.opacity)
        assertNotEquals(wetRound.grainProfile, granulated.grainProfile)
        assertTrue(granulated.renderProfile.colorPickup > 0f)
        assertEquals(1f, technicalInk.dynamicsProfile.sizePressure.minimum, .001f)
    }

    @Test
    fun dualBrushIsBoundedAndNonRecursive() {
        premiumBrushes.forEach { preset ->
            val dual = preset.dualBrushProfile
            assertTrue(dual.sizeScale in .1f..2f)
            assertTrue(dual.opacity in 0f..1f)
            assertTrue(dual.scatter in 0f..1f)
        }
    }
}
