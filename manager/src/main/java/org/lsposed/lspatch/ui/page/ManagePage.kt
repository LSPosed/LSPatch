package org.lsposed.lspatch.ui.page

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.lsposed.lspatch.Constants.PREFS_STORAGE_DIRECTORY
import org.lsposed.lspatch.R
import org.lsposed.lspatch.config.ConfigManager
import org.lsposed.lspatch.database.entity.Module
import org.lsposed.lspatch.lspApp
import org.lsposed.lspatch.share.LSPConfig
import org.lsposed.lspatch.ui.component.AppItem
import org.lsposed.lspatch.ui.component.LoadingDialog
import org.lsposed.lspatch.ui.util.LocalNavController
import org.lsposed.lspatch.ui.util.LocalSnackbarHost
import org.lsposed.lspatch.ui.util.observeState
import org.lsposed.lspatch.ui.util.setState
import org.lsposed.lspatch.ui.viewmodel.ManageViewModel
import org.lsposed.lspatch.ui.viewmodel.ManageViewModel.ViewAction
import org.lsposed.lspatch.util.LSPPackageManager
import org.lsposed.lspatch.util.LSPPackageManager.AppInfo
import org.lsposed.lspatch.util.ShizukuApi
import java.io.IOException

private const val TAG = "ManagePage"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagePage() {
    val viewModel = viewModel<ManageViewModel>()

    Scaffold(
        topBar = { TopBar() },
        floatingActionButton = { Fab() }
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            Body()
        }
    }
}

@Composable
private fun TopBar() {
    SmallTopAppBar(
        title = { Text(PageList.Manage.title) }
    )
}

@Composable
private fun Fab() {
    val context = LocalContext.current
    val snackbarHost = LocalSnackbarHost.current
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    var shouldSelectDirectory by remember { mutableStateOf(false) }
    var showNewPatchDialog by remember { mutableStateOf(false) }

    val errorText = stringResource(R.string.patch_select_dir_error)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        try {
            if (it.resultCode == Activity.RESULT_CANCELED) return@rememberLauncherForActivityResult
            val uri = it.data?.data ?: throw IOException("No data")
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            lspApp.prefs.edit().putString(PREFS_STORAGE_DIRECTORY, uri.toString()).apply()
            Log.i(TAG, "Storage directory: ${uri.path}")
            showNewPatchDialog = true
        } catch (e: Exception) {
            Log.e(TAG, "Error when requesting saving directory", e)
            scope.launch { snackbarHost.showSnackbar(errorText) }
        }
    }

    if (shouldSelectDirectory) {
        AlertDialog(
            onDismissRequest = { shouldSelectDirectory = false },
            confirmButton = {
                TextButton(
                    content = { Text(stringResource(android.R.string.ok)) },
                    onClick = {
                        launcher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
                        shouldSelectDirectory = false
                    }
                )
            },
            dismissButton = {
                TextButton(
                    content = { Text(stringResource(android.R.string.cancel)) },
                    onClick = { shouldSelectDirectory = false }
                )
            },
            title = {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.patch_select_dir_title),
                    textAlign = TextAlign.Center
                )
            },
            text = { Text(stringResource(R.string.patch_select_dir_text)) }
        )
    }

    if (showNewPatchDialog) {
        AlertDialog(
            onDismissRequest = { showNewPatchDialog = false },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    content = { Text(stringResource(android.R.string.cancel)) },
                    onClick = { showNewPatchDialog = false }
                )
            },
            title = {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.page_new_patch),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                        onClick = {
                            navController.navigate(PageList.NewPatch.name + "?from=storage")
                            showNewPatchDialog = false
                        }
                    ) {
                        Text(
                            modifier = Modifier.padding(vertical = 8.dp),
                            text = stringResource(R.string.patch_from_storage),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                        onClick = {
                            navController.navigate(PageList.NewPatch.name + "?from=applist")
                            showNewPatchDialog = false
                        }
                    ) {
                        Text(
                            modifier = Modifier.padding(vertical = 8.dp),
                            text = stringResource(R.string.patch_from_applist),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        )
    }

    FloatingActionButton(
        content = { Icon(Icons.Filled.Add, stringResource(R.string.add)) },
        onClick = {
            val uri = lspApp.prefs.getString(PREFS_STORAGE_DIRECTORY, null)?.toUri()
            if (uri == null) {
                shouldSelectDirectory = true
            } else {
                runCatching {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                    if (DocumentFile.fromTreeUri(context, uri)?.exists() == false) throw IOException("Storage directory was deleted")
                }.onSuccess {
                    showNewPatchDialog = true
                }.onFailure {
                    Log.w(TAG, "Failed to take persistable permission for saved uri", it)
                    lspApp.prefs.edit().putString(PREFS_STORAGE_DIRECTORY, null).apply()
                    shouldSelectDirectory = true
                }
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Body() {
    val viewModel = viewModel<ManageViewModel>()
    val snackbarHost = LocalSnackbarHost.current
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    if (viewModel.appList.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            Text(
                modifier = Modifier.align(Alignment.Center),
                text = run {
                    if (LSPPackageManager.appList.isEmpty()) stringResource(R.string.manage_loading)
                    else stringResource(R.string.manage_no_apps)
                },
                fontFamily = FontFamily.Serif,
                style = MaterialTheme.typography.headlineSmall
            )
        }
    } else {
        var scopeApp by rememberSaveable { mutableStateOf("") }
        val isCancelled by navController.currentBackStackEntry!!.observeState<Boolean>("isCancelled")
        LaunchedEffect(isCancelled) {
            if (isCancelled == false) {
                val selected = navController.currentBackStackEntry!!
                    .savedStateHandle.getLiveData<SnapshotStateList<AppInfo>>("selected").value!!.toSet()
                Log.d(TAG, "Clear module list for $scopeApp")
                ConfigManager.getModulesForApp(scopeApp).forEach {
                    ConfigManager.deactivateModule(scopeApp, it)
                }
                selected.forEach {
                    Log.d(TAG, "Activate ${it.app.packageName} for $scopeApp")
                    ConfigManager.activateModule(scopeApp, Module(it.app.packageName, it.app.sourceDir))
                }
                navController.currentBackStackEntry!!.setState("isCancelled", null)
            }
        }

        if (viewModel.processingUpdate) LoadingDialog()
        viewModel.updateLoaderResult?.let {
            val updateSuccessfully = stringResource(R.string.manage_update_loader_successfully)
            val updateFailed = stringResource(R.string.manage_update_loader_failed)
            val copyError = stringResource(R.string.copy_error)
            LaunchedEffect(Unit) {
                it.onSuccess {
                    LSPPackageManager.fetchAppList()
                    snackbarHost.showSnackbar(updateSuccessfully)
                }.onFailure {
                    val result = snackbarHost.showSnackbar(updateFailed, copyError)
                    if (result == SnackbarResult.ActionPerformed) {
                        val cm = lspApp.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("LSPatch", it.toString()))
                    }
                }
                viewModel.dispatch(ViewAction.ClearUpdateLoaderResult)
            }
        }

        LazyColumn {
            items(
                items = viewModel.appList,
                key = { it.first.app.packageName }
            ) {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    AppItem(
                        modifier = Modifier.animateItemPlacement(spring(stiffness = Spring.StiffnessLow)),
                        icon = LSPPackageManager.getIcon(it.first),
                        label = it.first.label,
                        packageName = it.first.app.packageName,
                        onClick = { expanded = true },
                        onLongClick = { expanded = true },
                        additionalContent = {
                            val text = buildAnnotatedString {
                                val (text, color) =
                                    if (it.second.useManager) stringResource(R.string.patch_local) to MaterialTheme.colorScheme.secondary
                                    else stringResource(R.string.patch_portable) to MaterialTheme.colorScheme.tertiary
                                append(AnnotatedString(text, SpanStyle(color = color)))
                                append("  ")
                                append(it.second.lspConfig.VERSION_CODE.toString())
                            }
                            Text(
                                text = text,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Serif,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        val shizukuUnavailable = stringResource(R.string.shizuku_unavailable)
                        if (it.second.lspConfig.VERSION_CODE >= 319 && it.second.lspConfig.VERSION_CODE < LSPConfig.instance.VERSION_CODE) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.manage_update_loader)) },
                                onClick = {
                                    expanded = false
                                    scope.launch {
                                        if (!ShizukuApi.isPermissionGranted) {
                                            snackbarHost.showSnackbar(shizukuUnavailable)
                                        } else {
                                            viewModel.dispatch(ViewAction.UpdateLoader(it.first, it.second))
                                        }
                                    }
                                }
                            )
                        }
                        if (it.second.useManager) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.manage_module_scope)) },
                                onClick = {
                                    expanded = false
                                    scope.launch {
                                        scopeApp = it.first.app.packageName
                                        val activated = ConfigManager.getModulesForApp(scopeApp).map { it.pkgName }.toSet()
                                        navController.currentBackStackEntry!!.setState(
                                            "selected",
                                            SnapshotStateList<AppInfo>().apply {
                                                LSPPackageManager.appList.filterTo(this) { activated.contains(it.app.packageName) }
                                            }
                                        )
                                        navController.navigate(PageList.SelectApps.name + "?multiSelect=true")
                                    }
                                }
                            )
                        }
                        val optimizeSucceed = stringResource(R.string.manage_optimize_successfully)
                        val optimizeFailed = stringResource(R.string.manage_optimize_failed)
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.manage_optimize)) },
                            onClick = {
                                expanded = false
                                scope.launch {
                                    if (!ShizukuApi.isPermissionGranted) {
                                        snackbarHost.showSnackbar(shizukuUnavailable)
                                    } else {
                                        val result = ShizukuApi.performDexOptMode(it.first.app.packageName)
                                        snackbarHost.showSnackbar(if (result) optimizeSucceed else optimizeFailed)
                                    }
                                }
                            }
                        )
                        val uninstallSuccessfully = stringResource(R.string.manage_uninstall_successfully)
                        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                            if (it.resultCode == Activity.RESULT_OK) {
                                scope.launch {
                                    LSPPackageManager.fetchAppList()
                                    snackbarHost.showSnackbar(uninstallSuccessfully)
                                }
                            }
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.uninstall)) },
                            onClick = {
                                expanded = false
                                val intent = Intent(Intent.ACTION_DELETE).apply {
                                    data = Uri.parse("package:${it.first.app.packageName}")
                                    putExtra(Intent.EXTRA_RETURN_RESULT, true)
                                }
                                launcher.launch(intent)
                            }
                        )
                    }
                }
            }
        }
    }
}
