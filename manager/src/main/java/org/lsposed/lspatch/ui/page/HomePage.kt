package org.lsposed.lspatch.ui.page

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.lsposed.lspatch.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePage() {
    Scaffold(
        topBar = { TopBar() }
    ) { innerPadding ->

    }
}

@Composable
private fun TopBar() {
    SmallTopAppBar(
        title = { Text(stringResource(R.string.app_name)) }
    )
}
