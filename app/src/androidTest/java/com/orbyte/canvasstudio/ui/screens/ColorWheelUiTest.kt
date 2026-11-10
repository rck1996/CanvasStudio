package com.orbyte.canvasstudio.ui.screens

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.orbyte.canvasstudio.ui.theme.CanvasStudioTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ColorWheelUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun draggingHueRingUpdatesHueContinuously() {
        var hue by mutableFloatStateOf(0f)
        compose.setContent {
            CanvasStudioTheme {
                HueWheel(
                    hue = hue,
                    saturation = 1f,
                    value = 1f,
                    modifier = Modifier.size(225.dp),
                    onHueChanged = { hue = it },
                    onSaturationValueChanged = { _, _ -> },
                )
            }
        }
        compose.onNodeWithTag("hsv_color_wheel").performTouchInput {
            val start = Offset(right - 8f, center.y)
            val end = Offset(center.x, bottom - 8f)
            swipe(start = start, end = end, durationMillis = 380)
        }
        compose.runOnIdle { assertTrue("Hue should approach 90° after dragging", hue in 65f..115f) }
    }

    @Test
    fun draggingInsideSaturationSquareChangesBothComponents() {
        var saturation by mutableFloatStateOf(.5f)
        var value by mutableFloatStateOf(.5f)
        compose.setContent {
            CanvasStudioTheme {
                HueWheel(
                    hue = 140f,
                    saturation = saturation,
                    value = value,
                    modifier = Modifier.size(225.dp),
                    onHueChanged = {},
                    onSaturationValueChanged = { s, v -> saturation = s; value = v },
                )
            }
        }
        compose.onNodeWithTag("hsv_color_wheel").performTouchInput {
            val origin = center
            swipe(
                start = origin,
                end = Offset(origin.x + right * .15f, origin.y - bottom * .15f),
                durationMillis = 380,
            )
        }
        compose.runOnIdle {
            assertTrue("Saturation should increase during drag", saturation > .65f)
            assertTrue("Brightness should increase during drag", value > .65f)
        }
    }
}
