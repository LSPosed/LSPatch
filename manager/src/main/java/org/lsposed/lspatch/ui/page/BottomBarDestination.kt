package org.lsposed.lspatch.ui.page

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.ramcosta.composedestinations.spec.DirectionDestinationSpec
import org.lsposed.lspatch.R
import org.lsposed.lspatch.ui.page.destinations.*

enum class BottomBarDestination(
    val direction: DirectionDestinationSpec,
    @StringRes val label: Int,
    val iconSelected: ImageVector,
    val iconNotSelected: ImageVector
) {
    Repo(RepoScreenDestination, R.string.screen_repo, Icons.Filled.GetApp, Icons.Outlined.GetApp),
    Manage(ManageScreenDestination, R.string.screen_manage, Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    Home(HomeScreenDestination, R.string.app_name, Icons.Filled.Home, Icons.Outlined.Home),
    Logs(LogsScreenDestination, R.string.screen_logs, Icons.Filled.Assignment, Icons.Outlined.Assignment),
    Settings(SettingsScreenDestination, R.string.screen_settings, Icons.Filled.Settings, Icons.Outlined.Settings);
}
