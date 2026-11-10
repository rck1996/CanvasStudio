package com.orbyte.canvasstudio.ui.screens

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orbyte.canvasstudio.drawing.BrushPreset
import com.orbyte.canvasstudio.model.StudioPalette

/** Non-modal, lazy brush picker; advanced brush editing stays available separately. */
@Composable
internal fun FloatingBrushPalette(
    brushes: List<BrushPreset>,
    selectedPresetId: String,
    favoriteBrushIds: Set<String>,
    recentBrushIds: List<String>,
    maxPanelHeight: Dp,
    onPresetSelected: (BrushPreset) -> Unit,
    onOpenAdvanced: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onDragBy: (Offset) -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("Todos") }
    val currentOnDragBy by rememberUpdatedState(onDragBy)
    val recentOrder = remember(recentBrushIds) {
        recentBrushIds.withIndex().associate { it.value to it.index }
    }
    val visible = remember(brushes, favoriteBrushIds, recentOrder, query, filter) {
        val search = query.trim()
        brushes.filter { brush ->
            (filter == "Todos" ||
                (filter == "Favoritos" && brush.id in favoriteBrushIds) ||
                (filter == "Recientes" && brush.id in recentOrder)) &&
                (search.isEmpty() || brush.name.contains(search, ignoreCase = true) ||
                    brush.category.contains(search, ignoreCase = true))
        }.let {
            if (filter == "Recientes") it.sortedBy { brush -> recentOrder[brush.id] ?: Int.MAX_VALUE }
            else it
        }
    }
    Surface(
        modifier = modifier.width(308.dp).heightIn(max = maxPanelHeight)
            .shadow(12.dp, RoundedCornerShape(17.dp))
            .testTag("floating_brush_palette"),
        color = StudioPalette.Surface,
        shape = RoundedCornerShape(17.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioPalette.Border),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth()
                    .testTag("floating_brush_drag_handle")
                    .pointerInput(Unit) {
                        detectDragGestures { change, distance ->
                            change.consume()
                            currentOnDragBy(distance)
                        }
                    },
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text("Pinceles", modifier = Modifier.weight(1f), color = StudioPalette.Text,
                    style = MaterialTheme.typography.titleMedium)
                Text(visible.size.toString(), color = StudioPalette.TextMuted)
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("floating_brush_close")) {
                    Icon(Icons.Outlined.Close, "Cerrar paleta", tint = StudioPalette.TextMuted)
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(60) },
                modifier = Modifier.fillMaxWidth().testTag("floating_brush_search"),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                placeholder = { Text("Buscar pincel") },
            )
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Todos", "Favoritos", "Recientes").forEach { option ->
                    FilterChip(selected = filter == option, onClick = { filter = option },
                        label = { Text(option) })
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false).testTag("floating_brush_list"),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                items(visible, key = { it.id }) { brush ->
                    BrushPresetRow(
                        preset = brush,
                        selected = brush.id == selectedPresetId,
                        onClick = { onPresetSelected(brush) },
                    )
                }
            }
            if (visible.isEmpty()) Text("No hay pinceles con ese filtro.", color = StudioPalette.TextMuted)
            TextButton(onClick = onOpenAdvanced,
                modifier = Modifier.fillMaxWidth().testTag("floating_brush_advanced")) {
                Text("Abrir biblioteca completa")
            }
        }
    }
}
