package org.lsposed.lspatch.ui.page

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import org.lsposed.lspatch.Patcher
import org.lsposed.lspatch.R
import org.lsposed.lspatch.TAG
import org.lsposed.lspatch.ui.component.SelectionColumn
import org.lsposed.lspatch.ui.component.ShimmerAnimation
import org.lsposed.lspatch.ui.component.settings.SettingsCheckBox
import org.lsposed.lspatch.ui.component.settings.SettingsItem
import org.lsposed.lspatch.ui.util.LocalNavController
import org.lsposed.lspatch.ui.util.isScrolledToEnd
import org.lsposed.lspatch.ui.util.lastItemIndex
import org.lsposed.lspatch.ui.util.observeState
import org.lsposed.lspatch.ui.viewmodel.AppInfo
import org.lsposed.lspatch.ui.viewmodel.NewPatchViewModel
import org.lsposed.patch.util.Logger
import java.io.File

private enum class PatchState {
    SELECTING, CONFIGURING, SUBMITTING, PATCHING, FINISHED, ERROR
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NewPatchPage(entry: NavBackStackEntry) {
    val navController = LocalNavController.current
    val patchApp by entry.observeState<AppInfo>("appInfo")
    val isCancelled by entry.observeState<Boolean>("isCancelled")
    var patchState by rememberSaveable { mutableStateOf(PatchState.SELECTING) }
    var patchOptions by rememberSaveable { mutableStateOf<Patcher.Options?>(null) }
    if (patchState == PatchState.SELECTING) {
        when {
            isCancelled == true -> {
                LaunchedEffect(entry) { navController.popBackStack() }
                return
            }
            patchApp != null -> patchState = PatchState.CONFIGURING
        }
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        val filePermissionState = rememberPermissionState(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (filePermissionState.status is PermissionStatus.Denied) {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {
                    TextButton(onClick = { filePermissionState.launchPermissionRequest() }) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { navController.popBackStack() }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
                title = { Text(stringResource(R.string.patch_permission_title)) },
                text = { Text(stringResource(R.string.patch_permission_text)) }
            )
            return
        }
    }

    Log.d(TAG, "NewPatchPage: $patchState")
    if (patchState == PatchState.SELECTING) {
        LaunchedEffect(entry) {
            navController.navigate(PageList.SelectApps.name + "/false")
        }
    } else {
        Scaffold(
            topBar = { TopBar(patchApp!!) },
            floatingActionButton = {
                if (patchState == PatchState.CONFIGURING) {
                    ConfiguringFab { patchState = PatchState.SUBMITTING }
                }
            }
        ) { innerPadding ->
            if (patchState == PatchState.CONFIGURING || patchState == PatchState.SUBMITTING) {
                PatchOptionsBody(
                    modifier = Modifier.padding(innerPadding),
                    patchState = patchState,
                    patchApp = patchApp!!,
                    onSubmit = {
                        patchOptions = it
                        patchState = PatchState.PATCHING
                    }
                )
            } else {
                DoPatchBody(
                    modifier = Modifier.padding(innerPadding),
                    patchState = patchState,
                    patchOptions = patchOptions!!,
                    onFinish = { patchState = PatchState.FINISHED },
                    onFail = { patchState = PatchState.ERROR }
                )
            }
        }
    }
}

@Composable
private fun TopBar(patchApp: AppInfo) {
    SmallTopAppBar(
        title = {
            Column {
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
            }
        }
    )
}

@Composable
private fun ConfiguringFab(onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        text = { Text(stringResource(R.string.patch_start)) },
        icon = { Icon(Icons.Outlined.AutoFixHigh, null) },
        onClick = onClick
    )
}

@Composable
private fun sigBypassLvStr(level: Int) = when (level) {
    0 -> stringResource(R.string.patch_sigbypasslv0)
    1 -> stringResource(R.string.patch_sigbypasslv1)
    2 -> stringResource(R.string.patch_sigbypasslv2)
    else -> throw IllegalArgumentException("Invalid sigBypassLv: $level")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PatchOptionsBody(
    modifier: Modifier,
    patchState: PatchState,
    patchApp: AppInfo,
    onSubmit: (Patcher.Options) -> Unit
) {
    val navController = LocalNavController.current
    val viewModel = viewModel<NewPatchViewModel>()
    val embeddedModules = navController.currentBackStackEntry!!
        .savedStateHandle.getLiveData<SnapshotStateList<AppInfo>>("selected", SnapshotStateList())

    if (patchState == PatchState.SUBMITTING) LaunchedEffect(patchApp) {
        if (viewModel.useManager) embeddedModules.value?.clear()
        val options = Patcher.Options(
            apkPaths = arrayOf(patchApp.app.sourceDir), // TODO: Split Apk
            debuggable = viewModel.debuggable,
            sigbypassLevel = viewModel.sigBypassLevel,
            v1 = viewModel.sign[0], v2 = viewModel.sign[1], v3 = viewModel.sign[2],
            useManager = viewModel.useManager,
            overrideVersionCode = viewModel.overrideVersionCode,
            verbose = true,
            embeddedModules = embeddedModules.value?.map { it.app.sourceDir } ?: emptyList() // TODO: Split Apk
        )
        onSubmit(options)
    }

    Column(modifier.verticalScroll(rememberScrollState())) {
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
                title = stringResource(R.string.patch_portable),
                desc = stringResource(R.string.patch_portable_desc),
                extraContent = {
                    TextButton(
                        onClick = { navController.navigate(PageList.SelectApps.name + "/true") }
                    ) {
                        Text(text = stringResource(R.string.patch_embed_modules), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            )
        }
        SettingsCheckBox(
            modifier = Modifier.padding(top = 6.dp),
            checked = viewModel.debuggable,
            onClick = { viewModel.debuggable = !viewModel.debuggable },
            icon = Icons.Outlined.BugReport,
            title = stringResource(R.string.patch_debuggable)
        )
        SettingsCheckBox(
            checked = viewModel.overrideVersionCode,
            onClick = { viewModel.overrideVersionCode = !viewModel.overrideVersionCode },
            icon = Icons.Outlined.Layers,
            title = stringResource(R.string.patch_override_version_code),
            desc = stringResource(R.string.patch_override_version_code_desc)
        )
        Box {
            var expanded by remember { mutableStateOf(false) }
            SettingsItem(
                onClick = { expanded = true },
                icon = Icons.Outlined.Edit,
                title = stringResource(R.string.patch_sign),
                desc = viewModel.sign.mapIndexedNotNull { index, on -> if (on) "V" + (index + 1) else null }.joinToString(" + ").ifEmpty { "None" }
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                repeat(3) { index ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = viewModel.sign[index], onCheckedChange = { viewModel.sign[index] = !viewModel.sign[index] })
                                Text("V" + (index + 1))
                            }
                        },
                        onClick = { viewModel.sign[index] = !viewModel.sign[index] }
                    )
                }
            }
        }
        Box {
            var expanded by remember { mutableStateOf(false) }
            SettingsItem(
                onClick = { expanded = true },
                icon = Icons.Outlined.RemoveModerator,
                title = stringResource(R.string.patch_sigbypass),
                desc = sigBypassLvStr(viewModel.sigBypassLevel)
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DoPatchBody(
    modifier: Modifier,
    patchState: PatchState,
    patchOptions: Patcher.Options,
    onFinish: () -> Unit,
    onFail: () -> Unit
) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val logs = remember { mutableStateListOf<Pair<Int, String>>() }
    val logger = remember {
        object : Logger() {
            override fun d(msg: String) {
                if (verbose) {
                    Log.d(TAG, msg)
                    logs += Log.DEBUG to msg
                }
            }

            override fun i(msg: String) {
                Log.i(TAG, msg)
                logs += Log.INFO to msg
            }

            override fun e(msg: String) {
                Log.e(TAG, msg)
                logs += Log.ERROR to msg
            }
        }
    }

    LaunchedEffect(patchOptions) {
        try {
            Patcher.patch(context, logger, patchOptions)
            onFinish()
        } catch (t: Throwable) {
            logger.e(t.message.orEmpty())
            logger.e(t.stackTraceToString())
            onFail()
        } finally {
            File(patchOptions.outputPath).deleteRecursively()
        }
    }

    BoxWithConstraints(modifier.padding(24.dp)) {
        val shellBoxMaxHeight =
            if (patchState == PatchState.PATCHING) maxHeight
            else maxHeight - ButtonDefaults.MinHeight - 12.dp
        Column(
            Modifier
                .fillMaxSize()
                .wrapContentHeight()
                .animateContentSize(spring(stiffness = Spring.StiffnessLow))
        ) {
            ShimmerAnimation(enabled = patchState == PatchState.PATCHING) {
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                ) {
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
                        items(logs) {
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

            if (patchState == PatchState.FINISHED) {
                Row(Modifier.padding(top = 12.dp)) {
                    Button(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.weight(1f),
                        content = { Text(stringResource(R.string.patch_return)) }
                    )
                    Spacer(Modifier.weight(0.2f))
                    Button(
                        onClick = { /* TODO: Install */ },
                        modifier = Modifier.weight(1f),
                        content = { Text(stringResource(R.string.patch_install)) }
                    )
                }
            } else if (patchState == PatchState.ERROR) {
                Row(Modifier.padding(top = 12.dp)) {
                    Button(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.weight(1f),
                        content = { Text(stringResource(R.string.patch_return)) }
                    )
                    Spacer(Modifier.weight(0.2f))
                    Button(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("LSPatch", logs.joinToString { it.second + "\n" }))
                        },
                        modifier = Modifier.weight(1f),
                        content = { Text(stringResource(R.string.patch_copy_error)) }
                    )
                }
            }
        }
    }
}
