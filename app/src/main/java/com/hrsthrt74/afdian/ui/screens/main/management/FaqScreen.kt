package com.hrsthrt74.afdian.ui.screens.main

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hrsthrt74.afdian.data.content.FaqItem
import com.hrsthrt74.afdian.ui.components.basic.AppDialog
import com.hrsthrt74.afdian.ui.components.basic.BackButton
import com.hrsthrt74.afdian.ui.components.basic.BlurredBar
import com.hrsthrt74.afdian.ui.components.basic.CardActionBar
import com.hrsthrt74.afdian.ui.components.basic.CardActionBarItem
import com.hrsthrt74.afdian.ui.components.basic.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.basic.EmptyStateView
import com.hrsthrt74.afdian.ui.components.basic.ErrorStateView
import com.hrsthrt74.afdian.ui.components.basic.LoadingComponent
import com.hrsthrt74.afdian.ui.components.basic.blurBarColor
import com.hrsthrt74.afdian.ui.components.basic.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.basic.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.components.basic.rememberDialogState
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.viewmodel.FaqViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * FAQ 设置页（网页版「设置自动回复菜单」/setting/faq）。
 *
 * 创作者在这里维护若干条「问题 → 自动回复」：粉丝在创作者主页会话页
 * 点一下问题就能快捷提问，服务器随即自动发出对应的回复。
 *
 * @param onBack 返回
 * @param backStack 供返回按钮读取来源做过渡动画
 */
@Composable
fun FaqScreen(onBack: () -> Unit, backStack: NavBackStack) {
    val app = LocalContext.current.applicationContext as Application
    // AndroidViewModel 走 AndroidViewModelFactory（项目规则，参考 AffiliateScreen）
    val viewModel: FaqViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(app),
    )
    val faqs by viewModel.faqs.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val submitting by viewModel.isSubmitting.collectAsState()
    val deleting by viewModel.isDeleting.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    // 下拉刷新：本地状态与 ViewModel 的刷新态同步
    var pullRefreshing by remember { mutableStateOf(false) }
    // 编辑弹层显示状态；配合 editing 区分新增（null）与编辑（条目对象）
    var showSheet by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<FaqItem?>(null) }
    // 列表页删除确认：记住待删除条目，确认后交给 ViewModel
    var deletingTarget by remember { mutableStateOf<FaqItem?>(null) }
    val deleteDialogState = rememberDialogState()

    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    // 列表滚动状态：双击顶栏回顶 + 滚动条共用
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // 下拉刷新：触发刷新请求
    LaunchedEffect(pullRefreshing) {
        if (pullRefreshing) viewModel.refresh()
    }
    // 刷新完成后重置本地状态
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) pullRefreshing = false
    }

    Scaffold(
        topBar = {
            // 双击顶栏回顶包装层：内层照旧是模糊栏 + 标准顶栏
            DoubleTapToTop(listState, scrollBehavior) {
                // 顶栏实时模糊：与其他页面同款写法（BlurredBar + blurBarColor 半透明底色）
                BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
                    TopAppBar(
                        title = "FAQ 设置",
                        largeTitle = "FAQ 设置",
                        color = blurBarColor(backdrop),
                        scrollBehavior = scrollBehavior,
                        navigationIcon = { BackButton(onBack = onBack, backStack = backStack) },
                        actions = {
                            // 右上角「新增」：进入创建模式（editing 置空）
                            IconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                editing = null
                                showSheet = true
                            }) {
                                Icon(imageVector = MiuixIcons.Add, contentDescription = "新增自动回复")
                            }
                        },
                    )
                }
            }
        }
    ) { paddingValues ->
        PullToRefresh(
            isRefreshing = pullRefreshing,
            onRefresh = { pullRefreshing = true },
            pullToRefreshState = pullToRefreshState,
            refreshTexts = listOf("下拉刷新", "松开刷新", "正在刷新", "刷新成功"),
            contentPadding = PaddingValues(top = paddingValues.calculateTopPadding()),
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
        ) {
            Box {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        // 放在 nestedScroll 之后（最贴近列表），fling 到边界的剩余速度最先被它捕获
                        .scrollEndHaptic(),
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding(),
                        bottom = contentBottomPadding(),
                    ),
                ) {
                    // 说明卡片：告诉用户这些回复会出现在哪里、给谁看
                    item(key = "intro") {
                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "在这里设置自动回复菜单，粉丝在你的主页会话页可以看到这些问题，" +
                                        "点击后即可快捷提问并收到对应回复。",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }

                    when {
                        // 首次加载：列表为空且正在请求
                        loading && faqs.isEmpty() -> item(key = "loading") {
                            LoadingComponent(modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp))
                        }
                        // 加载失败且无缓存：空状态 + 重试
                        error != null && faqs.isEmpty() -> item(key = "error") {
                            ErrorStateView(
                                onRetry = viewModel::load,
                                modifier = Modifier.fillParentMaxSize(),
                                summary = error,
                            )
                        }
                        // 请求成功但一条都没有
                        faqs.isEmpty() -> item(key = "empty") {
                            EmptyStateView(
                                title = "还没有自动回复",
                                summary = "点右上角的加号，添加你的第一条自动回复吧～",
                                modifier = Modifier.fillParentMaxSize(),
                            )
                        }
                        else -> items(faqs, key = { it.id }) { faq ->
                            FaqCard(
                                faq = faq,
                                onEdit = {
                                    // 列表项数据已含 title / content，直接带入编辑弹层，无需二次请求
                                    editing = faq
                                    showSheet = true
                                },
                                onDelete = {
                                    // 删除确认在列表页统一处理：记住目标条目，弹二次确认
                                    deletingTarget = faq
                                    deleteDialogState.show()
                                },
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .padding(top = 12.dp),
                            )
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    // 新增 / 编辑弹层：成功后由内部关闭，列表由 ViewModel 自动刷新
    FaqEditSheet(
        show = showSheet,
        editFaq = editing,
        submitting = submitting,
        deleting = deleting,
        onDismiss = {
            showSheet = false
            // 关闭时清掉编辑态，避免下次新增带着旧数据
            editing = null
        },
        onSubmit = { id, title, content, onResult ->
            viewModel.saveFaq(id, title, content) { success, message ->
                // 结果必须原样回传给弹层：关闭弹层（并顺带清空 editing）是弹层 onResult 的职责，
                // 这里若自行清 editing 却不回调，弹层会留在「show=true 且 editFaq=null」的状态，
                // 既不收起又会触发表单初始化把内容清空。
                onResult(success, message)
            }
        },
        onDelete = {
            val target = editing
            if (target != null) {
                viewModel.deleteFaq(target.id) { success, message ->
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    if (success) {
                        showSheet = false
                        editing = null
                    }
                }
            }
        },
        onToast = { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() },
    )

    // 列表页直接删除的二次确认：破坏性操作，确认按钮变红
    AppDialog(
        state = deleteDialogState,
        title = "删除自动回复",
        summary = "确定删除「${deletingTarget?.title.orEmpty()}」吗？删除后不可恢复。",
        confirmText = "删除",
        destructive = true,
        onConfirm = {
            val target = deletingTarget ?: return@AppDialog
            viewModel.deleteFaq(target.id) { success, message ->
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                // 无论成败都清掉目标，避免下次弹窗残留旧条目
                if (success) deletingTarget = null
            }
        },
    )
}

/**
 * 单条自动回复卡片：问题标题 + 回复正文摘要 + 底部操作栏。
 *
 * 摘要限 2 行，长内容由编辑弹层里的多行输入框完整编辑；
 * 卡片整体不可点，编辑 / 删除统一走底部操作栏两个动作。
 * 创建与更新时间放在编辑弹层里展示，列表保持干净。
 *
 * @param onEdit 点击「编辑」（打开编辑弹层）
 * @param onDelete 点击「删除」
 */
@Composable
private fun FaqCard(
    faq: FaqItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column {
            // 内容区：问题标题 + 回复摘要（bottom 留 16dp，分割线改由操作栏组件内置后保住间隔）
            Column(modifier = Modifier.padding(16.dp)) {
                // 问题标题：粉丝看到的提问按钮文案，加粗突出
                Text(
                    text = faq.title.ifBlank { "未命名问题" },
                    style = MiuixTheme.textStyles.title4,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 回复正文摘要：限 2 行，完整内容去编辑弹层看
                if (faq.content.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = faq.content,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // 底部操作栏：编辑 / 删除两列均分（通用 CardActionBar，触觉、热区、分割线由组件内置）
            CardActionBar(
                items = listOf(
                    // ── 编辑：打开编辑弹层 ──
                    CardActionBarItem(
                        icon = MiuixIcons.Demibold.Edit,
                        label = "编辑",
                        onClick = onEdit,
                    ),
                    // ── 删除：破坏性动作，确认弹窗由上层统一处理 ──
                    CardActionBarItem(
                        icon = MiuixIcons.Demibold.Delete,
                        label = "删除",
                        onClick = onDelete,
                    ),
                ),
                showDivider = true,
            )
        }
    }
}
