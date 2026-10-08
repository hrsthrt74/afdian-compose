package com.hrsthrt74.afdian.ui.screens.main

import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.ui.components.basic.Avatar
import com.hrsthrt74.afdian.ui.components.basic.BackButton
import com.hrsthrt74.afdian.ui.components.basic.BlurredBar
import com.hrsthrt74.afdian.ui.components.basic.CreatorListItem
import com.hrsthrt74.afdian.ui.components.basic.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.basic.ErrorStateView
import com.hrsthrt74.afdian.ui.components.basic.LoadMoreEffect
import com.hrsthrt74.afdian.ui.components.basic.blurBarColor
import com.hrsthrt74.afdian.ui.components.basic.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.basic.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.navigation.rememberCreatorOrigin
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.viewmodel.ThankSponsorsViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.time.YearMonth

/**
 * 赞助者感谢页
 *
 * 布局参考爱发电网页版感谢页（/a/<slug>/thank）：
 * 顶部比心插画 + 居中创作者信息 + 感谢语卡片 + 「感谢 x月 xx名赞助者」标题 + 赞助者列表。
 *
 * 数据全部来自真实接口：
 * - 创作者信息（头像/昵称/正在创作）来自当前登录账号的资料（/api/my/profile）
 * - 赞助者列表来自创作者后台 get-thank-sponsors（按年月分页，默认上个月）
 * - 感谢语来自 get-thank-word，编辑后经 edit-thank-word 保存
 *
 * @param viewModel 页面数据（赞助者列表 / 加载状态 / 分页 / 感谢语）
 * @param creatorName 创作者昵称（当前登录账号）
 * @param creatorAvatar 创作者头像 URL（当前登录账号）
 * @param creatorDoing 创作者的「正在创作」内容
 * @param onBack 返回
 * @param onShare 分享（顶栏右侧，暂未实现）
 * @param onOpenCreator 点击赞助者跳转其主页
 */
@Composable
fun ThankSponsorsScreen(
    viewModel: ThankSponsorsViewModel,
    creatorName: String,
    creatorAvatar: String?,
    creatorDoing: String?,
    onBack: () -> Unit = {},
    backStack: NavBackStack,
    onShare: () -> Unit = {},
    onOpenCreator: (AfadianUser) -> Unit = {},
) {
    val sponsors by viewModel.sponsors.collectAsState()
    val totalLabel by viewModel.totalLabel.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val loadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val error by viewModel.error.collectAsState()
    // 感谢语：服务端值 + 保存中状态
    val thankWord by viewModel.thankWord.collectAsState()
    val savingWord by viewModel.isSavingWord.collectAsState()

    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)
    // 列表滚动状态：供 LoadMoreEffect 判断是否滚到了底部附近
    val listState = rememberLazyListState()

    // 滚动到底部附近自动加载下一页（与发现页/收藏页同款逻辑）
    LoadMoreEffect(listState, hasMore, loadingMore) { viewModel.loadMore() }

    // ===== 年月选择弹层的状态 =====
    var showPicker by remember { mutableStateOf(false) }
    // 选择器里正在拨动的年月，初始为当前查看的年月
    var pickerYear by remember { mutableIntStateOf(viewModel.year) }
    var pickerMonth by remember { mutableIntStateOf(viewModel.month) }
    val now = remember { YearMonth.now() }
    // 月份可选范围：今年则本月不可选（数据还不完整），其他年份整年可选
    val monthRange = if (pickerYear == now.year) 1..(now.monthValue - 1) else 1..12
    // 切到今年时，把月份钳回可选范围（比如原来停在 12 月）
    LaunchedEffect(pickerYear) {
        if (pickerMonth !in monthRange) pickerMonth = monthRange.last
    }

    Scaffold(
        topBar = {
            // 双击顶栏回顶包装层：内层照旧是模糊栏 + 小标题栏（固定栏无折叠态，scrollBehavior 传 null）
            DoubleTapToTop(listState, scrollBehavior = null) {
            // 顶栏实时模糊：与其他页面同款写法（BlurredBar + blurBarColor 半透明底色）
            BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
                SmallTopAppBar(
                    title = "",
                    color = blurBarColor(backdrop),
                    navigationIcon = {
                        BackButton(onBack = onBack, backStack = backStack)
                    },
                    actions = {
                        // 手写「图标 + 弹层」绑定：WindowListPopup 锚定到父容器（这个 Box），
                        // 所以图标和弹层必须包在同一个 Box 里
//                        Box {
//                            IconButton(onClick = { showPicker = true }) {
//                                Icon(imageVector = MiuixIcons.More, contentDescription = "选择年月")
//                            }
//                            WindowListPopup(
//                                show = showPicker,
//                                alignment = PopupPositionProvider.Align.End,
//                                onDismissRequest = { showPicker = false },
//                            ) {
//                                ListPopupColumn {
//                                    Column(
//                                        modifier = Modifier.padding(16.dp)
//                                    ) {
//                                        SmallTitle("月份跳转", insideMargin = PaddingValues(start = 12.dp, bottom = 8.dp))
//                                        // 左边年份（2017~今年）、右边月份（今年则本月不可选）
//                                        Card(
//                                            // 弹层（Popup）内 Card 统一配色：与弹层背景拉开层次
//                                            colors = AfdianCardDefaults.overlayColors(),
//                                        ) {
//                                            // 横排：年选择器 + 「年」 + 月选择器 + 「月」，文字与滚轮垂直居中；
//                                            // picker 用固定宽度，避免选中值位数不同导致宽度抖动
//                                            Row(
//                                                verticalAlignment = Alignment.CenterVertically,
//                                                modifier = Modifier.padding(horizontal = 12.dp)
//                                                ) {
//                                                NumberPicker(
//                                                    textStyle = MiuixTheme.textStyles.title2,
//                                                    value = pickerYear,
//                                                    onValueChange = { pickerYear = it },
//                                                    range = 2018..now.year,
//                                                    label = { it.toString() },
//                                                    modifier = Modifier.width(88.dp)
//                                                )
//                                                Text(
//                                                    "年",
//                                                    style = MiuixTheme.textStyles.title2,
//                                                    modifier = Modifier.padding(start = 8.dp)
//                                                )
//                                                Spacer(Modifier.width(16.dp))
//                                                NumberPicker(
//                                                    textStyle = MiuixTheme.textStyles.title2,
//                                                    value = pickerMonth,
//                                                    onValueChange = { pickerMonth = it },
//                                                    range = monthRange,
//                                                    label = { it.toString() },
//                                                    modifier = Modifier.width(56.dp)
//                                                )
//                                                Text(
//                                                    "月",
//                                                    style = MiuixTheme.textStyles.title2,
//                                                    modifier = Modifier.padding(start = 8.dp)
//                                                )
//                                            }
//                                        }
//                                        // 跳转按钮：关掉弹层并加载所选年月
//                                        val dismiss = LocalDismissState.current
//                                        Button(
//                                            onClick = {
//                                                dismiss?.invoke()
//                                                viewModel.jumpTo(pickerYear, pickerMonth)
//                                            },
//                                            modifier = Modifier
//                                                .fillMaxWidth()
//                                                .padding(top = 12.dp)
//                                        ) {
//                                            Text("跳转")
//                                        }
//                                    }
//                                }
//                            }
//                        }
                    },
                )
            }
            }
        }
    ) { paddingValues ->
        // 点击输入框以外的空白区域时清除焦点、收起键盘；
        // 子组件（按钮/卡片等可点击项）会消费点击事件，不会触发这里的 onTap
        val focusManager = LocalFocusManager.current
        Box(modifier = Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    // 放在最贴近列表的位置，fling 到边界的剩余速度最先被它捕获
                    .scrollEndHaptic()
                    // 空白区域点击 → 清除输入框焦点
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { focusManager.clearFocus() })
                    },
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = contentBottomPadding()
                )
            ) {
                // 顶部比心插画：暂用大号 emoji 占位，之后有图再换
                item(key = "illustration") {
                    Text(
                        text = "🫰",
                        fontSize = 96.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        textAlign = TextAlign.Center
                    )
                }

                // 创作者信息：头像 / 昵称 / 正在创作（当前登录账号）
                item(key = "creator") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Avatar(
                            model = creatorAvatar,
                            size = 64.dp,
                            contentDescription = creatorName
                        )
                        Text(
                            text = creatorName,
                            style = MiuixTheme.textStyles.title2,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                        if (!creatorDoing.isNullOrBlank()) {
                            Text(
                                text = "正在创作 $creatorDoing",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }

                // 感谢语：可直接编辑，内容有改动时出现保存按钮
                item(key = "message") {
                    // 以服务端值为准初始化本地编辑内容；服务端值变化（保存成功/加载完成）时重置
                    var editingWord by remember(thankWord) { mutableStateOf(thankWord) }
                    TextField(
                        value = editingWord,
                        onValueChange = { editingWord = it },
                        label = "感谢语",
                        useLabelAsPlaceholder = true,
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 24.dp)
                    )
                    // 只有内容相对服务端有改动时才显示保存按钮
                    if (editingWord != thankWord) {
                        Button(
                            onClick = {
                                // 保存时顺手清除输入框焦点、收起键盘
                                focusManager.clearFocus()
                                viewModel.saveThankWord(editingWord)
                            },
                            enabled = !savingWord,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(top = 12.dp)
                        ) {
                            Text(if (savingWord) "保存中…" else "保存感谢语")
                        }
                    }
                    Text(
                        text = "点击上方可自定义感谢语",
                        style = MiuixTheme.textStyles.footnote2 ,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )
                }

                item { HorizontalDivider(Modifier.padding(64.dp, 36.dp)) }

                // 标题：感谢 {month}月 {totalLabel} 赞助者，人数标签用主题紫高亮
                item(key = "title") {
                    Text(
                        text = buildAnnotatedString {
                            append("感谢 ${viewModel.month}月 ")
                            withStyle(SpanStyle(color = MiuixTheme.colorScheme.primary)) {
                                append(totalLabel)
                            }
                            append(" 赞助者")
                        },
                        style = MiuixTheme.textStyles.title2.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }

                item { Spacer(Modifier.height(8.dp)) }

                // 加载中 / 出错 / 空列表的占位（列表区）
                if (loading && sponsors.isEmpty()) {
                    item(key = "loading") {
                        Text(
                            "加载中…",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        )
                    }
                } else if (error != null && sponsors.isEmpty()) {
                    // 加载失败：空状态视图 + 重试按钮
                    item(key = "error") {
                        ErrorStateView(
                            onRetry = viewModel::load,
                            modifier = Modifier.fillParentMaxSize(),
                            summary = error!!,
                        )
                    }
                } else if (sponsors.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "这个月还没有可感谢的赞助者",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        )
                    }
                } else {
                    // 赞助者列表：复用长条形创作者卡片，点击跳其主页
                    items(sponsors, key = { it.id }) { sponsor ->
                        // 捕获卡片边界，作为「主页展开」过渡动画的起点
                        val origin = rememberCreatorOrigin()
                        CreatorListItem(
                            avatarUrl = sponsor.avatar,
                            creatorName = sponsor.name.ifBlank { "爱发电用户" },
                            doing = sponsor.doing,
                            onClick = origin.onClick { onOpenCreator(sponsor.toAfadianUser()) },
                            modifier = origin.trackModifier()
                                .padding(horizontal = 16.dp)
                                .padding(vertical = 4.dp)
                        )
                    }

                    // 加载更多提示
                    if (loadingMore) {
                        item(key = "loading_more") {
                            Text(
                                "加载更多…",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}
