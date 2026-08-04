package com.orbyte.canvasstudio.drawing

import android.content.Context
import android.graphics.Color
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ProfessionalBrushLibraryStressTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext

    @Test
    @LargeTest
    fun twoHundredLongStrokesWithMostExpensiveProductionBrushesRetainOldInk() {
        val view = onMain {
            DrawingView(context).apply {
                debugPerformanceMetricsEnabled = true
                configureDocument(2048, 1536)
            }
        }
        val expensive = listOf("gouache", "dry-brush", "bristle", "charcoal", "airbrush")
            .map { id -> premiumBrushes.single { it.id == id } }
        val started = SystemClock.elapsedRealtime()
        try {
            val sentinel = onMain {
                view.brushSettings = premiumBrushes.single { it.id == "technical-ink" }
                    .toSettings(Color.rgb(220, 35, 70)).copy(sizePx = 24f)
                view.debugDrawStrokeForTest(longLine(100f, 95f, .8f, 0f))
                view.debugPixelForTest(714f, 116f)
            }
            onMain {
                repeat(200) { index ->
                    val preset = expensive[index % expensive.size]
                    view.brushSettings = preset.toSettings(Color.rgb(32, 42 + index % 80, 70)).copy(
                        sizePx = preset.sizePx.coerceIn(72f, 148f),
                        stabilization = 0f,
                    )
                    view.debugDrawStrokeForTest(
                        longLine(220f + (index % 20) * 61f, 180f + (index % 7) * 3f, .62f),
                    )
                }
            }
            assertTrue("La preparación no produjo el trazo centinela", Color.alpha(sentinel) > 0)
            assertTrue("El trazo inicial desapareció tras 200 trazos costosos", Color.alpha(onMain {
                view.debugPixelForTest(714f, 116f)
            }) >= Color.alpha(sentinel))
            Log.i(
                "CanvasStudioBrushAudit",
                "EXPENSIVE_200 durationMs=${SystemClock.elapsedRealtime() - started} metrics=${onMain { view.debugPerformanceMetrics() }}",
            )
        } finally {
            onMain { view.configureDocument(256, 256) }
        }
    }

    @Test
    @LargeTest
    fun fiveHundredLongPencilStrokesUndoSaveCloseAndReopenWithoutLoss() {
        val projectId = "brush-certification-${System.nanoTime()}"
        val view = onMain {
            DrawingView(context).apply {
                debugPerformanceMetricsEnabled = true
                configureDocument(2048, 1536)
            }
        }
        val pencils = listOf("pencil-hb", "pencil-6b", "mechanical-pencil")
            .map { id -> premiumBrushes.single { it.id == id } }
        val started = SystemClock.elapsedRealtime()
        val expected: Int
        try {
            expected = onMain {
                repeat(500) { index ->
                    val preset = pencils[index % pencils.size]
                    view.brushSettings = preset.toSettings(Color.rgb(28, 31, 38)).copy(
                        sizePx = when (preset.id) {
                            "mechanical-pencil" -> 11f
                            "pencil-hb" -> 34f
                            else -> 52f
                        },
                        stabilization = 0f,
                    )
                    view.debugDrawStrokeForTest(
                        longLine(80f + (index % 22) * 61f, 70f + (index % 9) * 4f, .2f + (index % 8) * .1f, (index % 6) / 5f),
                    )
                }
                view.undo()
                view.redo()
                view.debugPixelForTest(700f, 80f)
            }
            assertTrue("Los 500 trazos de lápiz no dejaron contenido", Color.alpha(expected) > 0)

            val saved = CountDownLatch(1)
            var savedOk = false
            onMain {
                view.onProjectSaved = { success -> savedOk = success; saved.countDown() }
                view.saveProject(projectId, "Certificación pinceles", 300, includePreview = false)
            }
            assertTrue("El proyecto de certificación no se guardó", saved.await(90, TimeUnit.SECONDS) && savedOk)
            onMain { view.configureDocument(256, 256) }

            val reopened = onMain { DrawingView(context) }
            try {
                assertTrue("El proyecto de certificación no reabrió", onMain { reopened.loadProject(projectId) })
                val actual = onMain { reopened.debugPixelForTest(700f, 80f) }
                assertEquals("El píxel de referencia cambió tras cerrar y reabrir", expected, actual)
            } finally {
                onMain { reopened.configureDocument(256, 256) }
            }
            Log.i(
                "CanvasStudioBrushAudit",
                "PENCIL_500_SAVE_REOPEN durationMs=${SystemClock.elapsedRealtime() - started} metrics=${onMain { view.debugPerformanceMetrics() }}",
            )
        } finally {
            onMain { view.configureDocument(256, 256) }
        }
    }

    private fun longLine(y: Float, xInset: Float, pressure: Float, tilt: Float = 0f): List<StrokePoint> =
        List(13) { step ->
            val progress = step / 12f
            StrokePoint(
                x = xInset + progress * (2048f - xInset * 2f),
                y = y + kotlin.math.sin(progress * Math.PI * 2.0).toFloat() * 18f,
                pressure = (pressure * (.35f + progress * .65f)).coerceIn(.04f, 1f),
                tilt = tilt,
                timestampMillis = step * 8L,
                orientation = progress * .8f,
            )
        }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }
}
