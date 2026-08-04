package com.orbyte.canvasstudio.drawing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object BrushRepository {
    private const val PREFERENCES = "canvas_studio_brushes"
    private const val KEY_CUSTOM_BRUSHES = "custom_brushes_v1"
    private const val KEY_FAVORITES = "favorite_brushes_v1"
    private const val KEY_RECENTS = "recent_brushes_v1"
    private const val KEY_SHOW_EXPERIMENTAL = "show_experimental_brushes_v1"
    private const val EXPORT_VERSION = 1
    const val MAX_CUSTOM_BRUSHES = 80
    const val MAX_RECENT_BRUSHES = 12

    fun load(context: Context): List<BrushPreset> {
        val raw = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM_BRUSHES, null)
            ?: return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                repeat(array.length()) { index ->
                    val item = array.optJSONObject(index) ?: return@repeat
                    decode(item)?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, brushes: List<BrushPreset>) {
        val array = JSONArray()
        brushes.takeLast(MAX_CUSTOM_BRUSHES).forEach { brush -> array.put(encode(brush)) }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_BRUSHES, array.toString())
            .apply()
    }

    fun loadFavorites(context: Context): Set<String> {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val stored = preferences
            .getStringSet(KEY_FAVORITES, emptySet())
            ?.toSet()
            .orEmpty()
        val migrated = stored.mapTo(linkedSetOf(), ::migrateStoredBrushId)
        if (migrated != stored) preferences.edit().putStringSet(KEY_FAVORITES, migrated).apply()
        return migrated
    }

    fun saveFavorites(context: Context, ids: Set<String>) {
        val migrated = ids.mapTo(linkedSetOf(), ::migrateStoredBrushId)
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_FAVORITES, migrated)
            .apply()
    }

    fun loadRecents(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_RECENTS, null)
            ?: return emptyList()
        val decoded = runCatching {
            val array = JSONArray(raw)
            buildList {
                repeat(array.length()) { index ->
                    array.optString(index).takeIf(String::isNotBlank)?.let {
                        add(migrateStoredBrushId(it))
                    }
                }
            }.distinct().take(MAX_RECENT_BRUSHES)
        }.getOrDefault(emptyList())
        if (decoded.isNotEmpty()) saveRecents(context, decoded)
        return decoded
    }

    fun recordRecent(context: Context, brushId: String, previous: List<String>): List<String> {
        val migratedId = migrateStoredBrushId(brushId)
        val updated = (listOf(migratedId) + previous.map(::migrateStoredBrushId).filterNot { it == migratedId })
            .distinct()
            .take(MAX_RECENT_BRUSHES)
        saveRecents(context, updated)
        return updated
    }

    fun loadExperimentalVisibility(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_EXPERIMENTAL, false)

    fun saveExperimentalVisibility(context: Context, visible: Boolean) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOW_EXPERIMENTAL, visible)
            .apply()
    }

    private fun saveRecents(context: Context, ids: List<String>) {
        val array = JSONArray()
        ids.take(MAX_RECENT_BRUSHES).forEach(array::put)
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECENTS, array.toString())
            .apply()
    }

    private fun migrateStoredBrushId(id: String): String =
        if (id.startsWith("custom-")) id else migrateBuiltInBrushId(id)

    fun exportJson(brushes: List<BrushPreset>): String = exportJsonInternal(brushes, embedAssets = false)

    fun exportJsonWithAssets(brushes: List<BrushPreset>): String =
        exportJsonInternal(brushes, embedAssets = true)

    private fun exportJsonInternal(brushes: List<BrushPreset>, embedAssets: Boolean): String = JSONObject().apply {
        put("format", "CanvasStudioBrushLibrary")
        put("version", EXPORT_VERSION)
        put("brushes", JSONArray().apply {
            brushes.takeLast(MAX_CUSTOM_BRUSHES).forEach { brush ->
                put(
                    encode(brush).apply {
                        if (embedAssets) {
                            brush.tipAssetPath
                                ?.let(::File)
                                ?.takeIf { it.isFile && it.length() in 1..MAX_EMBEDDED_TIP_BYTES }
                                ?.readBytes()
                                ?.let { bytes -> put("tipAssetPng", Base64.encodeToString(bytes, Base64.NO_WRAP)) }
                        }
                    },
                )
            }
        })
    }.toString(2)

    fun importJson(raw: String): List<BrushPreset> = runCatching {
        val root = JSONObject(raw)
        require(root.optString("format") == "CanvasStudioBrushLibrary") {
            "El archivo no es una biblioteca de Canvas Studio."
        }
        require(root.optInt("version", -1) in 1..EXPORT_VERSION) {
            "La versión de la biblioteca no es compatible."
        }
        val array = root.getJSONArray("brushes")
        buildList {
            repeat(array.length().coerceAtMost(MAX_CUSTOM_BRUSHES)) { index ->
                decode(array.optJSONObject(index) ?: return@repeat)?.let { brush ->
                    add(
                        brush.copy(
                            id = "custom-${System.currentTimeMillis()}-$index",
                            category = "Personalizados",
                        ),
                    )
                }
            }
        }
    }.getOrElse { error ->
        throw IllegalArgumentException(error.message ?: "No se pudo importar la biblioteca.", error)
    }

    fun importJsonWithAssets(context: Context, raw: String): List<BrushPreset> {
        val imported = importJson(raw)
        val root = JSONObject(raw)
        val source = root.getJSONArray("brushes")
        return imported.mapIndexed { index, brush ->
            val encoded = source.optJSONObject(index)?.optString("tipAssetPng").orEmpty()
            if (encoded.isBlank()) return@mapIndexed brush.copy(tipAssetPath = null)
            val bytes = runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull()
                ?.takeIf { it.size.toLong() in 1L..MAX_EMBEDDED_TIP_BYTES }
                ?: return@mapIndexed brush.copy(tipAssetPath = null)
            val directory = File(context.filesDir, "brush-tips").apply { mkdirs() }
            val output = File(directory, "${UUID.randomUUID()}.png")
            output.writeBytes(bytes)
            brush.copy(tipAssetPath = output.absolutePath)
        }
    }

    fun importTipAsset(context: Context, uri: Uri): String {
        val decoded = context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
            ?: throw IllegalArgumentException("No se pudo leer la imagen de la punta.")
        require(decoded.width > 0 && decoded.height > 0) { "La imagen de la punta está vacía." }
        val scale = (256f / maxOf(decoded.width, decoded.height)).coerceAtMost(1f)
        val width = (decoded.width * scale).toInt().coerceAtLeast(1)
        val height = (decoded.height * scale).toInt().coerceAtLeast(1)
        val source = if (width == decoded.width && height == decoded.height) {
            decoded
        } else {
            Bitmap.createScaledBitmap(decoded, width, height, true).also { decoded.recycle() }
        }
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val hasTransparency = pixels.any { Color.alpha(it) < 250 }
        pixels.indices.forEach { index ->
            val pixel = pixels[index]
            val alpha = if (hasTransparency) {
                Color.alpha(pixel)
            } else {
                val luminance = (
                    Color.red(pixel) * .2126f +
                        Color.green(pixel) * .7152f +
                        Color.blue(pixel) * .0722f
                    ).toInt()
                255 - luminance
            }
            pixels[index] = Color.argb(alpha.coerceIn(0, 255), 255, 255, 255)
        }
        source.recycle()
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        mask.setPixels(pixels, 0, width, 0, 0, width, height)
        val directory = File(context.filesDir, "brush-tips").apply { mkdirs() }
        val output = File(directory, "${UUID.randomUUID()}.png")
        output.outputStream().use { stream ->
            check(mask.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                "No se pudo guardar la punta."
            }
        }
        mask.recycle()
        return output.absolutePath
    }

    private fun encode(brush: BrushPreset): JSONObject = JSONObject().apply {
        put("id", brush.id)
        put("name", brush.name)
        put("category", brush.category)
        put("kind", brush.kind.name)
        put("sizePx", brush.sizePx.toDouble())
        put("opacity", brush.opacity.toDouble())
        put("hardness", brush.hardness.toDouble())
        put("spacing", brush.spacing.toDouble())
        put("stabilization", brush.stabilization.toDouble())
        put("flow", brush.flow.toDouble())
        put("minSize", brush.minSize.toDouble())
        put("pressureSize", brush.pressureSize)
        put("pressureOpacity", brush.pressureOpacity)
        put("pressureCurve", brush.pressureCurve.toDouble())
        put("tiltResponse", brush.tiltResponse.toDouble())
        put("taperStart", brush.taperStart.toDouble())
        put("taperEnd", brush.taperEnd.toDouble())
        put("scatter", brush.scatter.toDouble())
        put("grain", brush.grain.toDouble())
        put("velocitySize", brush.velocitySize.toDouble())
        brush.tipAssetPath?.let { put("tipAssetPath", it) }
        put("tipProfile", JSONObject().apply {
            put("shape", brush.tipProfile.shape.name)
            put("roundness", brush.tipProfile.roundness.toDouble())
            put("angleDegrees", brush.tipProfile.angleDegrees.toDouble())
            put("rotationMode", brush.tipProfile.rotationMode.name)
            put("rotationJitter", brush.tipProfile.rotationJitter.toDouble())
            put("count", brush.tipProfile.count)
            put("countJitter", brush.tipProfile.countJitter.toDouble())
        })
        put("grainProfile", JSONObject().apply {
            put("mode", brush.grainProfile.mode.name)
            put("scale", brush.grainProfile.scale.toDouble())
            put("depth", brush.grainProfile.depth.toDouble())
            put("contrast", brush.grainProfile.contrast.toDouble())
            put("movement", brush.grainProfile.movement.toDouble())
            put("source", brush.grainProfile.source.name)
        })
        put("renderProfile", JSONObject().apply {
            put("mode", brush.renderProfile.mode.name)
            put("buildup", brush.renderProfile.buildup.toDouble())
            put("wetness", brush.renderProfile.wetness.toDouble())
            put("dilution", brush.renderProfile.dilution.toDouble())
            put("drag", brush.renderProfile.drag.toDouble())
            put("charge", brush.renderProfile.charge.toDouble())
            put("attack", brush.renderProfile.attack.toDouble())
            put("bleed", brush.renderProfile.bleed.toDouble())
            put("colorPickup", brush.renderProfile.colorPickup.toDouble())
        })
        put("dynamicsProfile", JSONObject().apply {
            put("sizePressure", encodeCurve(brush.dynamicsProfile.sizePressure))
            put("opacityPressure", encodeCurve(brush.dynamicsProfile.opacityPressure))
            put("flowPressure", encodeCurve(brush.dynamicsProfile.flowPressure))
            put("velocitySize", brush.dynamicsProfile.velocitySize.toDouble())
            put("velocityOpacity", brush.dynamicsProfile.velocityOpacity.toDouble())
            put("tiltSize", brush.dynamicsProfile.tiltSize.toDouble())
            put("tiltOpacity", brush.dynamicsProfile.tiltOpacity.toDouble())
            put("tiltThreshold", brush.dynamicsProfile.tiltThreshold.toDouble())
        })
        put("dualBrushProfile", JSONObject().apply {
            put("enabled", brush.dualBrushProfile.enabled)
            put("sizeScale", brush.dualBrushProfile.sizeScale.toDouble())
            put("opacity", brush.dualBrushProfile.opacity.toDouble())
            put("offset", brush.dualBrushProfile.offset.toDouble())
            put("scatter", brush.dualBrushProfile.scatter.toDouble())
            put("blendMode", brush.dualBrushProfile.blendMode.name)
            put("tip", JSONObject().apply {
                put("shape", brush.dualBrushProfile.tip.shape.name)
                put("roundness", brush.dualBrushProfile.tip.roundness.toDouble())
                put("angleDegrees", brush.dualBrushProfile.tip.angleDegrees.toDouble())
                put("rotationMode", brush.dualBrushProfile.tip.rotationMode.name)
                put("rotationJitter", brush.dualBrushProfile.tip.rotationJitter.toDouble())
                put("count", brush.dualBrushProfile.tip.count)
                put("countJitter", brush.dualBrushProfile.tip.countJitter.toDouble())
            })
            put("grain", JSONObject().apply {
                put("mode", brush.dualBrushProfile.grain.mode.name)
                put("scale", brush.dualBrushProfile.grain.scale.toDouble())
                put("depth", brush.dualBrushProfile.grain.depth.toDouble())
                put("contrast", brush.dualBrushProfile.grain.contrast.toDouble())
                put("movement", brush.dualBrushProfile.grain.movement.toDouble())
                put("source", brush.dualBrushProfile.grain.source.name)
            })
        })
    }

    private fun encodeCurve(curve: BrushInputCurve): JSONObject = JSONObject().apply {
        put("gamma", curve.gamma.toDouble())
        put("minimum", curve.minimum.toDouble())
        put("maximum", curve.maximum.toDouble())
    }

    private fun decode(item: JSONObject): BrushPreset? = runCatching {
        val kind = enumValueOrDefault(
            item.optString("kind"),
            BrushKind.PENCIL,
        )
        val grain = item.optDouble("grain", 0.0).toFloat()
        val defaultTip = defaultTipProfile(kind)
        val defaultGrain = defaultGrainProfile(kind, grain)
        val defaultRender = defaultRenderProfile(kind)
        val tipJson = item.optJSONObject("tipProfile")
        val grainJson = item.optJSONObject("grainProfile")
        val renderJson = item.optJSONObject("renderProfile")
        val dynamicsJson = item.optJSONObject("dynamicsProfile")
        val dualJson = item.optJSONObject("dualBrushProfile")
        val defaultDynamics = defaultDynamicsProfile(kind)
        val defaultDual = defaultDualBrushProfile(kind)
        BrushPreset(
            id = item.getString("id"),
            name = item.optString("name", "Pincel personalizado"),
            category = item.optString("category", "Personalizados"),
            kind = kind,
            sizePx = item.optDouble("sizePx", 24.0).toFloat(),
            opacity = item.optDouble("opacity", 1.0).toFloat(),
            hardness = item.optDouble("hardness", .85).toFloat(),
            spacing = item.optDouble("spacing", .12).toFloat(),
            stabilization = item.optDouble("stabilization", .22).toFloat(),
            flow = item.optDouble("flow", 1.0).toFloat(),
            minSize = item.optDouble("minSize", .22).toFloat(),
            pressureSize = item.optBoolean("pressureSize", true),
            pressureOpacity = item.optBoolean("pressureOpacity", false),
            pressureCurve = item.optDouble("pressureCurve", 1.0).toFloat(),
            tiltResponse = item.optDouble("tiltResponse", 0.0).toFloat(),
            taperStart = item.optDouble("taperStart", .08).toFloat(),
            taperEnd = item.optDouble("taperEnd", .06).toFloat(),
            scatter = item.optDouble("scatter", 0.0).toFloat(),
            grain = grain,
            velocitySize = item.optDouble("velocitySize", 0.0).toFloat(),
            tipAssetPath = item.optString("tipAssetPath").takeIf(String::isNotBlank),
            tipProfile = BrushTipProfile(
                shape = enumValueOrDefault(tipJson?.optString("shape"), defaultTip.shape),
                roundness = tipJson?.optDouble("roundness", defaultTip.roundness.toDouble())?.toFloat()
                    ?: defaultTip.roundness,
                angleDegrees = tipJson?.optDouble("angleDegrees", defaultTip.angleDegrees.toDouble())?.toFloat()
                    ?: defaultTip.angleDegrees,
                rotationMode = enumValueOrDefault(
                    tipJson?.optString("rotationMode"),
                    defaultTip.rotationMode,
                ),
                rotationJitter = tipJson?.optDouble(
                    "rotationJitter",
                    defaultTip.rotationJitter.toDouble(),
                )?.toFloat() ?: defaultTip.rotationJitter,
                count = tipJson?.optInt("count", defaultTip.count) ?: defaultTip.count,
                countJitter = tipJson?.optDouble(
                    "countJitter",
                    defaultTip.countJitter.toDouble(),
                )?.toFloat() ?: defaultTip.countJitter,
            ),
            grainProfile = BrushGrainProfile(
                mode = enumValueOrDefault(grainJson?.optString("mode"), defaultGrain.mode),
                scale = grainJson?.optDouble("scale", defaultGrain.scale.toDouble())?.toFloat()
                    ?: defaultGrain.scale,
                depth = grainJson?.optDouble("depth", defaultGrain.depth.toDouble())?.toFloat()
                    ?: defaultGrain.depth,
                contrast = grainJson?.optDouble(
                    "contrast",
                    defaultGrain.contrast.toDouble(),
                )?.toFloat() ?: defaultGrain.contrast,
                movement = grainJson?.optDouble(
                    "movement",
                    defaultGrain.movement.toDouble(),
                )?.toFloat() ?: defaultGrain.movement,
                source = enumValueOrDefault(
                    grainJson?.optString("source"),
                    defaultGrain.source,
                ),
            ),
            renderProfile = BrushRenderProfile(
                mode = enumValueOrDefault(renderJson?.optString("mode"), defaultRender.mode),
                buildup = renderJson?.optDouble(
                    "buildup",
                    defaultRender.buildup.toDouble(),
                )?.toFloat() ?: defaultRender.buildup,
                wetness = renderJson?.optDouble(
                    "wetness",
                    defaultRender.wetness.toDouble(),
                )?.toFloat() ?: defaultRender.wetness,
                dilution = renderJson?.optDouble(
                    "dilution",
                    defaultRender.dilution.toDouble(),
                )?.toFloat() ?: defaultRender.dilution,
                drag = renderJson?.optDouble("drag", defaultRender.drag.toDouble())?.toFloat()
                    ?: defaultRender.drag,
                charge = renderJson?.optDouble("charge", defaultRender.charge.toDouble())?.toFloat()
                    ?: defaultRender.charge,
                attack = renderJson?.optDouble("attack", defaultRender.attack.toDouble())?.toFloat()
                    ?: defaultRender.attack,
                bleed = renderJson?.optDouble("bleed", defaultRender.bleed.toDouble())?.toFloat()
                    ?: defaultRender.bleed,
                colorPickup = renderJson?.optDouble(
                    "colorPickup",
                    defaultRender.colorPickup.toDouble(),
                )?.toFloat() ?: defaultRender.colorPickup,
            ),
            dynamicsProfile = BrushDynamicsProfile(
                sizePressure = decodeCurve(
                    dynamicsJson?.optJSONObject("sizePressure"),
                    defaultDynamics.sizePressure,
                ),
                opacityPressure = decodeCurve(
                    dynamicsJson?.optJSONObject("opacityPressure"),
                    defaultDynamics.opacityPressure,
                ),
                flowPressure = decodeCurve(
                    dynamicsJson?.optJSONObject("flowPressure"),
                    defaultDynamics.flowPressure,
                ),
                velocitySize = dynamicsJson?.optDouble(
                    "velocitySize",
                    defaultDynamics.velocitySize.toDouble(),
                )?.toFloat() ?: defaultDynamics.velocitySize,
                velocityOpacity = dynamicsJson?.optDouble(
                    "velocityOpacity",
                    defaultDynamics.velocityOpacity.toDouble(),
                )?.toFloat() ?: defaultDynamics.velocityOpacity,
                tiltSize = dynamicsJson?.optDouble(
                    "tiltSize",
                    defaultDynamics.tiltSize.toDouble(),
                )?.toFloat() ?: defaultDynamics.tiltSize,
                tiltOpacity = dynamicsJson?.optDouble(
                    "tiltOpacity",
                    defaultDynamics.tiltOpacity.toDouble(),
                )?.toFloat() ?: defaultDynamics.tiltOpacity,
                tiltThreshold = dynamicsJson?.optDouble(
                    "tiltThreshold",
                    defaultDynamics.tiltThreshold.toDouble(),
                )?.toFloat() ?: defaultDynamics.tiltThreshold,
            ),
            dualBrushProfile = decodeDualBrush(dualJson, defaultDual),
        )
    }.getOrNull()

    private fun decodeCurve(json: JSONObject?, default: BrushInputCurve): BrushInputCurve =
        BrushInputCurve(
            gamma = json?.optDouble("gamma", default.gamma.toDouble())?.toFloat() ?: default.gamma,
            minimum = json?.optDouble("minimum", default.minimum.toDouble())?.toFloat()
                ?: default.minimum,
            maximum = json?.optDouble("maximum", default.maximum.toDouble())?.toFloat()
                ?: default.maximum,
        )

    private fun decodeDualBrush(json: JSONObject?, default: DualBrushProfile): DualBrushProfile {
        if (json == null) return default
        val tip = json.optJSONObject("tip")
        val grain = json.optJSONObject("grain")
        return DualBrushProfile(
            enabled = json.optBoolean("enabled", default.enabled),
            tip = BrushTipProfile(
                shape = enumValueOrDefault(tip?.optString("shape"), default.tip.shape),
                roundness = tip?.optDouble("roundness", default.tip.roundness.toDouble())?.toFloat()
                    ?: default.tip.roundness,
                angleDegrees = tip?.optDouble(
                    "angleDegrees",
                    default.tip.angleDegrees.toDouble(),
                )?.toFloat() ?: default.tip.angleDegrees,
                rotationMode = enumValueOrDefault(
                    tip?.optString("rotationMode"),
                    default.tip.rotationMode,
                ),
                rotationJitter = tip?.optDouble(
                    "rotationJitter",
                    default.tip.rotationJitter.toDouble(),
                )?.toFloat() ?: default.tip.rotationJitter,
                count = tip?.optInt("count", default.tip.count) ?: default.tip.count,
                countJitter = tip?.optDouble(
                    "countJitter",
                    default.tip.countJitter.toDouble(),
                )?.toFloat() ?: default.tip.countJitter,
            ),
            grain = BrushGrainProfile(
                mode = enumValueOrDefault(grain?.optString("mode"), default.grain.mode),
                scale = grain?.optDouble("scale", default.grain.scale.toDouble())?.toFloat()
                    ?: default.grain.scale,
                depth = grain?.optDouble("depth", default.grain.depth.toDouble())?.toFloat()
                    ?: default.grain.depth,
                contrast = grain?.optDouble(
                    "contrast",
                    default.grain.contrast.toDouble(),
                )?.toFloat() ?: default.grain.contrast,
                movement = grain?.optDouble(
                    "movement",
                    default.grain.movement.toDouble(),
                )?.toFloat() ?: default.grain.movement,
                source = enumValueOrDefault(grain?.optString("source"), default.grain.source),
            ),
            sizeScale = json.optDouble("sizeScale", default.sizeScale.toDouble()).toFloat(),
            opacity = json.optDouble("opacity", default.opacity.toDouble()).toFloat(),
            offset = json.optDouble("offset", default.offset.toDouble()).toFloat(),
            scatter = json.optDouble("scatter", default.scatter.toDouble()).toFloat(),
            blendMode = enumValueOrDefault(json.optString("blendMode"), default.blendMode),
        )
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: default

    private const val MAX_EMBEDDED_TIP_BYTES = 2L * 1024L * 1024L
}
