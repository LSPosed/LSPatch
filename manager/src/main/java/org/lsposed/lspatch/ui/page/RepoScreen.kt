package org.lsposed.lspatch.ui.page

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.ramcosta.composedestinations.annotation.Destination
import org.lsposed.lspatch.ui.component.CenterTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Destination
@Composable
fun RepoScreen() {
    Scaffold(
        topBar = { CenterTopBar(stringResource(BottomBarDestination.Repo.label)) }
    ) { innerPadding ->
        Text(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            text = "This page is not yet implemented",
            textAlign = TextAlign.Center
        )
    }
}
