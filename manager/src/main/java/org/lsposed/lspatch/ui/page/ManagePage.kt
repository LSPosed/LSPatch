package org.lsposed.lspatch.ui.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import kotlinx.coroutines.launch
import org.lsposed.lspatch.R
import org.lsposed.lspatch.ui.page.manage.AppManageBody
import org.lsposed.lspatch.ui.page.manage.AppManageFab
import org.lsposed.lspatch.ui.page.manage.ModuleManageBody

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPagerApi::class)
@Composable
fun ManagePage() {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState()
    Scaffold(
        topBar = { TopBar() },
        floatingActionButton = { if (pagerState.currentPage == 0) AppManageFab() }
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            Column {
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]))
                    }
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } }
                    ) {
                        Text(
                            modifier = Modifier.padding(vertical = 12.dp),
                            text = stringResource(R.string.apps)
                        )
                    }
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } }
                    ) {
                        Text(
                            modifier = Modifier.padding(vertical = 12.dp),
                            text = stringResource(R.string.modules)
                        )
                    }
                }

                HorizontalPager(count = 2, state = pagerState) { page ->
                    when (page) {
                        0 -> AppManageBody()
                        1 -> ModuleManageBody()
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar() {
    SmallTopAppBar(
        title = { Text(PageList.Manage.title) }
    )
}
