package org.lsposed.lspatch.ui.page

import android.content.pm.ApplicationInfo
import android.os.Parcelable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toLowerCase
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.result.ResultBackNavigator
import kotlinx.parcelize.Parcelize
import org.lsposed.lspatch.R
import org.lsposed.lspatch.ui.component.AppItem
import org.lsposed.lspatch.ui.component.SearchAppBar
import org.lsposed.lspatch.ui.viewmodel.SelectAppsViewModel
import org.lsposed.lspatch.util.LSPPackageManager
import org.lsposed.lspatch.util.LSPPackageManager.AppInfo

@Parcelize
sealed class SelectAppsResult : Parcelable {
    data class SingleApp(val selected: AppInfo) : SelectAppsResult()
    data class MultipleApps(val selected: List<AppInfo>) : SelectAppsResult()
}

@OptIn(ExperimentalMaterial3Api::class)
@Destination
@Composable
fun SelectAppsScreen(
    navigator: ResultBackNavigator<SelectAppsResult>,
    multiSelect: Boolean,
    initialSelected: ArrayList<String>? = null
) {
    val viewModel = viewModel<SelectAppsViewModel>()

    var searchPackage by remember { mutableStateOf("") }
    val filter: (AppInfo) -> Boolean = {
        val packageLowerCase = searchPackage.toLowerCase(Locale.current)
        val contains = it.label.toLowerCase(Locale.current).contains(packageLowerCase) || it.app.packageName.contains(packageLowerCase)
        if (multiSelect) contains && it.isXposedModule
        else contains && it.app.flags and ApplicationInfo.FLAG_SYSTEM == 0
    }

    LaunchedEffect(Unit) {
        viewModel.filterAppList(false, filter)
        initialSelected?.let {
            val tmp = initialSelected.toSet()
            viewModel.multiSelected.addAll(LSPPackageManager.appList.filter { tmp.contains(it.app.packageName) })
        }
    }

    BackHandler {
        navigator.navigateBack()
    }

    Scaffold(
        topBar = {
            SearchAppBar(
                title = { Text(stringResource(R.string.screen_select_apps)) },
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
                    navigator.navigateBack()
                }
            )
        },
        floatingActionButton = {
            if (multiSelect) MultiSelectFab {
                navigator.navigateBack(SelectAppsResult.MultipleApps(viewModel.multiSelected))
            }
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
            else SingleSelect {
                navigator.navigateBack(SelectAppsResult.SingleApp(it))
            }
        }
    }
}

@Composable
private fun MultiSelectFab(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        content = { Icon(Icons.Outlined.Done, stringResource(R.string.add)) }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SingleSelect(onSelect: (AppInfo) -> Unit) {
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
                onClick = { onSelect(it) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MultiSelect() {
    val viewModel = viewModel<SelectAppsViewModel>()
    LazyColumn {
        items(
            items = viewModel.filteredList,
            key = { it.app.packageName }
        ) {
            val checked = viewModel.multiSelected.contains(it)
            AppItem(
                modifier = Modifier.animateItemPlacement(spring(stiffness = Spring.StiffnessLow)),
                icon = LSPPackageManager.getIcon(it),
                label = it.label,
                packageName = it.app.packageName,
                onClick = {
                    if (checked) viewModel.multiSelected.remove(it)
                    else viewModel.multiSelected.add(it)
                },
                checked = checked
            )
        }
    }
}
