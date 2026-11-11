package com.orbyte.canvasstudio.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Per-device canvas formats, independent of document storage.
 *
 * Presets are *templates*, not projects; deleting one never touches drawings.
 * Never trust persisted dimensions when estimating a new canvas's memory footprint.
 */
object CanvasPresetRepository {
    const val MAX_CUSTOM_PRESETS = 24
    private const val PREFS = "canvas_studio_canvas_presets"
    private const val KEY = "custom_presets_v1"
    private const val MAX_NAME_LENGTH = 40

    fun load(context: Context): List<CanvasPreset> = runCatching {
        val source = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]")
        val json = JSONArray(source ?: "[]")
        val presets = mutableListOf<CanvasPreset>()
        for (index in 0 until json.length()) {
            val entry = json.optJSONObject(index) ?: continue
            val title = entry.optString("title").trim()
            val width = entry.optInt("width", -1)
            val height = entry.optInt("height", -1)
            val dpi = entry.optInt("dpi", -1)
            if (!isValid(title, width, height, dpi) ||
                canvasPresets.any { it.title.equals(title, ignoreCase = true) } ||
                presets.any { it.title.equals(title, ignoreCase = true) }
            ) continue
            presets += CanvasPreset(
                title = title,
                subtitle = "Personalizado · $dpi DPI",
                width = width,
                height = height,
                dpi = dpi,
            )
            if (presets.size >= MAX_CUSTOM_PRESETS) break
        }
        presets
    }.getOrDefault(emptyList())

    /** Returns false for invalid names/dimensions, reserved names or an exhausted preset limit. */
    @Synchronized
    fun upsert(context: Context, name: String, width: Int, height: Int, dpi: Int): Boolean {
        val title = name.trim().replace(Regex("\\s+"), " ").take(MAX_NAME_LENGTH)
        if (!isValid(title, width, height, dpi) ||
            canvasPresets.any { it.title.equals(title, ignoreCase = true) }
        ) return false
        val existing = load(context)
        val replacementIndex = existing.indexOfFirst { it.title.equals(title, ignoreCase = true) }
        if (replacementIndex < 0 && existing.size >= MAX_CUSTOM_PRESETS) return false
        val preset = CanvasPreset(title, "Personalizado · $dpi DPI", width, height, dpi)
        val result = existing.toMutableList()
        if (replacementIndex >= 0) result[replacementIndex] = preset else result += preset
        persist(context, result)
        return true
    }

    @Synchronized
    fun remove(context: Context, name: String): Boolean {
        val current = load(context)
        val remaining = current.filterNot { it.title.equals(name.trim(), ignoreCase = true) }
        if (remaining.size == current.size) return false
        persist(context, remaining)
        return true
    }

    private fun persist(context: Context, presets: List<CanvasPreset>) {
        val json = JSONArray()
        presets.forEach { p ->
            json.put(JSONObject().apply {
                put("title", p.title)
                put("width", p.width)
                put("height", p.height)
                put("dpi", p.dpi)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, json.toString()).apply()
    }

    private fun isValid(name: String, width: Int, height: Int, dpi: Int): Boolean =
        name.isNotBlank() && name.length <= MAX_NAME_LENGTH &&
            width in 256..MAX_NEW_CANVAS_DIMENSION &&
            height in 256..MAX_NEW_CANVAS_DIMENSION &&
            width.toLong() * height.toLong() <= MAX_NEW_CANVAS_PIXELS &&
            dpi in 72..600
}
