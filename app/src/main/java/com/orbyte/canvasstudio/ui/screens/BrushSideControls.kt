package com.orbyte.canvasstudio.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.orbyte.canvasstudio.drawing.BrushSettings
import com.orbyte.canvasstudio.model.StudioPalette
import com.orbyte.canvasstudio.ui.workspace.QuickBrushSizing

@Composable
internal fun BrushSideControls(
    settings: BrushSettings,
    onSettingsChanged: (BrushSettings) -> Unit,
    onRight: Boolean,
    onSwitchSide: () -> Unit,
) {
    Column(Modifier.testTag("editor_brush_side"), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalDivider(color = StudioPalette.Border)
        SideSlider("Tamaño", "${settings.sizePx.toInt()} px", "quick_slider_size",
            QuickBrushSizing.sizeProgress(settings.sizePx)) {
            onSettingsChanged(settings.copy(sizePx = QuickBrushSizing.sizeFromProgress(it)))
        }
        SideSlider("Opacidad", "${(settings.opacity * 100).toInt()}%", "quick_slider_opacity",
            QuickBrushSizing.opacityProgress(settings.opacity)) {
            onSettingsChanged(settings.copy(opacity = QuickBrushSizing.opacityFromProgress(it)))
        }
        IconButton(onClick = onSwitchSide, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.SwapHoriz, if (onRight) "Mover controles a la izquierda" else "Mover controles a la derecha",
                tint = StudioPalette.TextMuted)
        }
    }
}

@Composable
private fun SideSlider(label: String, value: String, tag: String, progress: Float, onChange: (Float) -> Unit) {
    Text(label, color = StudioPalette.TextMuted, style = MaterialTheme.typography.labelSmall)
    Text(value, color = StudioPalette.Text, style = MaterialTheme.typography.labelMedium)
    Slider(
        value = progress.coerceIn(0f, 1f),
        onValueChange = onChange,
        modifier = Modifier
            .size(width = 48.dp, height = 86.dp)
            .testTag(tag)
            .semantics {
                contentDescription = "Deslizar $label"
                stateDescription = value
            }
            // Keep Material's gesture and SetProgress behavior; swap measured axes before rotation.
            .layout { measurable, constraints ->
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                val placeable = measurable.measure(Constraints.fixed(height, width))
                layout(width, height) {
                    placeable.place((width - placeable.width) / 2, (height - placeable.height) / 2)
                }
            }
            .rotate(-90f),
        colors = SliderDefaults.colors(
            thumbColor = StudioPalette.Accent,
            activeTrackColor = StudioPalette.Accent,
            inactiveTrackColor = StudioPalette.SurfaceHover,
        ),
    )
}
