package org.lsposed.lspatch.ui.util

import androidx.compose.foundation.lazy.LazyListState

val LazyListState.lastVisibleItemIndex
    get() = layoutInfo.visibleItemsInfo.lastOrNull()?.index

val LazyListState.lastItemIndex
    get() = layoutInfo.totalItemsCount.let { if (it == 0) null else it }

val LazyListState.isScrolledToEnd
    get() = lastVisibleItemIndex == lastItemIndex
