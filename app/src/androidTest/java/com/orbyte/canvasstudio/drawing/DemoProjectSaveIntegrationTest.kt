package com.orbyte.canvasstudio.drawing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orbyte.canvasstudio.model.ProjectRepository
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoProjectSaveIntegrationTest {
    @Test fun saveDuringBackgroundDemoSeedingKeepsItsTiles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = "demo-save-${UUID.randomUUID()}"
        val completed = CountDownLatch(1)
        var saved = false
        val view = DrawingView(context)
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                view.configureDocument(1024, 1536)
                view.onProjectSaved = { success ->
                    saved = success
                    completed.countDown()
                }
                view.seedDemoArtwork("MOUNTAIN")
                view.saveProject(id, "Guardado de prueba", 300, includePreview = false)
            }
            assertTrue("Save callback was not received", completed.await(40, TimeUnit.SECONDS))
            assertTrue("Save during demo rasterization must succeed", saved)
            val project = ProjectRepository.projectDirectory(context, id)
            assertTrue(
                "Bottom tile was not saved",
                File(project, "layers").walkTopDown().any { it.name == "0_2.png" },
            )
        } finally {
            ProjectRepository.projectDirectory(context, id).deleteRecursively()
        }
    }
}
