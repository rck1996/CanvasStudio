package com.orbyte.canvasstudio.model

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecentColorRepositoryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun recentColorsStayUniqueAndBoundedAcrossSelections() {
        RecentColorRepository.clear(context)
        try {
            repeat(16) { RecentColorRepository.record(context, 0xFF000000.toInt() or it) }
            assertEquals(10, RecentColorRepository.load(context).size)
            val returned = RecentColorRepository.record(context, 0xFF00000F.toInt())
            assertEquals(10, returned.size)
            assertEquals(0xFF00000F.toInt(), returned.first())
            assertEquals(returned.size, returned.distinct().size)
            assertEquals(returned, RecentColorRepository.load(context))
        } finally { RecentColorRepository.clear(context) }
    }

    @Test
    fun clearAndForceOpaqueKeepColorHistoryPredictable() {
        RecentColorRepository.clear(context)
        try {
            assertEquals(0xFFABCDEF.toInt(), RecentColorRepository.record(context, 0x00ABCDEF).first())
            RecentColorRepository.clear(context)
            assertTrue(RecentColorRepository.load(context).isEmpty())
        } finally { RecentColorRepository.clear(context) }
    }
}
