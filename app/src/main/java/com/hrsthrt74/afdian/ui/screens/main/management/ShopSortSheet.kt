package com.hrsthrt74.afdian.ui.screens.main.management

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.hrsthrt74.afdian.data.content.ShopProduct
import java.util.Locale
import com.hrsthrt74.afdian.ui.components.basic.SaveButton
import com.hrsthrt74.afdian.ui.util.AfdianCardDefaults
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/**
 * 商品排序底部弹层：长按 ☰ 把手拖动调整全量商品的顺序，点右上角 ✓ 提交。
 *
 * 拖拽交互、拖拽态缩放、震动反馈都对齐首页的 `HomeCustomizationSheet`：
 * 拖动过程只改弹层内的**草稿顺序**，点确定才把全量 plan_ids 提交给服务端
 * （接口按全量顺序保存，所以底稿始终取「全部」列表而不是当前筛选 tab）。
 *
 * 两个容易踩的点：
 * - LazyColumn 里**只放可拖拽条目**：`onMove` 给的 from/to 是列表条目下标，
 *   中间夹了提示文案就会错位（拖到提示上还可能越界崩溃），所以提示留在列表外。
 * - 草稿顺序只在「打开」时重建（`LaunchedEffect(show)`）：不用 `remember(items, show)`，
 *   否则关掉（show→false）也会重建，而内容在退场动画期间仍会渲染，会看到顺序闪回原样。
 *
 * @param show 是否显示
 * @param items 全量商品（按当前顺序），作为草稿的初始值
 * @param saving 提交中：按钮转圈、禁止关闭
 * @param onDismiss 关闭弹层
 * @param onSubmit 提交草稿顺序（弹层内的新顺序）
 */
@Composable
fun ShopSortSheet(
    show: Boolean,
    items: List<ShopProduct>,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (List<ShopProduct>) -> Unit,
) {
    // 弹层内的草稿顺序：拖动只改它，提交才外发
    var editableOrder by remember { mutableStateOf(items) }
    // 每次「打开」时按最新数据重建草稿：拖了没保存就关掉的话，下次打开不能还留着上次拖过的顺序。
    // 这里用 LaunchedEffect 而不是 remember(items, show) —— 后者在 show→false（关闭）时也会重建，
    // 而弹层内容在退场动画期间仍会渲染，会看到顺序「闪回」成原样。
    // 只以 show 为 key：弹层开着时后台刷新换了 items 也不会把用户拖到一半的顺序冲掉。
    LaunchedEffect(show) {
        if (show) editableOrder = items
    }
    val haptic = LocalHapticFeedback.current

    WindowBottomSheet(
        show = show,
        title = "编辑排序",
        onDismissRequest = onDismiss,
        // 提交中不允许关闭（含点遮罩、返回键）：避免请求在飞、弹层先没了
        allowDismiss = !saving,
        // Sheet 自身已有左右边距，这里只补 12dp 内衬，与首页自定义弹层一致
        insideMargin = DpSize(12.dp, 0.dp),
        startAction = {
            // 左上角：关闭（提交中置灰）
            IconButton(onClick = onDismiss, enabled = !saving) {
                Icon(imageVector = MiuixIcons.Close, contentDescription = "关闭")
            }
        },
        endAction = {
            // 右上角：确定，提交中自动换成进度圈
            SaveButton(saving = saving, onClick = { onSubmit(editableOrder) })
        },
    ) {
        val listState = rememberLazyListState()
        val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
            // 只改草稿顺序；震动档位与首页拖拽一致
            editableOrder = editableOrder.toMutableList().apply { add(to.index, removeAt(from.index)) }
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().scrollEndHaptic(),
                contentPadding = PaddingValues(bottom = 48.dp),
            ) {
                itemsIndexed(editableOrder, key = { _, product -> product.planId }) { index, product ->
                    ReorderableItem(state = reorderableState, key = product.planId) { isDragging ->
                        // 拖拽中整行轻微放大，突出「被抓起」的状态
                        val dragScale by animateFloatAsState(
                            targetValue = if (isDragging) 1.05f else 1f,
                            animationSpec = spring(),
                            label = "shop_sort_drag_scale",
                        )
                        // 外层预留不被缩放的水平空隙，避免放大后顶到弹层边界被裁剪
                        Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                            // 内层才做缩放，溢出量被外层空隙吸收
                            Box(modifier = Modifier.graphicsLayer { scaleX = dragScale; scaleY = dragScale }) {
                                ShopSortRow(
                                    index = index,
                                    product = product,
                                    dragModifier = with(this@ReorderableItem) {
                                        Modifier.longPressDraggableHandle(
                                            // 长按进入拖拽时震动，给用户明确的抓取反馈
                                            onDragStarted = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 排序行：☰ 拖拽把手 + 序号 + 商品名与价格。
 * 只有把手能拖起（整行可拖会和弹层自身的下拉关闭抢手势），因此把手要足够好点。
 */
@Composable
private fun ShopSortRow(
    index: Int,
    product: ShopProduct,
    dragModifier: Modifier,
) {
    Card(
        colors = AfdianCardDefaults.overlayColors(),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(    // 拖拽把手
                verticalAlignment = Alignment.CenterVertically,
                modifier = dragModifier.padding(vertical = 8.dp).padding(end = 12.dp)
            ) {
                Text(
                    text = "☰",
                    fontSize = 20.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    modifier = Modifier.padding(end = 8.dp),
                )
                // 序号
                // 补零成两位（01、02…10），否则个位数只有一半宽，右侧名称会左右抖动
                Text(
                    text = String.format(Locale.ROOT, "%02d", index + 1),
                    style = MiuixTheme.textStyles.body2.copy(fontFeatureSettings = "tnum"),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            // 商品封面
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MiuixTheme.colorScheme.disabledSecondary),
            ) {
                product.pic?.let { pic ->
                    AsyncImage(
                        model = pic,
                        contentDescription = "商品封面",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            // 商品名 + 价格
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MiuixTheme.textStyles.body1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "¥ ${product.price}",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
