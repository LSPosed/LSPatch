package org.lsposed.lspatch.ui.page

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
import org.lsposed.lspatch.R
import org.lsposed.lspatch.share.LSPConfig
import org.lsposed.lspatch.ui.component.CenterTopBar
import org.lsposed.lspatch.ui.page.destinations.ManageScreenDestination
import org.lsposed.lspatch.ui.page.destinations.NewPatchScreenDestination
import org.lsposed.lspatch.ui.util.HtmlText
import org.lsposed.lspatch.ui.util.LocalSnackbarHost
import org.lsposed.lspatch.util.ShizukuApi
import rikka.shizuku.Shizuku

@OptIn(ExperimentalMaterial3Api::class)
@RootNavGraph(start = true)
@Destination
@Composable
fun HomeScreen(navigator: DestinationsNavigator) {
    // Install from intent
    var isIntentLaunched by rememberSaveable { mutableStateOf(false) }
    val activity = LocalContext.current as Activity
    val intent = activity.intent
    LaunchedEffect(Unit) {
        if (!isIntentLaunched && intent.action == Intent.ACTION_VIEW && intent.hasCategory(Intent.CATEGORY_DEFAULT) && intent.type == "application/vnd.android.package-archive") {
            isIntentLaunched = true
            val uri = intent.data
            if (uri != null) {
                navigator.navigate(ManageScreenDestination)
                navigator.navigate(
                    NewPatchScreenDestination(
                        id = ACTION_INTENT_INSTALL,
                        data = uri
                    )
                )
            }
        }
    }

    Scaffold(
        topBar = { CenterTopBar(stringResource(R.string.app_name)) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ShizukuCard()
            InfoCard()
            SupportCard()
            Spacer(Modifier)
        }
    }
}

private val listener: (Int, Int) -> Unit = { _, grantResult ->
    ShizukuApi.isPermissionGranted = grantResult == PackageManager.PERMISSION_GRANTED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShizukuCard() {
    LaunchedEffect(Unit) {
        Shizuku.addRequestPermissionResultListener(listener)
    }
    DisposableEffect(Unit) {
        onDispose {
            Shizuku.removeRequestPermissionResultListener(listener)
        }
    }

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = run {
            if (ShizukuApi.isPermissionGranted) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.errorContainer
        })
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (ShizukuApi.isBinderAvailable && !ShizukuApi.isPermissionGranted) {
                        Shizuku.requestPermission(114514)
                    }
                }
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (ShizukuApi.isPermissionGranted) {
                Icon(Icons.Outlined.CheckCircle, stringResource(R.string.shizuku_available))
                Column(Modifier.padding(start = 20.dp)) {
                    Text(
                        text = stringResource(R.string.shizuku_available),
                        fontFamily = FontFamily.Serif,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "API " + Shizuku.getVersion(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Icon(Icons.Outlined.Warning, stringResource(R.string.shizuku_unavailable))
                Column(Modifier.padding(start = 20.dp)) {
                    Text(
                        text = stringResource(R.string.shizuku_unavailable),
                        fontFamily = FontFamily.Serif,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.home_shizuku_warning),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

private val apiVersion = if (Build.VERSION.PREVIEW_SDK_INT != 0) {
    "${Build.VERSION.CODENAME} Preview (API ${Build.VERSION.PREVIEW_SDK_INT})"
} else {
    "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
}

private val device = buildString {
    append(Build.MANUFACTURER[0].uppercaseChar().toString() + Build.MANUFACTURER.substring(1))
    if (Build.BRAND != Build.MANUFACTURER) {
        append(" " + Build.BRAND[0].uppercaseChar() + Build.BRAND.substring(1))
    }
    append(" " + Build.MODEL + " ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoCard() {
    val context = LocalContext.current
    val snackbarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    ElevatedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 16.dp)
        ) {
            val contents = StringBuilder()
            val infoCardContent: @Composable (Pair<String, String>) -> Unit = { texts ->
                contents.appendLine(texts.first).appendLine(texts.second).appendLine()
                Text(text = texts.first, style = MaterialTheme.typography.bodyLarge)
                Text(text = texts.second, style = MaterialTheme.typography.bodyMedium)
            }

            infoCardContent(stringResource(R.string.home_api_version) to "${LSPConfig.instance.API_CODE}")

            Spacer(Modifier.height(24.dp))
            infoCardContent(stringResource(R.string.home_lspatch_version) to LSPConfig.instance.VERSION_NAME + " (${LSPConfig.instance.VERSION_CODE})")

            Spacer(Modifier.height(24.dp))
            infoCardContent(stringResource(R.string.home_framework_version) to LSPConfig.instance.CORE_VERSION_NAME + " (${LSPConfig.instance.CORE_VERSION_CODE})")

            Spacer(Modifier.height(24.dp))
            infoCardContent(stringResource(R.string.home_system_version) to apiVersion)

            Spacer(Modifier.height(24.dp))
            infoCardContent(stringResource(R.string.home_device) to device)

            Spacer(Modifier.height(24.dp))
            infoCardContent(stringResource(R.string.home_system_abi) to Build.SUPPORTED_ABIS[0])

            val copiedMessage = stringResource(R.string.home_info_copied)
            TextButton(
                modifier = Modifier.align(Alignment.End),
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("LSPatch", contents.toString()))
                    scope.launch { snackbarHost.showSnackbar(copiedMessage) }
                },
                content = { Text(stringResource(android.R.string.copy)) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun SupportCard() {
    ElevatedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.home_support),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                modifier = Modifier.padding(vertical = 8.dp),
                text = stringResource(R.string.home_description),
                style = MaterialTheme.typography.bodyMedium
            )
            HtmlText(
                stringResource(
                    R.string.home_view_source_code,
                    "<b><a href=\"https://github.com/LSPosed/LSPatch\">GitHub</a></b>",
                    "<b><a href=\"https://t.me/LSPosed\">Telegram</a></b>"
                )
            )
        }
    }
}
