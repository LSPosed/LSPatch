package org.lsposed.lspatch.ui.page

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.lsposed.lspatch.R
import org.lsposed.lspatch.ui.util.LocalNavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagePage() {
    val navController = LocalNavController.current
    Scaffold(
        topBar = { TopBar() },
        floatingActionButton = {
            Fab { navController.navigate(PageList.NewPatch.name) }
        }
    ) { innerPadding ->

    }
}

@Composable
private fun TopBar() {
    SmallTopAppBar(
        title = { Text(PageList.Manage.title) }
    )
}

@Composable
private fun Fab(onClick: () -> Unit) {
    FloatingActionButton(onClick = onClick) {
        Icon(Icons.Filled.Add, stringResource(R.string.add))
    }
}
