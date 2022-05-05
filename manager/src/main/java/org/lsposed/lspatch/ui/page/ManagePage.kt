package org.lsposed.lspatch.ui.page

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.launch
import org.lsposed.lspatch.*
import org.lsposed.lspatch.Constants.PREFS_STORAGE_DIRECTORY
import org.lsposed.lspatch.R
import org.lsposed.lspatch.ui.util.LocalNavController
import org.lsposed.lspatch.ui.util.LocalSnackbarHost
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagePage() {
    Scaffold(
        topBar = { TopBar() },
        floatingActionButton = { Fab() }
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
private fun Fab() {
    val context = LocalContext.current
    val snackbarHost = LocalSnackbarHost.current
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
            lspApp.prefs.edit().putString(PREFS_STORAGE_DIRECTORY, uri.toString()).apply()
            Log.i(TAG, "Storage directory: ${uri.path}")
            navController.navigate(PageList.NewPatch.name)
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
                    navController.navigate(PageList.NewPatch.name)
                }.onFailure {
                    Log.w(TAG, "Failed to take persistable permission for saved uri", it)
                    lspApp.prefs.edit().putString(PREFS_STORAGE_DIRECTORY, null).apply()
                    shouldSelectDirectory = true
                }
            }
        }
    )
}
