package com.mastercontrol.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mastercontrol.app.feature.activity.ActivityRoute
import com.mastercontrol.app.feature.categories.CategoriesRoute
import com.mastercontrol.app.feature.channels.ChannelsRoute
import com.mastercontrol.app.feature.dashboard.DashboardRoute
import com.mastercontrol.app.feature.folders.FoldersRoute
import com.mastercontrol.app.feature.library.LibraryRoute
import com.mastercontrol.app.feature.onboarding.OnboardingRoute
import com.mastercontrol.app.feature.settings.SettingsRoute
import com.mastercontrol.app.feature.upload.UploadRoute
import com.mastercontrol.app.feature.video.VideoNavigation
import com.mastercontrol.app.feature.video.VideoRoute

/**
 * The app's NavHost.
 *
 * Feature modules know nothing about navigation: each `*Route` composable takes
 * callbacks, and this file is the only place where those callbacks are wired to
 * destinations. The permanent video ID is the argument of the video route, so a
 * video can be opened from the library, the queue or the activity log alike.
 */
@Composable
fun McNavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Destinations.ONBOARDING) {
            OnboardingRoute(
                onFinished = {
                    navController.navigate(Destinations.DASHBOARD) {
                        popUpTo(Destinations.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }

        composable(Destinations.DASHBOARD) {
            DashboardRoute(
                onOpenLibrary = { navController.toTopLevel(Destinations.LIBRARY) },
                // Importing happens in the library, where the SAF picker lives.
                onAddVideo = { navController.toTopLevel(Destinations.LIBRARY) },
                onOpenUploads = { navController.toTopLevel(Destinations.UPLOADS) },
                onOpenChannels = { navController.toTopLevel(Destinations.CHANNELS) },
                onOpenCategories = { navController.toDetail(Destinations.CATEGORIES) },
                onOpenFolders = { navController.toDetail(Destinations.FOLDERS) },
                onOpenActivity = { navController.toDetail(Destinations.ACTIVITY) },
                onConfigureTelegram = { navController.toDetail(Destinations.ONBOARDING) },
            )
        }

        composable(Destinations.LIBRARY) {
            LibraryRoute(onOpenVideo = { videoId -> navController.toDetail(Destinations.video(videoId)) })
        }

        composable(
            route = Destinations.VIDEO,
            arguments = listOf(
                navArgument(VideoNavigation.ARG_VIDEO_ID) { type = NavType.StringType },
            ),
        ) {
            VideoRoute(onNavigateUp = { navController.navigateUp() })
        }

        composable(Destinations.UPLOADS) {
            UploadRoute(onOpenVideo = { videoId -> navController.toDetail(Destinations.video(videoId)) })
        }

        composable(Destinations.CHANNELS) {
            ChannelsRoute()
        }

        composable(Destinations.CATEGORIES) {
            CategoriesRoute()
        }

        composable(Destinations.FOLDERS) {
            FoldersRoute()
        }

        composable(Destinations.ACTIVITY) {
            ActivityRoute(onOpenVideo = { videoId -> navController.toDetail(Destinations.video(videoId)) })
        }

        composable(Destinations.SETTINGS) {
            SettingsRoute(
                onEditCredentials = { navController.toDetail(Destinations.ONBOARDING) },
                onOpenActivityLog = { navController.toDetail(Destinations.ACTIVITY) },
                onOpenChannels = { navController.toTopLevel(Destinations.CHANNELS) },
                onRequireOnboarding = {
                    navController.navigate(Destinations.ONBOARDING) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
    }
}

/** Bottom-bar / rail navigation: keeps state per tab and never stacks duplicates. */
private fun NavHostController.toTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Detail navigation: pushed on top of the current tab. */
private fun NavHostController.toDetail(route: String) {
    navigate(route) { launchSingleTop = true }
}
