package com.orbyte.canvasstudio.drawing

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orbyte.canvasstudio.model.ProjectRepository
import java.io.File
import java.util.Properties
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoalescedProjectSaveIntegrationTest {
    @Test
    fun latestSaveWinsAndWorkerGeneratesThumbnail() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = "coalesced-save-${UUID.randomUUID()}"
        val savedLatest = CountDownLatch(1)
        val view = DrawingView(context)
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                view.configureDocument(384, 256)
                view.onProjectSaved = { success ->
                    if (success) {
                        val metadata = ProjectRepository.metadataFile(context, id)
                        val title = runCatching {
                            Properties().apply { metadata.inputStream().use(::load) }
                                .getProperty("title")
                        }.getOrNull()
                        if (title == "Latest") savedLatest.countDown()
                    }
                }
                view.saveProject(id, "First", 300, includePreview = true)
                view.saveProject(id, "Intermediate", 300, includePreview = true)
                view.saveProject(id, "Latest", 300, includePreview = true)
            }
            assertTrue("Last serialized save never completed", savedLatest.await(50, TimeUnit.SECONDS))
            val directory = ProjectRepository.projectDirectory(context, id)
            val previewFile = File(directory, "preview.png")
            assertTrue("Off-thread thumbnail missing", previewFile.isFile)
            val bitmap = BitmapFactory.decodeFile(previewFile.absolutePath)
            assertTrue("Preview could not be decoded", bitmap != null)
            bitmap?.let {
                try {
                    assertEquals(384, it.width)
                    assertEquals(256, it.height)
                } finally {
                    it.recycle()
                }
            }
        } finally {
            ProjectRepository.projectDirectory(context, id).deleteRecursively()
        }
    }
}
