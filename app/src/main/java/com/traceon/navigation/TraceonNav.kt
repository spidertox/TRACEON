package com.traceon.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.traceon.core.ui.components.BarChartIcon
import com.traceon.feature.activity.ActivityScreen
import com.traceon.feature.analytics.AnalyticsScreen
import com.traceon.feature.backup.BackupScreen
import com.traceon.feature.browser.BrowserScreen
import com.traceon.feature.contentdetails.DetailScreen
import com.traceon.feature.contentmap.ContentMapScreen
import com.traceon.feature.dashboard.HomeScreen
import com.traceon.feature.discovery.DiscoveryScreen
import com.traceon.feature.importcenter.ImportCenterScreen
import com.traceon.feature.importcenter.ManualEntryScreen
import com.traceon.feature.search.SearchScreen
import com.traceon.feature.settings.PermissionsScreen
import com.traceon.feature.settings.SettingsScreen

private enum class Tab(val route: String, val label: String) {
    HOME("home", "Home"), MAP("map", "Content Map"), ACTIVITY("activity", "Activity"), ANALYTICS("analytics", "Analytics"), SEARCH("search", "Search"),
}

@Composable
fun TraceonNav() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route?.substringBefore('?')
    val showBar = Tab.values().any { it.route == route }
    val openContent: (Long) -> Unit = { id -> nav.navigate("content/$id") }
    val go: (String) -> Unit = { nav.navigate(it) }
    val back: () -> Unit = { nav.popBackStack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    Tab.values().forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                when (tab) {
                                    Tab.HOME -> Icon(Icons.Default.Home, contentDescription = tab.label)
                                    Tab.MAP -> Icon(Icons.Default.Share, contentDescription = tab.label)
                                    Tab.ACTIVITY -> Icon(Icons.Default.DateRange, contentDescription = tab.label)
                                    Tab.ANALYTICS -> BarChartIcon(LocalContentColor.current)
                                    Tab.SEARCH -> Icon(Icons.Default.Search, contentDescription = tab.label)
                                }
                            },
                            label = { Text(tab.label, maxLines = 1) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        NavHost(nav, startDestination = Tab.HOME.route, modifier = Modifier.padding(inner)) {
            composable("home") { HomeScreen(onOpenContent = openContent, onNavigate = go) }
            composable(
                "map?focus={focus}",
                arguments = listOf(navArgument("focus") { type = NavType.LongType; defaultValue = -1L }),
            ) { e ->
                val focus = e.arguments?.getLong("focus") ?: -1L
                ContentMapScreen(focusId = focus.takeIf { it > 0 }, onOpenContent = openContent)
            }
            composable("activity") { ActivityScreen(onOpenContent = openContent) }
            composable("analytics") { AnalyticsScreen(onOpenContent = openContent) }
            composable("search") { SearchScreen(onOpenContent = openContent) }
            composable("content/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                DetailScreen(
                    id = e.arguments?.getLong("id") ?: -1L,
                    onBack = back,
                    onOpenContent = openContent,
                    onShowInMap = { id -> nav.navigate("map?focus=$id") { launchSingleTop = true } },
                )
            }
            composable("import") { ImportCenterScreen(onBack = back, onManual = { nav.navigate("manual") }) }
            composable("manual") { ManualEntryScreen(onBack = back) }
            composable("settings") { SettingsScreen(onBack = back, onNavigate = go) }
            composable("permissions") { PermissionsScreen(onBack = back) }
            composable("backup") { BackupScreen(onBack = back) }
            composable("discovery") { DiscoveryScreen(onBack = back, onOpenContent = openContent) }
            composable("browser") { BrowserScreen(onBack = back) }
        }
    }
}
