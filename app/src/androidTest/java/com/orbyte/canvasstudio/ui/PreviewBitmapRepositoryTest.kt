package com.orbyte.canvasstudio.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PreviewBitmapRepositoryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun largeImageIsDownsampledAndCachedForGallery() = runBlocking {
        val preview = File(context.cacheDir, "thumbnail-test-${System.nanoTime()}.png")
        val source = Bitmap.createBitmap(2048, 1536, Bitmap.Config.ARGB_8888)
        try {
            source.eraseColor(Color.BLUE)
            preview.outputStream().use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }
            source.recycle()
            val first = requireNotNull(PreviewBitmapRepository.load(preview, revision = 1L, maxEdge = 512))
            assertTrue(first.width <= 512 && first.height <= 512)
            val second = PreviewBitmapRepository.load(preview, revision = 1L, maxEdge = 512)
            assertSame(first, second)
            assertEquals(Color.BLUE, first.getPixel(first.width / 2, first.height / 2))
        } finally {
            if (!source.isRecycled) source.recycle()
            preview.delete()
        }
    }

    @Test
    fun missingOrInvalidPreviewDoesNotCrashTheGallery() = runBlocking {
        val invalid = File(context.cacheDir, "invalid-preview-${System.nanoTime()}.png")
        assertNull(PreviewBitmapRepository.load(invalid))
        try {
            invalid.writeText("not an image")
            assertNull(PreviewBitmapRepository.load(invalid))
        } finally {
            invalid.delete()
        }
    }
}
