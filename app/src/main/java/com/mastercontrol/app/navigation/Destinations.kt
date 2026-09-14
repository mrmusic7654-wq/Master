package com.mastercontrol.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import com.mastercontrol.app.feature.activity.ActivityNavigation
import com.mastercontrol.app.feature.categories.CategoriesNavigation
import com.mastercontrol.app.feature.channels.ChannelsNavigation
import com.mastercontrol.app.feature.dashboard.DashboardNavigation
import com.mastercontrol.app.feature.folders.FoldersNavigation
import com.mastercontrol.app.feature.library.LibraryNavigation
import com.mastercontrol.app.feature.onboarding.OnboardingNavigation
import com.mastercontrol.app.feature.settings.SettingsNavigation
import com.mastercontrol.app.feature.upload.UploadNavigation
import com.mastercontrol.app.feature.video.VideoNavigation

/**
 * Every route the app can show.
 *
 * Feature modules declare their own route constants; this object is the single
 * place where the NavHost, the app bar and the navigation bar agree on them.
 */
object Destinations {
    const val ONBOARDING = OnboardingNavigation.ROUTE
    const val DASHBOARD = DashboardNavigation.ROUTE
    const val LIBRARY = LibraryNavigation.ROUTE
    const val VIDEO = VideoNavigation.ROUTE
    const val UPLOADS = UploadNavigation.ROUTE
    const val CHANNELS = ChannelsNavigation.ROUTE
    const val CATEGORIES = CategoriesNavigation.ROUTE
    const val FOLDERS = FoldersNavigation.ROUTE
    const val ACTIVITY = ActivityNavigation.ROUTE
    const val SETTINGS = SettingsNavigation.ROUTE

    fun video(videoId: String): String = VideoNavigation.createRoute(videoId)

    /** Human-readable title for the app bar. */
    fun titleFor(route: String?): String = when (route?.substringBefore('/') ?: route) {
        ONBOARDING -> "Set up Master Control"
        DASHBOARD -> "Dashboard"
        LIBRARY -> "Library"
        VIDEO -> "Video"
        UPLOADS -> "Upload queue"
        CHANNELS -> "Storage channels"
        CATEGORIES -> "Categories"
        FOLDERS -> "Folders"
        ACTIVITY -> "Activity log"
        SETTINGS -> "Settings"
        else -> "Master Control"
    }
}

/** Destinations reachable from the persistent navigation bar / rail. */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    DASHBOARD(Destinations.DASHBOARD, "Dashboard", Icons.Filled.Dashboard),
    LIBRARY(Destinations.LIBRARY, "Library", Icons.Filled.VideoLibrary),
    UPLOADS(Destinations.UPLOADS, "Uploads", Icons.Filled.Upload),
    CHANNELS(Destinations.CHANNELS, "Channels", Icons.Filled.Storage),
    SETTINGS(Destinations.SETTINGS, "Settings", Icons.Filled.Settings),
}

/** Secondary destinations offered from the app bar overflow menu. */
enum class SecondaryDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    CATEGORIES(Destinations.CATEGORIES, "Categories", Icons.Filled.Category),
    FOLDERS(Destinations.FOLDERS, "Folders", Icons.Filled.Folder),
    ACTIVITY(Destinations.ACTIVITY, "Activity log", Icons.Filled.History),
    UPLOAD_QUEUE(Destinations.UPLOADS, "Upload queue", Icons.Filled.CloudUpload),
}
