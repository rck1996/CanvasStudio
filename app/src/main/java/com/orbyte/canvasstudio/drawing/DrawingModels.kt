package com.orbyte.canvasstudio.drawing

import android.graphics.Bitmap
import android.graphics.Color
import java.util.UUID
import kotlin.math.PI
import kotlin.math.pow

fun calibratedPressure(rawPressure: Float, curve: Float): Float =
    rawPressure.coerceIn(0f, 1f).pow(curve.coerceIn(0.35f, 2.5f))

fun normalizedStylusTilt(rawTiltRadians: Float): Float =
    (rawTiltRadians / (PI.toFloat() / 2f)).coerceIn(0f, 1f)

fun interpolateCircularRadians(from: Float, to: Float, amount: Float): Float {
    val fullTurn = (PI * 2).toFloat()
    var delta = (to - from) % fullTurn
    if (delta > PI) delta -= fullTurn
    if (delta < -PI) delta += fullTurn
    return from + delta * amount.coerceIn(0f, 1f)
}

fun inputSamplingDistance(settings: BrushSettings, stampBased: Boolean): Float {
    if (!stampBased) return 0.18f
    val spacingDistance = settings.sizePx * settings.spacing.coerceIn(0.025f, 0.5f)
    return (spacingDistance * 0.82f).coerceIn(0.75f, 28f)
}

/**
 * AndroidX Ink currently previews a solid pressure pen. Limit that front buffer to brush
 * families whose final raster is visually close to it; transparent and broken-media brushes
 * use DrawingView's local overlay so a solid temporary stroke cannot appear to disappear.
 */
fun platformInkPreviewCompatible(kind: BrushKind): Boolean = kind in setOf(
    BrushKind.MARKER,
    BrushKind.PAINT,
    BrushKind.OIL,
)

enum class DrawingTool {
    BRUSH,
    ERASER,
    LINE,
    RECTANGLE,
    ELLIPSE,
    FILL,
    GRADIENT,
    SELECT_RECTANGLE,
    SELECT_ELLIPSE,
    SELECT_LASSO,
    TRANSFORM,
    EYEDROPPER,
    HAND,
}

enum class GuideMode {
    NONE,
    PERSPECTIVE_ONE_POINT,
    PERSPECTIVE_TWO_POINT,
}

enum class LayerBlendMode {
    NORMAL,
    MULTIPLY,
    SCREEN,
    OVERLAY,
    ADD,
    DARKEN,
    LIGHTEN,
    SOFT_LIGHT,
    HARD_LIGHT,
    DIFFERENCE,
    COLOR_DODGE,
    COLOR_BURN,
}

enum class BrushKind {
    PENCIL,
    INK,
    MARKER,
    PAINT,
    AIRBRUSH,
    CHARCOAL,
    CHALK,
    DRY_BRUSH,
    BRISTLE,
    WATERCOLOR,
    OIL,
}

data class BrushPreset(
    val id: String,
    val name: String,
    val category: String,
    val kind: BrushKind,
    val sizePx: Float,
    val opacity: Float,
    val hardness: Float,
    val spacing: Float,
    val stabilization: Float,
    val flow: Float = 1f,
    val minSize: Float = 0.22f,
    val pressureSize: Boolean = true,
    val pressureOpacity: Boolean = false,
    val pressureCurve: Float = 1f,
    val tiltResponse: Float = 0f,
    val taperStart: Float = 0.08f,
    val taperEnd: Float = 0.06f,
    val scatter: Float = 0f,
    val grain: Float = 0f,
    val velocitySize: Float = 0f,
    val tipAssetPath: String? = null,
    val tipProfile: BrushTipProfile = defaultTipProfile(kind),
    val grainProfile: BrushGrainProfile = defaultGrainProfile(kind, grain),
    val renderProfile: BrushRenderProfile = defaultRenderProfile(kind),
    val dynamicsProfile: BrushDynamicsProfile = defaultDynamicsProfile(kind),
    val dualBrushProfile: DualBrushProfile = defaultDualBrushProfile(kind),
)

data class BrushSettings(
    /** Runtime hint only; project compatibility never depends on this preset identifier. */
    val presetId: String? = null,
    val sizePx: Float = 24f,
    val opacity: Float = 1f,
    val color: Int = Color.rgb(38, 42, 48),
    val hardness: Float = 0.85f,
    val spacing: Float = 0.12f,
    val stabilization: Float = 0.22f,
    val flow: Float = 1f,
    val minSize: Float = 0.22f,
    val pressureSize: Boolean = true,
    val pressureOpacity: Boolean = false,
    val pressureCurve: Float = 1f,
    val tiltResponse: Float = 0f,
    val taperStart: Float = 0.08f,
    val taperEnd: Float = 0.06f,
    val scatter: Float = 0f,
    val grain: Float = 0f,
    val velocitySize: Float = 0f,
    val kind: BrushKind = BrushKind.PENCIL,
    val tipAssetPath: String? = null,
    val tipProfile: BrushTipProfile = defaultTipProfile(kind),
    val grainProfile: BrushGrainProfile = defaultGrainProfile(kind, grain),
    val renderProfile: BrushRenderProfile = defaultRenderProfile(kind),
    val dynamicsProfile: BrushDynamicsProfile = defaultDynamicsProfile(kind),
    val dualBrushProfile: DualBrushProfile = defaultDualBrushProfile(kind),
)

internal fun BrushPreset.toSettings(color: Int = Color.rgb(38, 42, 48)): BrushSettings = BrushSettings(
    presetId = id,
    sizePx = sizePx,
    opacity = opacity,
    color = color,
    hardness = hardness,
    spacing = spacing,
    stabilization = stabilization,
    flow = flow,
    minSize = minSize,
    pressureSize = pressureSize,
    pressureOpacity = pressureOpacity,
    pressureCurve = pressureCurve,
    tiltResponse = tiltResponse,
    taperStart = taperStart,
    taperEnd = taperEnd,
    scatter = scatter,
    grain = grain,
    velocitySize = velocitySize,
    kind = kind,
    tipAssetPath = tipAssetPath,
    tipProfile = tipProfile,
    grainProfile = grainProfile,
    renderProfile = renderProfile,
    dynamicsProfile = dynamicsProfile,
    dualBrushProfile = dualBrushProfile,
)

data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float,
    val tilt: Float,
    val timestampMillis: Long,
    val orientation: Float = 0f,
)

sealed interface DrawCommand {
    val id: String
}

data class StrokeCommand(
    override val id: String = UUID.randomUUID().toString(),
    val points: List<StrokePoint>,
    val tool: DrawingTool,
    val settings: BrushSettings,
    val clipPoints: List<StrokePoint> = emptyList(),
    val clipInverted: Boolean = false,
    val clipFeatherPx: Float = 0f,
) : DrawCommand

data class ShapeCommand(
    override val id: String = UUID.randomUUID().toString(),
    val tool: DrawingTool,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val settings: BrushSettings,
    val clipPoints: List<StrokePoint> = emptyList(),
    val clipInverted: Boolean = false,
    val clipFeatherPx: Float = 0f,
) : DrawCommand

data class GradientCommand(
    override val id: String = UUID.randomUUID().toString(),
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val startColor: Int,
    val endColor: Int,
    val clipPoints: List<StrokePoint> = emptyList(),
    val clipInverted: Boolean = false,
    val clipFeatherPx: Float = 0f,
) : DrawCommand

/**
 * In-memory raster patch used by non-destructive fill and selection transforms.
 *
 * The bitmap is intentionally not serialized: project persistence stores the resulting raster
 * tiles. Keeping it as a command during the active session makes undo/redo deterministic.
 */
data class PixelPatchCommand(
    override val id: String = UUID.randomUUID().toString(),
    val bitmap: Bitmap,
    val left: Float,
    val top: Float,
) : DrawCommand

data class TransformSelectionCommand(
    override val id: String = UUID.randomUUID().toString(),
    val sourcePoints: List<StrokePoint>,
    val sourceBoundsLeft: Float,
    val sourceBoundsTop: Float,
    val sourceBoundsRight: Float,
    val sourceBoundsBottom: Float,
    val bitmap: Bitmap,
    val matrixValues: FloatArray,
    val sourceInverted: Boolean = false,
) : DrawCommand

data class LayerUiModel(
    val id: String,
    val name: String,
    val visible: Boolean,
    val opacity: Float,
    val blendMode: LayerBlendMode,
    val alphaLocked: Boolean,
    val clipping: Boolean,
    val isActive: Boolean,
    val isSelected: Boolean = isActive,
    val groupId: String? = null,
    val hasMask: Boolean = false,
    val maskEnabled: Boolean = true,
    val editingMask: Boolean = false,
)

data class LayerGroupUiModel(
    val id: String,
    val name: String,
    val visible: Boolean,
    val opacity: Float,
    val collapsed: Boolean,
    val layerCount: Int,
    val parentGroupId: String? = null,
    val depth: Int = 0,
)

private val authoredBuiltInBrushes = listOf(
    BrushPreset(
        id = "pencil-hb",
        name = "Lápiz HB",
        category = "Lápices",
        kind = BrushKind.PENCIL,
        sizePx = 16f,
        opacity = 0.68f,
        hardness = 0.7f,
        spacing = 0.055f,
        stabilization = 0.2f,
        flow = 0.64f,
        minSize = 0.2f,
        pressureOpacity = true,
        tiltResponse = 0.52f,
        taperStart = 0.18f,
        taperEnd = 0.12f,
        grain = 0.3f,
        velocitySize = 0.12f,
    ),
    BrushPreset(
        id = "pencil-6b",
        name = "Lápiz 6B",
        category = "Lápices",
        kind = BrushKind.PENCIL,
        sizePx = 30f,
        opacity = 0.86f,
        hardness = 0.42f,
        spacing = 0.065f,
        stabilization = 0.14f,
        flow = 0.76f,
        minSize = 0.22f,
        pressureOpacity = true,
        tiltResponse = 0.72f,
        taperStart = 0.14f,
        taperEnd = 0.1f,
        grain = 0.46f,
        scatter = 0.025f,
    ),
    BrushPreset(
        id = "mechanical-pencil",
        name = "Portaminas",
        category = "Lápices",
        kind = BrushKind.PENCIL,
        sizePx = 8f,
        opacity = 0.92f,
        hardness = 0.92f,
        spacing = 0.05f,
        stabilization = 0.34f,
        flow = 0.88f,
        minSize = 0.34f,
        pressureOpacity = true,
        tiltResponse = 0.12f,
        taperStart = 0.1f,
        taperEnd = 0.08f,
        grain = 0.08f,
        velocitySize = 0.08f,
    ),
    BrushPreset(
        id = "technical-ink",
        name = "Tinta técnica",
        category = "Tinta",
        kind = BrushKind.INK,
        sizePx = 11f,
        opacity = 1f,
        hardness = 1f,
        spacing = 0.025f,
        stabilization = 0.48f,
        flow = 1f,
        minSize = 0.78f,
        pressureSize = false,
        taperStart = 0.045f,
        taperEnd = 0.055f,
    ),
    BrushPreset(
        id = "pressure-ink",
        name = "Tinta con presión",
        category = "Tinta",
        kind = BrushKind.INK,
        sizePx = 25f,
        opacity = 1f,
        hardness = 0.96f,
        spacing = 0.05f,
        stabilization = 0.36f,
        flow = 1f,
        minSize = 0.08f,
        taperStart = 0.24f,
        taperEnd = 0.2f,
        velocitySize = 0.18f,
    ),
    BrushPreset(
        id = "comic-nib",
        name = "Plumilla cómic",
        category = "Tinta",
        kind = BrushKind.INK,
        sizePx = 34f,
        opacity = 1f,
        hardness = 0.94f,
        spacing = 0.028f,
        stabilization = 0.3f,
        flow = 1f,
        minSize = 0.04f,
        tiltResponse = 0.18f,
        taperStart = 0.32f,
        taperEnd = 0.28f,
        velocitySize = 0.22f,
    ),
    BrushPreset(
        id = "marker",
        name = "Marcador",
        category = "Pintura",
        kind = BrushKind.MARKER,
        sizePx = 58f,
        opacity = 0.38f,
        hardness = 0.8f,
        spacing = 0.08f,
        stabilization = 0.12f,
        flow = 0.82f,
        minSize = 0.7f,
        pressureSize = false,
        pressureOpacity = true,
        tiltResponse = 0.28f,
        taperStart = 0f,
        taperEnd = 0f,
        grain = 0.1f,
    ),
    BrushPreset(
        id = "gouache",
        name = "Gouache opaco",
        category = "Pintura",
        kind = BrushKind.PAINT,
        sizePx = 72f,
        opacity = 0.88f,
        hardness = 0.76f,
        spacing = 0.07f,
        stabilization = 0.14f,
        flow = 0.82f,
        minSize = 0.26f,
        pressureOpacity = true,
        tiltResponse = 0.2f,
        taperStart = 0.08f,
        taperEnd = 0.08f,
        grain = 0.16f,
        scatter = 0.025f,
    ),
    BrushPreset(
        id = "soft-paint",
        name = "Pintura suave",
        category = "Pintura",
        kind = BrushKind.PAINT,
        sizePx = 84f,
        opacity = 0.5f,
        hardness = 0.34f,
        spacing = 0.08f,
        stabilization = 0.18f,
        flow = 0.58f,
        minSize = 0.3f,
        pressureOpacity = true,
        tiltResponse = 0.16f,
        taperStart = 0.06f,
        taperEnd = 0.06f,
        grain = 0.08f,
    ),
    BrushPreset(
        id = "airbrush",
        name = "Aerógrafo suave",
        category = "Aerógrafo",
        kind = BrushKind.AIRBRUSH,
        sizePx = 112f,
        opacity = 0.2f,
        hardness = 0.06f,
        spacing = 0.06f,
        stabilization = 0.1f,
        flow = 0.46f,
        minSize = 0.45f,
        pressureOpacity = true,
        taperStart = 0f,
        taperEnd = 0f,
        scatter = 0.04f,
    ),
    BrushPreset(
        id = "charcoal",
        name = "Carboncillo",
        category = "Textura",
        kind = BrushKind.CHARCOAL,
        sizePx = 46f,
        opacity = 0.68f,
        hardness = 0.3f,
        spacing = 0.1f,
        stabilization = 0.1f,
        flow = 0.62f,
        minSize = 0.24f,
        pressureOpacity = true,
        tiltResponse = 0.78f,
        taperStart = 0.08f,
        taperEnd = 0.08f,
        scatter = 0.34f,
        grain = 0.72f,
    ),
    BrushPreset(
        id = "chalk",
        name = "Tiza seca",
        category = "Textura",
        kind = BrushKind.CHALK,
        sizePx = 38f,
        opacity = 0.62f,
        hardness = 0.58f,
        spacing = 0.09f,
        stabilization = 0.08f,
        flow = 0.54f,
        minSize = 0.2f,
        pressureOpacity = true,
        tiltResponse = 0.5f,
        taperStart = 0.06f,
        taperEnd = 0.06f,
        scatter = 0.24f,
        grain = 0.62f,
    ),
    BrushPreset("blue-sketch", "Lápiz azul", "Lápices", BrushKind.PENCIL, 14f, .72f, .7f, .07f, .24f, .7f, .14f, true, true, .82f, .38f, .18f, .12f, .02f, .2f, .1f),
    BrushPreset("colored-pencil", "Lápiz de color", "Lápices", BrushKind.PENCIL, 22f, .74f, .56f, .09f, .16f, .64f, .2f, true, true, 1.12f, .55f, .12f, .1f, .05f, .34f, .08f),
    BrushPreset("manga-ink", "Entintado manga", "Tinta", BrushKind.INK, 20f, 1f, .98f, .035f, .48f, 1f, .04f, true, false, .78f, .08f, .3f, .28f, 0f, 0f, .18f),
    BrushPreset("g-nib", "Plumilla G", "Tinta", BrushKind.INK, 38f, 1f, .94f, .04f, .34f, 1f, .02f, true, false, .68f, .22f, .36f, .32f, 0f, 0f, .24f),
    BrushPreset("flat-marker", "Rotulador plano", "Marcadores", BrushKind.MARKER, 64f, .52f, .88f, .045f, .12f, .82f, .78f, false, true, 1.05f, .42f, .02f, .02f, 0f, .05f, 0f),
    BrushPreset("hard-airbrush", "Aerógrafo duro", "Aerógrafo", BrushKind.AIRBRUSH, 96f, .28f, .42f, .055f, .08f, .52f, .38f, true, true, 1.18f, 0f, 0f, 0f, .02f, .04f, 0f),
    BrushPreset("dry-brush", "Pincel seco", "Pintura", BrushKind.DRY_BRUSH, 74f, .68f, .5f, .1f, .1f, .58f, .18f, true, true, .92f, .58f, .04f, .05f, .18f, .7f, .08f),
    BrushPreset("bristle", "Pincel de cerdas", "Pintura", BrushKind.BRISTLE, 88f, .76f, .62f, .08f, .12f, .64f, .2f, true, true, .84f, .48f, .06f, .06f, .14f, .54f, .06f),
    BrushPreset("granulated-watercolor", "Acuarela granulada", "Acuarela", BrushKind.WATERCOLOR, 104f, .26f, .18f, .075f, .14f, .42f, .32f, true, true, 1.24f, .32f, 0f, .04f, .08f, .74f, 0f),
    BrushPreset("thick-oil", "Óleo espeso", "Óleo", BrushKind.OIL, 92f, .9f, .82f, .07f, .16f, .8f, .26f, true, true, .86f, .24f, .04f, .06f, .06f, .32f, .04f),
    BrushPreset("pencil-2h", "Lápiz 2H", "Lápices", BrushKind.PENCIL, 9f, .58f, .9f, .055f, .3f, .62f, .3f, true, true, 1.35f, .16f, .1f, .08f, 0f, .08f, .05f),
    BrushPreset("graphite-shader", "Grafito inclinado", "Lápices", BrushKind.PENCIL, 48f, .48f, .38f, .085f, .1f, .5f, .32f, true, true, .88f, .9f, .04f, .04f, .03f, .48f, .02f),
    BrushPreset("sumi-ink", "Tinta sumi", "Tinta", BrushKind.INK, 58f, .86f, .72f, .045f, .24f, .88f, .04f, true, true, .72f, .38f, .34f, .26f, .02f, .08f, .2f),
    BrushPreset("calligraphy-flat", "Caligrafía plana", "Tinta", BrushKind.INK, 46f, 1f, .98f, .035f, .42f, 1f, .14f, true, false, .82f, .62f, .22f, .18f, 0f, 0f, .1f),
    BrushPreset("pastel-soft", "Pastel suave", "Textura", BrushKind.CHALK, 62f, .54f, .28f, .09f, .08f, .48f, .28f, true, true, 1.05f, .64f, .04f, .04f, .18f, .78f, .02f),
    BrushPreset("spray-grain", "Spray granulado", "Aerógrafo", BrushKind.AIRBRUSH, 118f, .24f, .16f, .065f, .06f, .42f, .46f, true, true, 1.2f, .08f, 0f, 0f, .48f, .62f, 0f),
    BrushPreset("wet-round", "Redondo húmedo", "Acuarela", BrushKind.WATERCOLOR, 76f, .32f, .22f, .06f, .18f, .4f, .18f, true, true, .92f, .28f, .1f, .08f, .04f, .52f, .08f),
    BrushPreset("impasto-bristle", "Cerda impasto", "Óleo", BrushKind.OIL, 108f, .94f, .86f, .075f, .12f, .84f, .22f, true, true, .78f, .54f, .06f, .05f, .12f, .44f, .06f),
).map(::professionalizePreset)

/**
 * Production catalog intentionally stays small. Legacy presets remain resolvable so favorites,
 * recents, duplicated custom brushes and old in-memory commands never depend on UI visibility.
 */
val builtInBrushAliases: Map<String, String> = mapOf(
    "pressure-ink" to "comic-nib",
    "soft-paint" to "airbrush",
    "chalk" to "charcoal",
    "blue-sketch" to "pencil-hb",
    "colored-pencil" to "pencil-hb",
    "manga-ink" to "comic-nib",
    "g-nib" to "comic-nib",
    "pencil-2h" to "mechanical-pencil",
    "graphite-shader" to "pencil-6b",
    "sumi-ink" to "calligraphy-flat",
    "pastel-soft" to "charcoal",
    "spray-grain" to "hard-airbrush",
)

private val professionalBrushIds = listOf(
    "pencil-hb",
    "pencil-6b",
    "mechanical-pencil",
    "technical-ink",
    "comic-nib",
    "calligraphy-flat",
    "marker",
    "flat-marker",
    "gouache",
    "dry-brush",
    "bristle",
    "charcoal",
    "airbrush",
    "hard-airbrush",
)

private val experimentalBrushIds = listOf(
    "granulated-watercolor",
    "wet-round",
    "thick-oil",
    "impasto-bristle",
)

val allBuiltInBrushes: List<BrushPreset> = authoredBuiltInBrushes

val premiumBrushes: List<BrushPreset> = professionalBrushIds.map { id ->
    checkNotNull(authoredBuiltInBrushes.firstOrNull { it.id == id }) { "Preset profesional ausente: $id" }
}.map { preset ->
    when (preset.id) {
        "marker", "flat-marker" -> preset.copy(category = "Marcadores")
        "airbrush", "hard-airbrush" -> preset.copy(category = "Aerógrafos")
        else -> preset
    }
}

val experimentalBrushes: List<BrushPreset> = experimentalBrushIds.map { id ->
    checkNotNull(authoredBuiltInBrushes.firstOrNull { it.id == id }) { "Preset experimental ausente: $id" }
}.map { it.copy(category = "Experimental") }

fun migrateBuiltInBrushId(id: String): String = builtInBrushAliases[id] ?: id

fun resolveBuiltInBrush(id: String): BrushPreset? {
    val resolvedId = migrateBuiltInBrushId(id)
    return authoredBuiltInBrushes.firstOrNull { it.id == resolvedId }
}

fun BrushSettings.sanitized(): BrushSettings = copy(
    sizePx = sizePx.coerceIn(2f, 600f),
    opacity = opacity.coerceIn(.02f, 1f),
    hardness = hardness.coerceIn(0f, 1f),
    spacing = spacing.coerceIn(.025f, .4f),
    stabilization = stabilization.coerceIn(0f, .95f),
    flow = flow.coerceIn(.02f, 1f),
    minSize = minSize.coerceIn(.02f, 1f),
    pressureCurve = pressureCurve.coerceIn(.25f, 4f),
    tiltResponse = tiltResponse.coerceIn(0f, 1f),
    taperStart = taperStart.coerceIn(0f, .48f),
    taperEnd = taperEnd.coerceIn(0f, .48f),
    scatter = scatter.coerceIn(0f, .5f),
    grain = grain.coerceIn(0f, 1f),
    velocitySize = velocitySize.coerceIn(0f, 1f),
    tipProfile = tipProfile.copy(
        roundness = tipProfile.roundness.coerceIn(.08f, 1f),
        angleDegrees = tipProfile.angleDegrees.coerceIn(-180f, 180f),
        rotationJitter = tipProfile.rotationJitter.coerceIn(0f, 1f),
        count = tipProfile.count.coerceIn(1, 14),
        countJitter = tipProfile.countJitter.coerceIn(0f, 1f),
    ),
    grainProfile = grainProfile.copy(
        scale = grainProfile.scale.coerceIn(.15f, 4f),
        depth = grainProfile.depth.coerceIn(0f, 1f),
        contrast = grainProfile.contrast.coerceIn(0f, 1f),
        movement = grainProfile.movement.coerceIn(0f, 1f),
    ),
    renderProfile = renderProfile.copy(
        buildup = renderProfile.buildup.coerceIn(0f, 1f),
        wetness = renderProfile.wetness.coerceIn(0f, 1f),
        dilution = renderProfile.dilution.coerceIn(0f, 1f),
        drag = renderProfile.drag.coerceIn(0f, 1f),
        charge = renderProfile.charge.coerceIn(0f, 1f),
        attack = renderProfile.attack.coerceIn(0f, 1f),
        bleed = renderProfile.bleed.coerceIn(0f, 1f),
        colorPickup = renderProfile.colorPickup.coerceIn(0f, 1f),
    ),
    dualBrushProfile = dualBrushProfile.copy(
        sizeScale = dualBrushProfile.sizeScale.coerceIn(.1f, 2f),
        opacity = dualBrushProfile.opacity.coerceIn(0f, 1f),
        offset = dualBrushProfile.offset.coerceIn(-1f, 1f),
        scatter = dualBrushProfile.scatter.coerceIn(0f, 1f),
    ),
)

/**
 * Preset-level tuning is intentional: two brushes in the same family should not
 * merely differ by size and label.
 */
private fun professionalizePreset(preset: BrushPreset): BrushPreset {
    val dynamics = preset.dynamicsProfile.copy(
        sizePressure = preset.dynamicsProfile.sizePressure.copy(gamma = preset.pressureCurve),
        velocitySize = maxOf(preset.dynamicsProfile.velocitySize, preset.velocitySize),
        tiltSize = maxOf(preset.dynamicsProfile.tiltSize, preset.tiltResponse),
    )
    return when (preset.id) {
        "pencil-hb" -> preset.copy(
            tipProfile = preset.tipProfile.copy(
                shape = BrushTipShape.OVAL,
                roundness = .38f,
                rotationMode = BrushRotationMode.STYLUS,
                rotationJitter = .018f,
            ),
            grainProfile = preset.grainProfile.copy(
                mode = BrushGrainMode.TEXTURIZED,
                source = BrushGrainSource.PAPER_FINE,
                scale = .86f,
                depth = .42f,
                contrast = .58f,
                movement = 0f,
            ),
            dynamicsProfile = dynamics.copy(
                sizePressure = BrushInputCurve(gamma = .92f, minimum = .2f, maximum = .78f),
                opacityPressure = BrushInputCurve(gamma = 1.18f, minimum = .06f, maximum = .76f),
                flowPressure = BrushInputCurve(gamma = 1.08f, minimum = .18f, maximum = .82f),
                velocitySize = .1f,
                velocityOpacity = .08f,
                tiltSize = .58f,
                tiltOpacity = .08f,
                tiltThreshold = .1f,
            ),
            renderProfile = preset.renderProfile.copy(buildup = .64f),
            dualBrushProfile = preset.dualBrushProfile.copy(opacity = .13f, scatter = .14f),
        )
        "pencil-2h", "mechanical-pencil" -> preset.copy(
            tipProfile = preset.tipProfile.copy(
                shape = BrushTipShape.ROUND,
                roundness = .92f,
                rotationMode = BrushRotationMode.FIXED,
                rotationJitter = 0f,
            ),
            grainProfile = preset.grainProfile.copy(
                mode = BrushGrainMode.TEXTURIZED,
                source = BrushGrainSource.PAPER_FINE,
                scale = .58f,
                depth = .12f,
                contrast = .42f,
                movement = 0f,
            ),
            dynamicsProfile = dynamics.copy(
                sizePressure = BrushInputCurve(gamma = 1.25f, minimum = .58f, maximum = 1f),
                opacityPressure = BrushInputCurve(gamma = 1.5f, minimum = .08f, maximum = .72f),
                flowPressure = BrushInputCurve(gamma = 1.2f, minimum = .56f, maximum = .92f),
                velocitySize = .04f,
                tiltSize = .08f,
                tiltOpacity = 0f,
            ),
            renderProfile = preset.renderProfile.copy(buildup = .48f),
            dualBrushProfile = DualBrushProfile(),
        )
        "pencil-6b", "graphite-shader" -> preset.copy(
            tipProfile = preset.tipProfile.copy(roundness = .3f, rotationMode = BrushRotationMode.STYLUS),
            grainProfile = preset.grainProfile.copy(
                mode = BrushGrainMode.TEXTURIZED,
                source = BrushGrainSource.PAPER_ROUGH,
                scale = 1.08f,
                depth = .62f,
                contrast = .7f,
                movement = 0f,
            ),
            dynamicsProfile = dynamics.copy(
                sizePressure = BrushInputCurve(gamma = .72f, minimum = .24f),
                opacityPressure = BrushInputCurve(gamma = .78f, minimum = .16f, maximum = .94f),
                flowPressure = BrushInputCurve(gamma = .82f, minimum = .24f),
                tiltSize = .96f,
                tiltOpacity = .12f,
                tiltThreshold = .06f,
            ),
            renderProfile = preset.renderProfile.copy(buildup = .82f),
            dualBrushProfile = preset.dualBrushProfile.copy(opacity = .28f, scatter = .32f),
        )
        "technical-ink" -> preset.copy(
            tipProfile = preset.tipProfile.copy(shape = BrushTipShape.ROUND, roundness = 1f),
            grainProfile = BrushGrainProfile(),
            dualBrushProfile = DualBrushProfile(),
            dynamicsProfile = dynamics.copy(
                sizePressure = BrushInputCurve(minimum = 1f),
                opacityPressure = BrushInputCurve(minimum = 1f),
                flowPressure = BrushInputCurve(minimum = 1f),
                velocitySize = 0f,
                velocityOpacity = 0f,
                tiltSize = 0f,
                tiltOpacity = 0f,
            ),
        )
        "marker" -> preset.copy(
            tipProfile = preset.tipProfile.copy(
                shape = BrushTipShape.ROUND,
                roundness = 1f,
                rotationMode = BrushRotationMode.FIXED,
                rotationJitter = 0f,
            ),
            grainProfile = BrushGrainProfile(),
            dualBrushProfile = DualBrushProfile(),
            dynamicsProfile = dynamics.copy(
                sizePressure = BrushInputCurve(minimum = .86f, maximum = 1f),
                opacityPressure = BrushInputCurve(gamma = .92f, minimum = .36f, maximum = .82f),
                flowPressure = BrushInputCurve(gamma = .88f, minimum = .68f, maximum = .94f),
                velocitySize = 0f,
                velocityOpacity = .05f,
                tiltSize = 0f,
                tiltOpacity = 0f,
            ),
            renderProfile = preset.renderProfile.copy(
                mode = BrushRenderMode.UNIFORM_GLAZE,
                buildup = .58f,
            ),
        )
        "gouache" -> preset.copy(
            tipProfile = preset.tipProfile.copy(
                shape = BrushTipShape.OVAL,
                roundness = .68f,
                rotationMode = BrushRotationMode.FOLLOW_STROKE,
                rotationJitter = .025f,
            ),
            grainProfile = preset.grainProfile.copy(
                mode = BrushGrainMode.TEXTURIZED,
                source = BrushGrainSource.CANVAS,
                scale = .82f,
                depth = .2f,
                contrast = .48f,
                movement = 0f,
            ),
            dynamicsProfile = dynamics.copy(
                sizePressure = BrushInputCurve(gamma = .86f, minimum = .34f),
                opacityPressure = BrushInputCurve(gamma = .82f, minimum = .72f, maximum = 1f),
                flowPressure = BrushInputCurve(gamma = .78f, minimum = .7f),
                velocityOpacity = .04f,
                tiltSize = .16f,
            ),
            renderProfile = preset.renderProfile.copy(
                mode = BrushRenderMode.INTENSE_GLAZE,
                buildup = .9f,
                wetness = .08f,
                drag = .12f,
                charge = .96f,
                attack = .78f,
                colorPickup = .06f,
            ),
            dualBrushProfile = DualBrushProfile(),
        )
        "airbrush", "hard-airbrush" -> preset.copy(
            tipProfile = BrushTipProfile(
                shape = BrushTipShape.ROUND,
                roundness = 1f,
                rotationMode = BrushRotationMode.FIXED,
            ),
            grainProfile = BrushGrainProfile(),
            dualBrushProfile = DualBrushProfile(),
            scatter = 0f,
            dynamicsProfile = dynamics.copy(
                sizePressure = BrushInputCurve(
                    gamma = 1f,
                    minimum = if (preset.id == "airbrush") .48f else .68f,
                ),
                opacityPressure = BrushInputCurve(
                    gamma = 1.12f,
                    minimum = if (preset.id == "airbrush") .025f else .12f,
                    maximum = if (preset.id == "airbrush") .62f else .82f,
                ),
                flowPressure = BrushInputCurve(gamma = 1f, minimum = .18f, maximum = .72f),
                velocitySize = 0f,
                velocityOpacity = 0f,
                tiltSize = 0f,
                tiltOpacity = 0f,
            ),
            renderProfile = preset.renderProfile.copy(
                mode = BrushRenderMode.LIGHT_GLAZE,
                buildup = if (preset.id == "airbrush") .34f else .52f,
            ),
        )
        "g-nib", "comic-nib", "manga-ink" -> preset.copy(
            tipProfile = preset.tipProfile.copy(
                shape = BrushTipShape.OVAL,
                roundness = .68f,
                rotationMode = BrushRotationMode.FOLLOW_STROKE,
            ),
            grainProfile = BrushGrainProfile(),
            dualBrushProfile = DualBrushProfile(),
            dynamicsProfile = dynamics.copy(
                sizePressure = BrushInputCurve(gamma = .58f, minimum = .015f),
                opacityPressure = BrushInputCurve(minimum = 1f),
                velocitySize = maxOf(.22f, dynamics.velocitySize),
            ),
        )
        "flat-marker" -> preset.copy(
            tipProfile = preset.tipProfile.copy(
                shape = BrushTipShape.CHISEL,
                roundness = .2f,
                angleDegrees = -22f,
                rotationMode = BrushRotationMode.STYLUS,
            ),
            grainProfile = preset.grainProfile.copy(
                mode = BrushGrainMode.TEXTURIZED,
                source = BrushGrainSource.PAPER_FINE,
                depth = .12f,
                movement = 0f,
            ),
            dynamicsProfile = dynamics.copy(
                sizePressure = BrushInputCurve(minimum = .82f, maximum = 1f),
                opacityPressure = BrushInputCurve(gamma = .9f, minimum = .48f, maximum = .9f),
                flowPressure = BrushInputCurve(gamma = .86f, minimum = .72f),
                tiltSize = .48f,
                tiltOpacity = .04f,
            ),
            renderProfile = preset.renderProfile.copy(buildup = .58f),
            dualBrushProfile = DualBrushProfile(),
        )
        "calligraphy-flat", "sumi-ink" -> preset.copy(
            tipProfile = preset.tipProfile.copy(
                shape = BrushTipShape.CHISEL,
                roundness = if (preset.id == "calligraphy-flat") .18f else .46f,
                rotationMode = BrushRotationMode.STYLUS,
                angleDegrees = -24f,
            ),
            dynamicsProfile = dynamics.copy(tiltSize = .56f, tiltOpacity = .18f),
        )
        "granulated-watercolor" -> preset.copy(
            grainProfile = preset.grainProfile.copy(
                scale = 1.72f,
                depth = maxOf(.76f, preset.grainProfile.depth),
                contrast = .76f,
            ),
            renderProfile = preset.renderProfile.copy(
                charge = .58f,
                attack = .28f,
                bleed = .82f,
                colorPickup = .5f,
            ),
            dynamicsProfile = dynamics.copy(velocityOpacity = .36f),
            dualBrushProfile = preset.dualBrushProfile.copy(opacity = .22f, scatter = .14f),
        )
        "wet-round" -> preset.copy(
            renderProfile = preset.renderProfile.copy(
                charge = .82f,
                attack = .12f,
                bleed = .66f,
                colorPickup = .56f,
            ),
            dynamicsProfile = dynamics.copy(velocityOpacity = .22f),
        )
        "impasto-bristle", "thick-oil" -> preset.copy(
            tipProfile = preset.tipProfile.copy(count = if (preset.id == "impasto-bristle") 11 else 8),
            renderProfile = preset.renderProfile.copy(
                charge = .96f,
                drag = if (preset.id == "impasto-bristle") .88f else .72f,
                colorPickup = .34f,
            ),
            dualBrushProfile = preset.dualBrushProfile.copy(opacity = .42f, sizeScale = .9f),
        )
        "dry-brush", "bristle" -> preset.copy(
            tipProfile = preset.tipProfile.copy(
                shape = BrushTipShape.BRISTLE,
                rotationMode = BrushRotationMode.FOLLOW_STROKE,
                count = if (preset.id == "dry-brush") 7 else 10,
                countJitter = if (preset.id == "dry-brush") .42f else .16f,
            ),
            grainProfile = preset.grainProfile.copy(
                mode = BrushGrainMode.MOVING,
                source = BrushGrainSource.BRISTLE,
                scale = if (preset.id == "dry-brush") .74f else .62f,
                depth = if (preset.id == "dry-brush") .72f else .42f,
                contrast = if (preset.id == "dry-brush") .82f else .58f,
                movement = .86f,
            ),
            dynamicsProfile = dynamics.copy(
                velocityOpacity = if (preset.id == "dry-brush") .34f else .12f,
                tiltSize = if (preset.id == "dry-brush") .62f else .38f,
            ),
            renderProfile = preset.renderProfile.copy(
                buildup = if (preset.id == "dry-brush") .38f else .7f,
                charge = if (preset.id == "dry-brush") .48f else .78f,
                attack = if (preset.id == "dry-brush") .08f else .26f,
                drag = if (preset.id == "dry-brush") .72f else .46f,
            ),
            dualBrushProfile = preset.dualBrushProfile.copy(
                opacity = if (preset.id == "dry-brush") .18f else .3f,
                scatter = if (preset.id == "dry-brush") .18f else .05f,
            ),
        )
        "charcoal", "pastel-soft", "chalk" -> preset.copy(
            tipProfile = preset.tipProfile.copy(
                shape = BrushTipShape.PARTICLE,
                roundness = .42f,
                rotationMode = BrushRotationMode.RANDOM,
                rotationJitter = .48f,
                count = 6,
                countJitter = .26f,
            ),
            grainProfile = preset.grainProfile.copy(
                mode = BrushGrainMode.TEXTURIZED,
                source = BrushGrainSource.PAPER_ROUGH,
                scale = 1.14f,
                depth = .68f,
                contrast = .74f,
                movement = 0f,
            ),
            scatter = minOf(preset.scatter, .16f),
            dynamicsProfile = dynamics.copy(
                sizePressure = BrushInputCurve(gamma = .8f, minimum = .18f),
                opacityPressure = BrushInputCurve(gamma = .92f, minimum = .08f, maximum = .92f),
                flowPressure = BrushInputCurve(gamma = .9f, minimum = .12f),
                tiltSize = .96f,
                tiltOpacity = .28f,
                tiltThreshold = .06f,
            ),
            renderProfile = preset.renderProfile.copy(buildup = .74f),
            dualBrushProfile = preset.dualBrushProfile.copy(
                opacity = if (preset.id == "charcoal") .22f else .16f,
                scatter = .34f,
            ),
        )
        else -> preset.copy(dynamicsProfile = dynamics)
    }
}
