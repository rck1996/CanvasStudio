package com.orbyte.canvasstudio.ui.workspace

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.orbyte.canvasstudio.drawing.BrushPreset
import com.orbyte.canvasstudio.drawing.brush.BrushPreviewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val brushPreviewDispatcher = Dispatchers.IO.limitedParallelism(2)

/** Decorative library swatch. The caller labels the brush and can supply a row or detail size. */
@Composable
internal fun BrushStrokePreview(
    preset: BrushPreset,
    modifier: Modifier = Modifier,
    fixedFrame: Boolean = false,
    pressure: BrushPreviewModel.Pressure = BrushPreviewModel.Pressure.NATURAL,
    inkColor: Int? = null,
    backgroundColor: Color = Color(0xFF1A1D22),
) {
    // Keep the last frame while editing this preset, but never show a different brush's frame.
    var image by remember(preset.id, fixedFrame, pressure) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(preset, fixedFrame, pressure, inkColor) {
        image = withContext(brushPreviewDispatcher) {
            (if (fixedFrame) {
                BrushPreviewModel.rasterizeSwatch(preset, pressure, inkColor ?: android.graphics.Color.WHITE)
            } else BrushPreviewModel.rasterize(preset)).asImageBitmap()
        }
    }

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 96.dp, minHeight = 34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        image?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
