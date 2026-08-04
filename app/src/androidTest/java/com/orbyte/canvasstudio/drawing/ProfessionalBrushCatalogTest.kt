package com.orbyte.canvasstudio.drawing

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orbyte.canvasstudio.drawing.brush.BrushFixture
import com.orbyte.canvasstudio.drawing.brush.BrushPreviewModel
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfessionalBrushCatalogTest {
    private val baseContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val testPrefix = "brush-catalog-test-${System.nanoTime()}-"
    private val context = object : ContextWrapper(baseContext) {
        override fun getSharedPreferences(name: String, mode: Int) =
            super.getSharedPreferences(testPrefix + name, mode)
    }

    @After fun cleanPreferences() {
        context.getSharedPreferences("canvas_studio_brushes", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun initialInventoryIsRetainedButProductionCatalogContainsFourteenDistinctTools() {
        assertEquals(30, allBuiltInBrushes.size)
        assertEquals(30, allBuiltInBrushes.map { it.id }.distinct().size)
        assertEquals(14, premiumBrushes.size)
        assertEquals(
            setOf("Lápices", "Tinta", "Marcadores", "Pintura", "Textura", "Aerógrafos"),
            premiumBrushes.mapTo(linkedSetOf(), BrushPreset::category),
        )
        assertEquals(4, experimentalBrushes.size)
        assertTrue(experimentalBrushes.all { it.category == "Experimental" })
    }

    @Test fun everyMergedPresetHasAResolvableCanonicalReplacement() {
        assertEquals(12, builtInBrushAliases.size)
        builtInBrushAliases.forEach { (legacy, canonical) ->
            assertTrue(allBuiltInBrushes.any { it.id == legacy })
            assertTrue(premiumBrushes.any { it.id == canonical })
            assertEquals(canonical, migrateBuiltInBrushId(legacy))
            assertEquals(canonical, resolveBuiltInBrush(legacy)?.id)
        }
    }

    @Test fun favoritesAndRecentsMigrateAliasesWithoutTouchingCustomIds() {
        val preferences = context.getSharedPreferences("canvas_studio_brushes", Context.MODE_PRIVATE)
        preferences.edit()
            .putStringSet("favorite_brushes_v1", setOf("g-nib", "pencil-2h", "custom-keep"))
            .putString("recent_brushes_v1", JSONArray(listOf("spray-grain", "blue-sketch", "custom-keep")).toString())
            .commit()

        assertEquals(setOf("comic-nib", "mechanical-pencil", "custom-keep"), BrushRepository.loadFavorites(context))
        assertEquals(listOf("hard-airbrush", "pencil-hb", "custom-keep"), BrushRepository.loadRecents(context))
    }

    @Test fun customBrushDerivedFromRemovedPresetRoundTripsUnchanged() {
        val legacy = requireNotNull(resolveBuiltInBrush("manga-ink")).copy(
            id = "custom-derived-manga",
            name = "Mi manga histórico",
            category = "Personalizados",
            sizePx = 47f,
            pressureCurve = 1.73f,
        )
        BrushRepository.save(context, listOf(legacy))
        val restored = BrushRepository.load(context).single()
        assertEquals(legacy, restored)
    }

    @Test fun experimentalVisibilityIsLocalAndDefaultsToHidden() {
        assertTrue(!BrushRepository.loadExperimentalVisibility(context))
        BrushRepository.saveExperimentalVisibility(context, true)
        assertTrue(BrushRepository.loadExperimentalVisibility(context))
    }

    @Test fun productionBrushesHaveUniqueDeterministicFixtureAndPreviewSignatures() {
        val scenarios = listOf(
            BrushFixture.Scenario.SLOW_LINE,
            BrushFixture.Scenario.FAST_LINE,
            BrushFixture.Scenario.PRESSURE_INCREASING,
            BrushFixture.Scenario.PRESSURE_DECREASING,
            BrushFixture.Scenario.CURVE,
            BrushFixture.Scenario.ZIGZAG,
            BrushFixture.Scenario.CIRCLES,
            BrushFixture.Scenario.TILT_PROGRESSIVE,
            BrushFixture.Scenario.TILT_SHADING,
            BrushFixture.Scenario.OVERLAPPING_PASSES,
            BrushFixture.Scenario.FOUR_TILES,
        )
        val signatures = premiumBrushes.associate { preset ->
            preset.id to scenarios.fold(17L) { hash, scenario ->
                hash * 31L + BrushFixture.stableHash(BrushFixture.evaluate(preset.toSettings(), scenario))
            }
        }
        assertEquals(signatures.size, signatures.values.distinct().size)
        premiumBrushes.forEach { preset ->
            assertEquals(
                BrushFixture.stableHash(BrushPreviewModel.dabs(preset)),
                BrushFixture.stableHash(BrushPreviewModel.dabs(preset)),
            )
        }
    }

    @Test fun requiredMaterialPairsRemainBlindlyDistinguishableByDabSignature() {
        listOf(
            "pencil-hb" to "pencil-6b",
            "mechanical-pencil" to "pencil-hb",
            "technical-ink" to "comic-nib",
            "marker" to "flat-marker",
            "gouache" to "airbrush",
            "dry-brush" to "bristle",
        ).forEach { (firstId, secondId) ->
            val first = premiumBrushes.single { it.id == firstId }
            val second = premiumBrushes.single { it.id == secondId }
            assertNotEquals(
                "$firstId y $secondId no deben compartir la misma respuesta",
                BrushFixture.stableHash(BrushPreviewModel.dabs(first)),
                BrushFixture.stableHash(BrushPreviewModel.dabs(second)),
            )
        }
    }

    @Test fun unsafeImportedSettingsAreClampedWithoutMutatingStoredCustomPreset() {
        val unsafe = requireNotNull(resolveBuiltInBrush("pencil-hb")).toSettings().copy(
            sizePx = 8_000f,
            spacing = 0f,
            opacity = 9f,
            flow = -4f,
            scatter = 8f,
            grainProfile = BrushGrainProfile(scale = 50f, depth = 9f),
            tipProfile = BrushTipProfile(count = 900),
        )
        val safe = unsafe.sanitized()
        assertEquals(600f, safe.sizePx)
        assertEquals(.025f, safe.spacing)
        assertEquals(1f, safe.opacity)
        assertEquals(.02f, safe.flow)
        assertEquals(.5f, safe.scatter)
        assertEquals(14, safe.tipProfile.count)
        assertEquals(4f, safe.grainProfile.scale)
    }
}
