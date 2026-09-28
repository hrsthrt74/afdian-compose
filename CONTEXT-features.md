# 已实现功能

UI 写法约定（空状态、占位色、通用组件等）见 `DESIGN.md`，这里只记录功能行为。

## 应用基础

- 单一 `MiuixTheme`，支持跟随系统/浅色/深色三模式，并可切换成 Monet 色板（见设置页）。
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
- `CreatorStatsRepository` 拉取 4 个接口：`/api/my/dashboard`（总量摘要 + 可提现）、
  `/api/my/sponsored-bill-filter`（时间段内的已支付订单，聚合收入/发电人数/发电次数）、
  `/api/my/stat`（按日 UV、图表）、`/api/my/income`（月度账单，抽成后收入）。
  前三者之外的可选接口失败只让对应卡片显示「--」，dashboard 失败则整体抛出。
- 收入卡口径：「收入」= 订单 `total_amount` 实时聚合（含当月）；「收入（抽成后）」上月/历史用月度账单
  的真实 `creator_amount`，**本月**因账单要次月初才出账、接口里根本没有当月条目，改为
  「本月收入 × `CREATOR_SHARE_RATIO`(0.94)」估算（平台抽成 6%），折算结果**向下取整到分**
  （`setScale(2, RoundingMode.FLOOR)`，3.1490 → 3.14，宁可少算不多算）；
  若账单里意外带了当月则以官方值覆盖。
  连带影响：月末提现提醒里的「到手」也走这条估算值，不再是 `---`。
- `CardModel` / `ChartModel` 数据模型。
- `DataCardGrid` / `ChartDataCard` 数据卡片组件。
- `DataCard` 公共数据卡：数值右侧支持后导单位 `unit`（`unitStyle` 默认 footnote1、`unitColor` 默认次级灰，与数值基线对齐）。
- 数据卡单位由 `CardMetric.unitText(showIncomeUnit, showCountUnit)` 决定：收入/可提现→「元」、
  发电人数/访问人数→「人」、发电次数/访问次数→「次」、距离下次提现→「天」（始终显示）。
  前两类受设置开关 `showIncomeUnit` / `showCountUnit` 控制，默认关闭；开关在 UI 边界
  `toDataCardItem(showIncomeUnit, showCountUnit)` 处生效（首页、管理页各自 collect 设置）。
- `ManagementEditableDataGrid` 整页编辑网格。
- 首页首屏缓存（`data/content/HomeCache`，DataStore 文件 `home`）：缓存发电列表、收藏创作者、数据卡文字
  与图表数据（`creatorCharts`，key 为 `ChartType.storageValue`），冷启动先回填秒显、网络结果回来再覆盖；
  只缓存可展示数据，不缓存加载状态与分页游标。`ChartSeries` / `ChartDataItem` 是 `@Serializable`，
  图表整体无损落盘（数据卡因 `CardValue` 类型会退化，才单独拍平成 `HomeCacheCard`）。
  新增字段一律带默认值，旧快照反序列化为空集合而不是解析失败。
  图表刷新是**合并**语义（`_creatorCharts.value + overview.charts`）：数据源临时失败时该图键缺失，
  保留上一份好数据而不抹成加载占位，缓存里也就能留住上次的图。
- 按日图表（近30天收入 / 访问人数趋势）边界处理：`/api/my/stat` 对今天/昨天返回空值，`ChartModel.trimTrailingBlankDays` 只裁掉窗口末端（今天方向）连续的空/0 天，中间为 0 的天保留；全为 0/无数据时回退完整窗口。抓取时额外回溯 `CHART_TRAILING_TRIM_BUFFER_DAYS`(7) 天，保证裁剪后仍有 `CHART_DAILY_WINDOW_DAYS`(30) 个点。

## 发现页

- 动态流（LazyColumn + PostCard），支持滚动加载。
- 顶部提供只读搜索入口；点击后在发现页内展开搜索层，采用 Miuix 官方 IconPage 的 `SearchBarFake` / `SearchPager` 状态切换模式。
- 搜索层当前提供真实输入框、取消操作与「搜索结果 / 最近搜索」占位区域，网络搜索和历史持久化后续接入。

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

- 加载失败统一用 `ErrorStateView`（标题「加载失败」+ 内置 fillMaxWidth 重试按钮，各页保留原重试回调，不要手写 action）。
  已覆盖 13 处：发现、我发电的、收藏、管理面板、优惠码（Affiliate）、收入明细、月度汇总、感谢页、订单、赞助管理、发电记录、创作者详情（首屏）、方案详情 sheet。
- `CreatorDetailScreen` 首屏错误态额外加 `padding(top = paddingValues.calculateTopPadding())` 让出顶栏。
- `PlanDetailSheet` 拆成两支：`detail == null && error != null` → `ErrorStateView`（重试 `viewModel::retry`）；
  `detail == null`（如 planId 缺失、无错误）→ `EmptyStateView("暂无详情")` 兜底，不要合并成一个分支。

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

- TabRow 固定头部模式已随原「发电者管理」占位分区一并移除（赞助者管理独立成页后只剩整体概况一个分区）。
- 管理分区：整体概况（原「赞助者管理」占位 tab 已删除）；收到发电已独立为单独页面。
- 数据卡片系统：`DataCardItem` + `DataCardGrid` + `ChartDataCard`（Vico 图表库）+ `IncomeDataCards`。
- 可自定义布局：`ManagementAddCardSheet` 添加卡片，支持拖拽排序。

## 赞助者管理页（SponsorManageScreen）

- 创作者后台功能（对应网页 `/dashboard/sponsors`），入口在「我的」页创作者分组（原管理页「发电者管理」占位 tab 已删除并迁至此）。
- 路由 `Route.SponsorManage`（无参）；列表数据 `POST /api/my/who-sponsored-me`（ManagementService.fetchSponsors，页码分页，每页 20）。
- 排序：列表顶部排序下拉 Chip（FilterChip + WindowListPopup，样式照搬收到发电页），`SponsorManageSort` 共 7 档 = 默认排序（sort_field/sort_value 空串，可取消排序）+ 三字段 × 升降序成对（`all_sum_amount` / `last_pay_time` / `create_time` × asc/desc，字段名抓包自网页表头）；切换回第 1 页重载（加载中不响应），默认档 Chip 不高亮。
- 筛选：筛选 Chip（与排序 Chip 并排）弹**双层 WindowListPopup 级联**（对齐网页「筛选档位」dialog，载体为 popup）：第一层条件构建主页（锚 Chip）= 条件行（type 段/操作段/value 段三段 FilterChip + 删除）+「新增筛选项」多条件（最多 4 行，AND 关系）+「清除/确定」；点段时从**该段自身**叠弹第二层选择视图（段级 popup 组合在主页内容的段 Box 内，主页/子层均为全屏透明 Dialog 坐标系对齐，`enableWindowDim=false`），选完只关第二层，点外先关第二层再关主页。4 种 type（正在赞助 `sponsoring`/赞助过 `sponsored`/累计金额 `all_sum_amount`/赞助月份 `month`）、方案型动作 包含/不包含（`=`/`!=`）、金额型动作 `>=` `<=` `≠(!=)` `>` `=` `<`（展示用 ≥/≤/≠，抓包验证）、月份型固定 `=`；value 按类型分流：方案列表（`GET /api/creator/all-plans`，list+sale_list 去重、排除 0 元）/ 金额数字输入 / **月份多选**（自定义 4 位年份 + 1–12 月格跨年累加，YYYYMM 降序，提交时 value=`"202610,202609"` 逗号串 + `month_value` 名值数组，抓包协议）。草稿与已应用分离（打开主页时重置），「确定」只提交完整行（未选方案/空数值 = 该行不生效，等价官方「全部」），Chip 摘要：无条件「筛选档位」/ 单条条件摘要 / 多条「筛选 (N)」；切换回第 1 页重载（加载中不响应）。
- 展示：「共 N 名赞助者」统计 + 卡片列表（头像 / 昵称 / 当前方案或「无」/ 累计金额 / 最近赞助相对时间），`LoadMoreEffect` 滚动加载。
- 行点击弹 `WindowBottomSheet` 详情：头像昵称、当前方案、累计金额 DataCard、初次/最近赞助绝对时间、赞助过的方案列表、「查看主页」跳 `Route.CreatorDetail`。
- 模型：`SponsorManageItem` / `SponsorPlanBrief` / `SponsorManagePage` / `SponsorManageSort` / `SponsorFilterPlan` / `SponsorFilterType` / `SponsorFilterAction` / `SponsorFilterRow` / `monthDisplayName()`（ContentModels.kt）；解析/构建为顶层 `internal parseSponsorManagePage` / `parseFilterPlans` / `buildSponsorFilterArray`（`SponsorManageParserTest` / `SponsorFilterPlanParserTest` / `SponsorFilterBuildTest` 覆盖），plan 的巨型 HTML desc 不映射。
- 不含：官方面板的「自选金额」快捷项、条件行数上限 4 以上的扩展、导出、群发私信（群发私信按需求永久排除）。

## 收到发电（ReceivedSponsorsScreen）

- 独立分页订单列表，支持下拉刷新、滚动加载、错误重试和本地订单搜索。
- 筛选：兑换码、有留言、发货状态（不筛选 / 含收件信息 / 已发货 / 待发货）、起止时间；排序支持时间和金额。
- 发货状态请求参数：含收件信息 `status=10`、已发货 `status=9`、待发货 `status=8`；不筛选时省略 `status`。
- 时间请求参数使用 `begin_time` / `end_time` Unix 秒，任一边界可为空；结束上界取选中分钟的第 59 秒（接口按半开区间处理）。
- 时间选择器复用通用组件 `DateTimeField`（日期 / 时间双滚轮，年份范围 2018–2100）；`ReceivedTimeRange` 以毫秒存储，未设置的边界在界面上回落到「今天」，只有拨动滚轮才会写入，「清除」按钮恢复为不限。
- 卡片上半部分进入私信，下半部分打开订单详情 Sheet；支持展示捆绑包优惠和兑换码提示。
- 日期分组：仅当排序为「新订单在前」且没有任何筛选（兑换码 / 有留言 / 发货状态 / 时间范围）与搜索时，列表才插入「今天 / 昨天 / 更早」标题；某段没有订单时不显示该段标题，只要存在筛选或搜索就整体平铺不做分组。

## 浏览历史（HistoryScreen）

- 按访问时间分组（今天 / 昨天 / 更早）。
- 记录类型：创作者主页、动态详情、私信会话（进入会话页即记录，点击条目跳回聊天）。
- 条目用 `HistoryEntryOption` 组件（复用头像 + 名称 + 操作按钮）。
- 清空历史确认弹窗。
- 可在设置中开关。
- `HistoryStore` DataStore 持久化：**只存文本与头像链接，不下载头像**。头像在渲染时由
  `HistoryEntryOption` 直接把 `entry.target.avatarUrl` 交给 Coil 按需加载，磁盘缓存归 Coil 全局缓存
  （存储页里因此算在「图片缓存」而不是「浏览历史」）。
- 旧版本曾用 `HistoryAvatarCache` 把头像预下载到 `files/history_record_cache`（为了在设置页能精确删除），
  该目录现在不再写入；`HistoryViewModel` 启动时调一次 `HistoryStore.clearLegacyAvatarCache()` 回收老用户的残留，
  存储页的「浏览历史」清理也仍然涵盖这个目录。
- 空状态用 `EmptyStateView`（`fillParentMaxSize` 撑满视口居中）。

## 每月汇总（MonthlySummaryScreen）

- 「我的」页「创作者」分组入口（标题「每月汇总」，图标沿用 `ThN` 数字图标=上个月）；原「感谢页」入口由它取代。
- 数据来自创作者后台 `/api/my/income`（一次返回自开通以来全部月份），仓库 `MonthlySummaryRepository`；
  解析逻辑 `parseMonthlyBills` 与 `CreatorStatsRepository.fetchMonthlyIncome()` 共用（`data/stats/MonthlyBill.kt`）。
- 展示：按年分节（`SmallTitle`「2026年」）的扁平列表，行内「月份 + N 赞助者 + ¥金额收入」，右侧「感谢页」；整行可点进该年月的感谢页。
  列表**不显示**账单单据的 `create_time` 结算时间；0 收入的月份也照常列出。
- 顶栏三点菜单（`WindowIconDropdownMenu` + `DropdownItem.selected`）切换金额口径：
  「抽成前金额」`total_amount`（默认）/「抽成后金额」`creator_amount`。
- 金额口径持久化到 DataStore（`SettingsStore.monthlySummaryAmountMode`，键 `monthly_summary_amount_mode`）。
- 加载/空（「暂无月度账单」）/错误+重试三分支，右侧 `VerticalScrollBar`。

## 赞助者感谢页（ThankSponsorsScreen）

- 创作者后台功能：顶部比心插画 + 创作者信息 + 感谢语卡片 + 赞助者列表。
- 按年月分页查看赞助者。
- 感谢语编辑和保存。
- 数据来自 `get-thank-sponsors` 和 `get-thank-word` 接口。
- 路由 `Route.ThankSponsors(year, month)` 带年月参数，由「每月汇总」页按行传入；
  `ThankSponsorsViewModel` 的 `initialYear`/`initialMonth` 为 null 时才回退到上个月（页内「月份跳转」仍用 `jumpTo`）。

## 设置页（SettingsScreen）

- `SwitchPreference`（爱发电主题色开关、Monet 配色开关）、`OverlayDropdownPreference`（启动默认页、配色模式）、
  `OverlaySpinnerPreference`（Key Color）等。
- 开关项：高级过渡动画、手势返回、系统预测性返回、浏览历史、
  显示收入单位、显示人次单位（后两项在「数据卡片」分区，默认关闭）。
  「调试日志」开关已迁移到「其他 → 日志」页（与日志落盘/导出同属一套排查工作流）。
- 配色模式选择：跟随系统 / 浅色 / 深色。
- Monet 配色（「外观」分区，默认关闭）：打开后色板改由 Miuix 用种子色生成，
  与配色模式正交 —— 跟随系统/浅色/深色分别映射到 `MonetSystem`/`MonetLight`/`MonetDark`。
  开启时隐藏「使用爱发电主题色」（其持久化值不清除，关闭后自动恢复），并显示 `Key Color`。
- `Key Color` 用 `OverlaySpinnerPreference` 而非普通 Dropdown，因为只有 Spinner 的
  `DropdownItem` 带 `icon` 槽位，能在选项文字前插颜色球。展示顺序：跟随系统壁纸 → 爱发电主题色 →
  官方 demo 的 6 色（预设见 `data/settings/KeyColorPreset.kt`，官方那 7 色里的「紫色」因与品牌紫太像已剔除）。
  「跟随系统壁纸」没有固定色值，球用 `Brush.sweepGradient` 色环区分；Miuix 传给 icon 的 Modifier
  已含「最小 26dp 图标格 + 末尾 12dp 间距」，不要再自己补间距。
  注意折叠态那一行只显示选项**文字**（Spinner 的取值区只渲染 `DropdownItem.text`，不渲染 icon）。
  系统壁纸取色要 API 31+，低版本下拉里会过滤掉该项、并把已选中的它解析成蓝色
  （`KeyColorPreset.resolveForSdk`），保证下拉显示色与主题实际用色一致。
  模式映射集中在 `AfdianTheme`（`ui/theme/Theme.kt`），是「明暗」与「是否 Monet」两个维度的唯一权威出处。
- Monet 相关 UI 的显隐/勾选态读 `MiuixTheme.isDynamicColor`（全局主题真实生效的模式），**不要**读
  `SettingsViewModel` 的流：设置页的 VM 是进入页面时才创建的，初值固定为「Monet 关闭」，
  会让「使用爱发电主题色」先渲染出来再被隐藏（闪一下）。这也是 `ThemeColorTransition` 里
  「抄全局主题的结果最稳」的同一套思路。
- 切配色时的平滑过渡：整页包在 `ThemeColorTransition`（`ui/theme/ThemeColorTransition.kt`）里，
  改动配色模式或主题色时逐帧插值到新色板（时长见文件内 `ColorTransitionDurationMillis`），不再瞬间换色。只设置页有，其他页面不参与。
- 过渡只作用于中性色；主色家族（primary 及派生色：禁用态、强调容器、下拉选中项、Slider 刻度点）
  直接取目标值一步到位，不跟着渐变。
- 系统栏图标颜色跟随 App 深浅：`AfdianTheme` 内调 `ApplySystemBarAppearance`
  （`ui/theme/SystemBarAppearance.kt`），按生效色板的底色亮度决定图标深浅。
  开 edge-to-edge 后系统只按「系统深浅色」给图标，App 手动切深色时必须自己覆盖，
  否则黑图标压在深色背景上看不见。状态栏与导航栏一起处理，且是瞬间切换、不参与渐变。
- 退出登录。
- 「其他」分组：`存储空间管理` 与 `日志` 两个入口（原「清除浏览历史」入口已移除，清理统一收敛到存储页）。

## 存储空间管理页（StorageScreen）

- 入口在设置页「其他」分组；浏览历史的清理**只在这里**提供（设置页原「清除浏览历史」入口已移除）。
- 扫描器 `data/storage/StorageScanner`：以 `context.dataDir` 为根遍历一次，只累加文件字节数，按相对路径前缀归入 `StorageCategory`。
  - 遍历时跳过 `code_cache/`（编译产物）与软链接（`dataDir/lib` 指向 `/data/app/.../lib`，跟进会把 APK 原生库算进来）。
  - `Other` 分类用 `total - 其它分类之和` 兜底覆写，从构造上保证「应用总占用 == 各分类之和」。
  - `categorizeStoragePath` 用**前缀**匹配而非文件名全等：DataStore 先写 `<name>.preferences_pb.tmp` 再原子 rename，可能残留 `.tmp`。
  - `formatBytes` / `categorizeStoragePath` 是顶层 `internal` 纯函数，见 `StorageScannerTest`。
- 分类与清理动作一一对应：图片缓存 → Coil `diskCache.clear()`（同步阻塞，必须在 IO 线程，且**不能**直接删目录，目录内有 LRU journal）；
  历史记录 → 按勾选的子项分别走 `HistoryStore.clearAll()` / `SearchHistoryStore.clear()`；
  快照缓存（`Snapshot`）→ `HomeCache.clear()` + `ProfileCache.clear()` 一起清。
- 共 6 个分类：图片缓存、历史记录、快照缓存、日志、设置与登录数据、其他。
- 两处「合并展示」的取舍不同，区别就在要不要让用户选子项：
  - 浏览记录 + 搜索关键词 → **「历史记录」**（`History`）。两者都是本地历史、都能自动重建，
    但**保留分别清理的能力**：`StorageCategory.selectableParts` 声明可勾选子项（`HistoryPart`），
    点击该行弹的是勾选弹窗（`CheckboxPreference`，每次打开默认全选、可以全不选）而不是整类确认框，
    确认按钮在「一项都没勾」时 `confirmEnabled = false` 置灰。勾选项整体包在一张
    `Card(colors = CardColors(secondaryContainer, onSurfaceContainerHighest))` 里，与页面上的设置项观感一致；
    Card 自身不加 padding，缩进交给勾选行自带的 16dp 内边距。
    `StorageViewModel.clearHistoryParts` 只清勾中的那几项；`clearCategory` 与一键清理等同「子项全选」。
    清理完成的 Toast 会按子项分别措辞（只清一项时说「已清理浏览历史」，全清才说「已清理历史记录」）。
  - 首页快照 + 资料快照 → **「快照缓存」**（`Snapshot`）。两者都是「冷启动先回填、网络回来再覆盖」的临时快照，
    体积小、能自动重建，分开列出只会让明细列表变长、清理时还得选两次，所以**整类一起清、不带子项**。
- `selectableParts` 的约定：**非空才会弹勾选窗**，为空（默认）走原来的破坏性确认框；新增分类不用管它。
- 「设置与登录数据」（`auth` / `settings` / `theme_settings`）与「其他」恒为 `cleanable = false`：不可点、无箭头，一键清理也**绝不**包含——清掉 `auth` 等于静默登出。
- 可清理项按占用降序排列，不可清理项固定置底；清理中对应行置灰并显示「清理中…」，完成后自动重扫。
- 总览卡片顶部是环形占比图，其下一行是「占用设备」：summary 为「应用占用 / 设备总容量」，
  endActions 是百分比文案 + Miuix `CircularProgressIndicator`（`progress` = 应用占用 / 设备总容量）。
  - 占比与文案由 `deviceUsageFraction` / `formatPercent`（`data/storage/StorageScanner.kt` 顶层 `internal` 纯函数）算，
    见 `StorageScannerTest`。**设备容量读到 0 时必须回落成 0f**：`CircularProgressIndicator` 内部是
    `progress.coerceIn(0f, 1f)`，对 NaN 两个比较都为假会原样透传，最后画出 NaN 扫过角度的乱图像。
  - 百分比小于 0.1% 显示「<0.1%」而不是「0.0%」（应用占用对设备容量通常极小，显示 0.0% 像是没占空间）。
  - `snapshot.usableBytes` 仍由扫描器测量并写入调试日志，只是不再在 UI 上展示。

### 存储总览环形图（DonutChart）

- 通用组件 `ui/components/data/DonutChart.kt`：Vico `PieChart` + `PieSize.Inner`（挖内孔）实现，圆心支持自定义内容插槽。
- 存储页用法：扇区按占用降序、只保留 `bytes > 0` 的分类，圆心显示「应用总占用 + 数值」。
  原先独立的「应用总占用」总览行已合并进圆心，避免同一个数字出现两次。
- 分类配色 `StorageCategory.chartColor`（`StorageScreen.kt` 私有扩展）：**按分类绑定**而不是按扇区下标轮转，
  这样某类清零、排序变化时同一分类的颜色不会跳变。自定义固定色而非主题色 —— Miuix 没有分类调色板，
  而 primary / error 是语义色不能挪用；颜色本身就是这套图的图例，跟着主题色变会破坏「颜色 ↔ 分类」的记忆。
- 明细行左侧的圆点用同一个 `chartColor`，靠颜色而不是文字图例把图表和列表对应起来。
- Vico 饼图的两个要点（与 `CartesianChartHost` 不同，别照抄）：
  - 没有 `initialAnimationSpec` 参数，禁用进场动画要传 `animateIn = false`；
    这里**只关 `animateIn`、保留默认 `animationSpec`** —— 图表在 `LazyColumn` item 里，
    滚出再滚回会重新进入组合，关掉 `animateIn` 才不会重播闪烁，而数据变化（清理完某类）仍有平滑过渡。
  - `LocalVicoTheme` 默认值是 `null` 并会回退到 Light/Dark，因此不套 `ProvideVicoTheme` 也不会崩；
    本组件自行传入 sliceProvider 且不从主题取色，所以完全不需要主题包裹。
- 圆心数字是「大数值 + 小单位」排版：`formatBytesParts` 把字节数拆成两段，
  `Row(spacedBy(4.dp, CenterHorizontally))` 里数值用 `title2`、单位用 `footnote1` + 次级灰，两者 `alignByBaseline()`
  （与 `DataCard` 的数值/单位排版同一套做法，整串同字号会又宽又让单位喧宾夺主）。
- 宽度两层兜底：外层 `Column` 锁在圆孔直径内（`DonutHoleWidth - 12.dp`），
  数值再单独 `widthIn(max = DonutHoleWidth - 36.dp)`。后者是必要的 —— `Row` 里无 `weight` 的子项按顺序吃剩余宽度，
  字号极大时数值会把单位挤成 0 宽；给它一个宽度上限后是数值自己省略号收尾，单位始终完整。
- **最小扇区角度 `minSweepDegrees`（默认 5 度）**：解决「占比极低的分类在图上彻底看不见」。
  Vico 只按 `value / sum * 360` 画，没有任何最小角度参数，所以由 `enforceMinimumSweep`（同文件顶层 `internal` 纯函数，
  见 `DonutChartTest`）先把权重换算成角度再喂给图表。一趟解出、不做迭代：
  先按**真实占比**挑出低于下限的扇区抬到下限，剩余预算按原比例分给其余扇区；
  数据健康（无扇区低于下限）时退化为等比缩放，占比一点不变，因此可无条件调用。
- 下限为什么是 5 度而不是 2~3 度：扇区之间还有 `gap` 缝隙，库会把每段圆弧两端各削掉约 1 度，
  3 度的扇区被啃掉两头就只剩一丝，等于没解决问题。
- 代价：被抬过的分类视觉占比会略大于真实占比（例：图片缓存 332° → 313°，肉眼几乎无感）。
  真实数值始终由下方明细列表如实展示，图上不标注。

### 图片缓存上限（存储页「缓存策略」分组）

- UI：一个 4 档滑块（128MB / 256MB / 1GB / 无限制）+ 滑块下方一行刻度标签（`Row` + `SpaceBetween`）+ Card 下方的说明文字（交代「只作用于图片缓存」「重启后生效」）。
  不设 `valueText`（右上角不再显示当前档位名），当前档位由对应刻度的高亮（`primary` 色 + Medium 字重）表达。
- **为什么只能这么做**：Coil 没有任何「运行时按大小裁剪」的入口 —— `DiskCache.maxSize` 是不可变 `val`、
  `DiskLruCache.trimToSize()` 是 `private`、公开 API 里只有 `evictAll()`（整体清空）。
  所以「超出上限就删掉最早的」只能把上限在**构建期**交给 `DiskCache.Builder.maxSizeBytes`，
  由 Coil 自己的 LRU 在每次写入后执行 `while (size > maxSize) removeOldestEntry()` —— 正好就是想要的效果。
- 代价：上限属于构建期配置，改档位必须重建 ImageLoader；而 Coil 明确要求「同一目录不允许两个 DiskCache
  实例同时存活」，运行时热替换有损坏缓存的风险，所以**标注为「重启后生效」**（与日志页「调试日志」同一个取舍）。
- `MainActivity` 的装配有两个坑：
  - **ImageLoader 的工厂要读 `State.value`，不能直接捕获值**：`setSafe` 只在首次记下这个工厂，
    直接捕获会把它永久钉在初始的 `null` 上。
  - 工厂本身是懒执行的（Coil 首次取 ImageLoader 时才调用），但为了不让首次图片加载赶在 DataStore 读出之前
    把 ImageLoader 定型，**上限读出来之前不渲染任何 UI**。
- 目录名常量 `IMAGE_CACHE_DIRECTORY_NAME` 与存储页的 `categorizeStoragePath` 共用
  （旧写法是在两处各写一份 `"coil3_disk_cache"`）。
- 「无限制」用 `Long.MAX_VALUE` 而不是「跳过 `maxSizeBytes`」：后者会退回 Coil 默认策略
  （可用空间的 2%，钳制 10~250MB），比 1GB 档还小，滑块档位就不单调了。`ImageCacheTest` 钉住了这条不变式。
- **没做「自动清理缓存（1d / 1w / 1m / 从不）」**：Coil 既不能枚举缓存条目、也没有按时间淘汰的 API，
  「只删 N 天前的图片」实现不了。真要做只能给所有图片请求显式指定 `diskCacheKey` + 自己记访问时间，
  改动面很大，暂不做。

## 日志页（LogScreen）

- 入口在设置页「其他」分组。页面结构：记录（两个开关）→ 状态（占用 / 文件数）→ 导出（分享 / 保存到本地）→ 清空日志 + 说明小字。
- **职责与动机**：内测用户遇到闪退时只有 Logcat 可看，无法向开发者提供任何线索。日志页补齐三件事：
  运行日志落盘、崩溃捕获、导出。所有文件操作集中在 `data/log/LogStore.kt`，纯函数（命名 / 淘汰 / 行格式化 / 合并）
  在 `data/log/LogFileNaming.kt`（见 `LogFileNamingTest`）。
- **写入管线**（`LogStore`）：
  - `AppLog` 保持零依赖：只多了一个 `sink` 回调注入点（`AfdianApplication.onCreate` 里接通 `LogStore::enqueue`）；
    sink 为 null（Application 之前）时只走 Logcat，不丢不崩。
  - D 级日志只在「调试日志」开关开启时落盘（与 Logcat 行为一致）；**W/E 始终落盘**——内测用户没开调试日志也能导出报错。
  - 「写入日志文件」开关（DataStore 键 `log_to_file`）只控制运行日志；**崩溃报告不受它控制，始终记录**——这是本功能存在的意义。
  - **两个开关（写入日志文件 / 调试日志）的默认值都是 `BuildConfig.INTERNAL_BUILD`**：internal 内测版默认全开、
    正式版默认关闭；用户手动改过后以存储值为准（默认值只在键缺失时兜底）。
    `AppLog.debugEnabled` / `LogStore.fileLoggingEnabled` 的**字段初值**也取 `INTERNAL_BUILD`，
    保证 DataStore 读取完成前的启动初期（崩溃多发段）日志不缺席，读出后由订阅方按真实设置校正；
    `MainActivity` / `LogViewModel` 的 collect 初值同步取 `INTERNAL_BUILD`，避免 internal 版首帧「先误关再读回」的闪烁。
  - 命令队列模型：写 / flush / 清空 / 淘汰统一建模成 `LogCommand`，全部在唯一 writer 线程串行执行（文件句柄免锁）；
    `LinkedBlockingQueue(4096)` 满时丢最旧并记账，flush 时补一条「日志队列已满，丢弃 N 条」标记。
    flush / clear 用 `CountDownLatch` + 超时（1s / 3s）转同步。
  - **writer 线程内部绝不调用 `AppLog`**（会递归入队），全部 `runCatching` 静默兜底。
- **文件策略**：目录 `filesDir/logs/`（**不用 cacheDir**——存储紧张时系统会无提示清空它，崩溃证据会丢）；
  运行日志 `app-yyyy-MM-dd.log` 按天一个、保留 3 个自然日（`selectExpiredLogs` 纯函数，启动与跨天时清理）；
  崩溃报告 `crash-yyyy-MM-dd_HHmmss.log` 不参与天数淘汰、只保留最近 10 份；导出文件 `export-*.log` 保留最近 3 份。
- **崩溃捕获**（`CrashHandler`）：**包装**而不是替换默认 handler——先 `flush()` 冲队列再同步写崩溃报告
  （`writeCrashReportSync`，崩溃线程上**必须同步**写，进程随时被杀），最后**必须交还系统 handler**，否则崩溃对话框不弹。
  报告含时间 / 线程 / 变体名 / 版本 / 构建时间 / 系统版本 / 设备型号 / 完整堆栈（`stackTraceToString` 保留 `Caused by` 链；
  release 混淆后类名要靠变体名找对应 mapping.txt 还原）。
  安装位置在 `AfdianApplication.onCreate` **最先**执行——项目此前没有自定义 Application，为此新增了 `AfdianApplication`
  （挂在 MainActivity 会漏掉启动阶段的崩溃）。
- **导出**：合并为**单个 log 文件**（崩溃报告置顶 + 运行日志按日期升序，末尾半截行裁掉），
  分享（`ACTION_SEND` + FileProvider，mimeType `text/plain`）与保存到本地（`CreateDocument`，对齐 `IncomeDetailScreen` 的 CSV 导出）共用一份合并逻辑。
  FileProvider 的 authority 用 `${applicationId}.fileprovider` 动态拼接（internal / legacy 有 `applicationIdSuffix`，硬编码包名会崩）；
  `file_paths.xml` 白名单**只暴露 `logs/`**（绝不能 `path="."`，会把 DataStore 登录凭证暴露给接收方）；
  分享 Intent 带 `ClipData` + `FLAG_GRANT_READ_URI_PERMISSION` 双保险（chooser 场景缺 ClipData 会 Permission Denial）。
- **敏感信息**：`sanitizeSensitive`（`LogFileNaming`）在落盘与导出前把 `auth_token=` / `Cookie:` / `"token":` 替换为 `***`，
  是「调用方不得拼敏感字段」红线之外的第二道防线。
- **存储页联动**：`StorageCategory.Logs`（可清理，紫色 `0xFF8B5CF6`）归类 `files/logs/`；
  清理走 `LogStore.clear()`（writer 线程内先关句柄再删，直接删文件会因句柄占用失败）；「清理全部缓存」也会清掉日志。
- **备份排除**：`backup_rules.xml` / `data_extraction_rules.xml` 排除 `file` domain 的 `logs`——调试产物不占云备份配额，
  崩溃现场要「当下」的，跨设备恢复旧日志反而误导排查。
- 调试开关：AfdianApplication 还订阅 `logToFile` 写入 `LogStore.fileLoggingEnabled`（进程内即时生效）；
  `debugLogging` 的订阅仍在 `MainActivity.AfdianApp` 的 `SideEffect`，未动。

## 内容 URL 拦截

- 拦截 `afdian.com` 和 `ifdian.net` 的公开链接：
  - `https://afdian.com/p/{post_id}` → 动态详情页。
  - `https://afdian.com/a/{slug}` 或 `https://afdian.com/@{slug}` → 创作者详情页。
- 其他 URL 交给系统浏览器。

## 通用组件

组件用法见 `DESIGN.md` 的「通用组件」一节。

- `EmptyStateView`（`ui/components/EmptyStateView.kt`）：通用空状态视图，96dp 图标（默认 `Light.Info`）+ title2 标题 + body2 引导语 + 可选 action 槽位（重试按钮）+ 可选 `iconModifier`（图标变换），组合时自带 0.8→1 缩放淡入动画（`animated = false` 可关）。
- `ErrorStateView`（同文件）：错误态预设，标题固定「加载失败」+ 内置 fillMaxWidth「重试」按钮，参数 `onRetry` / `summary` / `modifier`；图标默认是 Info 旋转 180°（miuix 无错误语义图标，`iconRotation` 可调），内部直接复用 `EmptyStateView`。

## 关键文件

- `MainActivity.kt`：应用入口、免责声明、认证分流。
- `AfdianApplication.kt`：自定义 Application，日志管线（LogStore / sink / CrashHandler）与「写入日志文件」开关订阅的唯一装配点。
- `CrashHandler.kt`：全局未捕获异常捕获，崩溃报告落盘后交还系统 handler。
- `AppLog.kt`：全局日志门面（Logcat + 落盘 sink 注入点）。
- `ui/screens/main/`：所有主页面（Home、Discover、PostDetail、Messages、Profile、CreatorDetail、Favorites、Sponsoring、Order、Management、ThankSponsors、Settings、History、OssLicenses、Storage、Log）。
- `ui/screens/auth/`：登录、免责声明。
- `ui/components/`：通用组件；`ui/components/data/`：数据卡片与图表组件（ChartDataCard、DonutChart）；`ui/components/home/`：首页模块组件。
- `ui/viewmodel/*.kt`：各页面 ViewModel（含 PostInteractionController 全局互动状态）。
- `data/auth/`：认证状态、Token 存储、登录请求和加密载荷。
- `data/content/`：领域接口服务（AccountService、MessageService、AffiliateService、FeedService、CreatorService、ReceivedSponsorService、ThankSponsorsService、OrderService、ManagementService），`AfadianContentService` 为兼容门面；消息解析集中在 `MessageParser`，模型见 `ContentModels` / `MessageModels`；缓存 `ProfileCache` / `HomeCache`。
- `data/network/`：`AfdianHttpClient`（域名优先与回退）、`requireAfdianSuccess`（业务码校验）、`sharedAfdianHttpClient`（全应用共享客户端）。
- `data/history/`：浏览历史（HistoryStore、HistoryModels）。
- `data/log/`：本地日志（LogStore 写入管线、LogFileNaming 纯函数：命名 / 淘汰 / 行格式化 / 合并 / 脱敏）。
- `data/stats/`：数据卡片和图表模型（CardModel、ChartModel、CreatorStatsRepository、WithdrawalSchedule）。
- `data/storage/`：本地存储占用扫描（StorageScanner、StorageCategory、formatBytes）。
- `data/settings/SettingsStore.kt`：DataStore 设置存储。
