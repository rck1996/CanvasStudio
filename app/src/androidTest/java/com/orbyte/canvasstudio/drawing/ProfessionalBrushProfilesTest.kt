package com.orbyte.canvasstudio.drawing

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfessionalBrushProfilesTest {
    @Test
    fun professionalFamiliesHaveDistinctTipGrainAndRenderingProfiles() {
        val pencil = premiumBrushes.first { it.kind == BrushKind.PENCIL }
        val marker = premiumBrushes.single { it.id == "flat-marker" }
        val watercolor = allBuiltInBrushes.first { it.kind == BrushKind.WATERCOLOR }
        val oil = allBuiltInBrushes.first { it.kind == BrushKind.OIL }

        assertEquals(BrushTipShape.OVAL, pencil.tipProfile.shape)
        assertEquals(BrushRotationMode.STYLUS, pencil.tipProfile.rotationMode)
        assertEquals(BrushTipShape.CHISEL, marker.tipProfile.shape)
        assertEquals(BrushGrainMode.TEXTURIZED, watercolor.grainProfile.mode)
        assertEquals(BrushRenderMode.LIGHT_GLAZE, watercolor.renderProfile.mode)
        assertEquals(BrushRenderMode.BLENDING, oil.renderProfile.mode)
        assertTrue(oil.tipProfile.count >= 5)
    }

    @Test
    fun paperGrainStaysAnchoredWhileMovingGrainTravelsWithTheDab() {
        val paper = BrushGrainProfile(
            mode = BrushGrainMode.TEXTURIZED,
            depth = .9f,
            movement = 1f,
        )
        val moving = paper.copy(mode = BrushGrainMode.MOVING)

        assertEquals(
            grainCoverage(paper, 112f, 248f, 0),
            grainCoverage(paper, 112f, 248f, 37),
            0.00001f,
        )
        assertNotEquals(
            grainCoverage(moving, 112f, 248f, 0),
            grainCoverage(moving, 112f, 248f, 37),
        )
    }

    @Test
    fun renderModesAccumulatePigmentInExpectedOrder() {
        val light = renderAlphaMultiplier(
            BrushRenderProfile(BrushRenderMode.LIGHT_GLAZE, buildup = .5f),
        )
        val uniform = renderAlphaMultiplier(
            BrushRenderProfile(BrushRenderMode.UNIFORM_GLAZE, buildup = .5f),
        )
        val intense = renderAlphaMultiplier(
            BrushRenderProfile(BrushRenderMode.INTENSE_GLAZE, buildup = .5f),
        )

        assertTrue(light < uniform)
        assertTrue(uniform < intense)
    }

    @Test
    fun materialGrainsUseDistinctOriginalTextureSources() {
        val pencil = defaultGrainProfile(BrushKind.PENCIL, .7f)
        val charcoal = defaultGrainProfile(BrushKind.CHARCOAL, .7f)
        val bristle = defaultGrainProfile(BrushKind.BRISTLE, .7f)
        val oil = defaultGrainProfile(BrushKind.OIL, .7f)
        val watercolor = defaultGrainProfile(BrushKind.WATERCOLOR, .7f)

        assertEquals(BrushGrainSource.PAPER_FINE, pencil.source)
        assertEquals(BrushGrainSource.PAPER_ROUGH, charcoal.source)
        assertEquals(BrushGrainSource.BRISTLE, bristle.source)
        assertEquals(BrushGrainSource.CANVAS, oil.source)
        assertEquals(BrushGrainSource.WATERCOLOR, watercolor.source)
    }

    @Test
    fun generatedGrainTilesMeetAtTheirBordersWithoutVisibleGrid() {
        val bitmap = createBrushGrainBitmap(
            BrushTextureKey(
                source = BrushGrainSource.PAPER_ROUGH,
                depthStep = 8,
                contrastStep = 7,
            ),
        )
        try {
            var edgeDifference = 0L
            repeat(bitmap.height) { y ->
                edgeDifference += kotlin.math.abs(
                    android.graphics.Color.alpha(bitmap.getPixel(0, y)) -
                        android.graphics.Color.alpha(bitmap.getPixel(bitmap.width - 1, y)),
                )
            }
            repeat(bitmap.width) { x ->
                edgeDifference += kotlin.math.abs(
                    android.graphics.Color.alpha(bitmap.getPixel(x, 0)) -
                        android.graphics.Color.alpha(bitmap.getPixel(x, bitmap.height - 1)),
                )
            }
            val averageDifference = edgeDifference.toFloat() / (bitmap.width + bitmap.height)
            assertTrue("El tile deja una costura de $averageDifference niveles alfa", averageDifference < 18f)
        } finally {
            bitmap.recycle()
        }
    }
}
