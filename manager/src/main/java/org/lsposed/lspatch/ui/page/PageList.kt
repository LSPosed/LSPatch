package org.lsposed.lspatch.ui.page

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.navArgument
import org.lsposed.lspatch.R

enum class PageList(
    val iconSelected: ImageVector? = null,
    val iconNotSelected: ImageVector? = null,
    val arguments: List<NamedNavArgument> = emptyList(),
    val topBar: @Composable () -> Unit,
    val fab: @Composable () -> Unit = {},
    val body: @Composable NavBackStackEntry.() -> Unit
) {
    Home(
        iconSelected = Icons.Filled.Home,
        iconNotSelected = Icons.Outlined.Home,
        topBar = { HomeTopBar() },
        body = { HomePage() }
    ),
    Manage(
        iconSelected = Icons.Filled.Dashboard,
        iconNotSelected = Icons.Outlined.Dashboard,
        topBar = { ManageTopBar() },
        fab = { ManageFab() },
        body = { ManagePage() }
    ),
    Repo(
        iconSelected = Icons.Filled.GetApp,
        iconNotSelected = Icons.Outlined.GetApp,
        topBar = {},
        body = {}
    ),
    Settings(
        iconSelected = Icons.Filled.Settings,
        iconNotSelected = Icons.Outlined.Settings,
        topBar = {},
        body = {}
    ),
    NewPatch(
        topBar = {},
        fab = { NewPatchFab() },
        body = { NewPatchPage() }
    ),
    SelectApps(
        arguments = listOf(
            navArgument("multiSelect") { type = NavType.BoolType }
        ),
        topBar = { SelectAppsTopBar() },
        fab = { SelectAppsFab() },
        body = { SelectAppsPage(this) }
    );

    val title: String
        @Composable get() = when (this) {
            Home -> stringResource(R.string.app_name)
            Manage -> stringResource(R.string.page_manage)
            Repo -> stringResource(R.string.page_repo)
            Settings -> stringResource(R.string.page_settings)
            NewPatch -> stringResource(R.string.page_new_patch)
            SelectApps -> stringResource(R.string.page_select_apps)
        }
}
