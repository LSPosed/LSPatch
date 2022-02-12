package org.lsposed.lspatch.ui.page

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import org.lsposed.lspatch.ui.component.AppItem
import org.lsposed.lspatch.ui.util.LocalNavController
import org.lsposed.lspatch.ui.viewmodel.AppInfo
import org.lsposed.lspatch.ui.viewmodel.SelectAppViewModel

@Composable
fun SelectAppsPage(entry: NavBackStackEntry) {
    val multiSelect = entry.arguments?.get("multiSelect") as? Boolean
        ?: throw IllegalArgumentException("multiSelect is null")
    if (multiSelect) {
        TODO("MultiSelect")
    } else {
        SelectSingle()
    }
}

@Composable
private fun SelectSingle(
    filter: (AppInfo) -> Boolean = { true }
) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val viewModel = viewModel<SelectAppViewModel>()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    if (SelectAppViewModel.appList.isEmpty())
        LaunchedEffect(viewModel) {
            viewModel.loadAppList(context)
        }

    SwipeRefresh(
        state = rememberSwipeRefreshState(isRefreshing),
        onRefresh = { viewModel.loadAppList(context) },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn {
            items(SelectAppViewModel.appList) {
                AppItem(
                    icon = it.icon,
                    label = it.label,
                    packageName = it.app.packageName,
                    onClick = {
                        navController.previousBackStackEntry!!.savedStateHandle.getLiveData<AppInfo>("appInfo").value = it
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
