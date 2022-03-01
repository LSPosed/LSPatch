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
    val body: @Composable NavBackStackEntry.() -> Unit
) {
    Home(
        iconSelected = Icons.Filled.Home,
        iconNotSelected = Icons.Outlined.Home,
        body = { HomePage() }
    ),
    Manage(
        iconSelected = Icons.Filled.Dashboard,
        iconNotSelected = Icons.Outlined.Dashboard,
        body = { ManagePage() }
    ),
    Repo(
        iconSelected = Icons.Filled.GetApp,
        iconNotSelected = Icons.Outlined.GetApp,
        body = {}
    ),
    Settings(
        iconSelected = Icons.Filled.Settings,
        iconNotSelected = Icons.Outlined.Settings,
        body = {}
    ),
    NewPatch(
        body = { NewPatchPage(this) }
    ),
    SelectApps(
        arguments = listOf(
            navArgument("multiSelect") { type = NavType.BoolType }
        ),
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
