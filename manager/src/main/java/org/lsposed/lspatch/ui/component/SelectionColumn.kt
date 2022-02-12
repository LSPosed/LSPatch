package org.lsposed.lspatch.ui.component

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

object SelectionColumnScope {

    @Composable
    fun SelectionItem(
        modifier: Modifier = Modifier,
        selected: Boolean,
        onClick: () -> Unit,
        icon: ImageVector,
        title: String,
        desc: String? = null,
        extraContent: (@Composable ColumnScope.() -> Unit)? = null
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.inverseOnSurface
                    ).value
                )
                .clickable { onClick() }
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                if (desc != null || extraContent != null) {
                    AnimatedVisibility(
                        visible = selected,
                        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
                    ) {
                        Column {
                            if (desc != null) {
                                Text(
                                    text = desc,
                                    modifier = Modifier.padding(top = 8.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            extraContent?.invoke(this)
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun SelectionColumn(
    modifier: Modifier = Modifier,
    content: @Composable() (SelectionColumnScope.() -> Unit)
) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(32.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = { SelectionColumnScope.content() }
    )
}
