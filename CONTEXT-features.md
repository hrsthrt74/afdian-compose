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

### 会话页 FAQ 快捷回复（MessageConversationScreen）

- FAQ 不常驻：输入框尾部 `trailingIcon` 放 `MiuixIcons.Help` 按钮，点击切换 FAQ 横排的显示。
- FAQ 横排动画：0→1 进度值同时驱动「高度裁切（layout 修饰符）+ 淡入淡出（graphicsLayer）」，收起时内容保持组合一起淡出，避免闪消。
- 有草稿文本时 Help 图标隐藏（淡出 + 缩小 + 模糊，模糊走 `renderEffect = BlurEffect`，受模糊开关控制）；布局尺寸不变，避免输入框宽度抖动。
- 输入框一有字自动收起 FAQ（只收起不自动展开）。
- FAQ 横排首项是「常见问题」说明标签，条目垂直居中（LazyRow `verticalAlignment`）。
- 没有 FAQ 数据时不渲染 Help 按钮。

### 会话页赞助详情 Sheet

- 顶栏 `actions` 的 `MiuixIcons.Info` 按钮打开；数据来自 `GET /api/user/sponsor-info?user_id=...`（ifdian.net + Cookie），首次打开拉取并缓存。
- `WindowBottomSheet` 撑满高度：内容 `fillMaxHeight`（Sheet 外层是 wrapContentHeight）。
- 两个分区「我发电了 TA」（out_detail，主色）与「TA 发电了我」（in_detail，官方橙 `0xFFFCB239`）：某方向无记录时整个分区隐藏；两区都空显示 `EmptyStateView` 空态。
- 每区：三张 `DataCard`（当前发电 元/月 / 总共发电 元 / 到期时间）+ Card 包裹的逐笔明细（条目间 `HorizontalDivider`，左侧方案名+留言、右侧大金额小字 ￥ 与 /月）。
- 模型：`SponsorAmount.list: List<SponsorRecord>`；服务层 `fetchSponsorInfo`；ViewModel 同名方法失败返回 null。

### 错误态统一

- 七个页面（发现/我发电的/收藏/管理面板/收入明细/感谢页/订单）的加载失败统一为 `EmptyStateView` + 底部 fillMaxWidth 重试按钮（各页保留原重试回调）。

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
- 管理分区：整体概况、赞助者管理；收到发电已独立为单独页面。
- 数据卡片系统：`DataCardItem` + `DataCardGrid` + `ChartDataCard`（Vico 图表库）+ `IncomeDataCards`。
- 可自定义布局：`ManagementAddCardSheet` 添加卡片，支持拖拽排序。

## 收到发电（ReceivedSponsorsScreen）

- 独立分页订单列表，支持下拉刷新、滚动加载、错误重试和本地订单搜索。
- 筛选：兑换码、有留言、发货状态（不筛选 / 含收件信息 / 已发货 / 待发货）、起止时间；排序支持时间和金额。
- 发货状态请求参数：含收件信息 `status=10`、已发货 `status=9`、待发货 `status=8`；不筛选时省略 `status`。
- 时间请求参数使用 `begin_time` / `end_time` Unix 秒，任一边界可为空；结束上界取选中分钟的第 59 秒（接口按半开区间处理）。
- 时间选择器复用通用组件 `DateTimeField`（日期 / 时间双滚轮，年份范围 2018–2100）；`ReceivedTimeRange` 以毫秒存储，未设置的边界在界面上回落到「今天」，只有拨动滚轮才会写入，「清除」按钮恢复为不限。
- 卡片上半部分进入私信，下半部分打开订单详情 Sheet；支持展示捆绑包优惠和兑换码提示。

## 浏览历史（HistoryScreen）

- 按访问时间分组（今天 / 昨天 / 更早）。
- 记录类型：创作者主页、动态详情、私信会话（进入会话页即记录，点击条目跳回聊天）。
- 条目用 `HistoryEntryOption` 组件（复用头像 + 名称 + 操作按钮）。
- 清空历史确认弹窗。
- 可在设置中开关。
- `HistoryAvatarCache` 本地缓存头像。
- `HistoryStore` DataStore 持久化。
- 空状态用 `EmptyStateView`（`fillParentMaxSize` 撑满视口居中）。

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

- `EmptyStateView`（`ui/components/EmptyStateView.kt`）：通用空状态/错误态视图，96dp 图标（默认 `Light.Info`）+ title2 标题 + body2 引导语 + 可选 action 槽位（重试按钮），组合时自带 0.8→1 缩放淡入动画（`animated = false` 可关）。

## 关键文件

- `MainActivity.kt`：应用入口、免责声明、认证分流。
- `AppLog.kt`：全局日志门面。
- `ui/screens/main/`：所有主页面（Home、Discover、PostDetail、Messages、Profile、CreatorDetail、Favorites、Sponsoring、Order、Management、ThankSponsors、Settings、History）。
- `ui/screens/auth/`：登录、免责声明。
- `ui/components/`：通用组件；`ui/components/data/`：数据卡片组件；`ui/components/home/`：首页模块组件。
- `ui/viewmodel/*.kt`：各页面 ViewModel（含 PostInteractionController 全局互动状态）。
- `data/auth/`：认证状态、Token 存储、登录请求和加密载荷。
- `data/content/`：领域接口服务（AccountService、MessageService、AffiliateService、FeedService、CreatorService、ReceivedSponsorService、ThankSponsorsService、OrderService、ManagementService），`AfadianContentService` 为兼容门面；消息解析集中在 `MessageParser`，模型见 `ContentModels` / `MessageModels`；缓存 `ProfileCache` / `HomeCache`。
- `data/network/`：`AfdianHttpClient`（域名优先与回退）、`requireAfdianSuccess`（业务码校验）、`sharedAfdianHttpClient`（全应用共享客户端）。
- `data/history/`：浏览历史（HistoryStore、HistoryModels、HistoryAvatarCache）。
- `data/stats/`：数据卡片和图表模型（CardModel、ChartModel、CreatorStatsRepository、WithdrawalSchedule）。
- `data/settings/SettingsStore.kt`：DataStore 设置存储。
