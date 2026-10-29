# 架构

## 目录结构

```
app/src/main/java/com/hrsthrt74/afdian/
├── data/
│   ├── ai/            # AI 集成（BYOK：供应商预设、配置归一化、OpenAI 兼容流式客户端、提示词构建）
│   ├── auth/          # 认证（Token 存储、登录加密）
│   ├── content/       # 内容接口（账号、消息、动态、创作者、收藏、优惠码、订单、管理、感谢页）
│   ├── history/       # 浏览历史（本地存储）
│   ├── mcp/           # MCP 本地服务器（Streamable HTTP 协议核心、工具集、生命周期控制）
│   ├── network/       # 统一 HTTP 入口（域名回退、业务码校验、共享 OkHttpClient）
│   ├── settings/      # DataStore 设置（首页模块、管理布局、主题、AI 配置等）
│   ├── stats/         # 数据卡片模型、图表模型、提现日程
│   └── storage/       # 存储空间扫描
├── ui/
│   ├── components/    # 通用 UI 组件
│   │   ├── ai/        # AI 结果底栏（AiResultSheet：聊天气泡 + 流式 Markdown + 停止/重试/复制/采用）
│   │   ├── data/      # 数据卡片组件（网格、图表、管理布局）
│   │   └── home/      # 首页模块组件（创作者数据、收藏、历史等）
│   ├── navigation/    # 路由定义、过渡动画
│   ├── screens/
│   │   ├── auth/      # 登录、免责声明
│   │   └── main/      # 所有主页面
│   ├── theme/         # Miuix 主题
│   ├── util/          # 工具类
│   └── viewmodel/     # 各页面 ViewModel
└── AppLog.kt          # 全局日志门面
```

## 数据层结构（data/content 按领域拆分）

`AfadianContentService` 已从两千行大文件拆成兼容门面（纯转发，保留历史方法签名），ViewModel 无需迁移。新代码可直接依赖对应领域 Service：

| 领域 | 文件 | 说明 |
|---|---|---|
| 兼容门面 | `AfadianContentService.kt` | 只做转发；新接口不要加到这里 |
| 账号 | `AccountService.kt` | 账号信息、个人资料、我支持的创作者、轻量未读检查 |
| 消息 | `MessageService.kt` + `MessageParser.kt` | 请求编排与 JSON 解析分离 |
| 优惠码 | `AffiliateService.kt` | 优惠码增删改查、可用方案 |
| 动态/收藏/互动 | `FeedService.kt` | 推荐动态、收藏动态、点赞、收藏创作者 |
| 创作者 | `CreatorService.kt` | 创作者详情聚合（复用 FeedService 的收藏查询） |
| 收到发电 | `ReceivedSponsorService.kt` | 筛选、游标分页、订单字段解析 |
| 感谢页 | `ThankSponsorsService.kt` | 感谢者分页、感谢语读写 |
| 赞助者管理 | `ManagementService.kt` | who-sponsored-me 排序+多条件筛选分页列表（buildSponsorFilterArray 可单测）、all-plans 筛选选项 |
| 订单/管理 | `OrderService.kt` | 早期拆分出的独立服务 |

数据层约定：

- 新增接口放进对应领域 Service；跨领域聚合新开 Service 组合，不要塞回门面。
- 所有请求走 `AfdianHttpClient.execute()`：按设置里的**优先线路**（ifdian.net 默认 / afdian.com / www.afdian.com）给出的三条有序链依次尝试，GET 遇域名不可用状态或网络异常换下一个，写请求只在「连接没建立」的异常时换下一个。响应统一用 `requireAfdianSuccess` 校验业务 `ec`。
- 不要直接构造 `OkHttpClient`，复用 `data/network/sharedAfdianHttpClient` 或由服务注入。
  AI 请求用 `data/ai/sharedAiHttpClient`（同一连接池派生，只放宽流式读超时），
  因为 AI 端点不是爱发电域名，不需要走域名链重试。
- 可单测的解析逻辑优先做成顶层 `internal` 函数（参考 `MessageParser.kt`），模型放 `ContentModels.kt` / `MessageModels.kt`。

## 路由结构

底部导航 4 个 Tab：首页、发现、消息、我的。

二级路由（覆盖全屏，由外层 NavBackStack 管理）：
- `CreatorDetail` / `PostDetail` / `PostDetailById` / `MessageConversation`
- `Favorites` / `Sponsoring` / `Orders` / `Management` / `IncomeDetail` / `MonthlySummary` / `ReceivedSponsors` / `SponsorManage` / `ThankSponsors` / `Settings` / `SettingsGeneral` / `SettingsAi` / `SettingsAppearance` / `SettingsAbout` / `History` / `OssLicenses` / `Storage`

带参路由：`ThankSponsors(year, month)` —— 年月由「每月汇总」页按行带入；miuix-nav 的 ViewModelStore 按 entry 的 contentKey（默认取路由对象）隔离，不同年月互不串数据。

## 导航模式

- 底部导航栏使用 Miuix `NavigationBar` + `HorizontalPager`（四个 Tab 常驻，beyondViewportPageCount = 3）
- 页面路由使用 `miuix-nav` 的 `NavDisplay` + `NavBackStack`
- 「我的」页的子路由（收藏/我发电的/订单/管理/设置）提升到外层栈，push 时盖住底部导航栏
- 页面过渡使用 `CreatorDetailTransition`（卡片展开动画），由设置开关控制

## TabRow 固定头部模式（Favorites/Order 已应用；Management 原有 TabRow 已随占位分区移除）

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

注意：`stickyHeader` 是 `LazyListScope` 成员函数，不能 import（新版 Compose 编译会报 Unresolved）。

## 关键文件

- `ui/theme/Theme.kt`：Miuix 根主题。
- `ui/navigation/Routes.kt`：Miuix 路由定义。
- `ui/navigation/CreatorDetailTransition.kt`：卡片展开过渡动画。
- `ui/screens/main/MainScreen.kt`：主导航（NavDisplay + HorizontalPager + 底部导航栏）。
- `data/ai/AiChatClient.kt`：OpenAI 兼容流式客户端（SSE 手写解析、取消断流）。
- `ui/viewmodel/AiGenerationEngine.kt`：AI 生成的可测内核——状态机 + 流收集 + 100ms 节流 +
  思考计时 + 落盘时机，依赖以 lambda 注入，**不含任何 Composable / Android 依赖**。
- `ui/viewmodel/AiGenerationViewModel.kt`：AI 生成的 ViewModel——类型（AiPhase /
  AiGenerationUiState / AiGenerationRequest）+ 事件壳，把真实数据层依赖接给引擎。
- `ui/components/ai/AiSheet.kt`：AI 底栏的 UI 层——共享骨架 `AiSheetScaffold` + 生成 / 回看两个底栏，
  只持有纯视觉局部态（思考块展开态、滚动位置）并渲染，请求与读写一律不在这里。
- `ui/components/ai/AiMessageParts.kt` / `AiThinkingBlock.kt` / `AiMarkdown.kt` / `AiSheetTheme.kt`：
  底栏零件（气泡 / chip / 思考块与贴底跟随 / Markdown 渲染 / 间距 token 与状态卡）。
- `data/mcp/McpProtocol.kt`：MCP JSON-RPC 协议核心（纯逻辑，单测直接喂字符串）。
- `data/mcp/McpServerController.kt`：MCP 服务器生命周期（订阅设置自动启停/热重启）。
