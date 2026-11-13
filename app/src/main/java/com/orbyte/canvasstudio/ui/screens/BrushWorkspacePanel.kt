package com.orbyte.canvasstudio.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.orbyte.canvasstudio.drawing.BrushKind
import com.orbyte.canvasstudio.drawing.BrushPreset
import com.orbyte.canvasstudio.drawing.BrushSettings
import com.orbyte.canvasstudio.drawing.brush.BrushTestSurface
import com.orbyte.canvasstudio.drawing.brush.BrushPreviewModel
import androidx.compose.ui.graphics.Color
import com.orbyte.canvasstudio.model.StudioPalette
import com.orbyte.canvasstudio.ui.workspace.BrushStrokePreview

@Composable
internal fun BrushWorkspacePanel(
    presets: List<BrushPreset>,
    activePreset: BrushPreset,
    settings: BrushSettings,
    favorites: Set<String>,
    recents: List<String>,
    showExperimental: Boolean,
    onShowExperimental: (Boolean) -> Unit,
    onSelect: (BrushPreset) -> Unit,
    onFavorite: (BrushPreset) -> Unit,
    onAdjust: (BrushSettings) -> Unit,
    onSaveCopy: () -> Unit,
    onReset: () -> Unit,
    onAdvanced: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("Todos") }
    var darkPaper by rememberSaveable { mutableStateOf(false) }
    var previewPressure by rememberSaveable { mutableStateOf(BrushPreviewModel.Pressure.NATURAL) }
    val listState = rememberLazyListState()
    val filters = remember(presets) {
        listOf("Todos", "Favoritos", "Recientes") + presets.map(BrushPreset::category).distinct()
    }
    val results = remember(presets, search, filter, favorites, recents) {
        val term = search.trim()
        val byFilter = when (filter) {
            "Todos" -> presets
            "Favoritos" -> presets.filter { it.id in favorites }
            "Recientes" -> {
                val indexed = presets.associateBy(BrushPreset::id)
                recents.mapNotNull(indexed::get)
            }
            else -> presets.filter { it.category == filter }
        }
        byFilter.filter { it.name.contains(term, ignoreCase = true) || it.category.contains(term, ignoreCase = true) }
    }
    Surface(modifier.testTag("brush_workspace"), color = StudioPalette.Surface, border = BorderStroke(1.dp, StudioPalette.Border)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val paperHeight = if (maxHeight < 490.dp) 72.dp else 114.dp
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pinceles", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = StudioPalette.Text)
                    IconButton(onClick = onClose) {
                        Icon(Icons.Outlined.Close, "Cerrar panel de pinceles", tint = StudioPalette.TextMuted)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !editing, onClick = { editing = false }, label = { Text("Biblioteca") })
                    FilterChip(selected = editing, onClick = { editing = true }, label = { Text("Ajustar") })
                }
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    if (editing) {
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(activePreset.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = StudioPalette.Text)
                                Text("Color actual", color = StudioPalette.TextMuted, style = MaterialTheme.typography.labelSmall)
                            }
                            BrushStrokePreview(
                                activePreset.withSettings(settings), Modifier.fillMaxWidth().height(62.dp),
                                fixedFrame = true, pressure = previewPressure,
                                inkColor = settings.color,
                                backgroundColor = Color(if (darkPaper) 0xFF2C2E34 else 0xFFE6DFD4),
                            )
                            Text("Presión de muestra", style = MaterialTheme.typography.labelMedium, color = StudioPalette.TextMuted)
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()).testTag("brush_preview_pressure"),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                BrushPreviewModel.Pressure.entries.forEach { pressure ->
                                    FilterChip(
                                        selected = pressure == previewPressure,
                                        onClick = { previewPressure = pressure },
                                        label = { Text(when (pressure) {
                                            BrushPreviewModel.Pressure.NATURAL -> "Variable"
                                            BrushPreviewModel.Pressure.SOFT -> "Suave"
                                            BrushPreviewModel.Pressure.FIRM -> "Firme"
                                        }) },
                                    )
                                }
                            }
                            SettingSlider("Tamaño", settings.sizePx, 2f..180f, "${settings.sizePx.toInt()} px") {
                                onAdjust(settings.copy(sizePx = it))
                            }
                            SettingSlider("Opacidad", settings.opacity, .05f..1f, "${(settings.opacity * 100).toInt()}%") {
                                onAdjust(settings.copy(opacity = it))
                            }
                            when (settings.kind) {
                                BrushKind.BRISTLE -> Column {
                                    SettingSlider("Densidad de cerdas", settings.tipProfile.count.toFloat(), 3f..14f,
                                        "${settings.tipProfile.count} grupos") {
                                        onAdjust(settings.copy(tipProfile = settings.tipProfile.copy(count = kotlin.math.round(it).toInt())))
                                    }
                                    SettingSlider("Flujo de pintura", settings.flow, .08f..1f,
                                        "${(settings.flow * 100).toInt()}%") {
                                        onAdjust(settings.copy(flow = it))
                                    }
                                }
                                BrushKind.PENCIL, BrushKind.CHARCOAL, BrushKind.CHALK ->
                                    SettingSlider("Grano", settings.grain, 0f..1f, "${(settings.grain * 100).toInt()}%") {
                                        onAdjust(settings.copy(grain = it, grainProfile = settings.grainProfile.copy(depth = it)))
                                    }
                                BrushKind.WATERCOLOR, BrushKind.OIL ->
                                    Column {
                                    SettingSlider("Mezcla de color", settings.renderProfile.colorPickup, 0f..1f,
                                        "${(settings.renderProfile.colorPickup * 100).toInt()}%") {
                                        onAdjust(settings.copy(renderProfile = settings.renderProfile.copy(colorPickup = it)))
                                    }
                                    Text(
                                        "Para ver la mezcla, pinta un trazo sobre otro en el papel de prueba.",
                                        style = MaterialTheme.typography.labelSmall, color = StudioPalette.TextMuted,
                                    )
                                    }
                                else -> SettingSlider("Flujo", settings.flow, .08f..1f, "${(settings.flow * 100).toInt()}%") {
                                    onAdjust(settings.copy(flow = it))
                                }
                            }
                            Row {
                                TextButton(onClick = onSaveCopy) { Text("Guardar copia") }
                                TextButton(onClick = onReset) { Text("Restablecer") }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it.take(60) },
                            modifier = Modifier.fillMaxWidth().testTag("brush_workspace_search"),
                            placeholder = { Text("Buscar pincel") },
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            singleLine = true,
                        )
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            filters.forEach { item ->
                                FilterChip(selected = item == filter, onClick = { filter = item }, label = { Text(item) })
                            }
                        }
                        TextButton(
                            onClick = {
                                if (showExperimental && filter == "Experimental") filter = "Todos"
                                onShowExperimental(!showExperimental)
                            },
                            modifier = Modifier.testTag("brush_workspace_experimental"),
                        ) {
                            Text(if (showExperimental) "Ocultar materiales experimentales" else "Mostrar óleo y acuarela (en prueba)")
                        }
                        if (results.isEmpty()) Text("No hay pinceles con ese filtro.", color = StudioPalette.TextMuted)
                        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(results, key = BrushPreset::id) { preset ->
                                BrushWorkspaceRow(preset, preset.id == activePreset.id, preset.id in favorites,
                                    onClick = { onSelect(preset) }, onFavorite = { onFavorite(preset) })
                            }
                        }
                    }
                }
                HorizontalDivider(color = StudioPalette.Border)
                var paper by remember { mutableStateOf<BrushTestSurface?>(null) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Probar pincel", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = StudioPalette.Text)
                    IconButton(onClick = { darkPaper = !darkPaper }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            if (darkPaper) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            if (darkPaper) "Usar papel claro" else "Usar papel oscuro",
                            tint = StudioPalette.TextMuted,
                        )
                    }
                    TextButton(onClick = { paper?.clearPaper() }) { Text("Limpiar") }
                }
                AndroidView(
                    factory = { BrushTestSurface(it).also { view -> paper = view } },
                    modifier = Modifier.fillMaxWidth().height(paperHeight).clip(RoundedCornerShape(9.dp))
                        .testTag("brush_test_paper").semantics { contentDescription = "Papel de prueba de pincel" },
                    update = { it.settings = settings; it.darkPaper = darkPaper },
                    onRelease = { it.release() },
                )
                TextButton(onClick = onAdvanced) { Text("Ajustes avanzados y biblioteca") }
            }
        }
    }
}

@Composable
private fun BrushWorkspaceRow(
    preset: BrushPreset,
    isActive: Boolean,
    favorite: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = "Pincel ${preset.name}"
            selected = isActive
        }.clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = StudioPalette.SurfaceRaised,
        border = BorderStroke(1.dp, if (isActive) StudioPalette.Accent else StudioPalette.Border),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(preset.name, Modifier.weight(1f), color = StudioPalette.Text,
                    style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onFavorite, modifier = Modifier.size(48.dp)) {
                    Icon(if (favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        if (favorite) "Quitar ${preset.name} de favoritos" else "Añadir ${preset.name} a favoritos",
                        tint = if (favorite) StudioPalette.Accent else StudioPalette.TextMuted,
                        modifier = Modifier.size(18.dp))
                }
            }
            BrushStrokePreview(preset, Modifier.fillMaxWidth().height(47.dp), fixedFrame = true)
        }
    }
}
