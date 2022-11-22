package org.lsposed.lspatch.ui.page

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramcosta.composedestinations.annotation.Destination
import org.lsposed.lspatch.ui.component.CenterTopBar
import org.lsposed.lspatch.ui.util.ManagerLogging

@OptIn(ExperimentalMaterial3Api::class)
@Destination
@Composable
fun LogsScreen() {
    Scaffold(
        topBar = { CenterTopBar(stringResource(BottomBarDestination.Logs.label)) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val scrollState = rememberLazyListState()
            LazyColumn(
                state = scrollState,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 2.dp)
            ) {
                items(ManagerLogging.logs) { log ->
                    Text(
                        text = log.str,
                        style = TextStyle(
                            fontSize = 14.sp, // TODO: make configurable
                            lineHeight = 15.sp,
                            textAlign = TextAlign.Start,
                            color = when (log.level) {
                                Log.ERROR -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onBackground
                            },
                        ),
                        modifier = Modifier
                            .fillMaxWidth(),
                        softWrap = true,
                        overflow = TextOverflow.Visible
                    )
                }
            }
        }
    }
}