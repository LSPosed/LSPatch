package org.lsposed.lspatch.ui.page

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.lsposed.lspatch.R
import org.lsposed.lspatch.TAG
import org.lsposed.lspatch.ui.component.SelectionColumn
import org.lsposed.lspatch.ui.util.LocalNavController
import org.lsposed.lspatch.ui.viewmodel.AppInfo

@Composable
fun NewPatchFab() {
    val navController = LocalNavController.current
    val patchApp by navController.currentBackStackEntry!!.savedStateHandle
        .getLiveData<AppInfo>("appInfo").observeAsState()
    if (patchApp != null) {
        ExtendedFloatingActionButton(
            text = { Text(stringResource(R.string.patch_start)) },
            icon = { Icon(Icons.Outlined.AutoFixHigh, null) },
            onClick = { /*TODO*/ }
        )
    }
}

@Composable
fun NewPatchPage() {
    val navController = LocalNavController.current
    val patchApp by navController.currentBackStackEntry!!.savedStateHandle
        .getLiveData<AppInfo>("appInfo").observeAsState()

    Log.d(TAG, "patchApp is $patchApp")
    if (patchApp == null) {
        navController.navigate(PageList.SelectApps.name + "/false")
    } else {
        var useManager by rememberSaveable { mutableStateOf(true) }

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp)
        ) {
            Text(text = patchApp!!.label, style = MaterialTheme.typography.headlineSmall)
            Text(text = patchApp!!.app.packageName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.patch_mode),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 24.dp, bottom = 12.dp)
            )
            SelectionColumn {
                SelectionItem(
                    selected = useManager,
                    onClick = { useManager = true },
                    icon = Icons.Outlined.Api,
                    title = stringResource(R.string.patch_local),
                    desc = stringResource(R.string.patch_local_desc)
                )
                SelectionItem(
                    selected = !useManager,
                    onClick = { useManager = false },
                    icon = Icons.Outlined.WorkOutline,
                    title = stringResource(R.string.patch_portable),
                    desc = stringResource(R.string.patch_portable_desc),
                    extraContent = {
                        TextButton(
                            onClick = { /* TODO */ }
                        ) {
                            Text(text = stringResource(R.string.patch_embed_modules), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                )
            }
        }
    }
}
