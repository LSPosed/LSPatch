package org.lsposed.lspatch.ui.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.lsposed.lspatch.R
import org.lsposed.lspatch.ui.page.manage.AppManageBody
import org.lsposed.lspatch.ui.page.manage.AppManageFab
import org.lsposed.lspatch.ui.page.manage.ModuleManageBody

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagePage() {
    var tab by rememberSaveable { mutableStateOf(0) }
    Scaffold(
        topBar = { TopBar() },
        floatingActionButton = { if (tab == 0) AppManageFab() }
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            Column {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }) {
                        Text(
                            modifier = Modifier.padding(vertical = 12.dp),
                            text = stringResource(R.string.apps)
                        )
                    }
                    Tab(selected = tab == 1, onClick = { tab = 1 }) {
                        Text(
                            modifier = Modifier.padding(vertical = 12.dp),
                            text = stringResource(R.string.modules)
                        )
                    }
                }
                when(tab) {
                    0 -> AppManageBody()
                    1 -> ModuleManageBody()
                }
            }
        }
    }
}

@Composable
private fun TopBar() {
    SmallTopAppBar(
        title = { Text(PageList.Manage.title) }
    )
}
