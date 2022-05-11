package org.lsposed.lspatch.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination

fun <T> NavBackStackEntry.setState(key: String, value: T?) {
    savedStateHandle.getLiveData<T>(key).value = value
}

@Composable
fun <T> NavBackStackEntry.observeState(key: String, initial: T? = null) =
    savedStateHandle.getLiveData(key, initial).observeAsState()

fun NavController.navigateWithState(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
