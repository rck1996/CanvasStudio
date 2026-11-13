package com.orbyte.canvasstudio.ui.screens

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orbyte.canvasstudio.drawing.BrushRepository
import com.orbyte.canvasstudio.drawing.InkDrawingContainer
import com.orbyte.canvasstudio.model.EditorDocument
import com.orbyte.canvasstudio.model.PreviewStyle
import com.orbyte.canvasstudio.model.defaultProjects
import com.orbyte.canvasstudio.ui.theme.CanvasStudioTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.math.floor

/**
 * Production Compose screens rendered on an Android tablet or emulator.
 * These are screenshots, not generated or hand-crafted mockups.
 */
@RunWith(AndroidJUnit4::class)
class DesignScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun singleShapeGroupOffersFourEditableShapesWithoutDuplicateActions() {
        compose.setContent {
            CaptureWorkspace {
                EditorScreen(
                    document = EditorDocument(id = "precise-shape-ui", title = "Figuras",
                        width = 1024, height = 1024, dpi = 300, preview = null, isLocal = false),
                    onBackToGallery = {},
                )
            }
        }
        val group = compose.onNodeWithTag("shape_tool_group")
        val controls = compose.onNodeWithTag("precise_shape_controls")
        val apply = compose.onNodeWithText("Aplicar")
        for (tool in listOf("rectangle", "triangle", "ellipse", "line")) {
            group.performScrollTo().performClick()
            compose.onNodeWithTag("shape_tool_menu").assertExists()
            compose.onNodeWithTag("shape_tool_$tool").performClick()
            controls.assertExists()
            apply.assertIsNotEnabled()
            compose.onNodeWithTag("editor_artwork_viewport").performTouchInput {
                val span = minOf(width, height) * .13f
                swipe(start = center - Offset(span, span), end = center + Offset(span, span),
                    durationMillis = 380L)
            }
            apply.assertIsEnabled()
            if (tool == "rectangle" || tool == "ellipse") apply.performClick()
            else compose.onNodeWithText("Cancelar").performClick()
            controls.assertDoesNotExist()
        }
        compose.onNodeWithContentDescription("Más opciones").performClick()
        compose.onNodeWithText("Rectángulo preciso").assertDoesNotExist()
        compose.onNodeWithText("Triángulo preciso").assertDoesNotExist()
    }

    @Test
    fun captureGallery() {
        val example = defaultProjects.first()
        val local = example.copy(id = "gallery-test-local", title = "Boceto local de prueba", isLocal = true)
        var opened: String? = null
        compose.setContent {
            CaptureWorkspace {
                GalleryScreen(
                    projects = listOf(example, local),
                    onNewCanvas = {},
                    onOpenProject = { opened = it.id },
                    onDuplicateProject = {},
                    onDeleteProject = {},
                    onRestoreLatestVersion = {},
                )
            }
        }
        compose.onNodeWithText("Tu estudio local de ilustración y pintura digital").assertExists()
        compose.onNodeWithText(local.title).assertExists()
        compose.onNodeWithText(example.title).assertDoesNotExist()
        compose.onNodeWithText("Crear nuevo lienzo").assertDoesNotExist()
        compose.onNodeWithText("Nuevo lienzo").assertExists()
        compose.onNodeWithText("Guardado local activo").assertDoesNotExist()
        capture("01-galeria")
        compose.onNodeWithText("Ejemplos").performClick()
        compose.onNodeWithText(local.title).assertDoesNotExist()
        compose.onNodeWithText(example.title).performClick()
        compose.runOnIdle { assertTrue(opened == example.id) }
        compose.onNodeWithText("Mis proyectos").performClick()
        compose.onNodeWithText(local.title).assertExists()
        compose.onNodeWithText(example.title).assertDoesNotExist()
    }

    @Test
    fun wheelOffersEditingWithoutHoldingASlot() {
        showEditor()
        compose.onNodeWithContentDescription("Abrir rueda de acceso rápido").performClick()
        compose.onNodeWithTag("quick_access_edit").performClick()
        compose.onNodeWithTag("quick_access_slot_0").performClick()
        compose.onNodeWithText("Configurar acceso 1").assertExists()
        compose.onNodeWithText("Buscar acción").assertExists()
    }

    @Test
    fun guidesAndExportsHaveOneHomeWithoutRemovingTools() {
        showEditor()
        compose.onNodeWithTag("editor_save_status").assertExists()
        compose.onNodeWithContentDescription("Más opciones").performClick()
        compose.onNodeWithText("Mostrar cuadrícula").assertDoesNotExist()
        compose.onNodeWithText("OpenRaster (.ora)").assertDoesNotExist()
        compose.onNodeWithText("Guías y asistencia").performClick()
        compose.onNodeWithText("Mostrar cuadrícula").performClick()
        compose.onNodeWithContentDescription("Más opciones").performClick()
        compose.onNodeWithText("Guías y asistencia").performClick()
        compose.onNodeWithText("Ocultar cuadrícula").assertExists()
        compose.onNodeWithText("Volver a más opciones").performClick()
        compose.onNodeWithText("Guardar ahora").assertExists()
        compose.onNodeWithText("Guardar ahora").performClick()
        compose.onNodeWithTag("editor_export_menu").performClick()
        compose.onNodeWithText("PNG").assertIsEnabled()
        compose.onNodeWithText("PSD por capas").assertIsEnabled()
        compose.onNodeWithText("OpenRaster (.ora)").assertIsEnabled()
    }

    @Test
    fun clippingNamesItsBaseAndExplainsUnsupportedExport() {
        showEditor()
        openLayers()
        compose.onNodeWithText("Recorte").performClick()
        compose.onNodeWithText("Recorte →", substring = true).assertExists()
        compose.onNodeWithTag("editor_export_menu").performClick()
        compose.onNodeWithText("No disponible con capas recortadas").assertExists()
        compose.onNodeWithText("OpenRaster (.ora)").assertIsNotEnabled()
        compose.onNodeWithText("PNG").assertIsEnabled()
    }

    @Test
    fun captureEditorAndInspector() {
        showEditor()
        openLayers()
        compose.onNodeWithContentDescription("Panel Capas, seleccionado").assertExists()
        compose.onNodeWithContentDescription("Selector de renderer de desarrollo").assertDoesNotExist()
        capture("02-editor-capas")
        compose.onNodeWithContentDescription("Más opciones").performClick()
        compose.onNodeWithText("Motor de renderizado · diagnóstico").performClick()
        compose.onNodeWithText("Motor de renderizado").assertExists()
    }

    @Test
    fun captureBrushWorkspace() {
        showEditor()
        compose.onNodeWithContentDescription("Ajustes de pincel").performClick()
        compose.onNodeWithTag("brush_workspace").assertExists()
        compose.onNodeWithTag("brush_test_paper").assertExists()
        capture("03-paleta-pinceles")
        compose.onNodeWithContentDescription("Cerrar ajustes de pincel").performClick()
        compose.onNodeWithTag("brush_workspace").assertDoesNotExist()
    }

    @Test
    fun mountainEditorDoesNotShowWhiteTileBoundariesAtFitZoom() {
        showEditor()
        val bitmap = compose.onNodeWithTag("editor_artwork_viewport").captureToImage().asAndroidBitmap()
        try {
            val scale = minOf((bitmap.width - 128) / 2048f, (bitmap.height - 128) / 1536f)
            val left = (bitmap.width - 2048 * scale) * .5f
            val top = (bitmap.height - 1536 * scale) * .5f
            val y = (top + 110f * scale).toInt()
            val sky = bitmap.getPixel((left + 300f * scale).toInt(), y)
            assertTrue("El ejemplo de montaña no se cargó", android.graphics.Color.blue(sky) > android.graphics.Color.red(sky) + 25)
            for (documentX in listOf(512, 1024, 1536)) {
                val x = floor(left + documentX * scale).toInt()
                for (pixel in x - 2..x + 2) {
                    val color = bitmap.getPixel(pixel, y)
                    val delta = abs(android.graphics.Color.red(color) - android.graphics.Color.red(sky))
                    assertTrue("Línea vertical en tile x=$documentX al zoom $scale (diferencia $delta)", delta <= 2)
                }
            }
            // Sample horizontal boundaries in sky to the right of the mountains.
            val x = (left + 1990 * scale).toInt()
            for (documentY in listOf(512, 1024)) {
                val boundary = floor(top + documentY * scale).toInt()
                val red = android.graphics.Color.red(bitmap.getPixel(x, boundary - 3))
                for (pixel in boundary - 2..boundary + 2) {
                    val delta = abs(android.graphics.Color.red(bitmap.getPixel(x, pixel)) - red)
                    assertTrue("Línea horizontal en tile y=$documentY al zoom $scale (diferencia $delta)", delta <= 2)
                }
            }
            saveViewportEvidence("canvasstudio-mountain-no-tile-lines.png", bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun updatedBrushesAreSelectableAndLeaveVisibleStrokesInEditor() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previousVisibility = BrushRepository.loadExperimentalVisibility(context)
        val previousRecents = BrushRepository.loadRecents(context)
        var root: View? = null
        BrushRepository.saveExperimentalVisibility(context, false)
        try {
            compose.setContent {
                root = LocalView.current
                CaptureWorkspace {
                    EditorScreen(
                        document = EditorDocument(id = "viewport-brush-regression", title = "Prueba de pinceles",
                            width = 2048, height = 1536, dpi = 300, preview = null, isLocal = false),
                        onBackToGallery = {},
                    )
                }
            }
            val viewport = compose.onNodeWithTag("editor_artwork_viewport")
            val baseline = viewport.captureToImage().asAndroidBitmap()
            try {
                val names = listOf("Marcador", "Rotulador plano", "Carboncillo", "Óleo espeso", "Cerda impasto", "Acuarela granulada")
                for ((i, name) in names.withIndex()) {
                    compose.onNodeWithContentDescription("Ajustes de pincel").performClick()
                    if (i == 0) {
                        val toggle = compose.onNodeWithTag("brush_workspace_experimental")
                        compose.onNodeWithText("Mostrar óleo y acuarela (en prueba)").assertExists()
                        toggle.performClick()
                    }
                    compose.onNodeWithTag("brush_workspace_search").performTextReplacement(name)
                    val brush = compose.onNodeWithContentDescription("Pincel $name")
                    brush.performSemanticsAction(SemanticsActions.OnClick) { click -> assertTrue(click()) }
                    brush.assertIsSelected()
                    compose.onNodeWithContentDescription("Cerrar panel de pinceles").performClick()
                    val band = .22f + i * .11f
                    compose.runOnIdle {
                        val canvas = checkNotNull(findDrawingContainer(checkNotNull(root)))
                        val time = SystemClock.uptimeMillis()
                        val stylus = arrayOf(MotionEvent.PointerProperties().apply {
                            id = 0
                            toolType = MotionEvent.TOOL_TYPE_STYLUS
                        })
                        val coordinate = MotionEvent.PointerCoords().apply {
                            pressure = .93f
                            y = canvas.height * band
                        }
                        for (step in 0..36) {
                            coordinate.x = canvas.width * (.29f + step / 36f * .41f)
                            val action = when (step) {
                                0 -> MotionEvent.ACTION_DOWN
                                36 -> MotionEvent.ACTION_UP
                                else -> MotionEvent.ACTION_MOVE
                            }
                            val event = MotionEvent.obtain(time, time + step * 12L, action, 1,
                                stylus, arrayOf(coordinate), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
                            try { canvas.dispatchTouchEvent(event) } finally { event.recycle() }
                        }
                        canvas.drawingView.debugFlushPendingStrokeCommits()
                    }
                    val actual = viewport.captureToImage().asAndroidBitmap()
                    try {
                        val y = (actual.height * band).toInt()
                        val darkened = (y - 14..y + 14).sumOf { row ->
                            ((actual.width * .37f).toInt()..(actual.width * .63f).toInt()).count { x ->
                                android.graphics.Color.red(baseline.getPixel(x, row)) -
                                    android.graphics.Color.red(actual.getPixel(x, row)) >= 12
                            }
                        }
                        assertTrue("$name no dejó una pincelada visible: $darkened píxeles", darkened > 240)
                        if (i == names.lastIndex) saveViewportEvidence("canvasstudio-pinceles-en-el-editor.png", actual)
                    } finally {
                        actual.recycle()
                    }
                }
            } finally {
                baseline.recycle()
            }
        } finally {
            BrushRepository.saveExperimentalVisibility(context, previousVisibility)
            BrushRepository.saveRecents(context, previousRecents)
        }
    }

    private fun findDrawingContainer(view: View): InkDrawingContainer? {
        if (view is InkDrawingContainer) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            findDrawingContainer(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    private fun saveViewportEvidence(name: String, bitmap: Bitmap) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(requireNotNull(context.getExternalFilesDir(null)), name)
        output.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    @Test
    fun captureColorInspector() {
        showEditor()
        compose.onNodeWithContentDescription("Abrir panel de color").performClick()
        compose.onNodeWithContentDescription("Panel Color, seleccionado").assertExists()
        compose.onNodeWithTag("hsv_color_wheel").assertExists()
        capture("04-editor-color")
        compose.onNodeWithContentDescription("Ajustes de pincel").performClick()
        compose.onNodeWithTag("brush_workspace").assertExists()
        compose.onNodeWithTag("hsv_color_wheel").assertDoesNotExist()
        compose.onNodeWithContentDescription("Pincel, seleccionada").assertIsSelected()
    }

    @Test
    fun brushPanelKeepsSearchAndKeepsTestPaperAcrossTabs() {
        showEditor()
        val viewport = compose.onNodeWithTag("editor_artwork_viewport")
        val widthWithoutPanel = viewport.fetchSemanticsNode().boundsInRoot.width
        compose.onNodeWithContentDescription("Ajustes de pincel").performClick()
        val paper = compose.onNodeWithTag("brush_test_paper")
        val search = compose.onNodeWithTag("brush_workspace_search")
        search.performTextInput("HB")
        compose.onNodeWithText("Ajustar").performClick().assertIsSelected()
        paper.assertExists()
        compose.onNodeWithText("Color actual").assertExists()
        compose.onNodeWithText("Variable").assertIsSelected()
        compose.onNodeWithText("Suave").performClick().assertIsSelected()
        compose.onNodeWithText("Firme").performClick().assertIsSelected()
        compose.onNodeWithText("Restablecer").assertExists()
        compose.onNodeWithText("Biblioteca").performClick().assertIsSelected()
        compose.onNodeWithText("Ajustar").performClick().assertIsSelected()
        compose.onNodeWithText("Firme").assertIsSelected()
        compose.onNodeWithText("Biblioteca").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Cerrar panel de pinceles").performClick()
        assertTrue(viewport.fetchSemanticsNode().boundsInRoot.width == widthWithoutPanel)
        compose.onNodeWithContentDescription("Ajustes de pincel").performClick()
        assertTrue(search.fetchSemanticsNode().config[SemanticsProperties.EditableText].text == "HB")
        assertTrue(paper.fetchSemanticsNode().boundsInRoot.left >= viewport.fetchSemanticsNode().boundsInRoot.right)
    }

    @Test
    fun actionSearchFindsCanvasModeAndRunsIt() {
        showEditor()
        compose.onNodeWithContentDescription("Más opciones").performClick()
        compose.onNodeWithText("Buscar acciones").performClick()
        compose.onNodeWithText("Buscar acción").performTextInput("modo lienzo")
        compose.onNodeWithText("Modo lienzo").performClick()
        compose.onNodeWithContentDescription("Mostrar paneles").assertExists()
    }

    @Test
    fun maskControlsSwitchToolsAndLeaveMaskEditing() {
        showEditor()
        openLayers()
        compose.onNodeWithContentDescription("Crear máscara").performClick()
        val controls = compose.onNodeWithTag("mask_editing_bar").fetchSemanticsNode().boundsInRoot
        val canvas = compose.onNodeWithTag("editor_artwork_viewport").fetchSemanticsNode().boundsInRoot
        assertTrue("La máscara no debe tapar la obra", controls.bottom <= canvas.top)
        compose.onNodeWithText("Ocultar").assertIsSelected()
        compose.onNodeWithText("Ver original").performClick()
        compose.onNodeWithText("Ver resultado").assertExists()
        compose.onNodeWithText("Ver resultado").performClick()
        compose.onNodeWithText("Recuperar").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Más opciones de máscara").performClick()
        compose.onNodeWithText("Invertir máscara").performClick()
        compose.onNodeWithText("Editando máscara invertida", substring = true).assertExists()
        compose.onNodeWithText("Recuperar").assertIsSelected()
        compose.onNodeWithContentDescription("Más opciones de máscara").performClick()
        compose.onNodeWithText("Quitar inversión").performClick()
        compose.onNodeWithText("Recuperar").assertIsSelected()
        val strength = compose.onNodeWithTag("mask_strength_slider")
        strength.assertDoesNotExist()
        compose.onNodeWithTag("mask_strength_toggle").performClick()
        strength.performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
            assertTrue(setProgress(50f))
        }
        compose.onNodeWithText("Intensidad · 50%").assertExists()
        assertTrue("El control expandido no debe cubrir la obra",
            compose.onNodeWithTag("mask_editing_bar").fetchSemanticsNode().boundsInRoot.bottom <=
                compose.onNodeWithTag("editor_artwork_viewport").fetchSemanticsNode().boundsInRoot.top)
        capture("06b-editor-intensidad-mascara")
        compose.onNodeWithTag("mask_strength_toggle").performClick()
        strength.assertDoesNotExist()
        capture("06-editor-mascara")
        compose.onNodeWithText("Listo").performClick()
        compose.onNodeWithText("Listo").assertDoesNotExist()
        openLayers()
        compose.onNodeWithContentDescription("Editar máscara").assertExists()
        compose.onNodeWithContentDescription("Editar máscara").performClick()
        openLayers()
        compose.onNodeWithContentDescription("Pintar en la capa Pinceladas").performClick()
        compose.onNodeWithTag("mask_editing_bar").assertDoesNotExist()
    }

    @Test
    fun editorStartsWithMoreCanvasAndBrushControlsOutsideIt() {
        showEditor()
        val canvas = compose.onNodeWithTag("editor_artwork_viewport")
        val expandedCanvas = canvas.fetchSemanticsNode().boundsInRoot
        val bar = runCatching { compose.onNodeWithTag("editor_brush_side").fetchSemanticsNode().boundsInRoot }.getOrElse {
            compose.onNodeWithTag("editor_brush_bar").fetchSemanticsNode().boundsInRoot
        }
        assertTrue(bar.top >= expandedCanvas.bottom || bar.right <= expandedCanvas.left || bar.left >= expandedCanvas.right)
        compose.onNodeWithTag("quick_slider_size").assertExists()
        compose.onNodeWithTag("quick_slider_opacity").assertExists()
        compose.onNodeWithContentDescription("Panel Capas, seleccionado").assertDoesNotExist()
        capture("05-editor-lienzo")
        openLayers()
        compose.onNodeWithContentDescription("Panel Capas, seleccionado").assertExists()
        assertTrue(canvas.fetchSemanticsNode().boundsInRoot.width < expandedCanvas.width)
        compose.onNodeWithContentDescription("Cerrar panel de capas").performClick()
        assertTrue(canvas.fetchSemanticsNode().boundsInRoot == expandedCanvas)
        if (compose.onAllNodesWithTag("editor_brush_side").fetchSemanticsNodes().isNotEmpty()) {
            val side = compose.onNodeWithTag("editor_brush_side")
            val startedOnLeft = side.fetchSemanticsNode().boundsInRoot.right <= expandedCanvas.left
            val size = compose.onNodeWithTag("quick_slider_size")
            val previous = size.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
            size.performTouchInput {
                swipe(Offset(width / 2f, height * .70f), Offset(width / 2f, height * .12f))
            }
            assertTrue("El control vertical debe responder al arrastre", previous != size.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
            val switchSide = if (startedOnLeft) "Mover controles a la derecha" else "Mover controles a la izquierda"
            val switchBack = if (startedOnLeft) "Mover controles a la izquierda" else "Mover controles a la derecha"
            compose.onNodeWithContentDescription(switchSide).performClick()
            val moved = side.fetchSemanticsNode().boundsInRoot
            val movedCanvas = canvas.fetchSemanticsNode().boundsInRoot
            assertTrue(if (startedOnLeft) moved.left >= movedCanvas.right else moved.right <= movedCanvas.left)
            compose.onNodeWithContentDescription(switchBack).performClick()
        }
    }

    @Test
    fun secondaryToolsRemainSelectableFromTheRail() {
        showEditor()
        compose.onNodeWithContentDescription("Más herramientas").performClick()
        compose.onNodeWithText("Relleno").performClick()
        compose.onNodeWithContentDescription("Más herramientas").performClick()
        compose.onNodeWithText("Relleno").assertIsSelected()
    }

    private fun openLayers() {
        compose.onNodeWithContentDescription("Abrir panel de capas").performClick()
    }

    private fun showEditor() {
        compose.setContent {
            CaptureWorkspace {
                EditorScreen(
                    document = EditorDocument(
                        id = "design-emulator-screenshot",
                        title = "Ilustración montaña",
                        width = 2048,
                        height = 1536,
                        dpi = 300,
                        preview = PreviewStyle.MOUNTAIN,
                        isLocal = false,
                    ),
                    onBackToGallery = {},
                )
            }
        }
        compose.waitForIdle()
        // Demo artwork is seeded by the same asynchronous rasterizer as the app.
        Thread.sleep(1200L)
        compose.waitForIdle()
    }

    @Composable
    private fun CaptureWorkspace(content: @Composable () -> Unit) {
        CanvasStudioTheme {
            Box(Modifier.fillMaxSize().padding(WindowInsets.safeDrawing.asPaddingValues())) {
                Box(Modifier.fillMaxSize().testTag("capture_app_area")) {
                    content()
                }
            }
        }
    }

    private fun capture(baseName: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val screenshot = compose.onNodeWithTag("capture_app_area").captureToImage().asAndroidBitmap()
        // Gradle may uninstall the test target after instrumented tests, deleting
        // its Android/data folder. MediaStore keeps these files in public Pictures.
        val resolver = instrumentation.targetContext.contentResolver
        val metadata = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$baseName.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/CanvasStudioCaptures/",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val imageUri = requireNotNull(
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, metadata),
        ) { "Could not register public screenshot $baseName" }
        val saved = resolver.openOutputStream(imageUri)?.use { stream ->
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream)
        } == true
        screenshot.recycle()
        assertTrue("Could not encode screenshot $baseName", saved)
        resolver.update(
            imageUri,
            ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
            null,
            null,
        )
    }
}
