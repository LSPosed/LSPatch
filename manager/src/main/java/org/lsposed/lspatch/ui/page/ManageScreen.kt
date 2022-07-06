package org.lsposed.lspatch.ui.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.result.ResultRecipient
import kotlinx.coroutines.launch
import org.lsposed.lspatch.R
import org.lsposed.lspatch.ui.component.CenterTopBar
import org.lsposed.lspatch.ui.page.destinations.SelectAppsScreenDestination
import org.lsposed.lspatch.ui.page.manage.AppManageBody
import org.lsposed.lspatch.ui.page.manage.AppManageFab
import org.lsposed.lspatch.ui.page.manage.ModuleManageBody

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPagerApi::class)
@Destination
@Composable
fun ManageScreen(
    navigator: DestinationsNavigator,
    resultRecipient: ResultRecipient<SelectAppsScreenDestination, SelectAppsResult>
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState()
    Scaffold(
        topBar = { CenterTopBar(stringResource(BottomBarDestination.Manage.label)) },
        floatingActionButton = { if (pagerState.currentPage == 0) AppManageFab(navigator) }
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            Column {
                TabRow(
                    contentColor = MaterialTheme.colorScheme.secondary,
                    selectedTabIndex = pagerState.currentPage
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } }
                    ) {
                        Text(
                            modifier = Modifier.padding(vertical = 16.dp),
                            text = stringResource(R.string.apps)
                        )
                    }
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } }
                    ) {
                        Text(
                            modifier = Modifier.padding(vertical = 16.dp),
                            text = stringResource(R.string.modules)
                        )
                    }
                }

                HorizontalPager(count = 2, state = pagerState) { page ->
                    when (page) {
                        0 -> AppManageBody(navigator, resultRecipient)
                        1 -> ModuleManageBody()
                    }
                }
            }
        }
    }
}
