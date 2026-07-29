package com.orbyte.canvasstudio.drawing

import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/**
 * Brush Engine 3.0 separates the visual source of a dab from the texture carried
 * by it and from the way pigment accumulates. Keeping these profiles independent
 * avoids the old situation where a family name merely selected hard-coded geometry.
 */
enum class BrushTipShape {
    ROUND,
    OVAL,
    CHISEL,
    BRISTLE,
    PARTICLE,
}

enum class BrushRotationMode {
    FOLLOW_STROKE,
    FIXED,
    STYLUS,
    RANDOM,
}

enum class BrushGrainMode {
    /** Texture travels with each dab, like material held by a brush or roller. */
    MOVING,

    /** Texture remains anchored to document coordinates, like paper tooth. */
    TEXTURIZED,
}

enum class BrushGrainSource {
    NONE,
    PAPER_FINE,
    PAPER_ROUGH,
    CANVAS,
    BRISTLE,
    WATERCOLOR,
}

enum class BrushRenderMode {
    LIGHT_GLAZE,
    UNIFORM_GLAZE,
    INTENSE_GLAZE,
    BLENDING,
}

enum class DualBrushBlendMode {
    NORMAL,
    MULTIPLY,
    SCREEN,
}

/**
 * Independent response curves prevent size, opacity and pigment flow from feeling
 * like three aliases of the same pressure slider.
 */
data class BrushInputCurve(
    val gamma: Float = 1f,
    val minimum: Float = 0f,
    val maximum: Float = 1f,
)

data class BrushDynamicsProfile(
    val sizePressure: BrushInputCurve = BrushInputCurve(),
    val opacityPressure: BrushInputCurve = BrushInputCurve(),
    val flowPressure: BrushInputCurve = BrushInputCurve(),
    val velocitySize: Float = 0f,
    val velocityOpacity: Float = 0f,
    val tiltSize: Float = 0f,
    val tiltOpacity: Float = 0f,
    val tiltThreshold: Float = 0.18f,
)

/**
 * A second independently shaped dab. It is deliberately non-recursive so custom
 * brushes cannot create unbounded render trees or multiply work per stamp.
 */
data class DualBrushProfile(
    val enabled: Boolean = false,
    val tip: BrushTipProfile = BrushTipProfile(),
    val grain: BrushGrainProfile = BrushGrainProfile(),
    val sizeScale: Float = 0.72f,
    val opacity: Float = 0.35f,
    val offset: Float = 0f,
    val scatter: Float = 0f,
    val blendMode: DualBrushBlendMode = DualBrushBlendMode.MULTIPLY,
)

data class BrushTipProfile(
    val shape: BrushTipShape = BrushTipShape.ROUND,
    val roundness: Float = 1f,
    val angleDegrees: Float = 0f,
    val rotationMode: BrushRotationMode = BrushRotationMode.FOLLOW_STROKE,
    val rotationJitter: Float = 0f,
    val count: Int = 1,
    val countJitter: Float = 0f,
)

data class BrushGrainProfile(
    val mode: BrushGrainMode = BrushGrainMode.MOVING,
    val scale: Float = 1f,
    val depth: Float = 0f,
    val contrast: Float = 0.5f,
    val movement: Float = 1f,
    val source: BrushGrainSource = BrushGrainSource.NONE,
)

data class BrushRenderProfile(
    val mode: BrushRenderMode = BrushRenderMode.UNIFORM_GLAZE,
    val buildup: Float = 0.5f,
    val wetness: Float = 0f,
    val dilution: Float = 0f,
    val drag: Float = 0f,
    val charge: Float = 1f,
    val attack: Float = 0f,
    val bleed: Float = 0f,
    val colorPickup: Float = 0f,
)

internal fun applyInputCurve(value: Float, curve: BrushInputCurve): Float {
    val normalized = value.coerceIn(0f, 1f).pow(curve.gamma.coerceIn(0.25f, 4f))
    val minimum = curve.minimum.coerceIn(0f, 1f)
    val maximum = curve.maximum.coerceIn(minimum, 1f)
    return minimum + normalized * (maximum - minimum)
}

internal fun mixPigmentColor(loadedColor: Int, sampledColor: Int?, pickup: Float): Int {
    val sampled = sampledColor ?: return loadedColor
    val sampledAlpha = sampled ushr 24 and 0xFF
    val amount = pickup.coerceIn(0f, 1f) * (sampledAlpha / 255f)
    if (amount <= .001f) return loadedColor

    fun mixChannel(shift: Int): Int {
        val loaded = ((loadedColor ushr shift) and 0xFF) / 255f
        val existing = ((sampled ushr shift) and 0xFF) / 255f
        val linearLoaded = loaded.pow(2.2f)
        val linearExisting = existing.pow(2.2f)
        return (
            (linearLoaded + (linearExisting - linearLoaded) * amount)
                .coerceIn(0f, 1f).pow(1f / 2.2f) * 255f
            ).toInt().coerceIn(0, 255)
    }

    return (0xFF shl 24) or
        (mixChannel(16) shl 16) or
        (mixChannel(8) shl 8) or
        mixChannel(0)
}

internal fun defaultDynamicsProfile(kind: BrushKind): BrushDynamicsProfile = when (kind) {
    BrushKind.PENCIL -> BrushDynamicsProfile(
        sizePressure = BrushInputCurve(gamma = .82f, minimum = .06f),
        opacityPressure = BrushInputCurve(gamma = 1.24f, minimum = .04f),
        flowPressure = BrushInputCurve(gamma = 1.08f, minimum = .08f),
        velocitySize = .12f,
        tiltSize = .72f,
        tiltOpacity = .18f,
        tiltThreshold = .12f,
    )
    BrushKind.INK -> BrushDynamicsProfile(
        sizePressure = BrushInputCurve(gamma = .68f, minimum = .02f),
        opacityPressure = BrushInputCurve(gamma = .78f, minimum = .32f),
        velocitySize = .18f,
    )
    BrushKind.MARKER -> BrushDynamicsProfile(
        sizePressure = BrushInputCurve(minimum = .72f),
        opacityPressure = BrushInputCurve(gamma = .9f, minimum = .28f),
        flowPressure = BrushInputCurve(gamma = .82f, minimum = .44f),
        tiltSize = .28f,
    )
    BrushKind.WATERCOLOR -> BrushDynamicsProfile(
        sizePressure = BrushInputCurve(gamma = .88f, minimum = .18f),
        opacityPressure = BrushInputCurve(gamma = 1.38f, minimum = .04f, maximum = .78f),
        flowPressure = BrushInputCurve(gamma = 1.2f, minimum = .08f, maximum = .7f),
        velocityOpacity = .28f,
        tiltSize = .34f,
    )
    BrushKind.OIL, BrushKind.BRISTLE, BrushKind.DRY_BRUSH -> BrushDynamicsProfile(
        sizePressure = BrushInputCurve(gamma = .78f, minimum = .14f),
        opacityPressure = BrushInputCurve(gamma = .86f, minimum = .18f),
        flowPressure = BrushInputCurve(gamma = .72f, minimum = .16f),
        velocityOpacity = .16f,
        tiltSize = .42f,
    )
    BrushKind.CHARCOAL, BrushKind.CHALK -> BrushDynamicsProfile(
        sizePressure = BrushInputCurve(gamma = .92f, minimum = .16f),
        opacityPressure = BrushInputCurve(gamma = 1.3f, minimum = .04f),
        flowPressure = BrushInputCurve(gamma = 1.18f, minimum = .06f),
        tiltSize = .86f,
        tiltOpacity = .34f,
        tiltThreshold = .08f,
    )
    BrushKind.PAINT, BrushKind.AIRBRUSH -> BrushDynamicsProfile(
        sizePressure = BrushInputCurve(gamma = .9f, minimum = .2f),
        opacityPressure = BrushInputCurve(gamma = 1.12f, minimum = .08f),
        flowPressure = BrushInputCurve(gamma = .94f, minimum = .12f),
        velocityOpacity = .12f,
        tiltSize = .16f,
    )
}

internal fun defaultDualBrushProfile(kind: BrushKind): DualBrushProfile = when (kind) {
    BrushKind.PENCIL -> DualBrushProfile(
        enabled = true,
        tip = BrushTipProfile(
            shape = BrushTipShape.PARTICLE,
            roundness = .3f,
            rotationMode = BrushRotationMode.RANDOM,
            rotationJitter = .8f,
            count = 2,
            countJitter = .35f,
        ),
        grain = BrushGrainProfile(
            mode = BrushGrainMode.TEXTURIZED,
            scale = .58f,
            depth = .42f,
            contrast = .74f,
            movement = 0f,
            source = BrushGrainSource.PAPER_FINE,
        ),
        sizeScale = .46f,
        opacity = .2f,
        scatter = .26f,
    )
    BrushKind.CHARCOAL, BrushKind.CHALK -> DualBrushProfile(
        enabled = true,
        tip = BrushTipProfile(
            shape = BrushTipShape.PARTICLE,
            roundness = .44f,
            rotationMode = BrushRotationMode.RANDOM,
            rotationJitter = 1f,
            count = 3,
            countJitter = .5f,
        ),
        grain = BrushGrainProfile(
            mode = BrushGrainMode.TEXTURIZED,
            scale = 1.3f,
            depth = .72f,
            contrast = .86f,
            movement = 0f,
            source = BrushGrainSource.PAPER_ROUGH,
        ),
        sizeScale = .64f,
        opacity = .28f,
        scatter = .72f,
    )
    BrushKind.DRY_BRUSH, BrushKind.BRISTLE, BrushKind.OIL -> DualBrushProfile(
        enabled = true,
        tip = BrushTipProfile(
            shape = BrushTipShape.BRISTLE,
            roundness = .14f,
            rotationMode = BrushRotationMode.FOLLOW_STROKE,
            count = 4,
            countJitter = .3f,
        ),
        grain = BrushGrainProfile(
            mode = BrushGrainMode.MOVING,
            scale = .7f,
            depth = .54f,
            contrast = .76f,
            movement = .9f,
            source = BrushGrainSource.BRISTLE,
        ),
        sizeScale = .82f,
        opacity = .32f,
        offset = .08f,
        scatter = .12f,
    )
    BrushKind.WATERCOLOR -> DualBrushProfile(
        enabled = true,
        tip = BrushTipProfile(
            shape = BrushTipShape.OVAL,
            roundness = .74f,
            rotationMode = BrushRotationMode.RANDOM,
            rotationJitter = .35f,
        ),
        grain = BrushGrainProfile(
            mode = BrushGrainMode.TEXTURIZED,
            scale = 1.6f,
            depth = .48f,
            contrast = .62f,
            movement = .04f,
            source = BrushGrainSource.WATERCOLOR,
        ),
        sizeScale = 1.06f,
        opacity = .14f,
        scatter = .08f,
        blendMode = DualBrushBlendMode.NORMAL,
    )
    else -> DualBrushProfile()
}

fun defaultTipProfile(kind: BrushKind): BrushTipProfile = when (kind) {
    BrushKind.PENCIL -> BrushTipProfile(
        shape = BrushTipShape.OVAL,
        roundness = 0.34f,
        rotationMode = BrushRotationMode.STYLUS,
        rotationJitter = 0.03f,
    )
    BrushKind.INK -> BrushTipProfile()
    BrushKind.MARKER -> BrushTipProfile(
        shape = BrushTipShape.CHISEL,
        roundness = 0.3f,
        angleDegrees = -18f,
        rotationMode = BrushRotationMode.STYLUS,
    )
    BrushKind.PAINT -> BrushTipProfile(
        shape = BrushTipShape.OVAL,
        roundness = 0.72f,
        count = 2,
        countJitter = 0.15f,
    )
    BrushKind.AIRBRUSH -> BrushTipProfile(rotationMode = BrushRotationMode.RANDOM)
    BrushKind.CHARCOAL -> BrushTipProfile(
        shape = BrushTipShape.PARTICLE,
        roundness = 0.5f,
        rotationMode = BrushRotationMode.RANDOM,
        rotationJitter = 0.85f,
        count = 5,
        countJitter = 0.4f,
    )
    BrushKind.CHALK -> BrushTipProfile(
        shape = BrushTipShape.PARTICLE,
        roundness = 0.62f,
        rotationMode = BrushRotationMode.RANDOM,
        rotationJitter = 0.7f,
        count = 4,
        countJitter = 0.35f,
    )
    BrushKind.DRY_BRUSH -> BrushTipProfile(
        shape = BrushTipShape.BRISTLE,
        roundness = 0.2f,
        count = 5,
        countJitter = 0.3f,
    )
    BrushKind.BRISTLE -> BrushTipProfile(
        shape = BrushTipShape.BRISTLE,
        roundness = 0.16f,
        count = 7,
        countJitter = 0.18f,
    )
    BrushKind.WATERCOLOR -> BrushTipProfile(
        shape = BrushTipShape.OVAL,
        roundness = 0.84f,
        rotationMode = BrushRotationMode.RANDOM,
        rotationJitter = 0.18f,
        count = 2,
    )
    BrushKind.OIL -> BrushTipProfile(
        shape = BrushTipShape.BRISTLE,
        roundness = 0.2f,
        count = 8,
        countJitter = 0.16f,
    )
}

fun defaultGrainProfile(kind: BrushKind, amount: Float): BrushGrainProfile {
    val depth = amount.coerceIn(0f, 1f)
    return when (kind) {
        BrushKind.PENCIL, BrushKind.CHARCOAL, BrushKind.CHALK -> BrushGrainProfile(
            mode = BrushGrainMode.TEXTURIZED,
            scale = if (kind == BrushKind.PENCIL) 0.72f else 1.18f,
            depth = depth,
            contrast = if (kind == BrushKind.PENCIL) 0.66f else 0.82f,
            movement = 0f,
            source = if (kind == BrushKind.PENCIL) {
                BrushGrainSource.PAPER_FINE
            } else {
                BrushGrainSource.PAPER_ROUGH
            },
        )
        BrushKind.WATERCOLOR -> BrushGrainProfile(
            mode = BrushGrainMode.TEXTURIZED,
            scale = 1.4f,
            depth = depth,
            contrast = 0.58f,
            movement = 0.08f,
            source = BrushGrainSource.WATERCOLOR,
        )
        BrushKind.DRY_BRUSH, BrushKind.BRISTLE, BrushKind.OIL -> BrushGrainProfile(
            mode = BrushGrainMode.MOVING,
            scale = 0.82f,
            depth = depth,
            contrast = 0.72f,
            movement = 0.9f,
            source = if (kind == BrushKind.OIL) {
                BrushGrainSource.CANVAS
            } else {
                BrushGrainSource.BRISTLE
            },
        )
        BrushKind.PAINT -> BrushGrainProfile(
            depth = depth,
            source = BrushGrainSource.CANVAS,
        )
        else -> BrushGrainProfile(depth = depth, source = BrushGrainSource.NONE)
    }
}

fun defaultRenderProfile(kind: BrushKind): BrushRenderProfile = when (kind) {
    BrushKind.PENCIL -> BrushRenderProfile(BrushRenderMode.UNIFORM_GLAZE, buildup = 0.3f)
    BrushKind.INK -> BrushRenderProfile(BrushRenderMode.INTENSE_GLAZE, buildup = 0.95f)
    BrushKind.MARKER -> BrushRenderProfile(BrushRenderMode.UNIFORM_GLAZE, buildup = 0.22f)
    BrushKind.AIRBRUSH -> BrushRenderProfile(BrushRenderMode.LIGHT_GLAZE, buildup = 0.16f)
    BrushKind.WATERCOLOR -> BrushRenderProfile(
        mode = BrushRenderMode.LIGHT_GLAZE,
        buildup = 0.12f,
        wetness = 0.82f,
        dilution = 0.72f,
        drag = 0.2f,
        charge = .72f,
        attack = .18f,
        bleed = .7f,
        colorPickup = .42f,
    )
    BrushKind.OIL -> BrushRenderProfile(
        mode = BrushRenderMode.BLENDING,
        buildup = 0.86f,
        wetness = 0.28f,
        dilution = 0.04f,
        drag = 0.76f,
        charge = .92f,
        attack = .08f,
        bleed = .08f,
        colorPickup = .28f,
    )
    BrushKind.PAINT, BrushKind.BRISTLE -> BrushRenderProfile(
        mode = BrushRenderMode.BLENDING,
        buildup = 0.68f,
        drag = 0.46f,
        charge = .86f,
        attack = .06f,
        colorPickup = .12f,
    )
    BrushKind.CHARCOAL, BrushKind.CHALK, BrushKind.DRY_BRUSH -> BrushRenderProfile(
        mode = BrushRenderMode.UNIFORM_GLAZE,
        buildup = 0.42f,
    )
}

internal fun renderAlphaMultiplier(profile: BrushRenderProfile): Float {
    val buildup = profile.buildup.coerceIn(0f, 1f)
    return when (profile.mode) {
        BrushRenderMode.LIGHT_GLAZE -> 0.28f + buildup * 0.34f
        BrushRenderMode.UNIFORM_GLAZE -> 0.56f + buildup * 0.36f
        BrushRenderMode.INTENSE_GLAZE -> 0.82f + buildup * 0.18f
        BrushRenderMode.BLENDING -> 0.68f + buildup * 0.28f
    }
}

/**
 * Cheap deterministic document-space grain. TEXTURIZED mode stays fixed under
 * the stroke (paper tooth); MOVING mode incorporates the dab index.
 */
internal fun grainCoverage(
    profile: BrushGrainProfile,
    x: Float,
    y: Float,
    stampIndex: Int,
): Float {
    val depth = profile.depth.coerceIn(0f, 1f)
    if (depth <= 0.001f) return 1f
    val scale = profile.scale.coerceIn(0.08f, 8f)
    val frequency = when (profile.source) {
        BrushGrainSource.PAPER_FINE -> 1f / (2.2f * scale)
        BrushGrainSource.PAPER_ROUGH -> 1f / (5.2f * scale)
        BrushGrainSource.CANVAS -> 1f / (6.4f * scale)
        BrushGrainSource.BRISTLE -> 1f / (3.8f * scale)
        BrushGrainSource.WATERCOLOR -> 1f / (8.5f * scale)
        BrushGrainSource.NONE -> 1f / (3.5f * scale)
    }
    val movingSeed = if (profile.mode == BrushGrainMode.MOVING) {
        (stampIndex * 374_761_393 * profile.movement.coerceIn(0f, 1f)).toInt()
    } else {
        0
    }
    val base = valueNoise(x * frequency, y * frequency, movingSeed)
    val detail = valueNoise(
        x * frequency * 2.17f + 19.4f,
        y * frequency * 2.17f - 7.8f,
        movingSeed xor 0x45D9F3B,
    )
    val material = when (profile.source) {
        BrushGrainSource.CANVAS -> {
            val weaveX = sin(x * frequency * 3.1f) * .5f + .5f
            val weaveY = sin(y * frequency * 3.4f + .9f) * .5f + .5f
            (base * .48f + detail * .2f + weaveX * weaveY * .32f)
        }
        BrushGrainSource.BRISTLE -> {
            val strand = sin((x + y * .16f) * frequency * 5.2f + detail * 2.4f) * .5f + .5f
            base * .38f + strand * .62f
        }
        BrushGrainSource.WATERCOLOR -> base * .72f + detail * .28f
        BrushGrainSource.PAPER_ROUGH -> base * .62f + detail * .38f
        BrushGrainSource.PAPER_FINE -> base * .42f + detail * .58f
        BrushGrainSource.NONE -> base
    }.coerceIn(0f, 1f)
    val contrast = profile.contrast.coerceIn(0f, 1f)
    val shaped = ((material - 0.5f) * (1f + contrast * 2.4f) + 0.5f).coerceIn(0f, 1f)
    return (1f - depth * (0.2f + shaped * 0.72f)).coerceIn(0.06f, 1f)
}

private fun valueNoise(x: Float, y: Float, seed: Int): Float {
    val x0 = floor(x).toInt()
    val y0 = floor(y).toInt()
    val tx = smoothStep(x - floor(x))
    val ty = smoothStep(y - floor(y))
    val top = lerp(hashNoise(x0, y0, seed), hashNoise(x0 + 1, y0, seed), tx)
    val bottom = lerp(hashNoise(x0, y0 + 1, seed), hashNoise(x0 + 1, y0 + 1, seed), tx)
    return lerp(top, bottom, ty)
}

private fun hashNoise(x: Int, y: Int, seed: Int): Float {
    var hash = x * 73856093 xor y * 19349663 xor seed
    hash = (hash xor (hash ushr 13)) * 1274126177
    return (hash ushr 8 and 0xFFFF) / 65535f
}

private fun smoothStep(value: Float): Float = value * value * (3f - 2f * value)

private fun lerp(start: Float, end: Float, amount: Float): Float =
    start + (end - start) * amount
