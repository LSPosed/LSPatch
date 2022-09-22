package org.lsposed.lspatch.ui.component

import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import org.lsposed.lspatch.ui.theme.LSPTheme

@Composable
fun AppItem(
    modifier: Modifier = Modifier,
    icon: ImageBitmap,
    label: String,
    packageName: String,
    checked: Boolean? = null,
    rightIcon: (@Composable () -> Unit)? = null,
    additionalContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    if (checked != null && rightIcon != null)
        throw IllegalArgumentException("`checked` and `rightIcon` should not be both set")
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                bitmap = icon,
                contentDescription = label,
                tint = Color.Unspecified
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(label)
                Text(
                    text = packageName,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
                additionalContent?.invoke(this)
            }
            if (checked != null) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = null,
                    modifier = Modifier.padding(start = 20.dp)
                )
            }
            if (rightIcon != null) {
                rightIcon()
            }
        }
    }
}

@Preview
@Composable
private fun AppItemPreview() {
    LSPTheme {
        val shape = GradientDrawable()
        shape.shape = GradientDrawable.RECTANGLE
        shape.setColor(MaterialTheme.colorScheme.primary.toArgb())
        AppItem(
            icon = shape.toBitmap().asImageBitmap(),
            label = "Sample App",
            packageName = "org.lsposed.sample",
            rightIcon = { Icon(Icons.Filled.ArrowForwardIos, null) }
        )
    }
}
