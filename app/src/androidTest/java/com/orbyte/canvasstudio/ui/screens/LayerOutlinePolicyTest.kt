package com.orbyte.canvasstudio.ui.screens

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbyte.canvasstudio.drawing.LayerBlendMode
import com.orbyte.canvasstudio.drawing.LayerGroupUiModel
import com.orbyte.canvasstudio.drawing.LayerUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LayerOutlinePolicyTest {
    private fun layer(id: String, groupId: String? = null) = LayerUiModel(
        id = id, name = id, visible = true, opacity = 1f,
        blendMode = LayerBlendMode.NORMAL, alphaLocked = false,
        clipping = false, isActive = false, groupId = groupId,
    )
    private fun group(id: String, parent: String? = null, collapsed: Boolean = false) =
        LayerGroupUiModel(id, id, true, 1f, collapsed, 2, parentGroupId = parent)

    @Test fun groupRowsAppearOnceInAncestorOrder() {
        val result = buildLayerOutline(
            listOf(layer("first", "inner"), layer("second", "inner"), layer("third")),
            listOf(group("outer"), group("inner", parent = "outer")), "",
        )
        assertEquals(
            listOf("group:outer", "group:inner", "layer:first", "layer:second", "layer:third"),
            result.map { it.key },
        )
        assertEquals(2, (result[2] as LayerOutlineEntry.Layer).indentDepth)
    }

    @Test fun collapsedGroupsKeepHeadersWithoutChildren() {
        val result = buildLayerOutline(
            listOf(layer("one", "g"), layer("two", "g")),
            listOf(group("g", collapsed = true)), "",
        )
        assertEquals(listOf("group:g"), result.map { it.key })
    }

    @Test fun searchDoesNotDuplicateGroupHeaders() {
        val result = buildLayerOutline(
            listOf(layer("mountain", "g"), layer("forest", "g")),
            listOf(group("g")), "FOR",
        )
        assertEquals(listOf("group:g", "layer:forest"), result.map { it.key })
        assertTrue(buildLayerOutline(listOf(layer("mountain")), emptyList(), "xyz").isEmpty())
    }

    @Test fun cyclicGroupReferencesAreBounded() {
        val result = buildLayerOutline(
            listOf(layer("child", "a")),
            listOf(group("a", parent = "b"), group("b", parent = "a")), "",
        )
        assertEquals(3, result.size)
        assertEquals(3, result.map { it.key }.toSet().size)
    }
}
