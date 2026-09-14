package com.mastercontrol.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mastercontrol.app.core.ui.adaptive.McWindowClass
import com.mastercontrol.app.core.ui.adaptive.rememberWindowClass
import com.mastercontrol.app.core.ui.component.LoadingState
import com.mastercontrol.app.core.ui.component.McDimens
import com.mastercontrol.app.core.ui.component.McIconAction
import com.mastercontrol.app.core.ui.component.McTopBar
import com.mastercontrol.app.core.ui.theme.MasterControlTheme
import com.mastercontrol.app.domain.model.ThemeMode
import com.mastercontrol.app.lock.AppLockGate
import com.mastercontrol.app.lock.AppLockViewModel
import com.mastercontrol.app.lock.LockState
import com.mastercontrol.app.navigation.Destinations
import com.mastercontrol.app.navigation.McNavHost
import com.mastercontrol.app.navigation.SecondaryDestination
import com.mastercontrol.app.navigation.TopLevelDestination

/**
 * Root of the Compose UI: theme, notification permission, app lock and navigation.
 *
 * The theme follows the operator's settings (system/light/dark, dynamic color)
 * and the system animator setting disables motion for people who need that.
 */
@Composable
fun MasterControlRoot() {
    val startViewModel: AppStartViewModel = hiltViewModel()
    val startState by startViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val reducedMotion = remember(context) { systemReducedMotion(context) }

    val darkTheme = when (startState.settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    RequestNotificationPermission()

    MasterControlTheme(
        darkTheme = darkTheme,
        dynamicColor = startState.settings.dynamicColor,
        reducedMotion = reducedMotion,
    ) {
        AppLockGate {
            val startDestination = startState.startDestination
            if (startDestination == null) {
                LoadingState(message = "Preparing Master Control…", modifier = Modifier.fillMaxSize())
            } else {
                AppScaffold(startDestination = startDestination, onboardingNeeded = startState.onboardingNeeded)
            }
        }
    }
}

@Composable
private fun AppScaffold(startDestination: String, onboardingNeeded: Boolean) {
    val navController: NavHostController = rememberNavController()
    val lockViewModel: AppLockViewModel = hiltViewModel()
    val lockState by lockViewModel.state.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var menuOpen by remember { mutableStateOf(false) }

    // Signing out or clearing secrets moves the stage back to onboarding; the UI
    // follows that state instead of leaving a signed-out operator in the catalog.
    LaunchedEffect(onboardingNeeded, currentRoute) {
        if (onboardingNeeded && currentRoute != null && currentRoute != Destinations.ONBOARDING) {
            navController.navigate(Destinations.ONBOARDING) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    val topLevel = TopLevelDestination.entries.firstOrNull { it.route == currentRoute }
    val showChrome = topLevel != null && currentRoute != Destinations.ONBOARDING
    val canNavigateUp = topLevel == null && currentRoute != Destinations.ONBOARDING &&
        navController.previousBackStackEntry != null

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useRail = rememberWindowClass() == McWindowClass.EXPANDED

        Scaffold(
            topBar = {
                if (currentRoute != Destinations.ONBOARDING) {
                    McTopBar(
                        title = Destinations.titleFor(currentRoute),
                        subtitle = if (currentRoute == Destinations.DASHBOARD) {
                            "Administrator console for the Telegram media library"
                        } else {
                            null
                        },
                        onNavigateUp = if (canNavigateUp) {
                            { navController.navigateUp() }
                        } else {
                            null
                        },
                        actions = {
                            if (lockState is LockState.Unlocked) {
                                McIconAction(
                                    icon = Icons.Filled.Lock,
                                    contentDescription = "Lock Master Control now",
                                    onClick = lockViewModel::onLockNow,
                                )
                            }
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "More destinations",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                SecondaryDestination.entries.forEach { destination ->
                                    DropdownMenuItem(
                                        text = { Text(destination.label) },
                                        leadingIcon = { Icon(destination.icon, contentDescription = null) },
                                        onClick = {
                                            menuOpen = false
                                            navController.navigate(destination.route) { launchSingleTop = true }
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
            },
            bottomBar = {
                if (showChrome && !useRail) {
                    NavigationBar {
                        TopLevelDestination.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = destination == topLevel,
                                onClick = { navController.selectTopLevel(destination.route) },
                                icon = { Icon(destination.icon, contentDescription = null) },
                                label = { Text(destination.label) },
                                alwaysShowLabel = false,
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(Modifier.fillMaxSize()) {
                if (showChrome && useRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        TopLevelDestination.entries.forEach { destination ->
                            NavigationRailItem(
                                selected = destination == topLevel,
                                onClick = { navController.selectTopLevel(destination.route) },
                                icon = { Icon(destination.icon, contentDescription = null) },
                                label = { Text(destination.label, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                }
                McNavHost(
                    navController = navController,
                    startDestination = startDestination,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = if (useRail) McDimens.SpacingXl else McDimens.SpacingXs),
                )
            }
        }
    }
}

private fun NavHostController.selectTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Upload progress is reported through a foreground-service notification, so the
 * runtime permission is requested once on Android 13+. Denial is not fatal: the
 * queue keeps running, and the upload screen reports progress in-app.
 */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* The decision is recorded by the system; the app never nags twice. */ }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/** True when the operator turned animations off system-wide. */
private fun systemReducedMotion(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f
}.getOrDefault(false)
