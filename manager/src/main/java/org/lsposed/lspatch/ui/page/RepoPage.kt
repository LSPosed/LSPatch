package org.lsposed.lspatch.ui.page

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoPage() {
    Scaffold(topBar = { TopBar() }) { innerPadding ->
        Text(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            text = "This page is not yet implemented",
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TopBar() {
    SmallTopAppBar(
        title = { Text(PageList.Repo.title) }
    )
}
