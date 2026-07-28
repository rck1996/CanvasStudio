package com.orbyte.canvasstudio

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompatibilityPhase64Test {
    @Test
    fun activityAndRuntimeRemainTabletReady() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val activity = context.packageManager.getActivityInfo(
            android.content.ComponentName(context, MainActivity::class.java),
            0,
        )
        assertTrue(activity.enabled)
        assertTrue(activity.exported)
        assertTrue(context.resources.configuration.smallestScreenWidthDp >= 600)
        assertTrue(Build.VERSION.SDK_INT >= 26)
    }
}
