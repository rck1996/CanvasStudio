package com.orbyte.canvasstudio.ui.workspace

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickAccessTest {
    @Test
    fun keepsHighFrequencyActionsInAStableOrder() {
        assertEquals(
            listOf(
                QuickAccessAction.BRUSH_LIBRARY,
                QuickAccessAction.EYEDROPPER,
                QuickAccessAction.ADD_LAYER,
                QuickAccessAction.TOGGLE_LAYER_VISIBILITY,
                QuickAccessAction.MASK_WORKFLOW,
                QuickAccessAction.OPEN_LAYERS,
            ),
            quickAccessItems(QuickAccessContext(hasActiveLayer = true)).map { it.action },
        )
    }

    @Test
    fun disablesOnlyLayerActionsWhenNoLayerIsActive() {
        val items = quickAccessItems(QuickAccessContext(hasActiveLayer = false), QuickAccessAction.entries).associateBy { it.action }
        assertTrue(items.getValue(QuickAccessAction.BRUSH_LIBRARY).enabled)
        assertTrue(items.getValue(QuickAccessAction.EYEDROPPER).enabled)
        assertTrue(items.getValue(QuickAccessAction.ADD_LAYER).enabled)
        assertTrue(items.getValue(QuickAccessAction.OPEN_LAYERS).enabled)
        assertFalse(items.getValue(QuickAccessAction.TOGGLE_LAYER_VISIBILITY).enabled)
        assertFalse(items.getValue(QuickAccessAction.DUPLICATE_LAYER).enabled)
        assertFalse(items.getValue(QuickAccessAction.TOGGLE_ALPHA_LOCK).enabled)
        assertFalse(items.getValue(QuickAccessAction.MASK_WORKFLOW).enabled)
    }

    @Test
    fun reflectsLayerAndMaskStateWithoutChangingActionIdentity() {
        val items = quickAccessItems(
            QuickAccessContext(
                hasActiveLayer = true,
                layerVisible = false,
                alphaLocked = true,
                hasMask = true,
                editingMask = true,
            ),
            QuickAccessAction.entries,
        ).associateBy { it.action }
        assertFalse(items.getValue(QuickAccessAction.TOGGLE_LAYER_VISIBILITY).selected)
        assertTrue(items.getValue(QuickAccessAction.TOGGLE_ALPHA_LOCK).selected)
        assertTrue(items.getValue(QuickAccessAction.MASK_WORKFLOW).selected)
    }

    @Test
    fun savedConfigurationIsNormalizedToSixUniqueActions() {
        val actions = normalizedQuickAccessActions(
            listOf("ADD_LAYER", "ADD_LAYER", "NOT_AN_ACTION", "TOGGLE_ALPHA_LOCK"),
        )
        assertEquals(6, actions.size)
        assertTrue(QuickAccessAction.ADD_LAYER in actions)
        assertTrue(QuickAccessAction.TOGGLE_ALPHA_LOCK in actions)
        assertEquals(actions.size, actions.distinct().size)
    }

    @Test
    fun assigningAnExistingActionSwapsSlotsWithoutDuplicates() {
        val updated = reassignQuickAccessAction(defaultQuickAccessActions, 0, QuickAccessAction.ADD_LAYER)
        assertEquals(QuickAccessAction.ADD_LAYER, updated[0])
        assertEquals(QuickAccessAction.BRUSH_LIBRARY, updated[2])
        assertEquals(updated.size, updated.distinct().size)
    }

    @Test
    fun builtInProfilesAlwaysContainSixUniqueActionsAndCanBeRecognized() {
        QuickAccessProfile.entries.filterNot { it == QuickAccessProfile.CUSTOM }.forEach { profile ->
            val actions = quickAccessActionsFor(profile)
            assertEquals(profile.name, 6, actions.size)
            assertEquals(profile.name, actions.size, actions.distinct().size)
            assertEquals(profile, matchingQuickAccessProfile(actions))
        }
        assertEquals(
            QuickAccessProfile.CUSTOM,
            matchingQuickAccessProfile(defaultQuickAccessActions.reversed()),
        )
    }

    @Test
    fun wheelIsCenteredOnTouchAndClampedAtEveryCanvasEdge() {
        assertEquals(QuickMenuPosition(335f, 235f), quickMenuTopLeft(500f, 400f, 1000f, 800f, 330f))
        assertEquals(QuickMenuPosition(0f, 0f), quickMenuTopLeft(10f, 10f, 1000f, 800f, 330f))
        assertEquals(QuickMenuPosition(670f, 470f), quickMenuTopLeft(995f, 795f, 1000f, 800f, 330f))
        assertEquals(QuickMenuPosition(335f, 235f), quickMenuTopLeft(null, null, 1000f, 800f, 330f))
    }
}
