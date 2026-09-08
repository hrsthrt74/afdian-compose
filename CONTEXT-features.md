# 已实现功能

UI 写法约定（空状态、占位色、通用组件等）见 `DESIGN.md`，这里只记录功能行为。

## 应用基础

- 单一 `MiuixTheme`，使用 `ThemeController(ColorSchemeMode.System)`，支持跟随系统/浅色/深色三模式。
- 根页面使用 Miuix `Scaffold`。
- 使用 Miuix `NavigationBar` 展示 4 个 Tab：首页、发现、消息、我的。
- 使用 `miuix-nav` 的 `NavKey`、`rememberNavBackStack`、`NavDisplay` 和 `entry<T>`。
- 全页面渐进模糊效果（ProgressiveBlur + BlurredBar）。
- 系统预测性返回 + 手势返回，由设置开关控制。

## 认证

- 密码登录（AES/CBC/PKCS5Padding 加密账号和密码，RSA/PKCS1 加密 AES 密钥）。
- Token 使用 Android Keystore 的 AES-GCM 加密后保存。
- 登录失败显示错误文本，登录中禁用按钮。
- 免责声明弹窗：首次同意后保存本地状态，不同意则关闭任务。

## 首页（模块化）

首页采用可配置模块系统，用户可自由排序和开关模块。

已实现的 5 个模块：
1. **提现提醒** — 月末 7 天自动显示，点击跳转网页提现页。
2. **创作者数据** — 展示今日收入等数据卡片，支持长按进入整页编辑网格。
3. **我发电的** — 已发电创作者列表，横滑展示。
4. **收藏的创作者** — 收藏的创作者列表，横滑展示。
5. **浏览历史** — 最近浏览的创作者和动态，按时间分组。

模块配置：
- `HomeCustomizationSheet` 支持拖拽排序和显隐开关。
- 配置持久化到 DataStore。
- 首页底部有「自定义」按钮触发配置抽屉。

创作者数据系统：
- `CreatorStatsRepository` 拉取 dashboard + stat 接口。
- `CardModel` / `ChartModel` 数据模型。
- `DataCardGrid` / `ChartDataCard` / `IncomeDataCards` 数据卡片组件。
- `ManagementEditableDataGrid` 整页编辑网格。
- 按日图表（近30天收入 / 访问人数趋势）边界处理：`/api/my/stat` 对今天/昨天返回空值，`ChartModel.trimTrailingBlankDays` 只裁掉窗口末端（今天方向）连续的空/0 天，中间为 0 的天保留；全为 0/无数据时回退完整窗口。抓取时额外回溯 `CHART_TRAILING_TRIM_BUFFER_DAYS`(7) 天，保证裁剪后仍有 `CHART_DAILY_WINDOW_DAYS`(30) 个点。

## 发现页

- 动态流（LazyColumn + PostCard），支持滚动加载。

## 动态详情页（PostDetailScreen）

- 路由携带列表页已有数据秒显，后台按 id 拉取全文覆盖刷新。
- 也可仅通过 `PostDetailById`（只有 id）打开，先显示加载动画再拉取。
- 创作者头部（可点进主页）+ 封面图 + 标题 + HTML 正文渲染（Jsoup 解析 + 原生 Compose 渲染）。
- 付费动态：未解锁显示「发电解锁」按钮，跳转创作者主页。
- pics 数组图片：正文无 `<img>` 时逐张全宽展示。
- 上一篇 / 下一篇导航卡片。
- 文章末尾再次展示创作者头部。
- 分享功能：调起系统分享面板，分享 `https://afdian.com/p/{post_id}`。

## 动态互动（PostInteractionController）

- 全局单例控制器，管理「已点赞 / 已收藏」的动态 ID 集合。
- 乐观更新：点击瞬间先改本地状态，失败时回滚。
- 跨页面同步：发现页、收藏页、详情页共享同一份状态。
- `PostActionBar` 操作栏：三列均分布局（点赞 / 评论 / 收藏）。
- 按钮带轻触觉反馈。

## 消息

- 消息列表页（含未读数 Badge）和会话详情页（聊天记录、FAQ、发送消息）。

## 收藏页（FavoritesScreen）

- TabRow 三个分区：创作者 / 动态 / 众筹。
- 创作者 tab 用 `CreatorListItem`，副标题回退链：subtitle → creator.doing。
- 动态、众筹 tab 保持普通列表行。

## 我发电的页（SponsoringScreen）

- 列表项用 `CreatorListItem`，副标题为「我发电了 {planName}」。
- 「共 n 位创作者」统计文本。

## 订单页（OrderScreen）

- 订单摘要 + TabRow 固定头部，列表展示订单行。
- Tab 筛选：全部 / 待支付 / 已支付 / 待发货 / 已收货。

## 创作者详情（CreatorDetailScreen）

- 头部封面 + 头像信息区、目标、发电计划、商品、动态（PostCard 复用）、合集、赞助榜。
- 可折叠长文本。

## 管理页（ManagementScreen）

- TabRow 固定头部模式（保留 `minWidth = 110.dp`）。
- 四个分区：整体概况、收入统计、收到发电、赞助者管理。
- 数据卡片系统：`DataCardItem` + `DataCardGrid` + `ChartDataCard`（Vico 图表库）+ `IncomeDataCards`。
- 可自定义布局：`ManagementAddCardSheet` 添加卡片，支持拖拽排序。

## 浏览历史（HistoryScreen）

- 按访问时间分组（今天 / 昨天 / 更早）。
- 条目用 `HistoryEntryOption` 组件（复用头像 + 名称 + 操作按钮）。
- 清空历史确认弹窗。
- 可在设置中开关。
- `HistoryAvatarCache` 本地缓存头像。
- `HistoryStore` DataStore 持久化。

## 赞助者感谢页（ThankSponsorsScreen）

- 创作者后台功能：顶部比心插画 + 创作者信息 + 感谢语卡片 + 赞助者列表。
- 按年月分页查看赞助者。
- 感谢语编辑和保存。
- 数据来自 `get-thank-sponsors` 和 `get-thank-word` 接口。

## 设置页（SettingsScreen）

- `SwitchPreference`（爱发电主题色开关）、`OverlayDropdownPreference`（启动默认页）等。
- 开关项：高级过渡动画、手势返回、系统预测性返回、调试日志、浏览历史。
- 配色模式选择：跟随系统 / 浅色 / 深色。
- 退出登录。

## 内容 URL 拦截

- 拦截 `afdian.com` 和 `ifdian.net` 的公开链接：
  - `https://afdian.com/p/{post_id}` → 动态详情页。
  - `https://afdian.com/a/{slug}` 或 `https://afdian.com/@{slug}` → 创作者详情页。
- 其他 URL 交给系统浏览器。

## 通用组件

组件用法见 `DESIGN.md` 的「通用组件」一节。

## 关键文件

- `MainActivity.kt`：应用入口、免责声明、认证分流。
- `AppLog.kt`：全局日志门面。
- `ui/screens/main/`：所有主页面（Home、Discover、PostDetail、Messages、Profile、CreatorDetail、Favorites、Sponsoring、Order、Management、ThankSponsors、Settings、History）。
- `ui/screens/auth/`：登录、免责声明。
- `ui/components/`：通用组件；`ui/components/data/`：数据卡片组件；`ui/components/home/`：首页模块组件。
- `ui/viewmodel/*.kt`：各页面 ViewModel（含 PostInteractionController 全局互动状态）。
- `data/auth/`：认证状态、Token 存储、登录请求和加密载荷。
- `data/content/`：内容接口（AfadianContentService、OrderService、ManagementService）和缓存（ProfileCache、HomeCache）。
- `data/history/`：浏览历史（HistoryStore、HistoryModels、HistoryAvatarCache）。
- `data/stats/`：数据卡片和图表模型（CardModel、ChartModel、CreatorStatsRepository、WithdrawalSchedule）。
- `data/settings/SettingsStore.kt`：DataStore 设置存储。
