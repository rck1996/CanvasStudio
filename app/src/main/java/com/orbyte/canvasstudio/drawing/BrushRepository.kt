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

    fun loadFavorites(context: Context): Set<String> =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getStringSet(KEY_FAVORITES, emptySet())
            ?.toSet()
            .orEmpty()

    fun saveFavorites(context: Context, ids: Set<String>) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_FAVORITES, ids)
            .apply()
    }

    fun loadRecents(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_RECENTS, null)
            ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                repeat(array.length()) { index ->
                    array.optString(index).takeIf(String::isNotBlank)?.let(::add)
                }
            }.distinct().take(MAX_RECENT_BRUSHES)
        }.getOrDefault(emptyList())
    }

    fun recordRecent(context: Context, brushId: String, previous: List<String>): List<String> {
        val updated = (listOf(brushId) + previous.filterNot { it == brushId })
            .take(MAX_RECENT_BRUSHES)
        val array = JSONArray()
        updated.forEach(array::put)
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECENTS, array.toString())
            .apply()
        return updated
    }

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
    }

    private fun decode(item: JSONObject): BrushPreset? = runCatching {
        BrushPreset(
            id = item.getString("id"),
            name = item.optString("name", "Pincel personalizado"),
            category = item.optString("category", "Personalizados"),
            kind = BrushKind.valueOf(item.optString("kind", BrushKind.PENCIL.name)),
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
            grain = item.optDouble("grain", 0.0).toFloat(),
            velocitySize = item.optDouble("velocitySize", 0.0).toFloat(),
            tipAssetPath = item.optString("tipAssetPath").takeIf(String::isNotBlank),
        )
    }.getOrNull()

    private const val MAX_EMBEDDED_TIP_BYTES = 2L * 1024L * 1024L
}
