package com.orbyte.canvasstudio.ui.screens

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbyte.canvasstudio.model.PreviewStyle
import com.orbyte.canvasstudio.model.ProjectCard
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GallerySortPolicyTest {
    private val projects = listOf(
        ProjectCard("z", "Zorro", 1000, 1000, "hoy", PreviewStyle.SKETCH),
        ProjectCard("a", "Árbol", 500, 500, "ayer", PreviewStyle.SKETCH),
        ProjectCard("b", "Boceto", 2000, 1500, "antes", PreviewStyle.SKETCH),
    )

    @Test fun recentKeepsRepositoryOrder() {
        assertEquals(listOf("z", "a", "b"), sortGalleryProjects(projects, "", GallerySortOrder.RECENT).map { it.id })
    }

    @Test fun largestOrdersByPixelCountWithoutIntOverflow() {
        val large = ProjectCard("huge", "Gigante", 16384, 16384, "hoy", PreviewStyle.SKETCH)
        assertEquals("huge", sortGalleryProjects(projects + large, "", GallerySortOrder.LARGEST).first().id)
    }

    @Test fun searchMatchesCanvasDimensionsWithWhitespace() {
        assertEquals(listOf("b"), sortGalleryProjects(projects, "2000 x 1500", GallerySortOrder.RECENT).map { it.id })
    }

    @Test fun alphabeticalOrderDoesNotMutateInput() {
        assertEquals(listOf("a", "b", "z"), sortGalleryProjects(projects, "", GallerySortOrder.NAME).map { it.id })
        assertEquals("z", projects.first().id)
    }
}
