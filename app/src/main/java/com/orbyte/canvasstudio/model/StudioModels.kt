package com.orbyte.canvasstudio.model

import androidx.compose.ui.graphics.Color
import kotlin.math.sqrt
import kotlin.math.ceil


const val MAX_CANVAS_PIXELS: Long = 64_000_000L
const val MAX_NEW_CANVAS_PIXELS: Long = 40_000_000L
const val MAX_NEW_CANVAS_DIMENSION: Int = 8192

fun recommendedNewCanvasPixels(memoryClassMb: Int): Long = when {
    memoryClassMb >= 384 -> MAX_NEW_CANVAS_PIXELS
    memoryClassMb >= 256 -> 26_000_000L
    else -> 12_000_000L
}

fun constrainCanvasSize(
    width: Int,
    height: Int,
    maxPixels: Long = MAX_CANVAS_PIXELS,
    maxDimension: Int = 16384,
): Pair<Int, Int> {
    val safeWidth = width.coerceIn(256, maxDimension)
    val safeHeight = height.coerceIn(256, maxDimension)
    val pixels = safeWidth.toLong() * safeHeight.toLong()
    if (pixels <= maxPixels) return safeWidth to safeHeight

    val scale = sqrt(maxPixels.toDouble() / pixels.toDouble())
    return (safeWidth * scale).toInt().coerceAtLeast(256) to
        (safeHeight * scale).toInt().coerceAtLeast(256)
}

data class CanvasFootprint(
    val megapixels: Double,
    val flattenedRgbaMiB: Int,
    val tileCount: Int,
    val level: String,
)

fun estimateCanvasFootprint(width: Int, height: Int): CanvasFootprint {
    val pixels = width.coerceAtLeast(1).toLong() * height.coerceAtLeast(1).toLong()
    val megapixels = pixels / 1_000_000.0
    val rgbaMiB = ceil(pixels * 4.0 / (1024.0 * 1024.0)).toInt()
    val columns = ceil(width.coerceAtLeast(1) / 512.0).toInt()
    val rows = ceil(height.coerceAtLeast(1) / 512.0).toInt()
    val level = when {
        pixels <= 16_000_000L -> "Cómodo"
        pixels <= 32_000_000L -> "Grande"
        else -> "Exigente"
    }
    return CanvasFootprint(megapixels, rgbaMiB, columns * rows, level)
}

data class ProjectCard(
    val id: String,
    val title: String,
    val width: Int,
    val height: Int,
    val modifiedLabel: String,
    val preview: PreviewStyle,
    val dpi: Int = 300,
    val isLocal: Boolean = false,
    val localPreviewPath: String? = null,
    val modifiedEpoch: Long = 0L,
)

enum class PreviewStyle {
    MOUNTAIN,
    PORTRAIT,
    CITY,
    FOREST,
    CHARACTER,
    SKETCH,
}

data class CanvasPreset(
    val title: String,
    val subtitle: String,
    val width: Int,
    val height: Int,
    val dpi: Int,
)

data class EditorDocument(
    val id: String = "untitled",
    val title: String,
    val width: Int,
    val height: Int,
    val dpi: Int = 300,
    val preview: PreviewStyle? = null,
    val isLocal: Boolean = false,
)

object StudioPalette {
    val Background = Color(0xFF0D0F12)
    val Surface = Color(0xFF14171C)
    val SurfaceRaised = Color(0xFF1A1E24)
    val SurfaceHover = Color(0xFF222730)
    val Border = Color(0xFF2A3039)
    val Accent = Color(0xFF2F75FF)
    val AccentSoft = Color(0xFF183D81)
    val Text = Color(0xFFF3F5F8)
    val TextMuted = Color(0xFF9AA2AE)
    val Success = Color(0xFF4ED09A)
    val Warning = Color(0xFFFFB454)
}

val defaultProjects = listOf(
    ProjectCard("mountain", "Ilustración montaña", 4096, 2732, "Hace 12 min", PreviewStyle.MOUNTAIN),
    ProjectCard("portrait", "Retrato editorial", 3000, 4000, "Ayer", PreviewStyle.PORTRAIT),
    ProjectCard("city", "Ciudad futurista", 5000, 2812, "Hace 3 días", PreviewStyle.CITY),
    ProjectCard("forest", "Bosque mágico", 4000, 3000, "Hace 5 días", PreviewStyle.FOREST),
    ProjectCard("character", "Personaje", 3000, 4000, "Hace 1 semana", PreviewStyle.CHARACTER),
    ProjectCard("sketch", "Boceto de entorno", 2480, 3508, "Hace 2 semanas", PreviewStyle.SKETCH),
)

val canvasPresets = listOf(
    CanvasPreset("Pantalla 4K", "16:9 · presentación", 3840, 2160, 144),
    CanvasPreset("Concept art 8K", "16:9 · lienzo grande", 7680, 4320, 144),
    CanvasPreset("Ilustración", "3:2 · alta resolución", 4096, 2732, 300),
    CanvasPreset("Cómic vertical", "A4 · impresión", 2480, 3508, 300),
    CanvasPreset("Cuadrado", "redes y concepto", 3000, 3000, 300),
)
