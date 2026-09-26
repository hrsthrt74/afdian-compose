# 设计规范

本文件记录项目特有的 UI 约定和查阅指引。Miuix 组件用法、颜色语义、字体排版等通用规范请直接查阅 miuix-skill 。

## 配色

直接使用 miuix-skill 定义的配色即可。

## 本项目 UI 模式

### 卡片

使用 miuix 的 Card 来包裹元素时，请注意：Card 的职能只有：提供背景颜色、提供预设的圆角裁切。

对于 Card 的边距，请不要给 Card 加边距，而是让 Card 只做好自己的职能，padding 由内部的 Box / Column / … 负责。

#### 弹层内的 Card 必须显式指定颜色

弹层（Dialog / BottomSheet / Popup）的容器背景取的是 `colorScheme.background`，
而 Card 默认色是 `surfaceContainer`，两者在 Miuix 默认色板下**数值完全相同**
（浅色都是纯白 `#FFFFFF`、深色都是 `#242424`），所以默认 Card 放进弹层会与背景融为一体、直接看不见。

因此**弹层内部的 Card 一律用统一 util 指定颜色**，不要各写各的：

```kotlin
import com.hrsthrt74.afdian.ui.util.AfdianCardDefaults

Card(colors = AfdianCardDefaults.overlayColors()) { ... }
```

`AfdianCardDefaults.overlayColors()` = `secondaryContainer` 作底 + `onSurfaceContainerHighest` 作内容色
（浅 `#F0F0F0` / 深 `#434343`），明暗两种模式下都能与弹层背景分离。

注意：**只有弹层内部的 Card 需要它**。页面（Screen）内的 Card 背景本就与页面底不同，保持 Miuix 默认色即可，
不要全项目无脑替换。

### Dialog

所有 Dialog 统一用 `AppDialog` + `DialogState`，不再自写 `WindowDialog`。

```kotlin
val dialogState = rememberDialogState()
// 打开
dialogState.show()
// AppDialog 内部自动处理关闭，页面无需手动写 onDismissRequest
```

破坏性操作用 `destructive = true`，确认按钮变红。

### 头像

需要头像时使用 `Avatar` 组件。

### 占位元素

图片加载前/失败的占位背景色统一用 `MiuixTheme.colorScheme.disabledSecondary`。

### 空状态 / 错误态

整页/整区的空状态统一用 `EmptyStateView`（自带 Info 图标 + 从中心放大淡入动画），
加载失败统一用预设好的 `ErrorStateView`（标题「加载失败」+ 重试按钮已内置，不要再手写 action）：

```kotlin
// 错误态：标题「加载失败」、重试按钮均已内置
ErrorStateView(
    onRetry = viewModel::load,
    modifier = Modifier.fillParentMaxSize(), // LazyColumn item 内撑满视口；普通容器用 fillMaxSize
    summary = error,                          // 可选错误摘要，一般直接透传
)

// 空状态
EmptyStateView(
    title = "还没有收藏内容",
    summary = "...",                        // 可选引导语
    icon = MiuixIcons.Light.Recent,         // 可选图标，默认 Info
    modifier = Modifier.fillParentMaxSize(), // LazyColumn item 内撑满视口；普通容器用 fillMaxSize
    action = { Button(onClick = retry, modifier = Modifier.fillMaxWidth()) { Text("重试") } }, // 可选操作区
)
```

错误态图标：miuix-icons 没有 Warning / Alert / Error 语义的图标（只有 Help / Info / Refresh），
`ErrorStateView` 默认把 Info 旋转 180° 充作错误图标（`iconRotation = 180f`）；
换自定义图标且不想被旋转时，把 `iconRotation` 传 `0f`。

列表内的小占位（一行「暂无订单」之类的非整页空态）仍用统一样式：

```kotlin
Text(
    text = "...",
    style = MiuixTheme.textStyles.footnote1,
    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    textAlign = TextAlign.Center,
    modifier = Modifier.fillMaxWidth().padding(16.dp),
)
```

### 通用组件

需要以下能力时直接用封装好的组件，不要自己重写：

- 加载中 → `LoadingComponent`
- 空状态 → `EmptyStateView`（见「空状态 / 错误态」一节）
- 加载失败 / 错误态 → `ErrorStateView`（预设「加载失败」+ 重试按钮，见「空状态 / 错误态」一节）
- 复制按钮 → `CopyButton`（震动 + 写剪贴板 + 图标切 Ok + Toast，异步反馈全在内部；字重用 `iconWeight`，档位 `MiuixIconWeight.Light/Normal/Regular/Medium/Demibold`，默认 `Regular`）
- 动态卡片 → `PostCard`
- 环形占比图 → `DonutChart`（`ui/components/data/DonutChart.kt`，基于 Vico；圆心可放内容；颜色由调用方按业务含义固定给出，不要按扇区下标轮转取色；`minSweepDegrees` 保证小占比分类也看得见，默认 5 度）
- 创作者卡（长条形） → `CreatorListItem`
- 创作者卡（矩形） → `CreatorLargeCard` / `CreatorSmallCard`
- 双击顶栏回顶 → `DoubleTapToTop`（包住 `BlurredBar` + `TopAppBar`，双击栏身平滑回顶并展开大标题；输入框形态的栏传 `enabled = false`，固定小标题栏 `scrollBehavior` 传 `null`）
- and more in ui/components/

### 点击按压下沉反馈

对于 Card 包裹内容的场景，请只给 Card 添加此 modifier。

```kotlin
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable

Modifier.pressable(interactionSource = null, indication = SinkFeedback(), delay = null)
```

modifier.clickable 请只放在内部元素（`Box`/`Column`）上，`Card` 只做容器。

### Overscroll 触觉反馈

对于可滑动的 `LazyColumn` 等元素，请总是添加：

```kotlin
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
Modifier.scrollEndHaptic()
```

scrollEndHaptic 不需要传参。

### 滚动条

所有有纵向滚动内容的页面都应添加 miuix 的 `VerticalScrollBar`，提供滚动条。

#### 导入与 OptIn

```kotlin
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
```

函数（或内层 Composable）需加 `@OptIn(ExperimentalScrollBarApi::class)`。

#### Box 结构（LazyColumn 直接 fillMaxSize）

在 `LazyColumn` 所在的 `Box` 内，将滚动条叠放在右侧：

```kotlin
Box {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()...) { ... }
    VerticalScrollBar(
        adapter = rememberScrollBarAdapter(listState),
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight(),
        trackPadding = PaddingValues(
            top = paddingValues.calculateTopPadding(),
        ),
    )
}
```

#### Column 结构（TabRow 固定在上方，LazyColumn 用 weight(1f)）

把 `LazyColumn(weight(1f))` 包进 `Box(weight(1f).fillMaxSize())`，在 Box 内叠加滚动条：

```kotlin
Box(modifier = Modifier.weight(1f).fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().nestedScroll(...)....scrollEndHaptic(),
    ) { ... }
    VerticalScrollBar(
        adapter = rememberScrollBarAdapter(listState),
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
    )
}
```

#### 注意事项

- 没有 `listState` 的页面先补 `val listState = rememberLazyListState()`，并给 `LazyColumn` 传 `state = listState`。
- `VerticalScrollBar` 的 `trackPadding` 用于让出顶栏高度，值为 `paddingValues.calculateTopPadding()`。

### 模糊效果

- `rememberBlurBackdrop(enabled)` 返回 `LayerBackdrop?`，`null` 表示模糊关闭或设备不支持。
- `blurBarColor(backdrop)`：模糊开启时顶栏必须透明，否则遮住模糊层。
- 采样层 `layerBackdrop(backdrop)` 只包滚动内容，不包带 `textureBlur` 的栏，否则会原生层崩溃。
- 顶栏的渐变色蒙层用 `barTintBrush()`（`BlurredBar` 内部已接好），颜色与模糊斜坡解耦、上浓下淡；
  渐进模糊本身传 `blurBarColorsNoTint()`，不要再用 blend color 上色——miuix 会把它折进 ramp pass，
  颜色形状被模糊斜坡绑死且强端出现平台期，观感就是一块纯色板。
- 底栏若沿用渐进模糊（`PostDetailScreen` / `MessageConversationScreen` 手写的那两处），方向是
  下浓上淡，用 `barTintBrush(topHeavy = false)` 即可；它们目前仍走旧的 `progressiveBlurBarColors()`。
- 模糊不可用时（legacy 包体 / 用户关闭模糊 / 设备不支持 runtime shader），`BlurredBar` 会给整条栏
  铺一层 `surface` 底色兜底：`TopAppBar` 只会给自己上背景，往顶栏里塞它之外的内容（TabRow、表头等）
  不会自动获得底色，靠的就是这层。所以**别把顶栏内容拆到 `BlurredBar` 外面**。

### 状态管理

- 业务状态放 ViewModel，临时 UI 状态用 `remember`。
- 加载中、空状态、错误、重试显式建模，不要用一个 Boolean 含糊表达。
