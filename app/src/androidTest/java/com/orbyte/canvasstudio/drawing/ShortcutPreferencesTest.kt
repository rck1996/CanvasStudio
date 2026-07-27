package com.orbyte.canvasstudio.drawing

import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShortcutPreferencesTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun persistsSelectedShortcutProfile() {
        ShortcutPreferences.save(context, ShortcutProfile.NUMERIC)
        assertEquals(ShortcutProfile.NUMERIC, ShortcutPreferences.load(context))
        ShortcutPreferences.save(context, ShortcutProfile.STANDARD)
    }

    @Test
    fun profilesResolveBrushAndSelectionKeys() {
        assertEquals(DrawingTool.BRUSH, ShortcutPreferences.toolForKey(ShortcutProfile.STANDARD, KeyEvent.KEYCODE_B, false))
        assertEquals(DrawingTool.BRUSH, ShortcutPreferences.toolForKey(ShortcutProfile.NUMERIC, KeyEvent.KEYCODE_1, false))
        assertEquals(DrawingTool.SELECT_ELLIPSE, ShortcutPreferences.toolForKey(ShortcutProfile.NUMERIC, KeyEvent.KEYCODE_8, true))
    }
}
