package com.orbyte.canvasstudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.orbyte.canvasstudio.ui.StudioApp
import com.orbyte.canvasstudio.ui.theme.CanvasStudioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CanvasStudioTheme {
                StudioApp()
            }
        }
    }
}
