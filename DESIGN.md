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
- 卡片底部操作栏 → `CardActionBar`（N 个动作均分一行，「图标 + 文字」居中，触觉先震后动；`onClick = null` 表示纯展示/禁用，视觉为次要色；激活态由调用方显式传 `primary`，`tint = null` 为次要色。带点赞/收藏业务状态时用 `PostActionBar`，不要手写均分列底栏）
- 环形占比图 → `DonutChart`（`ui/components/data/DonutChart.kt`，基于 Vico；圆心可放内容；颜色由调用方按业务含义固定给出，不要按扇区下标轮转取色；`minSweepDegrees` 保证小占比分类也看得见，默认 5 度）
- 分类/文档入口的彩色图标块 → `CategoryIconTile`（`ui/components/basic/`：32dp squircle 底 + 白色单色图标；Monet 开启时统一主题主色，判断收在组件里。设置总览分类卡与文档页文档卡共用，不要另写同款）
- 创作者卡（长条形） → `CreatorListItem`
- 圆形快捷入口 → `QuickEntry`（圆形底 + 图标 + 下方标题，触感反馈内聚；`containerSize`/`iconSize` 可调紧凑档，面板网格与页面入口共用同一个组件）
- 创作者卡（矩形） → `CreatorLargeCard` / `CreatorSmallCard`
- 双击顶栏回顶 → `DoubleTapToTop`（包住 `BlurredBar` + `TopAppBar`，双击栏身平滑回顶并展开大标题；输入框形态的栏传 `enabled = false`，固定小标题栏 `scrollBehavior` 传 `null`）
- 说明按钮（ⓘ） → `TooltipInfoButton`（点击与长按都弹 Miuix Tooltip，气泡 `focusable`，点外部/返回键关闭，再次点击收起；预设 `TooltipInfoButtonStyle.Tiny/Small/Default` 成套给图标尺寸 + 字重 + 热区，与 `CopyButtonStyle` 同构，`Tiny` 档用于表格等行高敏感的位置；不需要手写 `TooltipBox` + `state.show()` 那套样板）
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

### 品牌图标

关于页 hero 用 `drawable/icon.webp`（不透明位图，背景烘进图里，深色主题下是一块深色卡片），
外面套 squircle 裁角，观感与系统启动器图标一致：

```kotlin
Image(
    painter = painterResource(R.drawable.icon),
    contentDescription = null,
    modifier = Modifier
        .size(88.dp)
        .graphicsLayer { /* 滚动时的缩放 / 淡出 */ }
        .squircleClip(cornerRadius = 22.dp), // 取边长的 1/4
)
```

注意 `squircleClip` 要写在 `graphicsLayer` **之后**（更内层）：蒙版被那个缩放图层一起缩放，
图标缩小时圆角才对得上。

产品名与版本标记：hero 的应用名读 `app_display_name`，**不要用 `app_name`** —— debug 变体把后者
改成了「爱发电 Debug」给桌面图标用，关于页要的是干净的产品名。版本行是 `版本 x (code) S|L`，
字母由 `BuildConfig.LEGACY_EDITION` 选（S = standard / L = legacy），用 `AnnotatedString`
单独上主题色 + SemiBold，读起来是版本号的一部分，又不跟主标题抢注意力。

坑：`drawable/icon_splash.xml` 的填充是 aapt 的 `<gradient>`，Compose 自带的矢量解析器
（`painterResource` / `ImageVector.vectorResource`）**不认 `aapt:attr`**，解析出来是一个没有填充的
path —— 界面上就是一块空白，还很难看出哪里错了。非要用这个矢量，只能借平台 `VectorDrawable`
画到 canvas 上（`context.getDrawable(...)` + `draw(drawContext.canvas.nativeCanvas)`）。

### 动态背景（关于页海水）

「设置 → 关于」整页背后是一层 AGSL 动态海水（`ui/components/background/WaterBackground.kt`），
自己写的「平静水面」：上亮下深的水色 + 透视压缩的连续波纹 + 右侧阳光反射带里稀疏的碎金，
与 miuix demo 的渐变色斑 shader 无关。

写这类 shader 有三个已经踩过的坑（别重复）：

- **浪脊不能对 fbm 直接取阈值**——噪声高处会整片点亮，看起来像白云，不像水；
  要在「被浪高扰动的 y」上取正弦再做窄化，等值线才是连续流动的横带。
- **不能取值噪声的双线性版本**——它的等值线是折线，取窄带会画出「低多边形碎片」；
  必须用梯度（Perlin）噪声 + 五次曲线淡入淡出。
- **碎金不能对连续噪声取高次幂**——值噪声每个格点顶点都恰好到 1，取幂的结果是整片「下雪」；
  要按格子哈希撒点（只让千分之几的格子亮）。

其余约定：

- **只有「standard 包 + 用户开着模糊效果」才画**：
  `showWater = !BuildConfig.LEGACY_EDITION && themeSettings.enableBlur`。
  包身份用 `LEGACY_EDITION` 判断（不要复用 `SUPPORT_BLUR`，两者是刻意解耦的）；
  用户在外观设置里关掉「模糊效果」时也整层不画 —— 全屏逐像素 shader 是纯装饰性开销，
  关模糊的人通常在意流畅度/发热。两处任一不满足就连 shader 都不实例化，回落普通主题底色。
- 深浅色各一套调色板（深海 / 浅海），判断走 `isDarkColorScheme(MiuixTheme.colorScheme)`，
  不要读「用户选了哪个模式」。
- 页面画了动态背景时，这三处必须一起改，少一处就会被盖住：
  - `Scaffold(containerColor = Color.Transparent)`；
  - `Card(colors = CardDefaults.defaultColors(color = surfaceContainer.copy(alpha = 深 0.70 / 浅 0.62)))`
    —— **卡片不透明的话水面只在卡片缝隙里露几条边，背景等于白做**；
  - 顶栏 `SmallTopAppBar(color = Color.Transparent)` + 外层滚动渐显的 surface 蒙层。
- 顶栏底色/标题的时机照 miuix demo 的 About 页走 **hero 占位的滚动进度**，不要用
  `scrollBehavior.state.contentOffset` 拉满 —— 它是累计像素，按 48dp 算的话稍微划一下整条顶栏
  就变成不透明白板。现在底色在 70%→100%、标题在 35%→100% 之间渐变。
  另外 `SmallTopAppBar` 会把 `heightOffsetLimit` 钉成 0，`collapsedFraction` /
  `overlappedFraction` 在这种 pinned 小栏下恒为 0，本来也读不到进度。
  标题色要自己按进度给 alpha：miuix 内部的 `smallTitleAlpha` 弹簧在滚动第一个像素时就跳到 1。
- 帧驱动：时间存在 `mutableFloatStateOf`，**只在 `drawBehind` 里读** —— 每帧只失效 draw、不重组；
  按 `FrameIntervalNanos` 抽帧（当前 60fps）；系统开启「移除动画」时静止。`setFloatUniform` 本身不会触发重绘，重绘靠读这个 State。
- **水随滚动淡出**：`alpha = 1 - heroProgress`（抄 demo 的 `BgEffectBackground(alpha = ...)`）。
  滑过 hero 之后整页回到实色 `surface`，卡片落在纯色上更好读，也让水面只当首屏的氛围元素。
  淡出后露出的底色是单独一层 `Box.background(surface)`，不能只靠 Scaffold 透明——
  否则会透到窗口背景色，和卡片色对不上。
- 组件自带降级：API < 33 或 AGSL 编译失败 → 静态竖向渐变兜底 + `AppLog.e`，不会崩。
- 别的页面想复用：把 `WaterBackground(Modifier.matchParentSize())` 放在内容区最底层即可，
  再按上面三条把容器/卡片/顶栏调成透明或半透明。

### 更新日志

「设置 → 关于 → 更新日志」按版本折叠展示改动，版式是「版本号 + 日期 + 展开箭头」的卡片列表，
手风琴式（同时只展开一个），当前版本默认展开并在版本号旁标「当前」。

关于页的「更新」分组里还有一张**外显卡**：卡头「更新日志」是可点进完整页面的入口，
下面是当前版本的版本号 + 日期、一条分割线、以及**全部**条目（不折叠、不截断）。
分割线的左右各留 16dp 跟内容走而不通栏——和上面 preference 的内边距对齐；
条目末行不带尾部间距，卡片底边的留白由调用方给一次，否则条目间距会叠加成「底部比顶部空一大截」。
条目正文的 `lineHeight` 固定 22sp（比 body2 默认松一档）：一条一句功能，行距太挤会糊成整段。

- **数据源只有一个**：仓库根的 `CHANGELOG.md`。`app/build.gradle.kts` 里的 `copyChangelogAssets`
  在构建时把它复制进 assets，`data/content/Changelog.kt` 运行时解析。
  不要在 `app/src/main/assets` 里另放一份 —— 两份必然漂移，而这种「文档不同步」的错误
  要等用户截图指出更新日志少了最新一版才会被发现。
- AGP 9 **禁止** `sourceSets.assets.srcDir(provider)`（Studio 无法判断 Provider 指向的是生成目录
  还是静态文件），必须走 `variant.sources.assets.addGeneratedSourceDirectory(...)`；
  那条路径要求任务输出声明成 `DirectoryProperty`，而内置 `Copy` 只暴露 `File` 类型的
  `destinationDir`，所以自己写了 `CopyChangelogAssets` 这个最小任务。
- **不引 Markdown 引擎**：只解析 `## 版本 · 日期` 与 `- 条目` 两种语法，
  行内 `**` / `__` / 反引号 / `~~` / `[文字](url)` 在解析时剥掉。为了一个只读信息页装几十上百 KB、
  还要处理链接点击与代码块主题色不划算，而 CHANGELOG 的格式本来就是自己定的。
- 日期写在 CHANGELOG 的版本标题里（`## 1.0.2 · 2026-10-27`），不靠 git tag 反查：
  CI 的 `actions/checkout` 默认浅克隆没有 tag，反查在正式构建里会静默拿不到。
  **`·` 前面必须留空格**：`release.yml` 用 `^## <版本>($| )` 判定段落起点，
  写成 `## 1.0.2·2026-10-27` 会让发版脚本找不到该版本（脚本会显式报错退出，
  不会发出空 Release，但发版得先改回来）。
  markdown 里保持 ISO 格式（Git/GitHub 通行写法），**界面上显示成 `2026/10/27`**——
  转换在解析层做（`Changelog.formatDate`，顺带把月/日补零），不要两个展示位各转一次，
  否则哪天新增一个入口就会漏掉。
- 「未发布」小节只在非 stable 通道显示（`BuildConfig.BUILD_CHANNEL != "stable"`），
  正式包用户不会看到还没发出去的内容。

### 状态管理

- 业务状态放 ViewModel，临时 UI 状态用 `remember`。
- 加载中、空状态、错误、重试显式建模，不要用一个 Boolean 含糊表达。
