package org.lsposed.lspatch.ui.component

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import org.lsposed.lspatch.ui.theme.LSPTheme

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppItem(
    modifier: Modifier = Modifier,
    icon: Drawable,
    label: String,
    packageName: String,
    additionalInfo: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    checked: Boolean? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(20.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = rememberDrawablePainter(icon),
                contentDescription = label,
                modifier = Modifier.size(32.dp),
                tint = Color.Unspecified
            )
            Column(Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
                Text(text = packageName, style = MaterialTheme.typography.bodySmall)
                additionalInfo?.invoke()
            }
            if (checked != null) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(start = 20.dp)
                )
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
            icon = shape,
            label = "Sample App",
            packageName = "org.lsposed.sample",
            onClick = {}
        )
    }
}
