package org.lsposed.lspatch.ui.component

import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import org.lsposed.lspatch.ui.util.SampleStringProvider

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun CenterTopBar(@PreviewParameter(SampleStringProvider::class, 1) text: String) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.titleMedium
            )
        }
    )
}
