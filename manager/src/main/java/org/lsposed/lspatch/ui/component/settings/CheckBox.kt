package org.lsposed.lspatch.ui.component.settings

import androidx.compose.foundation.clickable
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
    icon: ImageVector? = null,
    title: String,
    desc: String? = null,
    extraContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    SettingsSlot(modifier, enabled, icon, title, desc, extraContent) {
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

@Preview
@Composable
private fun SettingsCheckBoxPreview() {
    var checked1 by remember { mutableStateOf(false) }
    var checked2 by remember { mutableStateOf(false) }
    Column {
        SettingsCheckBox(
            modifier = Modifier.clickable { checked1 = !checked1 },
            checked = checked1,
            title = "Title",
            desc = "Description"
        )
        SettingsCheckBox(
            modifier = Modifier.clickable { checked2 = !checked2 },
            checked = checked2,
            icon = Icons.Outlined.Api,
            title = "Title",
            desc = "Description"
        )
    }
}
