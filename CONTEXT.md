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
- 目前仍是基础可用 UI，不是 Swift 版本的视觉还原。

### 我的页

- 已有账号、我发电的、购物车、订单、管理、收藏、设置和退出登录入口。
- 管理入口已进入真实管理页面。

### 管理页

- 已实现五个分区：整体概况、收入统计、收到发电、赞助者管理、每月汇总。
- 通过 `ManagementService` 请求对应接口，并使用容错 JSON 摘要解析：
  - `/api/my/dashboard`
  - `/api/my/stat?page=1&type=day`
  - `/api/my/sponsored-bill-filter`
  - `/api/my/who-sponsored-me`
  - `/api/my/income`
- 当前显示的是概况和列表摘要，尚未做完整的订单行、图表、分页和详情页面。

## 重要代码位置

- [MainActivity.kt](app/src/main/java/com/hrsthrt74/afdian/MainActivity.kt)：应用入口、免责声明、认证分流。
- `ui/theme/Theme.kt`：Miuix 根主题。
- `navigation/Routes.kt`：Miuix 路由。
- `data/auth/AuthSession.kt`：认证状态和 Token 存储。
- `data/auth/AuthService.kt`：登录请求和加密载荷。
- `data/content/AfadianContentService.kt`：首页接口和基础模型。
- `data/content/ManagementService.kt`：管理接口。
- `feature/main/MainScreen.kt`：主导航、首页和我的页。
- `feature/main/MainViewModel.kt`：首页状态。
- `feature/main/ManagementScreen.kt`：管理页面。
- `feature/main/ManagementViewModel.kt`：管理页面状态。

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
- 发现页、动态页、消息页。
- 创作者详情、动态详情、合集详情、搜索详情。
- 首页动态点击进入详情、点赞、收藏、评论。
- 管理页完整的订单行、分页、图表和详情交互。
- 订单、购物车、支付准备、取消订单、支付检查。
- 收藏创作者、收藏动态、收藏众筹的真实页面。
- 设置页及各页面偏好。
- 完整图片预览、正文 WebView 和媒体设置。
- 单元测试、Compose UI 测试和完整真机回归。

## 推荐后续顺序

1. 先把管理页的摘要列表改成真实模型和可分页列表。
2. 实现设置页，至少完成退出登录、显示开关和关于页面。
3. 实现发现页和创作者详情，因为它们能复用首页的用户/动态模型。
4. 实现动态详情、点赞、收藏和评论。
5. 实现消息列表、会话和发消息。
6. 最后补订单、支付、收藏和复杂媒体能力。

## 每次提交前的验证

```text
./gradlew.bat :app:assembleDebug
```

如果真机出现问题，先从日志中找第一个 `FATAL EXCEPTION` 或 `Caused by`。不要被 MIUI 的 `SettingTrigger`、`contentcatcher`、SELinux 或 Transition 连带警告带偏；除非堆栈明确指向应用代码，否则它们通常不是根因。

