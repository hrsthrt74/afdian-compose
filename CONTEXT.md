# Afdian Android 迁移上下文

## 文档目的

本文件记录将 `afdian-swift` 迁移到当前 Android Compose 工程时的事实、决策、实现方式和注意事项。后续开发应先阅读本文件，再阅读 Swift 对应页面和 `miuix-skill`。

## 项目来源

- Android 工程：`C:\Users\hrsth\AndroidStudioProjects\afdian`
- Swift 参考工程：`C:\Users\hrsth\Documents\GitHub\afdian-swift`
- Miuix 技能和参考：`C:\Users\hrsth\Documents\GitHub\miuix-skill`
- Swift 版本是功能和 API 请求协议的主要参考，不是 Android UI 的逐行翻译源。

## 迁移方法

迁移不是简单的 Swift → Kotlin 语法转换，而是分四层进行：

1. **功能提取**：从 Swift 页面、ViewModel、Service 和模型中提取页面功能、入口层级、加载状态、分页规则、错误处理和请求参数。
2. **平台重构**：将 SwiftUI 的 `ObservableObject`、`@Published`、`@State`、`NavigationStack`、`sheet` 等概念分别映射为 Android ViewModel、StateFlow、Compose state、`miuix-nav` 和 Miuix/Compose Dialog。
3. **库/API 核验**：不能凭 Material 3 经验猜 Miuix API。必须按 `miuix-skill` 的 0.9.4-rc01 文档和源码确认组件签名、主题、路由和弹窗宿主。
4. **编译/真机闭环**：每个阶段都要运行 Gradle 构建；真机日志优先于推测。此前已经通过日志修复过主线程网络请求和 ViewModel 工厂问题。

## 当前 Android 基线

- Kotlin：`2.4.10`
- AGP：`9.3.1`
- compileSdk：`37`
- targetSdk：`36`
- minSdk：`34`
- Java source/target：`21`
- Miuix：`0.9.4-rc01`
- Miuix Android 依赖：
  - `miuix-ui-android`
  - `miuix-preference-android`
  - `miuix-icons-android`
  - `miuix-nav-android`
- 其他主要依赖：Kotlin Serialization、OkHttp、Coil、Lifecycle ViewModel Compose。
- 不要加入 GitHub Release 检查更新；这是用户明确排除的首版功能。

## 已实现功能

### 应用基础

- 单一 `MiuixTheme`，使用 `ThemeController(ColorSchemeMode.System)`。
- 根页面使用 Miuix `Scaffold`。
- 使用 Miuix `NavigationBar` 展示：首页、发现、动态、消息、我的。
- 使用 `miuix-nav` 的 `NavKey`、`rememberNavBackStack`、`NavDisplay` 和 `entry<T>`。
- 路由定义在 `navigation/Routes.kt`，当前有五个主页面路由。

### 认证

- 密码登录已经可以实际请求接口。
- 登录载荷已按 Swift 版本使用：
  - AES/CBC/PKCS5Padding 加密账号和密码
  - 固定 IV：`7brVHncu7wIDAQAB`
  - RSA/PKCS1 加密 AES 密钥
  - 字段：`account`、`password`、`mp_token`、`ar_ept`
- 网络请求必须在 `Dispatchers.IO` 执行。
- Token 使用 Android Keystore 的 AES-GCM 加密后保存。
- 登录失败会显示错误文本，登录中会禁用按钮。
- 已实现免责声明：首次同意后保存本地状态，不同意则关闭任务。

### 首页

- 已接入：账号信息、我发电的、全站精选。
- 已接入首页接口：
  - `/api/my/account`
  - `/api/my/sponsoring`
  - `/api/post/get-rec-list`
- 有加载中、空状态、错误重试和手动刷新。
- 「我发电的」使用 `CreatorSmallCard`（双列小卡样式）横向滑动列表，点击进入创作者详情。
- 空状态提示文本统一样式：`footnote1` + `onSurfaceVariantSummary` + 居中 + 16dp 内边距。

### 我的页

- 已有账号、我发电的、购物车、订单、管理、收藏、设置和退出登录入口。
- 管理入口已进入真实管理页面。
- 「我的页」内部有自己的子路由（`NavDisplay`），管理收藏等入口从 Profile 进入。

### 通用组件（ui/components/）

- `Avatar`：圆形头像，Coil 加载，占位背景色 `disabledSecondary`，支持可选点击。
- `LoadingComponent`：统一加载指示器。
- `PostCard`：动态卡片，发现页和创作者详情复用。
- `CreatorListItem`：长条形创作者卡片（Card 包裹 + 自定义 Row：左头像 48dp、中名称+副标题、右箭头）。
  - 不再使用 miuix 的 `ArrowPreference`（视觉不符合预期，改为自写布局）。
  - 点击涟漪放在内部 `Box` 上，不放 Card 上；onClick 为 null 时不可点且无涟漪。
  - 副标题两种传参：`doing = "xxx"` 自动拼成「正在创作 xxx」（默认场景）；`summary = "自定义文字"` 完全自定义且优先级更高。
  - 箭头样式参考 miuix ArrowPreference 源码：`MiuixIcons.Basic.ArrowRight`，10x16dp，`onSurfaceVariantActions` 着色。
- `CreatorLargeCard` / `CreatorSmallCard`：矩形创作者卡片（上封面图 + 下头像名字）。
  - 大卡单列：封面 16:9，头像 40dp；小卡双列/横滑：封面 3:2，头像 28dp，更紧凑边距。
  - 封面加载前/失败的占位背景色为 `disabledSecondary`。

### 发现页

- 已接入动态流（LazyColumn + PostCard），支持滚动加载。

### 收藏页（FavoritesScreen）

- TabRow 三个分区：创作者 / 动态 / 众筹。
- 创作者 tab 条目用 `CreatorListItem`，副标题回退链：subtitle → creator.doing + 分区名（组件自动加「正在创作」前缀）。
- 动态、众筹 tab 保持普通列表行。

### 我发电的页（SponsoringScreen）

- 列表项用 `CreatorListItem`，副标题为完全自定义的「我发电了 {planName}」。
- 「共 n 位创作者」统计文本在卡片列表下方，居中 + `onSurfaceVariantSummary`。

### 订单页（OrderScreen）

- 订单摘要 + TabRow 固定头部（见下方 UI 模式），列表展示订单行。

### 消息

- 消息列表页（含未读数 Badge）和会话详情页（聊天记录、FAQ、发送消息）。

### 创作者详情（CreatorDetailScreen）

- 头部封面 + 头像信息区、目标、发电计划、商品、动态（PostCard 复用）、合集、赞助榜。
- 可折叠长文本（CollapsibleText 模式 + animateContentSize）。

### 设置页（SettingsScreen）

- `SwitchPreference`（爱发电主题色开关）、`OverlayDropdownPreference`(启动默认页) 等。

### 管理页（ManagementScreen）

- TabRow 固定头部模式已应用（保留 `minWidth = 110.dp`）。
- 已实现五个分区：整体概况、收入统计、收到发电、赞助者管理、每月汇总。
- 通过 `ManagementService` 请求对应接口，并使用容错 JSON 摘要解析：
  - `/api/my/dashboard`
  - `/api/my/stat?page=1&type=day`
  - `/api/my/sponsored-bill-filter`
  - `/api/my/who-sponsored-me`
  - `/api/my/income`
- 当前显示的是概况和列表摘要，尚未做完整的订单行、图表、分页和详情页面。

## UI 模式与规范（重要）

### TabRow 固定头部模式（Favorites/Order/Management 均已应用）

不要用 `stickyHeader` 实现 Tab 吸顶。原因：miuix Scaffold 把内容全屏铺在 (0,0)、TopAppBar 叠在内容上层绘制，LazyColumn 视口顶端被不透明的 TopAppBar 盖住，sticky 吸附点不可见，看起来就像没生效。

正确做法是把 TabRow 移出 LazyColumn，结构化固定：

```kotlin
Column(Modifier.fillMaxSize()) {
    // 高度随 miuix 大标题折叠动画动态变化
    Spacer(Modifier.fillMaxWidth().height(paddingValues.calculateTopPadding()))
    Box(Modifier.fillMaxWidth().background(MiuixTheme.colorScheme.surface).padding(vertical = 8.dp, horizontal = 16.dp)) {
        TabRow(...)  // Management 页保留 minWidth = 110.dp
    }
    LazyColumn(Modifier.weight(1f).nestedScroll(scrollBehavior.nestedScrollConnection)) { ... }
}
```

附带效果：往下滚时 TabRow 会跟随顶栏折叠平滑上移到小标题下方。

### 其他约定

- `stickyHeader` 是 `LazyListScope` 成员函数，不能 import（新版 Compose 编译会报 Unresolved）。
- 空/占位提示文本统一样式：`style = footnote1`，`color = onSurfaceVariantSummary`，`fillMaxWidth()` + `padding(16.dp)` + `textAlign = TextAlign.Center`。
- 图片类组件加载前/失败占位背景色统一用 `MiuixTheme.colorScheme.disabledSecondary`。

## 重要代码位置

- [MainActivity.kt](app/src/main/java/com/hrsthrt74/afdian/MainActivity.kt)：应用入口、免责声明、认证分流。
- `ui/theme/Theme.kt`：Miuix 根主题。
- `ui/navigation/Routes.kt`：Miuix 路由（MainTabs 五个主页 + CreatorDetail、MessageConversation、Favorites、Sponsoring、Orders、Management、Settings）。
- `ui/screens/main/MainScreen.kt`：主导航（NavDisplay + HorizontalPager）。
- `ui/screens/main/HomeScreen.kt`：首页。
- `ui/screens/main/DiscoverScreen.kt`：发现页。
- `ui/screens/main/MessagesScreen.kt` / `MessageConversationScreen.kt`：消息列表和会话。
- `ui/screens/main/ProfileScreen.kt`：我的页（含子路由 NavDisplay）。
- `ui/screens/main/CreatorDetailScreen.kt`：创作者详情。
- `ui/screens/main/FavoritesScreen.kt`：收藏页（创作者/动态/众筹）。
- `ui/screens/main/SponsoringScreen.kt`：我发电的页。
- `ui/screens/main/OrderScreen.kt`：订单页。
- `ui/screens/main/SettingsScreen.kt`：设置页。
- `ui/components/Avatar.kt`：圆形头像。
- `ui/components/LoadingComponent.kt`：加载指示器。
- `ui/components/PostCard.kt`：动态卡片。
- `ui/components/CreatorListItem.kt`：长条形创作者卡片。
- `ui/components/CreatorCard.kt`：矩形创作者卡片（大卡/小卡）。
- `ui/viewmodel/*.kt`：各页面 ViewModel（Main、Favorites、Messages、Order、Management、CreatorDetail、Settings）。
- `data/auth/AuthSession.kt`：认证状态和 Token 存储。
- `data/auth/AuthService.kt`：登录请求和加密载荷。
- `data/content/AfadianContentService.kt`：内容接口和基础模型（含 FavoriteItem、AfadianUser 等）。
- `data/content/OrderService.kt`：订单接口。
- `data/content/ManagementService.kt`：管理接口。

## 迁移时必须遵守的规则

### Swift 参考规则

- Swift 页面用于确认用户可见功能和交互，不要照搬 SwiftUI 的布局实现。
- Swift Service 的接口路径、HTTP 方法、参数名、响应字段和分页规则应优先保留。
- Swift 模型字段经常使用 snake_case；Kotlin DTO 应使用 `@Serializable` 和原始字段名，或显式 `@SerialName`。
- 爱发电接口部分字段可能是数字或字符串，不能假设类型始终固定；必要时使用 `JsonElement` 或自定义容错解析。

### Miuix 规则

- 只能使用已按 0.9.4-rc01 核验过的 Miuix API。
- 根部只创建一个 `MiuixTheme`。
- `NavigationBar` 是导航外观；页面路由仍由 `miuix-nav` 管理，两者不要混为一个机制。
- Overlay 组件需要位于 Miuix `Scaffold` 宿主下。
- 不要把 Material 3 的参数名、颜色 token、导航 API 直接套到 Miuix。
- Miuix 0.9.4-rc01 当前实际依赖要求 compileSdk 37、Kotlin 2.4.10、Java 21；改版本时必须重新核对技能文档和 AAR 元数据。

### Android 运行规则

- 任何 OkHttp `execute()`、文件读写、加密、图片或大 JSON 处理都必须放在 `Dispatchers.IO`。
- 带 `Application` 构造参数的 `AndroidViewModel` 不要依赖默认 Compose ViewModel 工厂；使用 `ViewModelProvider.AndroidViewModelFactory`，参考 `ManagementScreen.kt`。
- 网络异常必须转成 StateFlow 中的错误状态，不能让协程异常直接杀进程。
- Compose 列表使用稳定 key；没有稳定 ID 时不要强行使用可能重复的字段。
- 新增页面先做可运行的加载/空/错误/重试状态，再补视觉细节。

## 尚未完成的主要部分

- 短信验证码登录、图形验证码刷新和验证码登录。
- 动态详情页、点赞、收藏、评论。
- 管理页完整的订单行、分页、图表和详情交互。
- 购物车、支付准备、取消订单、支付检查。
- 完整图片预览、正文 WebView 和媒体设置。
- 首页/发现页的视觉细节向 Swift 版本对齐。
- 单元测试、Compose UI 测试和完整真机回归。

## 推荐后续顺序

1. 把管理页的摘要列表改成真实模型和可分页列表。
2. 实现动态详情、点赞、收藏和评论（复用 PostCard 和现有用户模型）。
3. 补齐购物车与支付链路。
4. 最后补短信验证码登录和复杂媒体能力。

## 每次提交前的验证

```text
./gradlew.bat :app:assembleDebug
```

如果真机出现问题，先从日志中找第一个 `FATAL EXCEPTION` 或 `Caused by`。不要被 MIUI 的 `SettingTrigger`、`contentcatcher`、SELinux 或 Transition 连带警告带偏；除非堆栈明确指向应用代码，否则它们通常不是根因。

