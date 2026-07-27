package com.orbyte.canvasstudio.drawing

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object BrushRepository {
    private const val PREFERENCES = "canvas_studio_brushes"
    private const val KEY_CUSTOM_BRUSHES = "custom_brushes_v1"

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
        brushes.takeLast(40).forEach { brush -> array.put(encode(brush)) }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_BRUSHES, array.toString())
            .apply()
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
            tiltResponse = item.optDouble("tiltResponse", 0.0).toFloat(),
            taperStart = item.optDouble("taperStart", .08).toFloat(),
            taperEnd = item.optDouble("taperEnd", .06).toFloat(),
            scatter = item.optDouble("scatter", 0.0).toFloat(),
            grain = item.optDouble("grain", 0.0).toFloat(),
            velocitySize = item.optDouble("velocitySize", 0.0).toFloat(),
        )
    }.getOrNull()
}
