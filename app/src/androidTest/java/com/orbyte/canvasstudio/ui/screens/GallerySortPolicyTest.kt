package com.orbyte.canvasstudio.ui.screens

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbyte.canvasstudio.model.PreviewStyle
import com.orbyte.canvasstudio.model.ProjectCard
import com.orbyte.canvasstudio.model.ProjectOrganization
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GallerySortPolicyTest {
    private val projects = listOf(
        ProjectCard("z", "Zorro", 1000, 1000, "hoy", PreviewStyle.SKETCH),
        ProjectCard("a", "Arbol", 500, 500, "ayer", PreviewStyle.SKETCH),
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

    @Test fun favoritesAndLibraryMetadataParticipateInBrowsing() {
        val organization = mapOf("b" to ProjectOrganization("Clientes", listOf("tinta")))
        assertEquals(
            listOf("b", "z", "a"),
            sortGalleryProjects(projects, "", GallerySortOrder.FAVORITES, setOf("b"), organization).map { it.id },
        )
        for (term in listOf("cliente", "TINTA")) {
            assertEquals(
                listOf("b"),
                sortGalleryProjects(projects, term, GallerySortOrder.RECENT, organizationById = organization).map { it.id },
            )
        }
    }
}
