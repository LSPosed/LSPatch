package org.lsposed.lspatch.ui.page

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.lsposed.lspatch.R
import org.lsposed.lspatch.ui.util.LocalNavController

@Composable
fun ManageTopBar() {
    SmallTopAppBar(
        title = { Text(PageList.Manage.title) }
    )
}

@Composable
fun ManageFab() {
    val navController = LocalNavController.current
    FloatingActionButton(onClick = { navController.navigate(PageList.NewPatch.name) }) {
        Icon(Icons.Filled.Add, stringResource(R.string.add))
    }
}

@Composable
fun ManagePage() {

}
