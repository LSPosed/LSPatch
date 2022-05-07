package org.lsposed.lspatch.ui.page

import android.content.pm.ApplicationInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toLowerCase
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import org.lsposed.lspatch.R
import org.lsposed.lspatch.ui.component.AppItem
import org.lsposed.lspatch.ui.component.SearchAppBar
import org.lsposed.lspatch.ui.util.LocalNavController
import org.lsposed.lspatch.ui.util.observeState
import org.lsposed.lspatch.ui.util.setState
import org.lsposed.lspatch.ui.viewmodel.SelectAppsViewModel
import org.lsposed.lspatch.util.LSPPackageManager
import org.lsposed.lspatch.util.LSPPackageManager.AppInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectAppsPage(entry: NavBackStackEntry) {
    val viewModel = viewModel<SelectAppsViewModel>()
    val navController = LocalNavController.current
    val multiSelect = entry.arguments?.get("multiSelect") as? Boolean
        ?: throw IllegalArgumentException("multiSelect is null")

    var searchPackage by remember { mutableStateOf("") }
    val filter: (AppInfo) -> Boolean = {
        val packageLowerCase = searchPackage.toLowerCase(Locale.current)
        val contains = it.label.toLowerCase(Locale.current).contains(packageLowerCase) || it.app.packageName.contains(packageLowerCase)
        if (multiSelect) contains && it.app.metaData?.get("xposedminversion") != null
        else contains && it.app.flags and ApplicationInfo.FLAG_SYSTEM == 0
    }

    LaunchedEffect(Unit) {
        viewModel.filterAppList(false, filter)
    }

    BackHandler {
        navController.previousBackStackEntry!!.setState("isCancelled", true)
        navController.popBackStack()
    }

    Scaffold(
        topBar = {
            SearchAppBar(
                title = { Text(stringResource(R.string.page_select_apps)) },
                searchText = searchPackage,
                onSearchTextChange = {
                    searchPackage = it
                    viewModel.filterAppList(false, filter)
                },
                onClearClick = {
                    searchPackage = ""
                    viewModel.filterAppList(false, filter)
                },
                onBackClick = {
                    navController.previousBackStackEntry!!.setState("isCancelled", true)
                    navController.popBackStack()
                }
            )
        },
        floatingActionButton = {
            if (multiSelect) MultiSelectFab()
        }
    ) { innerPadding ->
        SwipeRefresh(
            state = rememberSwipeRefreshState(viewModel.isRefreshing),
            onRefresh = { viewModel.filterAppList(true, filter) },
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (multiSelect) MultiSelect()
            else SingleSelect()
        }
    }
}

@Composable
private fun MultiSelectFab() {
    val navController = LocalNavController.current
    FloatingActionButton(onClick = { navController.popBackStack() }) {
        Icon(Icons.Outlined.Done, stringResource(R.string.add))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SingleSelect() {
    val navController = LocalNavController.current
    val viewModel = viewModel<SelectAppsViewModel>()

    LazyColumn {
        items(
            items = viewModel.filteredList,
            key = { it.app.packageName }
        ) {
            AppItem(
                modifier = Modifier.animateItemPlacement(spring(stiffness = Spring.StiffnessLow)),
                icon = LSPPackageManager.getIcon(it),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MultiSelect() {
    val navController = LocalNavController.current
    val viewModel = viewModel<SelectAppsViewModel>()
    val selected by navController.previousBackStackEntry!!.observeState<SnapshotStateList<AppInfo>>("selected")

    LazyColumn {
        items(
            items = viewModel.filteredList,
            key = { it.app.packageName }
        ) {
            val checked = selected!!.contains(it)
            AppItem(
                modifier = Modifier.animateItemPlacement(spring(stiffness = Spring.StiffnessLow)),
                icon = LSPPackageManager.getIcon(it),
                label = it.label,
                packageName = it.app.packageName,
                onClick = {
                    if (checked) selected!!.remove(it)
                    else selected!!.add(it)
                },
                checked = checked
            )
        }
    }
}
