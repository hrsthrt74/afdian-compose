package com.hrsthrt74.afdian.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

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
    // rememberUpdatedState：derivedStateOf 的闭包只会在首次组合时创建，
    // 直接捕获参数会拿到旧值（比如首屏 hasMore=false），之后永远不触发；
    // 包一层 State 保证闭包里每次读取的都是最新值
    val currentHasMore by rememberUpdatedState(hasMore)
    val currentIsLoadingMore by rememberUpdatedState(isLoadingMore)
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    // derivedStateOf：只有「是否该加载更多」这个布尔值变化时才触发重组/副作用
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = layoutInfo.totalItemsCount
            currentHasMore && !currentIsLoadingMore && total > 0 && lastVisible >= total - threshold
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) currentOnLoadMore()
    }
}
