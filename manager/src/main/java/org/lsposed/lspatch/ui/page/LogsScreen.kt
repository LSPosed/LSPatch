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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramcosta.composedestinations.annotation.Destination
import org.lsposed.lspatch.ui.component.CenterTopBar

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
                items(ManagerLogs.logs) {
                    Text(
                        text = it.second,
                        style = TextStyle(
                            fontSize = 12.sp, // TODO: make configurable
                            lineHeight = 14.5.sp,
                            textAlign = TextAlign.Start,
                            color = when (it.first) {
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

class ManagerLogs {
    companion object {
        @JvmStatic
        val logs = mutableStateListOf<Pair<Int, String>>()

        @JvmStatic
        fun d(
            tag: String,
            msg: String
        ) {
            logs += Log.DEBUG to "$tag: $msg"
            Log.d(tag, msg)
            preventOverflow()
        }

        @JvmStatic
        fun i(
            tag: String,
            msg: String
        ) {
            logs += Log.INFO to "$tag: $msg"
            Log.i(tag, msg)
            preventOverflow()
        }

        @JvmStatic
        fun w(
            tag: String,
            msg: String
        ) {
            logs += Log.WARN to "$tag: $msg"
            Log.e(tag, msg)
            preventOverflow()
        }

        @JvmStatic
        fun e(
            tag: String,
            msg: String
        ) {
            logs += Log.ERROR to "$tag: $msg"
            Log.e(tag, msg)
            preventOverflow()
        }

        private fun preventOverflow() {
            if (logs.size > 300 /* limit to 300 logs*/ /* TODO: make configurable */) {
                logs.removeAt(0) // prevent memory leak
            }
        }
    }
}