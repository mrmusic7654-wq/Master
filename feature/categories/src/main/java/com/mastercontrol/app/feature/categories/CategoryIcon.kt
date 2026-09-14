package com.mastercontrol.app.feature.categories

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Curated icon vocabulary for categories.
 *
 * The catalog stores [Category.icon] as a stable string key (not a resource id),
 * so icons survive an app update and the exported catalog stays portable. An
 * unknown or empty key renders [GENERIC] instead of guessing.
 */
enum class CategoryIcon(val key: String, val label: String) {
    MOVIE("movie", "Movies"),
    MUSIC("music", "Music"),
    SPORTS("sports", "Sports"),
    EDUCATION("education", "Education"),
    TRAVEL("travel", "Travel"),
    GAMING("gaming", "Gaming"),
    ARCHIVE("archive", "Archive"),
    STAR("star", "Featured"),
    GENERIC("category", "General"),
    ;

    companion object {
        fun fromKey(key: String?): CategoryIcon =
            entries.firstOrNull { it.key == key?.trim()?.lowercase() } ?: GENERIC
    }
}

fun CategoryIcon.vector(): ImageVector = when (this) {
    CategoryIcon.MOVIE -> Icons.Filled.Movie
    CategoryIcon.MUSIC -> Icons.Filled.MusicNote
    CategoryIcon.SPORTS -> Icons.Filled.SportsSoccer
    CategoryIcon.EDUCATION -> Icons.Filled.School
    CategoryIcon.TRAVEL -> Icons.Filled.Flight
    CategoryIcon.GAMING -> Icons.Filled.SportsEsports
    CategoryIcon.ARCHIVE -> Icons.Filled.Archive
    CategoryIcon.STAR -> Icons.Filled.Star
    CategoryIcon.GENERIC -> Icons.Filled.Category
}
