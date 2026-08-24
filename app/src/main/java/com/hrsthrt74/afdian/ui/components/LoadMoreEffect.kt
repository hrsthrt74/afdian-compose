package com.hrsthrt74.afdian.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

/**
 * 滚动加载更多：当列表滚动到倒数第 [threshold] 个元素时触发 [onLoadMore]。
 * 仅在有更多数据（[hasMore]）且当前未在加载中（[isLoadingMore]==false）时触发。
 *
 * @param listState 列表的滚动状态
 * @param hasMore 是否还有更多数据（由接口的 has_more 决定）
 * @param isLoadingMore 当前是否正在加载下一页，避免重复触发
 */
@Composable
fun LoadMoreEffect(
    listState: LazyListState,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    threshold: Int = 3,
    onLoadMore: () -> Unit,
) {
    // derivedStateOf：只有「是否该加载更多」这个布尔值变化时才触发重组/副作用
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = layoutInfo.totalItemsCount
            hasMore && !isLoadingMore && total > 0 && lastVisible >= total - threshold
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }
}
