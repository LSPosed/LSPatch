package org.lsposed.lspatch.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

val LocalNavController = compositionLocalOf<NavHostController> {
    error("CompositionLocal LocalNavController not present")
}

val NavController.currentRoute: String?
    @Composable get() = currentBackStackEntryAsState().value?.destination?.route

val NavController.startRoute: String?
    get() = graph.findStartDestination().route

@Composable
fun NavController.isAtStartRoute(): Boolean = currentRoute == startRoute

fun NavController.navigateWithState(route: String?) {
    navigate(route.toString()) {
        popUpTo(startRoute.toString()) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
