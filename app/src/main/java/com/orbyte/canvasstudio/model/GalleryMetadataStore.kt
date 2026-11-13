package com.orbyte.canvasstudio.model

import android.content.Context

internal data class ProjectOrganization(
    val collection: String? = null,
    val tags: List<String> = emptyList(),
)

/** Device-local metadata, independent of project packages and their version history. */
internal class GalleryMetadataStore(context: Context) {
    private val favorites = context.getSharedPreferences("canvas_studio_gallery", Context.MODE_PRIVATE)
    private val library = context.getSharedPreferences("canvas_studio_project_library", Context.MODE_PRIVATE)

    fun favoriteIds(): Set<String> = favorites.getStringSet(FAVORITES_KEY, emptySet()).orEmpty().toSet()

    fun toggleFavorite(projectId: String): Set<String> {
        val updated = favoriteIds().toMutableSet().apply {
            if (!add(projectId)) remove(projectId)
        }
        favorites.edit().putStringSet(FAVORITES_KEY, updated.toSet()).apply()
        return updated
    }

    fun organization(projectId: String): ProjectOrganization {
        val collection = library.getString("collection_$projectId", null)?.takeIf(String::isNotBlank)
        val tags = library.getString("tags_$projectId", null).orEmpty().split(TAG_SEPARATOR)
            .map(String::trim)
            .filter(String::isNotBlank)
        return ProjectOrganization(collection, tags)
    }

    fun saveOrganization(projectId: String, collection: String, rawTags: String): ProjectOrganization {
        val cleanCollection = collection.trim().take(32).trimEnd().takeIf(String::isNotBlank)
        val cleanTags = rawTags.split(',', ';', '\n', TAG_SEPARATOR)
            .map { it.trim().take(24).trimEnd() }
            .filter(String::isNotBlank)
            .distinctBy { it.lowercase() }
            .take(8)
        library.edit().apply {
            putString("collection_$projectId", cleanCollection)
            putString("tags_$projectId", cleanTags.takeIf { it.isNotEmpty() }?.joinToString(TAG_SEPARATOR.toString()))
        }.apply()
        return ProjectOrganization(cleanCollection, cleanTags)
    }

    private companion object {
        const val FAVORITES_KEY = "favorite_project_ids"
        const val TAG_SEPARATOR = '\u001F'
    }
}
