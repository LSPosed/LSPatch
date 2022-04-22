package org.lsposed.lspatch.ui.page

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import org.lsposed.lspatch.Constants
import org.lsposed.lspatch.LSPApplication
import org.lsposed.lspatch.R
import org.lsposed.lspatch.TAG
import org.lsposed.lspatch.ui.util.LocalNavController
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagePage() {
    val snackbarHostState = remember { SnackbarHostState() }
    Scaffold(
        topBar = { TopBar() },
        floatingActionButton = { Fab(snackbarHostState) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
private fun Fab(snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    var shouldSelectDirectory by remember { mutableStateOf(false) }

    val errorText = stringResource(R.string.patch_select_dir_error)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        try {
            if (it.resultCode == Activity.RESULT_CANCELED) return@rememberLauncherForActivityResult
            val uri = it.data?.data ?: throw IOException("No data")
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            LSPApplication.prefs.edit().putString(Constants.PREFS_STORAGE_DIRECTORY, uri.toString()).apply()
            Log.i(TAG, "Storage directory: ${uri.path}")
            navController.navigate(PageList.NewPatch.name)
        } catch (e: Exception) {
            Log.e(TAG, "Error when requesting saving directory", e)
            scope.launch { snackbarHostState.showSnackbar(errorText) }
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
            title = { Text(stringResource(R.string.patch_select_dir_title)) },
            text = { Text(stringResource(R.string.patch_select_dir_text)) }
        )
    }

    FloatingActionButton(
        content = { Icon(Icons.Filled.Add, stringResource(R.string.add)) },
        onClick = {
            val uri = LSPApplication.prefs.getString(Constants.PREFS_STORAGE_DIRECTORY, null)?.toUri()
            if (uri == null) {
                shouldSelectDirectory = true
            } else {
                try {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                    navController.navigate(PageList.NewPatch.name)
                } catch (e: SecurityException) {
                    Log.e(TAG, "Failed to take persistable permission for saved uri", e)
                    LSPApplication.prefs.edit().putString(Constants.PREFS_STORAGE_DIRECTORY, null).apply()
                    shouldSelectDirectory = true
                }
            }
        }
    )
}
