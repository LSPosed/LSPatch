package org.lsposed.lspatch.ui.page

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageInstaller
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.result.NavResult
import com.ramcosta.composedestinations.result.ResultRecipient
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.lsposed.lspatch.R
import org.lsposed.lspatch.lspApp
import org.lsposed.lspatch.ui.component.AnywhereDropdown
import org.lsposed.lspatch.ui.component.SelectionColumn
import org.lsposed.lspatch.ui.component.ShimmerAnimation
import org.lsposed.lspatch.ui.component.settings.SettingsCheckBox
import org.lsposed.lspatch.ui.component.settings.SettingsItem
import org.lsposed.lspatch.ui.page.destinations.SelectAppsScreenDestination
import org.lsposed.lspatch.ui.util.LocalSnackbarHost
import org.lsposed.lspatch.ui.util.isScrolledToEnd
import org.lsposed.lspatch.ui.util.lastItemIndex
import org.lsposed.lspatch.ui.viewmodel.NewPatchViewModel
import org.lsposed.lspatch.ui.viewmodel.NewPatchViewModel.PatchState
import org.lsposed.lspatch.ui.viewmodel.NewPatchViewModel.ViewAction
import org.lsposed.lspatch.util.LSPPackageManager
import org.lsposed.lspatch.util.LSPPackageManager.AppInfo
import org.lsposed.lspatch.util.ShizukuApi

private const val TAG = "NewPatchPage"

const val ACTION_STORAGE = 0
const val ACTION_APPLIST = 1
const val ACTION_INTENT_INSTALL = 2

@OptIn(ExperimentalMaterial3Api::class)
@Destination
@Composable
fun NewPatchScreen(
    navigator: DestinationsNavigator,
    resultRecipient: ResultRecipient<SelectAppsScreenDestination, SelectAppsResult>,
    id: Int,
    data: Uri? = null
) {
    val viewModel = viewModel<NewPatchViewModel>()
    val snackbarHost = LocalSnackbarHost.current
    val errorUnknown = stringResource(R.string.error_unknown)
    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { apks ->
        if (apks.isEmpty()) {
            navigator.navigateUp()
            return@rememberLauncherForActivityResult
        }
        runBlocking {
            LSPPackageManager.getAppInfoFromApks(apks)
                .onSuccess {
                    viewModel.dispatch(ViewAction.ConfigurePatch(it.first()))
                }
                .onFailure {
                    lspApp.globalScope.launch { snackbarHost.showSnackbar(it.message ?: errorUnknown) }
                    navigator.navigateUp()
                }
        }
    }

    var showSelectModuleDialog by remember { mutableStateOf(false) }
    val noXposedModules = stringResource(R.string.patch_no_xposed_module)
    val storageModuleLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { apks ->
            if (apks.isEmpty()) {
                return@rememberLauncherForActivityResult
            }
            runBlocking {
                LSPPackageManager.getAppInfoFromApks(apks).onSuccess { it ->
                    viewModel.embeddedModules = it.filter { it.isXposedModule }.ifEmpty {
                        lspApp.globalScope.launch {
                            snackbarHost.showSnackbar(noXposedModules)
                        }
                        return@onSuccess
                    }
                }.onFailure {
                    lspApp.globalScope.launch {
                        snackbarHost.showSnackbar(
                            it.message ?: errorUnknown
                        )
                    }
                }
            }
        }

    Log.d(TAG, "PatchState: ${viewModel.patchState}")
    when (viewModel.patchState) {
        PatchState.INIT -> {
            LaunchedEffect(Unit) {
                LSPPackageManager.cleanTmpApkDir()
                when (id) {
                    ACTION_STORAGE -> {
                        storageLauncher.launch(arrayOf("application/vnd.android.package-archive"))
                        viewModel.dispatch(ViewAction.DoneInit)
                    }

                    ACTION_APPLIST -> {
                        navigator.navigate(SelectAppsScreenDestination(false))
                        viewModel.dispatch(ViewAction.DoneInit)
                    }

                    ACTION_INTENT_INSTALL -> {
                        runBlocking {
                            data?.let { uri ->
                                LSPPackageManager.getAppInfoFromApks(listOf(uri)).onSuccess {
                                    viewModel.dispatch(ViewAction.ConfigurePatch(it.first()))
                                }.onFailure {
                                    lspApp.globalScope.launch {
                                        snackbarHost.showSnackbar(
                                            it.message ?: errorUnknown
                                        )
                                    }
                                    navigator.navigateUp()
                                }
                            }
                        }
                    }
                }
            }
        }
        PatchState.SELECTING -> {
            resultRecipient.onNavResult {
                Log.d(TAG, "onNavResult: $it")
                when (it) {
                    is NavResult.Canceled -> navigator.navigateUp()
                    is NavResult.Value -> {
                        val result = it.value as SelectAppsResult.SingleApp
                        viewModel.dispatch(ViewAction.ConfigurePatch(result.selected))
                    }
                }
            }
        }
        else -> {
            Scaffold(
                topBar = {
                    when (viewModel.patchState) {
                        PatchState.CONFIGURING -> ConfiguringTopBar { navigator.navigateUp() }
                        PatchState.PATCHING,
                        PatchState.FINISHED,
                        PatchState.ERROR -> CenterAlignedTopAppBar(title = { Text(viewModel.patchApp.app.packageName) })
                        else -> Unit
                    }
                },
                floatingActionButton = {
                    if (viewModel.patchState == PatchState.CONFIGURING) {
                        ConfiguringFab()
                    }
                }
            ) { innerPadding ->
                if (viewModel.patchState == PatchState.CONFIGURING) {
                    PatchOptionsBody(Modifier.padding(innerPadding)) {
                        showSelectModuleDialog = true
                    }
                    resultRecipient.onNavResult {
                        if (it is NavResult.Value) {
                            val result = it.value as SelectAppsResult.MultipleApps
                            viewModel.embeddedModules = result.selected
                        }
                    }
                } else {
                    DoPatchBody(Modifier.padding(innerPadding), navigator)
                }
            }

            if (showSelectModuleDialog) {
                AlertDialog(onDismissRequest = { showSelectModuleDialog = false },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(content = { Text(stringResource(android.R.string.cancel)) },
                            onClick = { showSelectModuleDialog = false })
                    },
                    title = {
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(R.string.patch_embed_modules),
                            textAlign = TextAlign.Center
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            TextButton(modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                                onClick = {
                                    storageModuleLauncher.launch(arrayOf("application/vnd.android.package-archive"))
                                    showSelectModuleDialog = false
                                }) {
                                Text(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    text = stringResource(R.string.patch_from_storage),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            TextButton(modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                                onClick = {
                                    navigator.navigate(
                                        SelectAppsScreenDestination(true,
                                            viewModel.embeddedModules.mapTo(ArrayList()) { it.app.packageName })
                                    )
                                    showSelectModuleDialog = false
                                }) {
                                Text(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    text = stringResource(R.string.patch_from_applist),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfiguringTopBar(onBackClick: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.screen_new_patch)) },
        navigationIcon = {
            IconButton(
                onClick = onBackClick,
                content = { Icon(Icons.Outlined.ArrowBack, null) }
            )
        }
    )
}

@Composable
private fun ConfiguringFab() {
    val viewModel = viewModel<NewPatchViewModel>()
    ExtendedFloatingActionButton(
        text = { Text(stringResource(R.string.patch_start)) },
        icon = { Icon(Icons.Outlined.AutoFixHigh, null) },
        onClick = { viewModel.dispatch(ViewAction.SubmitPatch) }
    )
}

@Composable
private fun sigBypassLvStr(level: Int) = when (level) {
    0 -> stringResource(R.string.patch_sigbypasslv0)
    1 -> stringResource(R.string.patch_sigbypasslv1)
    2 -> stringResource(R.string.patch_sigbypasslv2)
    else -> throw IllegalArgumentException("Invalid sigBypassLv: $level")
}

@Composable
private fun PatchOptionsBody(modifier: Modifier, onAddEmbed: () -> Unit) {
    val viewModel = viewModel<NewPatchViewModel>()

    Column(modifier.verticalScroll(rememberScrollState())) {
        Text(
            text = viewModel.patchApp.label,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Text(
            text = viewModel.patchApp.app.packageName,
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
                selected = viewModel.useManager,
                onClick = { viewModel.useManager = true },
                icon = Icons.Outlined.Api,
                title = stringResource(R.string.patch_local),
                desc = stringResource(R.string.patch_local_desc)
            )
            SelectionItem(
                selected = !viewModel.useManager,
                onClick = { viewModel.useManager = false },
                icon = Icons.Outlined.WorkOutline,
                title = stringResource(R.string.patch_integrated),
                desc = stringResource(R.string.patch_integrated_desc),
                extraContent = {
                    TextButton(
                        onClick = onAddEmbed,
                        content = { Text(text = stringResource(R.string.patch_embed_modules), style = MaterialTheme.typography.bodyLarge) }
                    )
                }
            )
        }
        SettingsCheckBox(
            modifier = Modifier
                .padding(top = 6.dp)
                .clickable { viewModel.debuggable = !viewModel.debuggable },
            checked = viewModel.debuggable,
            icon = Icons.Outlined.BugReport,
            title = stringResource(R.string.patch_debuggable)
        )
        SettingsCheckBox(
            modifier = Modifier.clickable { viewModel.overrideVersionCode = !viewModel.overrideVersionCode },
            checked = viewModel.overrideVersionCode,
            icon = Icons.Outlined.Layers,
            title = stringResource(R.string.patch_override_version_code),
            desc = stringResource(R.string.patch_override_version_code_desc)
        )
        var bypassExpanded by remember { mutableStateOf(false) }
        AnywhereDropdown(
            expanded = bypassExpanded,
            onDismissRequest = { bypassExpanded = false },
            onClick = { bypassExpanded = true },
            surface = {
                SettingsItem(
                    icon = Icons.Outlined.RemoveModerator,
                    title = stringResource(R.string.patch_sigbypass),
                    desc = sigBypassLvStr(viewModel.sigBypassLevel)
                )
            }
        ) {
            repeat(3) {
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = viewModel.sigBypassLevel == it, onClick = { viewModel.sigBypassLevel = it })
                            Text(sigBypassLvStr(it))
                        }
                    },
                    onClick = {
                        viewModel.sigBypassLevel = it
                        bypassExpanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun DoPatchBody(modifier: Modifier, navigator: DestinationsNavigator) {
    val viewModel = viewModel<NewPatchViewModel>()
    val snackbarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (viewModel.logs.isEmpty()) {
            viewModel.dispatch(ViewAction.LaunchPatch)
        }
    }

    BoxWithConstraints(modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
        val shellBoxMaxHeight =
            if (viewModel.patchState == PatchState.PATCHING) maxHeight
            else maxHeight - ButtonDefaults.MinHeight - 12.dp
        Column(
            Modifier
                .fillMaxSize()
                .wrapContentHeight()
                .animateContentSize(spring(stiffness = Spring.StiffnessLow))
        ) {
            ShimmerAnimation(enabled = viewModel.patchState == PatchState.PATCHING) {
                ProvideTextStyle(MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)) {
                    val scrollState = rememberLazyListState()
                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = shellBoxMaxHeight)
                            .clip(RoundedCornerShape(32.dp))
                            .background(brush)
                            .padding(horizontal = 24.dp, vertical = 18.dp)
                    ) {
                        items(viewModel.logs) {
                            when (it.first) {
                                Log.DEBUG -> Text(text = it.second)
                                Log.INFO -> Text(text = it.second)
                                Log.ERROR -> Text(text = it.second, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    LaunchedEffect(scrollState.lastItemIndex) {
                        if (!scrollState.isScrolledToEnd) {
                            scrollState.animateScrollToItem(scrollState.lastItemIndex!!)
                        }
                    }
                }
            }

            when (viewModel.patchState) {
                PatchState.PATCHING -> BackHandler {}
                PatchState.FINISHED -> {
                    val shizukuUnavailable = stringResource(R.string.shizuku_unavailable)
                    val installSuccessfully = stringResource(R.string.patch_install_successfully)
                    val installFailed = stringResource(R.string.patch_install_failed)
                    val copyError = stringResource(R.string.copy_error)
                    var installing by remember { mutableStateOf(false) }
                    if (installing) InstallDialog(viewModel.patchApp) { status, message ->
                        scope.launch {
                            installing = false
                            if (status == PackageInstaller.STATUS_SUCCESS) {
                                lspApp.globalScope.launch { snackbarHost.showSnackbar(installSuccessfully) }
                                navigator.navigateUp()
                            } else if (status != LSPPackageManager.STATUS_USER_CANCELLED) {
                                val result = snackbarHost.showSnackbar(installFailed, copyError)
                                if (result == SnackbarResult.ActionPerformed) {
                                    val cm = lspApp.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("LSPatch", message))
                                }
                            }
                        }
                    }
                    Row(Modifier.padding(top = 12.dp)) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = { navigator.navigateUp() },
                            content = { Text(stringResource(R.string.patch_return)) }
                        )
                        Spacer(Modifier.weight(0.2f))
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (!ShizukuApi.isPermissionGranted) {
                                    scope.launch {
                                        snackbarHost.showSnackbar(shizukuUnavailable)
                                    }
                                } else {
                                    installing = true
                                }
                            },
                            content = { Text(stringResource(R.string.install)) }
                        )
                    }
                }
                PatchState.ERROR -> {
                    Row(Modifier.padding(top = 12.dp)) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = { navigator.navigateUp() },
                            content = { Text(stringResource(R.string.patch_return)) }
                        )
                        Spacer(Modifier.weight(0.2f))
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val cm = lspApp.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("LSPatch", viewModel.logs.joinToString { it.second + "\n" }))
                            },
                            content = { Text(stringResource(R.string.copy_error)) }
                        )
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun InstallDialog(patchApp: AppInfo, onFinish: (Int, String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var uninstallFirst by remember { mutableStateOf(ShizukuApi.isPackageInstalledWithoutPatch(patchApp.app.packageName)) }
    var installing by remember { mutableStateOf(0) }
    suspend fun doInstall() {
        Log.i(TAG, "Installing app ${patchApp.app.packageName}")
        installing = 1
        val (status, message) = LSPPackageManager.install()
        installing = 0
        Log.i(TAG, "Installation end: $status, $message")
        onFinish(status, message)
    }

    LaunchedEffect(Unit) {
        if (!uninstallFirst) {
            doInstall()
        }
    }

    if (uninstallFirst) {
        AlertDialog(
            onDismissRequest = { onFinish(LSPPackageManager.STATUS_USER_CANCELLED, "User cancelled") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            Log.i(TAG, "Uninstalling app ${patchApp.app.packageName}")
                            uninstallFirst = false
                            installing = 2
                            val (status, message) = LSPPackageManager.uninstall(patchApp.app.packageName)
                            installing = 0
                            Log.i(TAG, "Uninstallation end: $status, $message")
                            if (status == PackageInstaller.STATUS_SUCCESS) {
                                doInstall()
                            } else {
                                onFinish(status, message)
                            }
                        }
                    },
                    content = { Text(stringResource(android.R.string.ok)) }
                )
            },
            dismissButton = {
                TextButton(
                    onClick = { onFinish(LSPPackageManager.STATUS_USER_CANCELLED, "User cancelled") },
                    content = { Text(stringResource(android.R.string.cancel)) }
                )
            },
            title = {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.uninstall),
                    textAlign = TextAlign.Center
                )
            },
            text = { Text(stringResource(R.string.patch_uninstall_text)) }
        )
    }

    if (installing != 0) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(if (installing == 1) R.string.installing else R.string.uninstalling),
                    fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Center
                )
            }
        )
    }
}
