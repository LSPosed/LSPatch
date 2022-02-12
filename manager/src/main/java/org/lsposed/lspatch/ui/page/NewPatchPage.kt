package org.lsposed.lspatch.ui.page

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import org.lsposed.lspatch.Patcher
import org.lsposed.lspatch.R
import org.lsposed.lspatch.TAG
import org.lsposed.lspatch.ui.component.SelectionColumn
import org.lsposed.lspatch.ui.component.settings.SettingsCheckBox
import org.lsposed.lspatch.ui.component.settings.SettingsItem
import org.lsposed.lspatch.ui.util.LocalNavController
import org.lsposed.lspatch.ui.viewmodel.AppInfo

class NewPatchPageViewModel : ViewModel() {
    var patchApp by mutableStateOf<AppInfo?>(null)
    var confirm by mutableStateOf(false)
    var patchOptions by mutableStateOf<Patcher.Options?>(null)
}

@Composable
fun NewPatchFab() {
    val viewModel = viewModel<NewPatchPageViewModel>()
    if (viewModel.patchApp != null) {
        ExtendedFloatingActionButton(
            text = { Text(stringResource(R.string.patch_start)) },
            icon = { Icon(Icons.Outlined.AutoFixHigh, null) },
            onClick = { viewModel.confirm = true }
        )
    }
}

@Composable
fun NewPatchPage() {
    val viewModel = viewModel<NewPatchPageViewModel>()
    val navController = LocalNavController.current
    val appInfo by navController.currentBackStackEntry!!.savedStateHandle
        .getLiveData<AppInfo>("appInfo").observeAsState()
    viewModel.patchApp = appInfo

    Log.d(TAG, "confirm = ${viewModel.confirm}")

    when {
        viewModel.patchApp == null -> navController.navigate(PageList.SelectApps.name + "/false")
        viewModel.patchOptions == null -> PatchOptionsPage(viewModel.patchApp!!, viewModel.confirm)
        else -> PatchingPage(viewModel.patchOptions!!)
    }
}

@Composable
private fun PatchOptionsPage(patchApp: AppInfo, confirm: Boolean) {
    val viewModel = viewModel<NewPatchPageViewModel>()
    var useManager by rememberSaveable { mutableStateOf(true) }
    var debuggable by rememberSaveable { mutableStateOf(false) }
    var v1 by rememberSaveable { mutableStateOf(false) }
    var v2 by rememberSaveable { mutableStateOf(true) }
    var v3 by rememberSaveable { mutableStateOf(true) }
    val sigBypassLevel by rememberSaveable { mutableStateOf(2) }
    var overrideVersionCode by rememberSaveable { mutableStateOf(false) }

    if (confirm) LaunchedEffect(patchApp) {
        viewModel.patchOptions = Patcher.Options(
            apkPaths = arrayOf(patchApp.app.sourceDir), // TODO: Split Apk
            debuggable = debuggable,
            sigbypassLevel = sigBypassLevel,
            v1 = v1, v2 = v2, v3 = v3,
            useManager = useManager,
            overrideVersionCode = overrideVersionCode,
            verbose = true,
            embeddedModules = emptyList() // TODO: Embed modules
        )
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(top = 24.dp)
    ) {
        Text(
            text = patchApp.label,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Text(
            text = patchApp.app.packageName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Text(
            text = stringResource(R.string.patch_mode),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 24.dp, bottom = 12.dp)
        )
        SelectionColumn(Modifier.padding(horizontal = 24.dp)) {
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
        SettingsCheckBox(
            checked = debuggable,
            onClick = { debuggable = !debuggable },
            icon = Icons.Outlined.BugReport,
            title = stringResource(R.string.patch_debuggable)
        )
        SettingsCheckBox(
            checked = v1,
            onClick = { v1 = !v1 },
            title = stringResource(R.string.patch_v1)
        )
        SettingsCheckBox(
            checked = v2,
            onClick = { v2 = !v2 },
            title = stringResource(R.string.patch_v2)
        )
        SettingsCheckBox(
            checked = v3,
            onClick = { v3 = !v3 },
            title = stringResource(R.string.patch_v3)
        )
        SettingsItem(
            onClick = { /*TODO*/ },
            title = stringResource(R.string.patch_sigbypasslv),
            desc = stringResource(R.string.patch_sigbypasslv_desc)
        )
        SettingsCheckBox(
            checked = overrideVersionCode,
            onClick = { overrideVersionCode = !overrideVersionCode },
            title = stringResource(R.string.patch_override_version_code),
            desc = stringResource(R.string.patch_override_version_code_desc)
        )
        Spacer(Modifier.height(56.dp))
    }
}

@Composable
private fun PatchingPage(patcherOptions: Patcher.Options) {

}
