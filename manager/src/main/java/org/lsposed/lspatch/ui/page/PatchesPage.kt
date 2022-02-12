package org.lsposed.lspatch.ui.page

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.lsposed.lspatch.R
import org.lsposed.lspatch.ui.util.LocalNavController

@Composable
fun PatchesTopBar() {
    SmallTopAppBar(
        title = { Text(PageList.Patches.title) }
    )
}

@Composable
fun PatchesFab() {
    val navController = LocalNavController.current
    FloatingActionButton(onClick = { navController.navigate(PageList.NewPatch.name) }) {
        Icon(Icons.Filled.Add, stringResource(R.string.patches_add))
    }
}

@Composable
fun PatchesPage() {

}
