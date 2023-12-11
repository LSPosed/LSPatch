package org.lsposed.lspatch.ui.page.manage

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardCapslock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.result.NavResult
import com.ramcosta.composedestinations.result.ResultRecipient
import kotlinx.coroutines.launch
import org.lsposed.lspatch.BuildConfig
import org.lsposed.lspatch.R
import org.lsposed.lspatch.config.ConfigManager
import org.lsposed.lspatch.config.Configs
import org.lsposed.lspatch.database.entity.Module
import org.lsposed.lspatch.lspApp
import org.lsposed.lspatch.share.Constants
import org.lsposed.lspatch.share.LSPConfig
import org.lsposed.lspatch.ui.component.AnywhereDropdown
import org.lsposed.lspatch.ui.component.AppItem
import org.lsposed.lspatch.ui.component.LoadingDialog
import org.lsposed.lspatch.ui.page.ACTION_APPLIST
import org.lsposed.lspatch.ui.page.ACTION_STORAGE
import org.lsposed.lspatch.ui.page.SelectAppsResult
import org.lsposed.lspatch.ui.page.destinations.NewPatchScreenDestination
import org.lsposed.lspatch.ui.page.destinations.SelectAppsScreenDestination
import org.lsposed.lspatch.ui.util.LocalSnackbarHost
import org.lsposed.lspatch.ui.viewmodel.manage.AppManageViewModel
import org.lsposed.lspatch.ui.viewstate.ProcessingState
import org.lsposed.lspatch.util.LSPPackageManager
import org.lsposed.lspatch.util.ShizukuApi
import java.io.IOException

private const val TAG = "AppManagePage"

@Composable
fun AppManageBody(
    navigator: DestinationsNavigator,
    resultRecipient: ResultRecipient<SelectAppsScreenDestination, SelectAppsResult>
) {
    val viewModel = viewModel<AppManageViewModel>()
    val snackbarHost = LocalSnackbarHost.current
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
        resultRecipient.onNavResult {
            if (it is NavResult.Value) {
                scope.launch {
                    val result = it.value as SelectAppsResult.MultipleApps
                    ConfigManager.getModulesForApp(scopeApp).forEach {
                        ConfigManager.deactivateModule(scopeApp, it)
                    }
                    result.selected.forEach {
                        Log.d(TAG, "Activate ${it.app.packageName} for $scopeApp")
                        ConfigManager.activateModule(scopeApp, Module(it.app.packageName, it.app.sourceDir))
                    }
                }
            }
        }

        when (viewModel.updateLoaderState) {
            is ProcessingState.Idle -> Unit
            is ProcessingState.Processing -> LoadingDialog()
            is ProcessingState.Done -> {
                val it = viewModel.updateLoaderState as ProcessingState.Done
                val updateSuccessfully = stringResource(R.string.manage_update_loader_successfully)
                val updateFailed = stringResource(R.string.manage_update_loader_failed)
                val copyError = stringResource(R.string.copy_error)
                LaunchedEffect(Unit) {
                    it.result.onSuccess {
                        snackbarHost.showSnackbar(updateSuccessfully)
                    }.onFailure {
                        val result = snackbarHost.showSnackbar(updateFailed, copyError)
                        if (result == SnackbarResult.ActionPerformed) {
                            val cm = lspApp.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("LSPatch", it.toString()))
                        }
                    }
                    viewModel.dispatch(AppManageViewModel.ViewAction.ClearUpdateLoaderResult)
                }
            }
        }
        when (viewModel.optimizeState) {
            is ProcessingState.Idle -> Unit
            is ProcessingState.Processing -> LoadingDialog()
            is ProcessingState.Done -> {
                val it = viewModel.optimizeState as ProcessingState.Done
                val optimizeSucceed = stringResource(R.string.manage_optimize_successfully)
                val optimizeFailed = stringResource(R.string.manage_optimize_failed)
                LaunchedEffect(Unit) {
                    snackbarHost.showSnackbar(if (it.result) optimizeSucceed else optimizeFailed)
                    viewModel.dispatch(AppManageViewModel.ViewAction.ClearOptimizeResult)
                }
            }
        }

        LazyColumn(Modifier.fillMaxHeight()) {
            items(
                items = viewModel.appList,
                key = { it.first.app.packageName }
            ) {
                val isRolling = it.second.useManager && it.second.lspConfig.VERSION_CODE >= Constants.MIN_ROLLING_VERSION_CODE
                val canUpdateLoader = !isRolling && it.second.lspConfig.VERSION_CODE < LSPConfig.instance.VERSION_CODE
                var expanded by remember { mutableStateOf(false) }
                AnywhereDropdown(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    onClick = { expanded = true },
                    onLongClick = { expanded = true },
                    surface = {
                        AppItem(
                            icon = LSPPackageManager.getIcon(it.first),
                            label = it.first.label,
                            packageName = it.first.app.packageName,
                            additionalContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = buildAnnotatedString {
                                            val (text, color) =
                                                if (it.second.useManager) stringResource(R.string.patch_local) to MaterialTheme.colorScheme.secondary
                                                else stringResource(R.string.patch_integrated) to MaterialTheme.colorScheme.tertiary
                                            append(AnnotatedString(text, SpanStyle(color = color)))
                                            append("  ")
                                            if (isRolling) append(stringResource(R.string.manage_rolling))
                                            else append(it.second.lspConfig.VERSION_CODE.toString())
                                        },
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Serif,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (canUpdateLoader) {
                                        with(LocalDensity.current) {
                                            val size = MaterialTheme.typography.bodySmall.fontSize * 1.2
                                            Icon(Icons.Filled.KeyboardCapslock, null, Modifier.size(size.toDp()))
                                        }
                                    }
                                }
                            }
                        )
                    }
                ) {
                    DropdownMenuItem(
                        text = { Text(text = it.first.label, color = MaterialTheme.colorScheme.primary) },
                        onClick = {}, enabled = false
                    )
                    val shizukuUnavailable = stringResource(R.string.shizuku_unavailable)
                    if (canUpdateLoader || BuildConfig.DEBUG) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.manage_update_loader)) },
                            onClick = {
                                expanded = false
                                scope.launch {
                                    if (!ShizukuApi.isPermissionGranted) {
                                        snackbarHost.showSnackbar(shizukuUnavailable)
                                    } else {
                                        viewModel.dispatch(AppManageViewModel.ViewAction.UpdateLoader(it.first, it.second))
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
                                    val initialSelected = LSPPackageManager.appList.mapNotNullTo(ArrayList()) {
                                        if (activated.contains(it.app.packageName)) it.app.packageName else null
                                    }
                                    navigator.navigate(SelectAppsScreenDestination(true, initialSelected))
                                }
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.manage_optimize)) },
                        onClick = {
                            expanded = false
                            scope.launch {
                                if (!ShizukuApi.isPermissionGranted) {
                                    snackbarHost.showSnackbar(shizukuUnavailable)
                                } else {
                                    viewModel.dispatch(AppManageViewModel.ViewAction.PerformOptimize(it.first))
                                }
                            }
                        }
                    )
                    val uninstallSuccessfully = stringResource(R.string.manage_uninstall_successfully)
                    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                        if (result.resultCode == Activity.RESULT_OK) {
                            scope.launch {
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

@Composable
fun AppManageFab(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val snackbarHost = LocalSnackbarHost.current
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
            Configs.storageDirectory = uri.toString()
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
                    text = stringResource(R.string.screen_new_patch),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                        onClick = {
                            navigator.navigate(NewPatchScreenDestination(id = ACTION_STORAGE))
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
                            navigator.navigate(NewPatchScreenDestination(id = ACTION_APPLIST))
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
            val uri = Configs.storageDirectory?.toUri()
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
                    Configs.storageDirectory = null
                    shouldSelectDirectory = true
                }
            }
        }
    )
}
