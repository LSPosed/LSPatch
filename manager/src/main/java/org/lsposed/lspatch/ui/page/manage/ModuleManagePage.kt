package org.lsposed.lspatch.ui.page.manage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import org.lsposed.lspatch.R
import org.lsposed.lspatch.manager.ManagerService
import org.lsposed.lspatch.ui.component.AppItem
import org.lsposed.lspatch.ui.viewmodel.manage.ModuleManageViewModel
import org.lsposed.lspatch.util.LSPPackageManager

@Composable
fun ModuleManageBody() {
    val viewModel = viewModel<ModuleManageViewModel>()
    if (viewModel.appList.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            Text(
                modifier = Modifier.align(Alignment.Center),
                text = run {
                    if (LSPPackageManager.appList.isEmpty()) stringResource(R.string.manage_loading)
                    else stringResource(R.string.manage_no_modules)
                },
                fontFamily = FontFamily.Serif,
                style = MaterialTheme.typography.headlineSmall
            )
        }
    } else {
        LazyColumn {
            items(
                items = viewModel.appList,
                key = { it.first.app.packageName }
            ) {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    AppItem(
                        icon = LSPPackageManager.getIcon(it.first),
                        label = it.first.label,
                        packageName = it.first.app.packageName,
                        onClick = { ManagerService.startAndSendServiceBinder(it.first.app.packageName) },
                        onLongClick = { expanded = true },
                        additionalContent = {
                            Text(
                                text = it.second.description,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = buildAnnotatedString {
                                    append(AnnotatedString("API", SpanStyle(color = MaterialTheme.colorScheme.secondary)))
                                    append("  ")
                                    append(it.second.api.toString())
                                },
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Serif,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    )
                }
            }
        }
    }
}
