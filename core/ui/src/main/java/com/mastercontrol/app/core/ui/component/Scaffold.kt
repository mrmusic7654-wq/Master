@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mastercontrol.app.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mastercontrol.app.core.ui.adaptive.McWindowClass
import com.mastercontrol.app.core.ui.adaptive.rememberWindowClass

/** One primary destination in the console navigation. */
data class McNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector? = null,
    /** Real pending/failed count; 0 hides the badge. */
    val badgeCount: Int = 0,
    val badgeContentDescription: String? = null,
)

/**
 * Adaptive application scaffold.
 *
 * Compact windows (phones, portrait) get a bottom navigation bar; medium and
 * expanded windows (large phones in landscape, tablets, foldables, desktop mode)
 * get a navigation rail so the content column stays wide enough for dense data.
 */
@Composable
fun MasterControlScaffold(
    items: List<McNavItem>,
    selectedRoute: String?,
    onNavigate: (McNavItem) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onNavigateUp: (() -> Unit)? = null,
    topBarActions: @Composable RowScope.() -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val windowClass: McWindowClass = rememberWindowClass()

        Scaffold(
            modifier = modifier,
            topBar = {
                McTopBar(
                    title = title,
                    subtitle = subtitle,
                    onNavigateUp = onNavigateUp,
                    actions = topBarActions,
                )
            },
            bottomBar = {
                if (windowClass.isCompact && items.isNotEmpty()) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        items.forEach { item ->
                            NavigationBarItem(
                                selected = item.route == selectedRoute,
                                onClick = { onNavigate(item) },
                                icon = { NavIcon(item, selected = item.route == selectedRoute) },
                                label = { Text(item.label, maxLines = 1) },
                                alwaysShowLabel = false,
                            )
                        }
                    }
                }
            },
            snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
            floatingActionButton = floatingActionButton,
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            if (windowClass.isCompact || items.isEmpty()) {
                content(padding)
            } else {
                Row(Modifier.padding(padding).fillMaxSize()) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        items.forEach { item ->
                            NavigationRailItem(
                                selected = item.route == selectedRoute,
                                onClick = { onNavigate(item) },
                                icon = { NavIcon(item, selected = item.route == selectedRoute) },
                                label = { Text(item.label, maxLines = 1) },
                                alwaysShowLabel = true,
                            )
                        }
                    }
                    Box(Modifier.fillMaxHeight().weight(1f)) {
                        content(PaddingValues(0.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun NavIcon(item: McNavItem, selected: Boolean) {
    val icon = if (selected) item.selectedIcon ?: item.icon else item.icon
    if (item.badgeCount > 0) {
        BadgedBox(
            badge = {
                Badge(containerColor = MaterialTheme.colorScheme.error) {
                    Text(
                        text = if (item.badgeCount > 99) "99+" else item.badgeCount.toString(),
                        color = MaterialTheme.colorScheme.onError,
                    )
                }
            },
        ) {
            Icon(
                imageVector = icon,
                contentDescription = item.badgeContentDescription ?: item.label,
                modifier = Modifier.size(22.dp),
            )
        }
    } else {
        Icon(
            imageVector = icon,
            contentDescription = item.label,
            modifier = Modifier.size(22.dp),
        )
    }
}
