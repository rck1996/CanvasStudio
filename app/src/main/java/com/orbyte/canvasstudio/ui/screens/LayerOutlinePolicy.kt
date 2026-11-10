package com.orbyte.canvasstudio.ui.screens

import com.orbyte.canvasstudio.drawing.LayerGroupUiModel
import com.orbyte.canvasstudio.drawing.LayerUiModel

/** Stable, flat outline so offscreen layers are not composed by the inspector. */
internal sealed interface LayerOutlineEntry {
    val key: String

    data class Group(val group: LayerGroupUiModel) : LayerOutlineEntry {
        override val key: String get() = "group:${group.id}"
    }

    data class Layer(
        val layer: LayerUiModel,
        val indentDepth: Int,
    ) : LayerOutlineEntry {
        override val key: String get() = "layer:${layer.id}"
    }
}

internal fun buildLayerOutline(
    layers: List<LayerUiModel>,
    groups: List<LayerGroupUiModel>,
    query: String,
): List<LayerOutlineEntry> {
    val byId = groups.associateBy { it.id }
    val emitted = HashSet<String>()
    val entries = ArrayList<LayerOutlineEntry>(layers.size + groups.size)
    val filter = query.trim()
    for (layer in layers) {
        if (filter.isNotEmpty() && !layer.name.contains(filter, ignoreCase = true)) continue
        val ancestors = ArrayList<LayerGroupUiModel>()
        val visited = HashSet<String>()
        var id = layer.groupId
        while (id != null && visited.add(id)) {
            val group = byId[id] ?: break
            ancestors.add(group)
            id = group.parentGroupId
        }
        ancestors.reverse()
        for (group in ancestors) {
            if (emitted.add(group.id)) entries.add(LayerOutlineEntry.Group(group))
        }
        if (ancestors.none { it.collapsed }) {
            entries.add(LayerOutlineEntry.Layer(layer, ancestors.size))
        }
    }
    return entries
}
