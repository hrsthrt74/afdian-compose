# 开发规则

代码风格（注释、AppLog）见 `AGENTS.md`；UI 写法约定见 `DESIGN.md`。

## Miuix 规则

- 根部只创建一个 `MiuixTheme`。
  - 唯一例外：「设置 → 外观」页的 `ThemeColorTransition` 会在本页范围内再套一层 `MiuixTheme(colors = ...)`
    提供过渡色板。这是为了让切配色时只重算可见的外观页（被它盖住的四个 Tab 页
    仍在组合中，全局动画会连它们一起每帧重组），不要照抄到其他页面。
- `NavigationBar` 是导航外观；页面路由仍由 `miuix-nav` 管理，两者不要混为一个机制。
- Overlay 组件需要位于 Miuix `Scaffold` 宿主下。
- 不要把 Material 3 的参数名、颜色 token、导航 API 直接套到 Miuix。

## 数据层规则

- 内容接口按领域拆在 `data/content/*Service`；新增接口放进对应领域 Service，不要加回 `AfadianContentService`（它只是兼容门面）。
- 所有请求走 `AfdianHttpClient`：按设置里的**优先线路**（ifdian.net 默认 / afdian.com / www.afdian.com）给出的三条有序链依次尝试；GET 遇域名不可用状态或网络异常换下一个，写请求只在「连接没建立」的异常时换下一个；响应统一用 `requireAfdianSuccess` 校验业务码。
- 不要直接构造 `OkHttpClient`，复用 `data/network/sharedAfdianHttpClient`。
- 解析逻辑优先做成顶层 `internal` 函数，便于单元测试（参考 `MessageParser`、`MessageParserTest`）。

## Android 运行规则

- 任何 OkHttp `execute()`、文件读写、加密、图片或大 JSON 处理都必须放在 `Dispatchers.IO`。
- 带 `Application` 构造参数的 `AndroidViewModel` 不要依赖默认 Compose ViewModel 工厂；使用 `ViewModelProvider.AndroidViewModelFactory`，参考 `ManagementScreen.kt`。
- 网络异常必须转成 StateFlow 中的错误状态，不能让协程异常直接杀进程。
- Compose 列表使用稳定 key；没有稳定 ID 时不要强行使用可能重复的字段。
- 新增页面先做可运行的加载/空/错误/重试状态，再补视觉细节。
