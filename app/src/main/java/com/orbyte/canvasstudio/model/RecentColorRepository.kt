package com.orbyte.canvasstudio.model

import android.content.Context
import org.json.JSONArray

/** Last used opaque colors on this device, with deduplication and a fixed disk/memory bound. */
internal object RecentColorRepository {
    const val MAX_COLORS = 10
    private const val PREFS = "canvas_studio_recent_colors"
    private const val KEY = "recent_argb_v1"

    fun load(context: Context): List<Int> = runCatching {
        val input = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        val json = JSONArray(input)
        (0 until json.length())
            .mapNotNull { index ->
                val value = json.opt(index)
                (value as? Number)?.toInt()?.or(0xFF000000.toInt())
            }
            .distinct()
            .take(MAX_COLORS)
    }.getOrDefault(emptyList())

    @Synchronized
    fun record(context: Context, argb: Int): List<Int> {
        val color = argb or 0xFF000000.toInt()
        val next = (listOf(color) + load(context)).distinct().take(MAX_COLORS)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, JSONArray(next).toString()).apply()
        return next
    }

    @Synchronized
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
