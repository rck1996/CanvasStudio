package com.orbyte.canvasstudio.drawing

import android.content.Context
import android.view.KeyEvent

enum class ShortcutProfile(val label: String) {
    STANDARD("Letras estándar"),
    NUMERIC("Fila numérica"),
}

object ShortcutPreferences {
    private const val PREFERENCES = "canvas_studio_preferences"
    private const val KEY_PROFILE = "shortcut_profile"

    fun load(context: Context): ShortcutProfile {
        val value = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_PROFILE, ShortcutProfile.STANDARD.name)
        return runCatching { ShortcutProfile.valueOf(value.orEmpty()) }
            .getOrDefault(ShortcutProfile.STANDARD)
    }

    fun save(context: Context, profile: ShortcutProfile) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROFILE, profile.name)
            .apply()
    }

    fun toolForKey(profile: ShortcutProfile, keyCode: Int, shiftPressed: Boolean): DrawingTool? =
        when (profile) {
            ShortcutProfile.STANDARD -> when (keyCode) {
                KeyEvent.KEYCODE_B -> DrawingTool.BRUSH
                KeyEvent.KEYCODE_E -> DrawingTool.ERASER
                KeyEvent.KEYCODE_H -> DrawingTool.HAND
                KeyEvent.KEYCODE_I -> DrawingTool.EYEDROPPER
                KeyEvent.KEYCODE_L -> DrawingTool.LINE
                KeyEvent.KEYCODE_G -> DrawingTool.GRADIENT
                KeyEvent.KEYCODE_F -> DrawingTool.FILL
                KeyEvent.KEYCODE_M -> if (shiftPressed) DrawingTool.SELECT_ELLIPSE else DrawingTool.SELECT_RECTANGLE
                KeyEvent.KEYCODE_V -> DrawingTool.TRANSFORM
                else -> null
            }
            ShortcutProfile.NUMERIC -> when (keyCode) {
                KeyEvent.KEYCODE_1 -> DrawingTool.BRUSH
                KeyEvent.KEYCODE_2 -> DrawingTool.ERASER
                KeyEvent.KEYCODE_3 -> DrawingTool.HAND
                KeyEvent.KEYCODE_4 -> DrawingTool.EYEDROPPER
                KeyEvent.KEYCODE_5 -> DrawingTool.LINE
                KeyEvent.KEYCODE_6 -> DrawingTool.FILL
                KeyEvent.KEYCODE_7 -> DrawingTool.GRADIENT
                KeyEvent.KEYCODE_8 -> if (shiftPressed) DrawingTool.SELECT_ELLIPSE else DrawingTool.SELECT_RECTANGLE
                KeyEvent.KEYCODE_9 -> DrawingTool.TRANSFORM
                else -> null
            }
        }
}
