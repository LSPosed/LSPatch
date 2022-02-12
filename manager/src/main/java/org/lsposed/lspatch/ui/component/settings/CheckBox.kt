package org.lsposed.lspatch.ui.component.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsCheckBox(
    modifier: Modifier = Modifier,
    checked: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    title: String,
    desc: String? = null,
    extraContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    SettingsSlot(modifier, enabled, onClick, icon, title, desc, extraContent) {
        Checkbox(checked = checked, onCheckedChange = { onClick() })
    }
}

@Preview
@Composable
private fun SettingsCheckBoxPreview() {
    var checked1 by remember { mutableStateOf(false) }
    var checked2 by remember { mutableStateOf(false) }
    Column {
        SettingsCheckBox(
            checked = checked1,
            onClick = { checked1 = !checked1 },
            title = "Title",
            desc = "Description"
        )
        SettingsCheckBox(
            checked = checked2,
            onClick = { checked2 = !checked2 },
            icon = Icons.Outlined.Api,
            title = "Title",
            desc = "Description"
        )
    }
}
