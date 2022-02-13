package org.lsposed.lspatch.ui.page

import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.lsposed.lspatch.R

@Composable
fun HomePage() {

}

@Composable
fun HomeTopBar() {
    SmallTopAppBar(
        title = { Text(stringResource(R.string.app_name)) }
    )
}
