package com.orbyte.canvasstudio.drawing

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

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

    fun exportJson(brushes: List<BrushPreset>): String = JSONObject().apply {
        put("format", "CanvasStudioBrushLibrary")
        put("version", EXPORT_VERSION)
        put("brushes", JSONArray().apply {
            brushes.takeLast(MAX_CUSTOM_BRUSHES).forEach { put(encode(it)) }
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
        )
    }.getOrNull()
}
