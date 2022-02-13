package org.lsposed.lspatch.ui.page

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import org.lsposed.lspatch.R
import org.lsposed.lspatch.ui.component.AppItem
import org.lsposed.lspatch.ui.util.LocalNavController
import org.lsposed.lspatch.ui.util.observeState
import org.lsposed.lspatch.ui.util.setState
import org.lsposed.lspatch.ui.viewmodel.AppInfo
import org.lsposed.lspatch.ui.viewmodel.SelectAppsViewModel

@Composable
fun SelectAppsTopBar() {
    SmallTopAppBar(
        title = { Text(stringResource(R.string.page_select_apps)) }
    )
}

@Composable
fun SelectAppsFab() {
    val navController = LocalNavController.current
    val viewModel = viewModel<SelectAppsViewModel>()
    val multiSelect = navController.currentBackStackEntry?.arguments?.get("multiSelect") as? Boolean
        ?: throw IllegalArgumentException("multiSelect is null")

    if (multiSelect) {
        FloatingActionButton(onClick = { viewModel.done = true }) {
            Icon(Icons.Outlined.Done, stringResource(R.string.add))
        }
    }
}

@Composable
fun SelectAppsPage(entry: NavBackStackEntry) {
    val multiSelect = entry.arguments?.get("multiSelect") as? Boolean
        ?: throw IllegalArgumentException("multiSelect is null")
    if (multiSelect) {
        MultiSelect(filter = { it.app.metaData?.get("xposedminversion") != null })
    } else {
        SingleSelect()
    }
}

@Composable
private fun SingleSelect(
    filter: (AppInfo) -> Boolean = { it.app.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val viewModel = viewModel<SelectAppsViewModel>()
    LaunchedEffect(viewModel) {
        viewModel.filterAppList(context, false, filter)
    }

    SwipeRefresh(
        state = rememberSwipeRefreshState(viewModel.isRefreshing),
        onRefresh = { viewModel.filterAppList(context, true, filter) },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn {
            items(viewModel.filteredList) {
                AppItem(
                    icon = viewModel.getIcon(it),
                    label = it.label,
                    packageName = it.app.packageName,
                    onClick = {
                        navController.previousBackStackEntry!!.setState("appInfo", it)
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}

@Composable
private fun MultiSelect(
    filter: (AppInfo) -> Boolean = { true }
) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val viewModel = viewModel<SelectAppsViewModel>()
    val selected by navController.previousBackStackEntry!!.observeState<SnapshotStateList<AppInfo>>("selected")

    LaunchedEffect(viewModel) {
        viewModel.filterAppList(context, false, filter)
    }
    if (viewModel.done) {
        LaunchedEffect(viewModel) {
            navController.popBackStack()
        }
    }

    SwipeRefresh(
        state = rememberSwipeRefreshState(viewModel.isRefreshing),
        onRefresh = { viewModel.filterAppList(context, true, filter) },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn {
            items(viewModel.filteredList) {
                val checked = selected!!.contains(it)
                AppItem(
                    icon = viewModel.getIcon(it),
                    label = it.label,
                    packageName = it.app.packageName,
                    onClick = {
                        if (checked) selected!!.remove(it) else selected!!.add(it)
                    },
                    checked = checked
                )
            }
        }
    }
}
