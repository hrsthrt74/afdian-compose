# 已实现功能

UI 写法约定（空状态、占位色、通用组件等）见 `DESIGN.md`，这里只记录功能行为。

## 应用基础

- 单一 `MiuixTheme`，支持跟随系统/浅色/深色三模式；主题可选 MIUIX（默认）/ Monet，
  两种主题共用一套主题色预设（见设置 → 外观）。
- 根页面使用 Miuix `Scaffold`。
- 使用 Miuix `NavigationBar` 展示 4 个 Tab：首页、发现、消息、我的。
- 使用 `miuix-nav` 的 `NavKey`、`rememberNavBackStack`、`NavDisplay` 和 `entry<T>`。
- 全页面渐进模糊效果（ProgressiveBlur + BlurredBar）。
- 系统预测性返回 + 手势返回，由设置开关控制。
- 版本拦截：legacy 兼容版跑在 API 34（Android 14）及以上的设备上时，启动即被 `EditionGuard` 拦下，只显示 `WrongEditionScreen`（「请安装完整版」+ 退出），无法进入应用。判定不读设置、不区分 buildType、不做持久化，每次 `onCreate` 现算；阈值与 standard 的 minSdk 同源（Gradle 注入的 `BuildConfig.STANDARD_MIN_SDK`）。
- debug 包视觉区分（只影响 debug 变体，两个 edition 都生效）：应用名带 `Debug` 后缀（`app/src/debug/res/values*/strings.xml`），桌面图标换成黑底版本（`app/src/debug/res/drawable/icon.webp` 覆盖 main 同名资源）。圆形图标（`android:roundIcon`）暂未适配 debug 版，仍指向正式版的紫色自适应图标，以后再做。

## 认证

- 密码登录（AES/CBC/PKCS5Padding 加密账号和密码，RSA/PKCS1 加密 AES 密钥）。
- **登录加密公钥会被服务端轮换**：`ar_ept` 是「用网页端 RSA 公钥加密的 AES 密钥」，服务端用对应私钥解密才能拿到账号密码。公钥一旦对不上，服务端解不出 AES 密钥，就不报「密码错误」，而是把请求当可疑流量返回 `ec=400 操作频繁，请稍后再试` —— 这个错报把「钥匙过期」伪装成「你操作太频繁」，极易误判。实测确认：用过期公钥发一个非法 JSON 的 body 也返回「操作频繁」；换成正确公钥则立刻变回正常业务响应（如 `ec=404 账号不正确`）。当前公钥与 IV 放在 `data/auth/AuthServicePublicKey.kt`（`AFDIAN_RSA_PUBLIC_KEY` / `AFDIAN_AES_IV`），**公钥指纹由 `AuthServicePublicKeyTest` 锁定**：服务端轮换后该测试立刻失败并提示如何更新，避免线上登录静默失效。更新方式：抓 `https://static.afdiancdn.com/js/app.<hash>.js` 搜 `BEGIN PUBLIC KEY`。
- 验证码登录（手机号 / 邮箱）：登录页用「密码登录 / 验证码登录」双 Tab 切换；发码接口 `/api/passport/send-ql-code` 在服务端要求人机校验时返回 `ec=201` + `captcha_img`，界面在验证码框下方就地展开图形验证码（点图片可换一张），填对后重新发码；发送成功后进入 60 秒重发倒计时。验证码登录走 `/api/passport/quick-login`。
- 网页登录：登录按钮下方的「使用网页登录」文字链接，打开 `WindowBottomSheet` 内嵌 `AndroidView(WebView)` 加载爱发电网页版登录页，登录成功后从 WebView 的 Cookie 里取 `auth_token` 交给 `AuthSession.loginWithToken` / `loginNewAccountWithToken`，后续落盘与切号复用既有流程。
  - **域名跟随优先线路**：地址由 `afdianWebUrl("/login")` 拼出，与网络层读同一个 `AfdianRouteState.preferred`。
  - **必须用 WebView 不能用 Custom Tabs**：浏览器在别的进程，Cookie 与本 App 的 `CookieManager` 隔离，拿不到凭据。
  - **凭据两条路径互为兜底**：Cookie 的 `auth_token`（js-cookie 写入，有效期 360 天）+ URL 的 `atn` 参数（网页端跨站跳转时用它携带凭据，落地页再写回 cookie，两者之间有时间差）；解析逻辑在 `WebLoginCookie.kt`，是纯函数，有单测覆盖。
  - **每次打开都清 WebView cookie**：否则残留的 `auth_token` 会让网页端判为「已登录」并跳过登录表单，用户没输密码就被登录成上次的账号。
  - `enableNestedScroll = false`：WebView 不上报 nested scroll，开着会让 Sheet 的下拉关闭手势行为不可预期。
  - 未登录登录页与「添加账号」弹层都提供该入口；后者先用 `onDismissFinished` 等弹层退场动画播完再开 Sheet，避免两层独立窗口同时存在。
- Token 使用 Android Keystore 的 AES-GCM 加密后保存。
- 登录失败显示错误文本，登录中禁用按钮。
- 免责声明弹窗：首次同意后保存本地状态，不同意则关闭任务。

## 首页（模块化）

首页采用可配置模块系统，用户可自由排序和开关模块。

已实现的 7 个模块：
1. **快捷方式** — 一行最多 4 个自定义入口，预设目录里的条目随便挑、随便换顺序。
2. **提现提醒** — 月末 7 天 + 每月 1 日 0:00–10:00（结算期）自动显示，点击跳转网页提现页。
   卡内 `now` 每分钟（对齐整分）推进一次，进程跨午夜不会停在旧月份；倒计时不足一天显示
   「n 小时 m 分」，不足一小时只显示 `m 分`，最后一分钟内为「不足一分钟」。
   每月**最后一天**文案开头额外加一句「距离结算还有 X」（结算时间点为下月 1 日 0:00，
   与提现倒计时共用 `formatCountdown`；其余日子 `settlementRemainingText` 为 null，整句不出现）。
   结算期金额口径见下文「创作者数据系统」。
3. **创作者数据** — 展示今日收入等数据卡片，支持长按进入整页编辑网格。
4. **近5天收入** — 最近 5 天每日收入明细表，样式照抄收入明细页，标题点击进入收入明细页。**默认关闭**。日期列显示 `月/日 + 星期`（`10/16 五`，`IncomeStatRecord.displayMonthDayWithWeekday`）：5 天窗口里年份没有信息量，省下的宽度留给数值列；收入明细页用短年份 `年/月/日`（`displayShortYearDate`），其中「按日」tab 同样附带星期（`26/10/16 五`，`displayShortYearDateWithWeekday`），按周/按月的日期是周期起始日、不加星期。
5. **我发电的** — 已发电创作者列表，横滑展示。
6. **收藏的创作者** — 收藏的创作者列表，横滑展示。
7. **浏览历史** — 最近浏览的创作者和动态，按时间分组。

模块配置：
- `HomeCustomizationSheet` 支持拖拽排序和显隐开关。
- 配置持久化到 DataStore。
- 首页底部有「自定义」按钮触发配置抽屉。
- **默认开关**写在 `HomeModule.defaultEnabled`，首帧初值取 `HomeModule.defaultEnabledSet`：
  枚举声明顺序即默认顺序，而 `defaultEnabled = false` 的模块（如近5天收入）不会在 DataStore
  读到真值前闪一帧出来。
- **默认顺序合并**用顶层纯函数 `mergeHomeModuleOrder`（`HomeModuleOrderTest` 覆盖）：
  老配置里缺失的模块**插到它在枚举声明顺序里的前驱之后**，而不是旧实现那样一律追加末尾
  ——否则新增模块升级后会莫名其妙跑到最后一位。
- `homeEnabledModules` 在已存配置非空时**不会**自动补入新模块，所以新增模块对老用户天然是关闭的。
- **跨天自动刷新 + 系统时间变化**（`data/content/DailyRefresh.kt` + `MainViewModel.startDailyRefreshTicker`）：
  应用一直开着时，每到本地自然日 0 点刷新一次主页，与具体日期无关。计时器放在会话级 ViewModel 里，
  切底栏、退到后台都不会被组合生命周期打断；每轮重新计算到下一个 0 点的时长，
  改系统时间 / 换时区 / 跨夏令时后自动纠正。延迟额外留 1 秒缓冲，保证触发时 `LocalDate.now()`
  已经翻篇；跨天瞬间若正好在刷新则跳过这一轮，避免两轮请求互相覆盖。
  ViewModel 还动态注册 `ACTION_TIME_CHANGED` / `ACTION_TIMEZONE_CHANGED`（这两个只能动态注册，
  清单声明收不到）：用户手动改时间或换时区后，先取消旧的跨天计时（`delay` 走单调时钟，
  不重排会按旧时刻到点），1.5 秒合并窗口后重排计时器并刷新一次主页；接收器在 `onCleared` 里注销。

快捷方式（`QuickEntries`）：
- 预设目录 `QuickEntryPreset`（`ui/navigation/QuickEntryPreset.kt`）是「入口 → 标题/图标/路由」的
  **唯一来源**，共 15 条分三组（常用 / 创作者 / 账号），「我的」页快捷入口区与首页该模块共用；
  新增入口只加一行，两处自动同步。
- 目录只收「点一下 = 压一个 `Route`」的入口；刷新、开外链这类特殊动作不进目录，
  在调用点直接用 `QuickEntry` 手写 `onClick`。
- 图标为 `null` 的条目（只有「每月汇总」）由 `resolveIcon()` 每次组合时算「上个月」数字图标——
  枚举构造参数只求值一次，写死会跨月不换。
- 存储：DataStore `home_quick_entries` 存逗号拼接的 `storageValue`；`SettingsStore` 只存字符串，
  过滤未知值 / 去重 / 截断到 `QUICK_ENTRY_SLOTS`(4) / 默认回落都在 `normalizeQuickEntries`
  （`QuickEntryPresetTest` 覆盖）。
- 面板 `QuickEntryPickerSheet` 挂在 `HomeScreen` 底部而非模块内部：模块滚出视口被 LazyColumn 回收时，
  弹层不能跟着消失。面板两段布局不同是刻意的：「已添加」只有 4 条，用可拖拽列表顺手把手柄放行尾；
  「可添加」15 条铺成列表太长，用四列网格。面板里混着不可拖动的小标题与网格，
  reorderable 的下标对不上「已添加」的下标，一律按 key 反查。

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
  该折算规则已抽成 `estimatedAfterFee`（`data/stats/WithdrawalSchedule.kt`），
  本月抽成后卡与结算期「到手」共用同一份实现。
- `dashboard.balance` 有两种形态（`parseDashboardBalance`，`DashboardBalanceTest` 覆盖）：
  金额文本 → 写「可提现（抽成前）」卡；每月 1 日 0:00–10:00 结算期间返回
  「结算中，请耐心等待」→ **不写卡**（保持 `--`，不伪造金额），同时把「正在结算」作为服务端信号
  `isBalanceSettling` 透给 UI。`balance_after_tax` 结算期同样落不可用。
- 结算期提现提醒金额口径（`settlementAmountTexts`）：「收入」直接取
  `dashboard.summary.unconfirmed_amount`（待结算的上月收入，**不回退**「上月收入」数据卡——
  那是账单聚合口径，结算期里通常还是空的）；「到手」= 该金额 × `CREATOR_SHARE_RATIO` 向下取整到分。
  `unconfirmed_amount` 为 null → 两处 `---`；为 0 → 照常 `0.00`（0 是有效值）。
- `WithdrawalSchedule.calculate(now, isBalanceSettling)`：结算期 = 本地窗口（1 日 0:00–10:00）
  ‖ 服务端结算中。服务端信号**只在 1 号当天认**——它没有时间戳、dashboard 只在刷新时更新，
  进程从 1 号挂到 5 号没刷新成功时不能把 5 号误判成结算期。服务端 10:00 后仍报结算中时，
  倒计时落到「已到提现日」（`isWithdrawalTimeReached`）、`shouldShowReminder` 仍为 true。
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
  快照还额外存 `unconfirmedAmount`（文本），它是结算期提现提醒的金额来源，冷启动首帧即可按上月口径显示；
  而结算期信号 `isBalanceSettling` **刻意不入缓存**——缓存 `true` 会让非结算日冷启动首帧误判结算期，
  结算日的冷启动由本地时钟兜住、金额由 `unconfirmedAmount` 提供。
- 近5天收入模块（首页）：复用收入明细页的 `IncomeStatRepository.fetch(IncomeStatType.Day, 1)`，
  只取第 1 页前 5 条（接口按日期倒序，前 5 条即最近 5 天），挂在 `MainViewModel.refresh()` 里
  与创作者数据并发加载、共享同一个 `isLoading` 窗口，因此下拉刷新会一并更新。
  失败时保留旧值并 `AppLog.w`，首次失败时列表为空 → 模块显示「暂无统计数据」而不是整页报错。
  **与 `CreatorStatsRepository` 重复请求同一个 `/api/my/stat?type=day`**：那份按日序列是
  私有聚合结构、不产出 `IncomeStatRecord`，为它开公开接口收益不抵，故各拉一次。
  表格组件（表头 / 数据行 / 列宽）已从 `IncomeDetailScreen` 提取到
  `ui/components/home/IncomeStatTable.kt`，两处共用；首页没有顶栏，表头放进 Card 内随内容滚动。
  日期格式由 `IncomeStatTableRow` 的 `compactDate` / `showWeekday` 控制：首页只开 `compactDate`（日期 `10/16 五`），
  `showWeekday` 默认 true，只有收入明细的按周/按月显式关掉（日期 `26/10/12`）。
  两套列宽常量都已按「日期带星期」加宽（`IncomeStatColumnWeights` 权重和 6.3 / 360dp 屏日期列 72.4dp，
  `CompactIncomeStatColumnWeights` 权重和 6.0 / 50.7dp），表头与数据行必须共用同一份。
- 按日图表（近30天收入 / 访问人数趋势）边界处理：`/api/my/stat` 对今天/昨天返回空值，`ChartModel.trimTrailingBlankDays` 只裁掉窗口末端（今天方向）连续的空/0 天，中间为 0 的天保留；全为 0/无数据时回退完整窗口。抓取时额外回溯 `CHART_TRAILING_TRIM_BUFFER_DAYS`(7) 天，保证裁剪后仍有 `CHART_DAILY_WINDOW_DAYS`(30) 个点。
- 图表指示器（marker）显示「日期 星期 • 值」（`9/6 五 • ¥12.34`）：星期来自 `ChartDataItem.categoryWeekdays`，与 `categories` 一一对应，由 `buildDailyIncomeChart` / `buildUvChart` 用 `weekdayLabelOf()` 填（固定中文表，不跟随系统 Locale）。横轴标签仍只放 `M/d`——10sp、一行最多 6 个，塞不下星期；月度图表的 `categoryWeekdays` 为空，指示器自动退回 `yy/M • 值`。该字段带默认值空集合，旧 HomeCache 快照反序列化不会失败。

### 顶栏账号切换器（HomeScreen）

- 首页 `TopAppBar` 的 **`navigationIcon`** 槽位放当前账号头像，点击弹出 `AccountSwitchPopup`
  （与「我的」页长按头像弹出的是同一个组件，含「添加 / 管理」底部操作）。
  注意 Miuix `TopAppBar` 起始侧叫 `navigationIcon`、结束侧叫 `actions`，**没有** `startAction` 参数；
  小标题居中排布，加 `navigationIcon` 不会把标题挤偏。
- 显示条件三者同时成立：不在「编辑创作者数据」整页编辑态、设置开关打开、已保存账号数 > 1（单账号没有可切换对象）。
- 开关是**设备级**设置 `SettingsStore.home_account_switcher`（默认关闭），入口在设置页「账号」分区；
  不进 `AccountKeys` 是因为它是纯 UI 偏好，切号后行为应保持一致。
- 头像优先取 `MainViewModel.profile.user.avatar`，profile 还没回来时回退到账号列表里当前账号的 `avatar`，
  避免冷启动空白帧。弹层锚点规则见 `AccountSwitchPopup`：头像与弹层必须同处一个 `Box`。
- 头像右下角有主色胶囊徽标（`ui/components/account/AccountSwitcherBadge.kt`）：横向三点，纯装饰不可点。
  胶囊尺寸写死 17×9dp、图标 13dp —— Miuix `More` 的画布是 24dp **正方形**，墨迹只占中间一条
  （viewport 1042.8 里宽 13.6%、高 83.3%），若让胶囊跟着正方形 wrapContent 会得到一条又高又胖的胶囊。
  图标因此用 `requiredSize` 画出去：普通 `size` 会被 9dp 的胶囊高度夹住，墨迹只剩 1.2dp 等于看不见；
  溢出的部分只是空白画布，被 `clip(Capsule())` 裁掉无视觉影响。

## 发现页

- 动态流（LazyColumn + PostCard），支持滚动加载。
- 顶部提供只读搜索入口；点击后在发现页内展开搜索层，采用 Miuix 官方 IconPage 的 `SearchBarFake` / `SearchPager` 状态切换模式。
- 搜索层当前提供真实输入框、取消操作与「搜索结果 / 最近搜索」占位区域，网络搜索和历史持久化后续接入。

## 动态详情页（PostDetailScreen）

- 路由携带列表页已有数据秒显，后台按 id 拉取全文覆盖刷新。
- 也可仅通过 `PostDetailById`（只有 id）打开，先显示加载动画再拉取。
- 创作者头部（可点进主页）+ 封面图 + 标题 + HTML 正文渲染（Jsoup 解析 + 原生 Compose 渲染）。
- 正文 `<img>` / `<figure>` 与 `pics` 图片均可点击，打开全屏图片查看器（见下节）。
- 付费动态：未解锁显示「发电解锁」按钮，跳转创作者主页。
- pics 数组图片：正文无 `<img>` 时逐张全宽展示。
- 上一篇 / 下一篇导航卡片。
- 文章末尾再次展示创作者头部。
- **评论区固定在页面最底部**（末尾创作者卡片之后），详见下节。
- 分享功能：调起系统分享面板，分享 `https://afdian.com/p/{post_id}`。

## 图片查看器（ImageViewer）

- 全局单例由 `LocalImageViewer` 提供（`MainScreen` 顶层），任意页面通过
  `LocalImageViewer.current.open(urls, index, origin)` 打开；`origin` 用缩略图的
  `boundsInRoot()` 采集，供展开/收回动画使用。
- 入口四处：动态卡片图片网格（`PostImageGrid`）、动态封面图（`PostCard` 与详情页，
  复用 `ViewerThumb` 的单图模式）、详情页正文 `<img>` / `<figure>`、详情页 `pics` 数组。
- 全屏黑底覆盖层，`HorizontalPager` + Telephoto `ZoomableAsyncImage`（`0.19.0`，Coil 3 包）：
  双指 / 双击缩放、拖动、多图左右翻页。
- 打开动画：容器尺寸与位置从缩略图 bounds 插值到全屏，圆角从 8dp 收到 0；
  关闭沿原路收回。没有使用 SharedElement，因为当前 nav 库不支持。
- 弹簧曲线：打开 / 关闭都用 `spring`（阻尼 0.82 / 0.84，刚度 280 / 340，约 0.6s / 0.5s），
  收尾时越过目标约 1% 再回来。过冲不进几何插值（容器尺寸一旦越过父约束会被裁回全屏，
  看起来像平移抖动），而是转成 `graphicsLayer` 上约 1% 的缩放：打开「涨过头」、关闭「缩过头」。
  `visibilityThreshold` 设为 0.001，避免动画停在 0.99 附近影响手势门槛。
- 关闭收尾淡出：`exitAlpha` 独立于 `progress` 驱动——当收回进度降到 22%（已经贴近缩略图）
  才启动一段 140ms 的淡出，淡出的同时下层页面（scrim 也接近透明）逐渐透出来，
  像「融回缩略图」而不是整块消失。不用进度直接映射透明度，是因为 spring 末段渐近逼近目标，
  最后 18% 的进度可能要走 300ms，会变成「停在缩略图附近慢慢变透明」。
  只在关闭方向生效，打开与下滑甩出不受影响。
- 关闭额外缩小：满宽单图的缩略图与全屏同宽，容器收回时图片内容尺寸几乎不变、只有位置在动，
  观感是「平移」而不是「缩回」。因此关闭时叠一个随进度递增的 `closeShrink`（最多 10%），
  配合末端淡出，看不出缩过头后的边缘。
- 打开不闪：查看器按整屏尺寸固定请求（否则容器展开动画每帧变尺寸，Coil 会反复取消/重启请求）；
  缩略图的 `MemoryCache.Key` 作为 `placeholderMemoryCacheKey`，先用低清图顶上，
  再由 Coil `crossfade(180)` 过渡到原图。
- 手势协调：放大状态（`zoomFraction >= 0.01`）锁住 Pager 与下滑关闭；
  未放大时向下拖跟手（X / Y 都跟随手指），图片同步缩小 / 淡出、背景变淡
  （按屏高 35% 归一化），超过 120dp 下滑关闭，否则弹回。
  向下越拖阻力越大（平方曲线，1/3 屏后迅速变沉，最低到 0.2 倍），往回拉不给阻力。
  一旦判定为下滑，立即把 Pager 的 `userScrollEnabled` 置 false，本次手势后续的横向移动只跟手、不再翻页。
- 下滑手势挂在 Pager 的父节点上（不能用一个全屏兄弟层去接，Compose 命中测试只给最上层节点，
  会把 Pager / Telephoto 的手势全部挡掉）。
- 默认 `ContentScale.FillWidth`：长图撑满屏宽、上下拖动查看，而不是 Fit 后两侧留黑边。
- 系统栏保持 App 原样：不隐藏、也不改图标颜色。
  scrim 与图片视口都铺满整屏，缩放 / 平移时图片可以一直拖到屏幕四边。
- 暂不做「静止时给图片上下留白」：Telephoto 的视口就是裁剪与平移边界，
  固定内缩会把放大后的平移一起裁住（图片永远到不了边框）；
  而「padding 跟着图片走」需要自绘一层 zoomable content，会牺牲长图默认撑满宽度的行为，
  收益不值当，先保持全屏。
- 顶部显示关闭按钮与 `n / 总数` 页码。
- **不做保存到相册**：部分创作者关闭了保存权限，客户端无法可靠判断。

## 动态评论区（只读 + 点赞）

**本轮范围**：只读评论 + 评论点赞。**不做**发评论 / 回复 / 删除。

- 接口（均为 Cookie 认证，匿名也能读；未登录时 `has_like` 为 null，按未赞处理）：
  - `GET /api/comment/get-list?post_id=&publish_sn=&type=old`（`FeedService.fetchCommentList`）
  - `GET /api/comment/get-sub-list?post_id=&root_comment_id=&publish_sn=`（`fetchSubComments`）
  - `POST /api/comment/like` / `/api/comment/unlike`，body `{comment_id}`（写请求不自动重试）
- **游标分页**：传上一页最旧一条的 `publish_sn`，服务端返回严格更早的数据（已实测无重复行）。
- **`hot_list` 前置合并**：照官方网页端做法把热门拼在 `list` 之前。
  **热门只影响排序，不打任何标签**（页面上看不出哪条是热门）。合并时按 id 去重。
  `list` 为空时强制 `hasMore = false`，避免游标不前进死循环。
  - ⚠️ **必须显式传 `hot=1` 才会下发 `hot_list`**，不传时服务端连这个 key 都不返回（实测）。
    而且**只有首屏（游标为空）才返回**，翻页时 `hot_list` 是空数组——所以 `FeedService`
    只在 `publishSn` 为空时拼 `hot=1`，后续页天然不会重复。
  - 热门评论本身是完整的 `PostComment`（带 `user` / `like_count` / `sub_comments`），
    直接复用 `CommentCard` 渲染，不需要区分数据结构。
  - 不传 `hot=1` 时 `hot_list` key 缺失，按空处理（`missingHotListKeyIsTolerated` 覆盖）。
- **「作者」标签**：评论者的 `user_id` 与动态作者一致时，在昵称旁打「作者」标签。
  判定函数 `isPostAuthor(authorId, comment)` 在数据层，两边 `user_id` 都非空且相等才算
  ——空串 == 空串 会把匿名评论全误判成作者。
- **懒加载**：评论区首次滚进视口才请求（进详情页不预取）；翻页复用 `LoadMoreEffect`。
- **楼中楼**：先渲染主列表内嵌的 `sub_comments`；`sub_comment_has_more == 1` 时点「展开更多回复」
  走 `get-sub-list` 补齐，游标取「已追加 + 内嵌」里最旧一条的 `publish_sn`。
  - **展开是单向的，没有「收起」**：收起一条已经读过的楼中楼没意义，还让人以为内容被藏起来了。
    `SubReplyState` 因此没有 `expanded` 字段，改用 `attempted`（成功请求过就置位）。
  - 那一行三态：没点过 = 「展开更多回复」；点过且还有更多 = 「加载更多回复」（翻页）；
    取完了整行不渲染。
  - ⚠️ 请求失败**不**置 `attempted`，否则这一行会连同重试入口一起消失，用户没法重试。
  - 展开动画：`AnimatedVisibility` + `expandVertically(Top) + fadeIn()`，
    外层再挂 `animateContentSize()`——顺带让「加载更多回复」追加的回复也带着卡片平滑长高。
    因为 visible 只会 false→true 一次，只需要写 `enter`。
- **点赞**：乐观更新 + 失败回滚；计数沿用 `displayLikeCount` 口径（与 `PostActionBar` 同一套：
  服务端 `like_count` 已含「我已赞」，只有本地态与服务端 `has_like` 不一致才 ±1）。
- 状态挂在 `PostDetailViewModel`（已按 post id 复用），解析是顶层 `internal parseCommentPage`
  （`CommentParserTest` 覆盖）。**解析器不打日志**——`AppLog` 最终落到 `android.util.Log`，
  单测未 mock 会抛异常，与 `MessageParser` 保持同一约定；耗时/条数日志由 `FeedService` 打。
- 评论正文按**纯文本**渲染（服务端不下发 HTML），不走 `HtmlContent`。
- **楼中楼「回复某人」提示**：服务端在 `sub_comments` 里下发 `reply_user`（被回复者，根评论没有该
  字段，模型回落成空 `AfadianUser`）。页面在正文前用 **inlineContent** 插入
  `MiuixIcons.Demibold.Undo` 旋转 180° 的图标 + 被回复者昵称（次要色）+ 正文。
  - 用 inlineContent 而不是 `Row + Text`：前缀要和正文**一起换行**，Row 做不到。
  - 本项目 Compose 1.12 的写法：`InlineTextContent(placeholder = Placeholder(w, h, align)) { Icon(...) }`，
    `appendInlineContent` 从 `androidx.compose.foundation.text` 导入，
    `Placeholder` / `PlaceholderVerticalAlign` 从 `androidx.compose.ui.text` 导入，
    且 **`Placeholder` 的宽高是 `TextUnit`(sp) 不是 Dp**。
  - ⚠️ `PostComment.reply_user` 必须写成**非空 + 默认值**（`val reply_user: AfadianUser = AfadianUser()`）：
    声明成可空时 Kotlin 序列化插件会报 `Unresolved reference 'AfudianUser'`，类型解析不过。
  - 判定「要不要标回复对象」放在数据层的 `replyTargetName(root, sub)`：服务端没下发 `reply_user`、
    昵称为空、或**回的是楼主**这三种都不标（回楼主时楼中楼本来就挂在母评论下，再标一次是冗余）。
    比对的是 `root.user.user_id` 而不是顶层 `user_id`——顶层那个在部分响应里可能缺失。

### 评论按钮 → 跳详情并滚动到评论区

- `PostActionBar` 保留 `onCommentClick`（默认 null = 评论列纯展示不可点）；
  `PostCard` 不再单独收评论回调：它的 `onClick` 改成 `(openComments: Boolean) -> Unit`，
  点卡片传 false、点评论列传 true，两个动作共用**一条**导航回调。
- 调用点因此只写一个 `onClick = { toComments -> onOpenPost(post, toComments) }`，
  发现页、收藏页动态 tab、创作者主页动态分区都一样，不需要各自再写评论动作。
- 路由 `PostDetail` / `PostDetailById` 新增 `scrollToComments: Boolean = false`；标记进路由而非
  全局变量，进程重建 / 返回栈重放后依然有效。
- `onOpenPost` 回调签名统一改成 `(RecommendedPost, Boolean) -> Unit`（第二参 = toComments）。
- 已在详情页内点评论按钮 → 只滚动，不重复 push。
- ⚠️ 滚动锚点 `commentsAnchorIndex`（`PostDetailScreen`）是**手算**的，必须与 LazyColumn 的
  item 顺序严格对齐；改动正文区结构时要同步改，否则会滚到上一篇/下一篇或末尾卡片上。
- 定位必须等 `PostDetailViewModel.detailSettled`（成功/失败都置位）：从列表页进来时页面先用
  路由里的预览 post 渲染，全文到位后 `cover / title / pics / 上一篇` 这些条件 item 会增减，
  提前按预览结构算出来的锚点会停在正文中间（正文图片多时最明显）。
- 落位策略：**近距离（≤2 个 item）用 `animateScrollToItem`，远距离直接 `scrollToItem`**。
  `animateScrollToItem` 在可变高度列表里长距离滚动会逐帧布局途中的高卡片（楼中楼盖得多时尤甚），
  掉帧明显；动画途中结构变化还会偏。
- 落位后用第一可见项的 key（`COMMENTS_HEADER_KEY`）校验一次；没贴顶就重试（最多 4 轮），
  因为「评论区比一屏短」时补位空白要到下一帧才长出来，需要再跳一次才能贴顶。
- **评论区最短占一屏**：末尾有一个 `comments_fill` 空白项，高度按「标题 → 最后一条内容」
  实测值补到一屏（`commentsFillPx`），内容够长时自动归零。否则评论区太短时列表滚不到
  「标题贴顶」的位置，跳转会停在半屏，看起来像动画没走完。
  - 测量只在「标题 + 最后一条内容同时可见」时进行（两者位置差就是评论区高度，与滚动无关）；
    标题已贴顶且末尾在屏幕外 → 直接归零；其他位置量不出来就保持原值，避免抖动。
  - 末尾空白项恒存在（高度可为 0），所以「最后一条真实内容」始终是 `totalItemsCount - 2`。

## 动态互动（PostInteractionController）

- 全局单例控制器，管理「已点赞 / 已收藏」的动态 ID 集合。
- 乐观更新：点击瞬间先改本地状态，失败时回滚。
- 跨页面同步：发现页、收藏页、详情页共享同一份状态。
- `PostActionBar` 操作栏：三列均分布局（点赞 / 评论 / 收藏）。评论列接 `onCommentClick`：
  不传即纯展示不可点；列表卡片由 `PostCard` 从卡片点击回调派生（落点 = 评论区），
  详情页直接传「滚到评论区」（见下节）。
- 按钮带轻触觉反馈。

## 统一链接跳转

- 所有用户可点击网页链接统一经过 `ui/navigation/AppLinkNavigator.kt`，
  App 内链接规则集中在 `ui/navigation/AppLinkRegistry.kt`：
  已实现的爱发电动态（`/p/{id}`、`/album/{albumId}/{postId}`）和创作者主页
  （`/a/{slug}`、`/@{slug}`）进入 App 内页面；发现页、收藏页、商品管理页及商品详情也支持原生跳转。
  album 链接目前暂时打开最后一段指定的动态，待动态合集页面实现后再调整。
  创作者后台链接也会分别进入管理、收到发电、每月汇总、我发电的、订单、优惠码和开发者页面；
  官方客服消息链接进入消息页的客服会话。
- 未实现的站内页面和其他合法网页统一使用 Chrome Custom Tabs；设备没有可用 Custom Tabs 服务时回退系统浏览器。
- 动态正文、私信、订单/发货通知、商品方案、提现提醒、开源许可等入口均使用同一套策略。
- 正文里的裸链接识别集中在 `ui/components/basic/LinkifiedText.kt`：匹配交给系统 `Linkify`
  （与 `autoLink="web"` 同一套规则，支持 `www.` 与裸域名），再把 Linkify 生成的 `URLSpan`
  换成应用内的 `ClickableSpan`，点击统一走 `AppLinkNavigator`。Linkify 按 IRI 规则会把紧贴
  链接的中文一起算进 URL（如「文档：example.com」），因此匹配后还要用 `trimCjkEdges`
  收一次首尾边界。
- 设置 → 通用 → 「链接打开方式」提供「优先使用官方客户端」开关，默认关闭即使用 CCT；开启后按官方
  `afd://app/openapp` 协议（`type=1` 网页参数）尝试拉起 `net.afdian.afdian`，
  未安装或拉起失败自动回退 CCT。
- **App 自己拼的站内网页地址统一走 `data/network/AfdianRoute.kt` 的 `afdianWebUrl(path)`**，
  不要各自写死域名：它按「优先线路」取 host，保证接口打哪条线路、页面就开哪条线路。
  目前用于购买页、商品编辑页、提现页、动态分享与优惠码分享；相对图片路径由
  `normalizeImageUrl(raw, host = 当前线路)` 补全。
  服务端下发的链接（消息卡片、正文富文本）不经过它，host 由服务端决定。

## 消息

- 消息列表页（含未读数 Badge）和会话详情页（聊天记录、FAQ、发送消息）。

### 会话列表分页、去重与列表 key（MessagesViewModel / MessageModels）

- `GET /api/message/dialogs` 是**页码分页**（`page` / `unread`），服务端按最新消息时间倒序返回。
  翻页期间来了新消息，尾部会话会下沉到下一页，所以 `loadMore` 拿到的页**必然可能**与上一页重叠，
  不能直接 `+`。`items(key = { it.id })`（id = `user.user_id`）撞上重复 key 会直接抛
  "Key ... was already used" 闪退，这就是消息页偶发闪退的根因。
- 列表写入统一走 `mergeDialogs(existing, incoming)`（`data/content/MessageModels.kt`，顶层 `internal` 纯函数，
  `MessageDialogMergeTest` 覆盖）：**丢弃 `user.user_id` 为空/空白**的行（跳会话页与 `markConversationRead`
  都只认它，这种行既打不开也标不了已读），同一 id **保留先出现**的一条（existing 是更新的快照，旧页不得覆盖）。
  输出恒满足「id 唯一且非空」。
- 因此 UI 侧直接 `key = { it.id }` 即可；`filteredDialogs` 的 filter 与页面都**不要**再 `distinctBy`
  （filter 保序保重数，重复机制只会掩盖数据层缺陷）。
- 并发用批次号 `listEpoch`（同 `ApiProbeViewModel.runEpoch`）：`refresh()` 自增批次并作废在飞的 `loadMore`，
  旧页返回时**整页丢弃**（不推进 `currentPage`、不改 `hasMore`）。`refresh()` 不因 `_isLoadingMore` 而放弃
  ——下拉刷新是用户明确意图，等翻页跑完再执行会让人以为刷新失效。
- `currentPage` 只在整页**成功提交后**推进（原来用 `++` 且 refresh 在请求前就重置，请求失败/过期都会让页码漂移，
  下次又请求同一页产生重复）。
- 合并真丢了条数才打 W（`page` / `got` / `blankId` / `kept`，不含 `user_id`）；末尾「共 N 条对话」是去重后的真实条数。
- `markConversationRead` 的 `map` 是 1:1 变换（不增删、不调序），不破坏该不变式，这条写路径不走 `mergeDialogs`。

### 会话特殊消息卡片（MessageCards.kt）

- `resolveTextContent`（MessageParser）先按固定文案把文本消息识别成系统通知，命中才出卡片，未命中才走 HTML 清洗后的普通文本气泡；卡片按 `MessageContent` 的 sealed 子类在 `MessageContentBubble` 里分发。
- 已实现：电圈邀请（对象含 `group`）、发电订单（订单对象）、兑换码（`type=5`）、发货通知（前缀「你购买的商品已发货」）、投诉通知（含「该笔订单会暂时冻结」）、补货通知（含「库存仅剩」）。
- 补货通知 `MessageContent.Restock` / `RestockNotice`（planName / skuName / stock / detailUrl）：模板为「你的发电方案「X」的型号「Y」库存仅剩 N，点此前往补货：…」，方案没有型号时缺省「型号「…」」一段；卡片只做提醒，不提供跳转补货入口。

### 全部标为已读（MessagesScreen）

- 爱发电**没有**标记已读接口：网页端只有 `dialogs`/`messages`/`send`/`can-send`/`delete-dialog` 五个消息接口，
  无 `read` 类接口，网页版自己也没有一键已读。实现方式是**逐个读取未读会话**，借服务端「加载历史消息即清未读」的行为顺带清零。
- 实测结论（2026-10，Cookie 认证验证）：
  - `GET /api/message/messages?user_id=X&type=old` **有效**；
  - `type=new&message_id=<latest_msg_id>`（返回空列表，60 字节）**无效**，未读数不变 —— 不要试图用它省流量；
  - 执行后 `/api/my/check` 的 `unread_count.message` 同步归零。
- 流程：先用 `GET /api/message/dialogs?unread=1` 分页取权威未读清单（**不能用 `/api/my/check` 判断**，它有缓存延迟，
  实测出现过 check 显示 0 而 dialogs 实际有 2 条未读）→ 以 `MARK_ALL_CONCURRENCY=3` 并发逐个读取 → 每个成功后立刻
  复用 `MessagesViewModel.markConversationRead` 本地清 badge（乐观更新）→ 结束后 `refreshUnreadCount()` 以服务端状态为准。
- 响应体对标记已读无用，`MessageService.markDialogRead` 只用正则抠 `ec` 校验后即丢弃，不构建 JSON DOM。
- UI：三点菜单**第一组**「全部标为已读」（`MiuixIcons.Ok`，无徽标），点击先弹 `AppDialog` 确认，
  说明文字如实告知「会依次读取每个未读会话」并写明并发上限；执行中确认按钮置灰。结果用 Toast 反馈（全成功 / 部分失败报数）。

### 会话置顶与长按菜单（MessagesScreen / ConversationActionMenu）

- 爱发电**没有**置顶接口，置顶只存本地：`SettingsStore.msg_pinned_dialog_cards`（JSON 数组，元素是
  `PinnedDialogCard` 卡片快照）。换设备/清数据会丢，也不按账号隔离。
- **存整卡而不是只存 id**：`/api/message/dialogs` 是页码分页，置顶的老会话可能落在后面的分页里，
  只存 id 会出现「有置顶配置但列表里没有入口」。快照在置顶瞬间生成（`MessageDialog.toPinnedCard()`）。
- 合并规则（`mergePinnedIntoDialogs`，`MessageModels.kt` 顶层 `internal` 纯函数，`ConversationPinTest` 覆盖）：
  置顶整块在最前，顺序 = 置顶顺序（新置顶的在前）；服务端已加载到该会话就用**服务端那一行**，
  没加载到才用快照补位。`_dialogs` 本身必须保持服务端顺序（`mergeDialogs` 的按页追加、
  `markConversationRead` 的 map 1:1 都依赖它），合并只发生在**展示流** `filteredDialogs` 里。
- 过滤在合并**之后**做：置顶会话若已加载到、只是被筛掉（如「未读」筛选下已读），不会再拿快照补一张重复卡。
- 新鲜度自愈：服务端按最新消息时间倒序返回，置顶会话一旦有新消息就会跳到第一页，下次 `refresh`/`loadMore`
  时 `syncPinnedCardsFromServer` 把快照刷成服务端值（无变化不写盘）。长期没消息的会话快照本来就准，
  唯一可能过期的是「在别的设备读过」的未读数（本地 `markConversationRead` 也会清快照里的未读）。
- `mergePinnedIntoDialogs` 无置顶时原样返回同一实例；同一会话不会既来自快照又来自服务端（LazyColumn key 不变式）。
- 长按卡片（`combinedClickable`）弹出操作菜单：`ConversationActionMenu` 冻结「整条会话 / 是否置顶 / 卡片窗口坐标」，
  菜单项按快照组装（「置顶」或「取消置顶」+「标为已读」）。「标为已读」**始终在列**，会话已读时置灰不可点
  （`InlineActionPopup(isEnabled = ...)`，禁用色 `disabledOnSecondaryVariant`，与 DropdownImpl 禁用项一致）；
  `dismiss` 不清 target，否则退场动画会闪空。
- 菜单视觉复用会话页气泡菜单的骨架：`ui/components/basic/InlinePopup.kt`（`InlinePopupLayout` / `InlineActionPopup` /
  `InlineActionRow` / `HolePunchDimScrim` / `calculateMenuOffset`）。`MessageBubbleActionPopup` 也改用它，
  会话页独有导航滑动闸门 `NavSwipeGate` 刻意留在气泡菜单里，不进公共骨架。
- 「标为已读」单条链路同「全部标为已读」：本地先清 badge（乐观），再调 `MessageService.markDialogRead` 触发服务端清未读，
  最后 `refreshUnreadCount()`；失败不回滚本地 badge，等下次列表刷新纠正。
- 已知取舍：重排会让 LazyColumn 按 index 保持视口，置顶瞬间其它卡片下移一格；被快照补位的老会话
  在点进去之前不知道有没有新消息（分页接口拿不到单条会话）。

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

- TabRow 三个分区：创作者 / 动态 / 众筹，切 tab 用「售卖商品 / 我的订单」同款
  `AnimatedContent` 转场（淡入淡出 + 1/8 横向位移）+ 点击轻震动。
- 创作者 tab 用 `CreatorListItem`，副标题回退链：subtitle → creator.doing。
- 动态 tab 用 `PostCard`，众筹 tab 用 `CrowdfundingCard`。
- ViewModel 每个 tab 各存一份 `FavoritesTabState` 快照（`_tabs: Map<Int, FavoritesTabState>`），
  页面按「转场中的那个 tab 下标」取自己那一份渲染，转场期间新旧两页各读各的。
  配套约定：
  - `FavoritesTabState.loading` 标志首屏是否在加载中。**不能用「`_tabs` 里有没有这个 key」
    来判断加载态**——没有条目既可能是「还没开始拉」也可能是「拉完了但真的没有收藏」，
    少了这个标志会在请求发出前先把空态闪一帧。
  - `FavoritesTabState.isEmpty` 是页面判空与 ViewModel 失败分支共用的判空条件。
  - `loadMore` 写回时读 `currentTab(kind)` 而不是发起请求时的快照，否则请求在飞期间
    首屏刷新过会被旧快照整段覆盖回去。
  - 页面侧每个 tab 一份独立 `LazyListState`：转场期间新旧两页会同时组合，
    共用一个会让两页互相覆盖 `scrollPosition` / remeasurement。

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
- 添加卡片 Sheet 右上角 `endAction` 是「连续放置」开关（`AppChip`，无尾部图标）：关闭时添加完立即收起弹层（管理页与首页行为一致），开启时弹层保持打开可连续添加；开关为临时 UI 态，每次打开重置为关，不落 DataStore。

## 赞助者管理页（SponsorManageScreen）

- 创作者后台功能（对应网页 `/dashboard/sponsors`），入口在「我的」页创作者分组（原管理页「发电者管理」占位 tab 已删除并迁至此）。
- 路由 `Route.SponsorManage`（无参）；列表数据 `POST /api/my/who-sponsored-me`（ManagementService.fetchSponsors，页码分页，每页 20）。
- 排序：列表顶部排序下拉 Chip（AppChip + WindowListPopup，样式照搬收到发电页），`SponsorManageSort` 共 7 档 = 默认排序（sort_field/sort_value 空串，可取消排序）+ 三字段 × 升降序成对（`all_sum_amount` / `last_pay_time` / `create_time` × asc/desc，字段名抓包自网页表头）；切换回第 1 页重载（加载中不响应），默认档 Chip 不高亮。
- 筛选：筛选 Chip（与排序 Chip 并排）弹**双层 WindowListPopup 级联**（对齐网页「筛选档位」dialog，载体为 popup）：第一层条件构建主页（锚 Chip）= 条件行（type 段/操作段/value 段三段 AppChip + 删除）+「新增筛选项」多条件（最多 4 行，AND 关系）+「清除/确定」；点段时从**该段自身**叠弹第二层选择视图（段级 popup 组合在主页内容的段 Box 内，主页/子层均为全屏透明 Dialog 坐标系对齐，`enableWindowDim=false`），选完只关第二层，点外先关第二层再关主页。4 种 type（正在赞助 `sponsoring`/赞助过 `sponsored`/累计金额 `all_sum_amount`/赞助月份 `month`）、方案型动作 包含/不包含（`=`/`!=`）、金额型动作 `>=` `<=` `≠(!=)` `>` `=` `<`（展示用 ≥/≤/≠，抓包验证）、月份型固定 `=`；value 按类型分流：方案列表（`GET /api/creator/all-plans`，list+sale_list 去重、排除 0 元）/ 金额数字输入 / **月份多选**（自定义 4 位年份 + 1–12 月格跨年累加，YYYYMM 降序，提交时 value=`"202610,202609"` 逗号串 + `month_value` 名值数组，抓包协议）。草稿与已应用分离（打开主页时重置），「确定」只提交完整行（未选方案/空数值 = 该行不生效，等价官方「全部」），Chip 摘要：无条件「筛选档位」/ 单条条件摘要 / 多条「筛选 (N)」；切换回第 1 页重载（加载中不响应）。
- 展示：「共 N 名赞助者」统计 + 卡片列表（头像 / 昵称 / 当前方案或「无」/ 累计金额 / 最近赞助相对时间），`LoadMoreEffect` 滚动加载。
- 行点击弹 `WindowBottomSheet` 详情：头像昵称、当前方案、累计金额 DataCard、初次/最近赞助绝对时间、赞助过的方案列表、「查看主页」跳 `Route.CreatorDetail`。
- 详情 Sheet 末尾（「查看主页」之后）支持「加入屏蔽列表 / 加入拒绝列表」：复用私信会话页的共享组件 `account/BlacklistActions.kt`（按钮区 + 破坏性确认弹窗 + 名单效果说明 + 拒绝类型的「@Lain音酱」客服链接），提交走 `SponsorManageViewModel.blackUser` → `AfadianContentService.black`（`POST /api/user/black`）；成功后 Toast 服务端文案并收起 Sheet，**不删除列表条目、不重载列表**（拉黑不改变历史赞助记录），名单在「屏蔽与拒绝名单」页管理。客服账号（`AFDIAN_CUSTOMER_SERVICE_ID`）不显示该操作区。
- 模型：`SponsorManageItem` / `SponsorPlanBrief` / `SponsorManagePage` / `SponsorManageSort` / `SponsorFilterPlan` / `SponsorFilterType` / `SponsorFilterAction` / `SponsorFilterRow` / `monthDisplayName()`（ContentModels.kt）；解析/构建为顶层 `internal parseSponsorManagePage` / `parseFilterPlans` / `buildSponsorFilterArray`（`SponsorManageParserTest` / `SponsorFilterPlanParserTest` / `SponsorFilterBuildTest` 覆盖），plan 的巨型 HTML desc 不映射。
- 不含：官方面板的「自选金额」快捷项、条件行数上限 4 以上的扩展、导出、群发私信（群发私信按需求永久排除）。

## 收到发电（ReceivedSponsorsScreen）

- 独立分页订单列表，支持下拉刷新、滚动加载、错误重试和本地订单搜索。
- 筛选：兑换码、有留言、订单状态（所有 / 成功 / 冻结 / 退款）、档位、发货状态（不筛选 / 含收件信息 / 已发货 / 待发货）、起止时间；排序支持时间和金额。
- 档位筛选：放在「筛选与排序」Sheet 内，使用 `WindowDropdownPreference`（窗口级下拉，适配 Sheet 的 Dialog 宿主），选项为「全部档位 / 自选金额 / all-plans 方案名」；全部档位不发送 `plan_id`，自选金额发送 `plan_id=-1`，具体方案发送对应 `plan_id`。方案列表来自 `GET /api/creator/all-plans`（复用 `ManagementService.fetchFilterPlans`），加载失败时仍保留前两项。
- 订单状态请求参数：成功 `status=2`、冻结 `status=5`、退款 `status=7`，所有时不发送；行内标签取响应新增的 `settlement_status.state`（`normal` / `freeze` / `refund` / `refund_and_freeze`），退款优先展示 `settlement_status.refund_amount` 为「已退款：￥金额」。列表卡片只在冻结 / 退款时显示状态，详情 Sheet 始终显示。
- 发货状态请求参数：含收件信息 `status=10`（此时不带订单状态，二者互斥）、已发货 `express_status=9`、待发货 `express_status=8`；不筛选时不发送。
- 时间请求参数使用 `begin_time` / `end_time` Unix 秒，任一边界可为空；结束上界取选中分钟的第 59 秒（接口按半开区间处理）。
- 时间选择器复用通用组件 `DateTimeField`（日期 / 时间双滚轮，年份范围 2018–2100）；`ReceivedTimeRange` 以毫秒存储，未设置的边界在界面上回落到「今天」，只有拨动滚轮才会写入，「清除」按钮恢复为不限。
- 卡片上半部分进入私信，下半部分打开订单详情 Sheet；支持展示捆绑包优惠和兑换码提示。
- 订单详情 Sheet 末尾支持「加入屏蔽列表 / 加入拒绝列表」（与赞助者管理详情 Sheet 共用 `account/BlacklistActions.kt`）：提交走 `ReceivedSponsorsViewModel.blackUser` → `POST /api/user/black`，成功后 Toast 服务端文案并收起 Sheet，订单列表保持不变；客服账号不显示该操作区。
- 日期分组：仅当排序为「新订单在前」且没有任何筛选（兑换码 / 有留言 / 订单状态 / 档位 / 发货状态 / 时间范围）与搜索时，列表才插入「今天 / 昨天 / 更早」标题；某段没有订单时不显示该段标题，只要存在筛选或搜索就整体平铺不做分组。

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

## 检查更新（UpdateCenter）

- 状态集中在进程级单例 `ui/viewmodel/UpdateCenter.kt`：`state`（检查结果）、`downloadState`（下载 / 安装）、
  `autoPrompt`（一次性信号）。`UpdateViewModel` 只是薄委托，所以 MainActivity 根部（自动检查）
  与设置页（手动检查）看到的是同一份结果。
  - **`autoPrompt` 只由冷启动自动检查置位**：状态合并后若还按「Available 就弹窗」，在设置页
    手动检查也会把根部弹窗拉起来。
  - 手动检查会取消正在跑的自动检查，保证用户一定看到「正在检查…」并拿到新结果。
- 两条通道，入口在「设置 → 关于」的更新通道（设备级，键 `update_channel`，默认正式版）：
  - **正式版**：`GET /repos/hrsthrt74/afdian-compose/releases/latest`，按 `isNewerVersion` 比 tag；
    附件直链在应用内下载（`UpdateDownloader`）后交给系统安装器。
  - **Actions 构建**：`GET .../actions/workflows/build.yml/runs?branch=master&status=success&per_page=1`。
    artifact 下载需要登录 GitHub，App 拿不到，因此只提供「查看构建」跳 `html_url`，
    不在应用内下载。是否更新由 `isActionsRunNewer` 判定：同 commit 不提示；本地是 Actions 包
    （`BUILD_NUMBER > 0`）严格比 run_number；本地是正式版则比运行时间与 `BUILD_TIMESTAMP_MS`。
- 节流（`UpdatePolicy.shouldAutoCheck`）：**成功才写 24 小时窗口**（`update_last_check_at`），
  失败写 `update_last_check_failure_at` 且只退避 1 小时。旧实现发请求前就写时间戳，
  一次弱网失败会让功能静默一整天。
- 忽略：双键分开记 —— 正式版存 tag（`update_ignored_version`），Actions 存构建号
  （`update_ignored_actions_run`）。共用一个键时切通道会互相覆盖。
- 下载：`UpdateDownloader` 流式写 `files/updates/`，先 `.part` 再原子改名，失败 / 取消自动清理；
  每次下载前清掉目录里的旧包。FileProvider 白名单只暴露 `logs/` 与 `updates/` 两个子目录。
- 安装（`UpdateInstaller`）：先 `getPackageArchiveInfo` 校验包名与 versionCode
  （防装错 edition、防降级），再查 `canRequestPackageInstalls()`，没权限就带去「安装未知应用」页；
  最后 `ACTION_VIEW` + `application/vnd.android.package-archive` 拉起系统安装器。
- 关闭弹窗 = 放弃本次下载（没有通知栏与前台服务），`consume()` 会删掉已下载 / 未完成的文件。
  下载中与待安装两档 `dismissOnConfirm = false`，弹窗不会因为点按钮而消失。
- `build.yml` 的文件名是 App 侧常量（`GithubReleaseService.WORKFLOW_FILE`），改名会让
  `run_number` 归零，必须同步改代码。

## 设置页（SettingsScreen）

设置采用「总览页 + 分类页」结构（通用 / AI 助手 / 外观 / 关于四个分类）：总览页只保留账号与分类入口，具体偏好项按分类拆到独立二级页。
原「更多」分类已并入「通用」，不再有独立路由。
所有页面共用 `SettingsPageScaffold`（大标题顶栏、模糊、双击回顶、右侧滚动条、边界触觉）。

### 总览页

- 路由 `Route.Settings`，两个入口共用：已登录「我的」页快捷入口（`MainScreen`），
  未登录登录页顶栏右侧的「设置」按钮（`MainActivity` 未登录栈）。
- 「账号」分区整块保持改造前的实现：账号列表卡片（点非当前账号行即切号，当前账号行尾是退出登录、
  其他账号行尾是删除）+「添加账号」行；下方**独立卡片**是「首页账号切换器」开关（设备级、默认关闭），
  仅在已保存账号数 > 1 时渲染 —— 单账号没有可切换对象，露出来只会让人困惑。
  未登录栈里 `LocalAccountSwitcher` 为 null，整块不渲染。
- 分类卡：「通用 / AI 助手 / 外观」一张 `Card`，「关于」单独一张卡（自身不加上边距，
  与上一张的分隔靠上一张卡的 12dp 下边距）。行是 `ArrowPreference`，左侧是 32dp 圆角方块 +
  24dp 单色图标（`Tune` 蓝 / AI 星芒绿 / `Theme` 紫 / `Info` 灰）。
  点击 push 对应分类页，入栈前做 `none { it == route }` 去重。
- 图标块是共用组件 `CategoryIconTile`（`ui/components/basic/`）：设置总览分类卡与文档页文档卡
  共用一份实现，Monet 适配只在组件里做——
  - **主题 = MIUIX** 时，用各入口自己的固定自定义色，图标为白色；
  - **主题 = Monet** 时，方块统一取 `MiuixTheme.colorScheme.primary`，图标改用 `onPrimary`，
    避免固定彩色和动态色板打架。是否 Monet 统一读 `MiuixTheme.isDynamicColor`。
- 版本号与构建类型页脚已移到「关于」页底部。

### 通用（`Route.SettingsGeneral`）

- 分组「通用」：打开 App 后默认进入（启动页）、记录历史。
- 分组「网络」：「优先线路」与「链接打开方式」同组。
  - ifdian.net（默认）/ afdian.com / www.afdian.com 三档，
    对应三条固定尝试顺序 —— ifdian 档：ifdian → afdian → www；afdian 档：afdian → ifdian → www；
    www 档：www → ifdian → afdian（即 www 只在它自己当主线路时排第一，其余两档都是最后备选）。
  - 副标题提示「所选项不可用时会回退到其他线路」。设备级设置存 `network_route`（存 `storageValue` 而非 ordinal），
    由 `AfdianApplication` 订阅后写入 `AfdianRouteState`，`AfdianHttpClient` 每次请求现读，切换后下一次请求即生效。
    生效范围：接口请求、App 自己构造的外跳网页（购买 / 商品编辑 / 提现）、分享链接（动态 / 优惠码）与
    图片相对路径补全都跟随该线路；**服务端下发**的链接（消息卡片、富文本正文）保持原样，不改写其 host。
- 链接打开方式：系统浏览器（Custom Tabs，默认）/ 优先官方客户端（失败仍回退 Custom Tabs）。
- 「爱发电文档」链接入口已迁到「我的 → 文档」聚合页（见「文档页」一节），本页不再有站外链接杂项。
  guide.afdian.com **刻意不跟「优先线路」走**的约束不变：另外两个域名（ifdian.net / www.afdian.com）下
  并没有 guide 子域，跟着线路改写 host 会直接 404，所以写死域名、不用 `afdianWebUrl()`。
- 分组「管理」：本地数据与排查工具 `存储空间管理`、`日志管理`、`接口调试`
  （原「清除浏览历史」入口已移除，清理统一收敛到存储页）。
- 分组「重置」：两个布局重置入口（管理页卡片、首页创作者数据卡片），破坏性确认弹窗沿用 `AppDialog`。

### 外观（`Route.SettingsAppearance`）

- 分组「外观」：底栏样式、配色模式、主题、主题色、模糊效果、渐进模糊。
- 底栏样式（`ui/screens/main/account/BottomBarStyleSelector.kt`）：常规 / 悬浮两档，
  不再是下拉，而是**两张并排的实时预览卡**——卡片里画的是真 navbar（`NavigationBar` /
  `FloatingNavigationBar`，与主界面同一份 `mainTabs`）。缩小靠**缩 `LocalDensity`**：
  内容以为自己在 360dp 宽的屏幕上排版，物理上只占卡片宽度，于是 `fillMaxWidth` 仍铺满、
  胶囊仍居中（别用 `graphicsLayer` 缩画布：排版宽度会宽出卡片一截，要靠裁剪收边，
  对齐一错整条底栏就偏到卡片左边）。卡片上盖一层透明命中层接住点击，所以预览本身不可交互。
  背景是固定渐变（上 `#4170FF` → 下 `#5ABAFE`，不跟主题色走）；圆角与描边都用 miuix 的
  squircle（`squircleClip` / `squircleBorder`，不用 `RoundedCornerShape`），选中档外面套一圈
  主题色描边，与卡片之间留 6dp 空隙。
  - 悬浮档的底部留白被裁到恒定 26dp：miuix 内部写死了「26dp + 系统手势条高度」，
    放开高度量出来后用 `clipToBounds` 裁掉多余部分，避免预览随设备跳动。
  - 档位本身由 `LocalBottomBarStyle`（`ui/theme/BottomBarStyle.kt`，MainActivity 根组合收集）下发，
    主界面底栏与预览卡共用。**不要**改读 `SettingsViewModel.bottomBarStyle`：VM 进页面才创建，
    初值恒为默认档，会先闪一下「常规」。
- 配色模式选择：跟随系统 / 浅色 / 深色，只决定明暗，与「主题」正交。
- 主题选择：**MIUIX（默认）/ Monet**，对应 `monetEnabled`（DataStore 键沿用 `monet_enabled`）。
  - MIUIX：Miuix 静态色板，主色由主题色决定；跟随系统/浅色/深色分别映射到
    `ColorSchemeMode.System`/`Light`/`Dark`；
  - Monet：色板改由 Miuix 用种子色生成，同样三档明暗，分别映射到
    `MonetSystem`/`MonetLight`/`MonetDark`。
- 主题色：**两种主题共用同一个值**（`theme_color`，默认 MIUIX 蓝），
  MIUIX 下当主色、Monet 下当种子色。预设见 `data/settings/ThemeColorPreset.kt`，
  顺序：自定义 → 跟随壁纸 → MIUIX 蓝 → 爱发电紫 → 绿色 / 黄色 / 橙色 / 粉色 / 青色 → 彩虹
  （官方 demo 那 7 色里的「紫色」因与品牌紫太像已剔除；彩虹固定放最后）。
  - 「自定义」：色值不写死，由用户在外观页调色盘里挑，存 `custom_theme_color`（ARGB Int，
    键与预设分开）。具体色值经 `LocalCustomThemeColor` 下发；未调过色时默认
    `ThemeColorPreset.DefaultCustomColor`（50% 灰 #808080）。
  - 「MIUIX 蓝」= `MiuixBrandBlue`（Miuix 默认色板的 primary #3482FF）。
    MIUIX 主题下选它直接使用官方默认色板；其余档位（含自定义）用
    `afdianLightColorScheme`/`afdianDarkColorScheme` 按主色染整套色板
    （禁用态、强调容器、下拉选中项等一并跟随）。
  - 主色变体 `primaryVariant` 是**同色系更深一档**，不等于主色（`AfdianColors` 里的
    `primaryVariantOf`：向黑色插值 15%，即 HSV 明度 ×0.85、色相与饱和度不变）。
    Miuix 官方那两套默认值本身不一致 —— 浅色 `#3482FF` 与 primary 相同，深色
    `#277AF7 → #0073DD` 深一档，生成式色板（Monet）又取 `primaryFixed`（tone 90 浅色调）；
    我们是从任意强调色现场算色板，照抄浅色那套会让变体永远看不出层次（调色盘的三段预览
    会出现两段同色），所以统一按深色那档的比例压暗。
  - 「跟随壁纸」两种主题**都提供**（只要 Android 12 / API 31 以上）：
    - MIUIX 主题下**只有主色跟随壁纸**：从系统动态色（`platformDynamicColors`）取主色，
      再按 Miuix 的派生规律染主色家族，背景 / 卡片 / 文字等中性色仍是 Miuix 静态色板
      （`AfdianTheme` 里的 `wallpaperAccent`）；
    - Monet 主题下则整套 Monet 色板都跟随壁纸。
    低版本没有系统取色能力时，选择器里过滤掉该项，已选中的它解析成 MIUIX 蓝
    （`ThemeColorPreset.resolveForTheme`），保证高亮色球与主题实际用色一致。
  - 「彩虹」：色相按固定节奏一直轮换，色值由时间算、不存 DataStore（`ThemeColorPreset.Rainbow.color` 为 null）。
    节奏在 `AfdianTheme` 里：每 100 毫秒推进 5 度色相（`RainbowStepMillis` / `RainbowHueStep`），
    转完一圈约 7 秒。步长保持很小 —— 除外观页外其他页面没有配色过渡，步子一大就会看到明显的跳色；
    节奏刻意调快，代价是整包重组更频繁（每秒 10 次重建 `ThemeController`）。
    只在真正选中彩虹档时才启动这个计时循环，其余档位不付出任何重组代价。
  - 点击彩虹档先弹警告弹窗（`AppDialog`，标题「彩虹主题色」）：
    「此配色仅供娱乐，可能会导致性能大幅下降，请勿反馈此配色相关的问题。」
    点「确定」才真正切换，并 Toast 「Enjoy~」。
  - 旧值迁移（`SettingsStore.themeColor`）：新键 `theme_color` 优先；否则读旧的 `key_color`
    （旧的「蓝色」并到 MIUIX 蓝）；再否则看旧的 `use_afdian_color` 开关 —— 显式设为 true 的保留爱发电紫，
    其余（含从未设置过）走新的默认值 MIUIX 蓝。旧键只读不写。
- 主题色选择器是**横向滚动的色球列表**（不是下拉）：每项上面 28dp 色球（外框 36dp）、下面名称，
  选中项的色球外框描 2dp 主题色圆环（与外框之间留空隙，切换选中不跳动）。
  点击**关闭水波纹**（`selectable(indication = null)`）：波纹会盖住色球，选中态已由描边表达；
  按下时用 `pressable(indication = SinkFeedback())` 做按压下沉，与项目里其他可点卡片同一套反馈。
  「跟随壁纸」没有固定色值，球用 `Brush.sweepGradient` 色环区分。整块（球 + 名字）是热区，
  语义为单选（`Role.RadioButton`），点击有触感反馈。
- 「彩虹」那颗的**球体**用 `AfdianCardDefaults.overlayColors().color`（浮层内容器色 `secondaryContainer`，
  直径与其他球一致 28dp），上面叠一个 18dp 的 Help 图标、颜色取同一套 `overlayColors().contentColor`。
  图标刻意比球小一圈：Demibold 的 Help 是实心圆，和球一样大会把球色整片盖住、看着像「这颗球更小」。
  选中态同样只靠外框描边表达。
- 「自定义」那颗球：球色取 `LocalCustomThemeColor`；**已选中时**球上叠一个 16dp 的「+」图标
  （未选中不显示）。点击行为分两种：未选中 → 只切到自定义档；已选中 → 再点一次弹出调色盘
  （`AppDialog` + Miuix `ColorPalette`，与官方 demo 同一用法）。调色盘里的颜色是草稿，
  点「确定」先保存色值、再切到自定义档；点「取消」不写 DataStore。
  弹窗顶部不用 ColorPalette 自带的单色预览条（传 `showPreview = false`），改为用草稿色
  试算一张真实色板（MIUIX 走 `afdian*ColorScheme`、Monet 走种子色生成），把
  **primary / primaryVariant / tertiaryContainer** 三等分铺成一条 32dp 色带，让用户先看到
  选完之后的配色大概长什么样。
- 主题 / 主题色的**当前值**一律读全局主题：是否 Monet 读 `MiuixTheme.isDynamicColor`，
  当前主题色读 `LocalThemeColor`。**不要**读 `SettingsViewModel` 的流 —— 外观页的 VM
  是进入页面时才创建的，初值固定是默认档，会先按默认值渲染一帧再跳回来。
  这也是 `ThemeColorTransition` 里「抄全局主题的结果最稳」的同一套思路。
- 模式映射集中在 `AfdianTheme`（`ui/theme/Theme.kt`），是「明暗」「是否 Monet」「主题色」
  三个维度的唯一权威出处；`LocalThemeColor` 下发的已是解析后的生效档位，
  `LocalCustomThemeColor` 下发自定义档的具体色值。
- 切配色时的平滑过渡：整页包在 `ThemeColorTransition`（`ui/theme/ThemeColorTransition.kt`）里，
  改动配色模式或主题色时逐帧插值到新色板（时长见文件内 `ColorTransitionDurationMillis`），不再瞬间换色。
  只有外观页有，总览页与其他页面不参与。
- 过渡只作用于中性色；主色家族（primary 及派生色：禁用态、强调容器、下拉选中项、Slider 刻度点）
  直接取目标值一步到位，不跟着渐变。
- 系统栏图标颜色跟随 App 深浅：`AfdianTheme` 内调 `ApplySystemBarAppearance`
  （`ui/theme/SystemBarAppearance.kt`），按生效色板的底色亮度决定图标深浅。
  开 edge-to-edge 后系统只按「系统深浅色」给图标，App 手动切深色时必须自己覆盖，
  否则黑图标压在深色背景上看不见。状态栏与导航栏一起处理，且是瞬间切换、不参与渐变。
- 分组「动画」：高级过渡动画、手势返回、系统预测性返回。
- 分组「数据卡片」：两张预览卡、数字滚动动画、显示收入单位、显示人次单位、数值窄字体、字体宽度。
  各项默认值、开关联动与拖动草稿逻辑沿用改造前的实现，没有改 DataStore 键。

### 关于（`Route.SettingsAbout`）

- 版式照 miuix demo 的 About 页：hero（`icon.webp` 88dp + squircle 裁角 + 应用名 34sp Bold + 版本号）
  → 分组「关于」→ 分组「更新」→ 页脚「构建类型」。应用名读 `app_display_name` 而**不是** `app_name`：
  debug 变体只给桌面图标加 Debug 后缀，关于页始终是干净的产品名。**不再用 `SettingsPageScaffold`**
  （那套骨架是大标题顶栏形态，与本页冲突），顶栏换成只有返回键的 `SmallTopAppBar`，标题滚动后淡入。
- hero 是**固定在列表之上的叠层**（不跟滚动），列表第一项是一块透明占位（高度 = hero 实测高度 + 底部留白）；
  随滚动进度逐级退场：版本号最先淡出 → 应用名 → 图标（同时缩到 0.94），卡片从 hero 上方滑过。
  进度只在 `graphicsLayer` 里读，不触发重组。
- **hero 区会吸附**：滚动停稳时如果还停在占位中间，自动补一段动画，只允许落在「顶部」或「完全滑过 hero」两个位置。
  方向和阈值都按「这一次手势的起点」判（对比手势开始时的 offset，而不是 `lastScrolledBackward`——
  松手回弹会把它翻向）：往上推只要 6% 就吸进列表（轻划即可），往回拉要掉过 30% 才退回顶部。
  **轻划在手指抬起那一拍（`onPreFling`）就吸附**，不等惯性衰减；速度超过 3000px/s 的重甩交给惯性自己飞，
  避免用力一甩被吸附动画掐断。`isScrollInProgress` 停稳 + 120ms 静默期只是兜底路径。
  miuix 没有这个行为，是本页自己的手感处理。
- 水面随滚动淡出（`alpha = 1 - heroProgress`），滑过 hero 后整页回到实色 `surface`。
- 整页背后铺**动态海水 shader**（`ui/components/background/WaterBackground.kt`）：
  legacy 包不画、用户关掉「模糊效果」时也不画（都回落普通主题底色）；浅色模式用浅海配色、深色模式用深海配色。
  细节与「复用时要同时改哪三处」见 `DESIGN.md` 的「动态背景（关于页海水）」。
- 功能完成度、**更新通道**（正式版 / Actions 构建，见「检查更新」一节）、检查更新、更新日志、开源许可、免责声明。
- **关于页直接外显本版更新**：分组「更新」的第二张卡是「更新日志」——
  卡头是可点进完整页面的入口，下面是当前版本的 `v1.1.0` + 日期、分割线、**全部**条目
  （不折叠、不截断：当前版本通常就几条，折叠反而多一次点击）。条目渲染复用更新日志页的
  `ReleaseNoteItems`，两处样式不会各自漂移。卡放在「检查更新 / 更新通道」下面。
- **更新日志页**（`Route.Changelog`，入口是上面那张卡的卡头「更新日志」）：
  数据来自**仓库根的 `CHANGELOG.md`**——构建时由 `copyChangelogAssets` 任务复制进 assets
  （AGP 9 禁止 `sourceSets.assets.srcDir(provider)`，走 Variant API 的 `addGeneratedSourceDirectory`），
  运行时由 `data/content/Changelog.kt` 解析。应用内不另存副本，发版只改 markdown 一处。
  只解析 `## 版本 · 日期` 与 `- 条目` 两种语法，不引 Markdown 引擎；行内 `**` / 反引号 / 链接会被剥掉。
  版式：每个版本一张卡、行内「版本号 + 日期 + 箭头」，手风琴式（同时只展开一个），当前版本默认展开并标「当前」。
  「未发布」小节只在非 stable 通道显示（`BuildConfig.BUILD_CHANNEL != "stable"`）。
- 版本号在 hero 里（`版本 x (code) S|L` —— 字母标 flavor：S = standard、L = legacy，跟着版本号走，
  两个包并存时一眼能分清；用 `AnnotatedString` 把字母上主题色 + SemiBold。Actions 包额外附带
  `· 构建 #N · <sha>`，方便报 issue 时直接截图），
  页脚只留「构建类型：standardDebug」。README 里「反馈请附设置页底部版本号」的说法仍指向「设置 → 关于」。
- 站外链接入口（都走 Custom Tabs，不进 App 内路由）：
  - **交流群** → `https://qm.qq.com/q/YVBgTJLi26`。
    点击一次干两件事：打开入群链接 **+ 把群号 `1108445904` 复制到剪贴板**
    （群号单独复制是给已经在 QQ 里、懒得再点一次「加入群聊」的用户用的）。
    复制走 `SettingsAboutScreen` 内的私有工具函数 `copyTextToClipboard`（写法抄 `MessageBubbleMenu.copyToClipboard`）。
  - **GitHub 仓库** → `https://github.com/hrsthrt74/afdian-compose`（源码、Issue、Release）。
  - （「爱发电文档」外链入口已迁到「我的 → 文档」聚合页，见「文档页」一节。）
- 这几个入口用 `ui/navigation/AppLinkNavigator.kt` 里的**顶层函数 `openExternalLink(...)`**，
  而不是 `LocalAppLinkNavigator`：后者带站内跳转能力（`onNavigate`），只在 MainScreen 里提供，
  而设置页在未登录栈（登录页顶栏的设置入口）也能进，那儿取不到这个组合局部。
  `AppLinkNavigator.openExternal()` 现在也委托给同一个函数，两处共用「官方客户端 → Custom Tabs → 系统浏览器」的回退链。
  品牌参数 `CustomTabsBranding` 在「通用」「关于」页顶层构建：两页都不在 `ThemeColorTransition` 内，
  不会因为色板逐帧插值而每帧重建位图。

### 导航注册

- `Route.Settings` 是总览页；四个分类页是独立的无参路由
  （`SettingsGeneral` / `SettingsAi` / `SettingsAppearance` / `SettingsAbout`）。
- 两处栈都必须注册总览页与四个分类页：已登录 `MainScreen` 的 `NavDisplay` 一套，
  未登录 `MainActivity` 登录页栈一套。miuix-nav 遇到未注册的路由是直接 `error()` 崩溃，
  给设置页加新入口时两处都要补。

## AI 集成（BYOK + MCP）

应用不内置任何模型，AI 能力分两半：**对内**是「把数据发给用户自配的 OpenAI 兼容服务」（BYOK），
**对外**是「把本机数据作为 MCP 工具暴露给外部 AI 客户端」。代码在 `data/ai/` 与 `data/mcp/`。

### AI 基础设施（`data/ai/`）

- **`AiProviderPreset`**：供应商预设（DeepSeek / Kimi / 智谱 / 自定义），各带官方兼容端点与常用模型；
  `Custom` 允许不填 Key。
- **`AiConfig` / `AiConfigDraft`**：完整配置（含解密后的 Key，仅在发请求前组装）与
  UI 展示草稿（不含 Key 明文，只有 `hasKey`）。归一化函数（`normalizeAiBaseUrl` 等）
  会剥掉用户多粘的 `/chat/completions` 后缀与末尾斜杠，拒绝缺协议头/非 http(s) 的输入，
  见 `AiConfigTest`。
- **Key 存储**：`AiKeyStore` 复用 `TokenCipher`（AndroidKeyStore AES/GCM）但用独立别名
  `afdian_ai_api_key`，密文存 DataStore；日志红线：Key 一个字符都不进日志。
- **`AiChatClient`**：OpenAI 兼容 `/chat/completions` 流式客户端。手写 SSE 解析
  （兼容 `data:` 带不带空格、多行 data、`[DONE]` 结束、流内嵌 `error` 对象、非流式 JSON 兜底）；
  正文取 `choices[0].delta.content`，思考过程取 `reasoning_content` 并作为独立事件
  `AiStreamEvent.Reasoning` 上抛。取消语义：Flow 被 collect 方取消时经
  `Job.invokeOnCompletion` 调 `call.cancel()` 断流。`sharedAiHttpClient` 基于爱发电共享 client
  派生，只放宽读超时到 5 分钟（推理模型思考间隔长）。
  - **推理模型要显示思考进度**：小米 MiMo 的 `mimo-v2.6-flash` / `-pro` 系列 `thinking.type`
    默认就是 `enabled`，会先长时间流 `reasoning_content`，正文要等思考完才来。早先版本把这类
    增量直接丢弃，表现是「HTTP 200 之后界面完全空白、像卡死」。现在思考增量单独成事件，
    气泡里显示「已输出 N 字思考过程」。
  - **读流不能按行阻塞**：`readUtf8Line()` 必须等到换行才返回，网关把事件拆成不带换行的数据块时
    会一直挂着。改成按块读（`source.read(buffer, 8K)`）再自己切行，并按 SSE 规范把多行 `data:`
    拼接后再解析。请求头显式带 `Accept-Encoding: identity`，避免反代对 SSE 做压缩缓冲导致零字节。
  - **流式判定不看 `Content-Type`，看内容**：大量中转/聚合网关把 SSE 标成 `application/json`
    或干脆不给这个头，信了它就会走 `readUtf8()` 整段读，表现是弹层长时间空白、模型说完才一次性出现。
    现在的做法是逐块读，见到第一行 `data:` 即认定为 SSE 并实时 emit 增量，从未见到 `data:`
    才把攒下的原文当整段 JSON 解析。
  - 结束日志会打出正文增量数 / 思考增量数 / 解析失败数 / 首包延迟，排查「有 HTTP 200 但不出字」先看这条。
  - **链路日志 + 看门狗**（调试日志开关打开时输出）：`发起请求：POST …，请求体 N 字节` →
    `HTTP 200，收到响应头（Xms）` → `首字节到达：响应头后 Xms` → 之后每 5 秒一条看门狗状态
    （`StreamProgress.describe()`：还没响应头 / 有响应头没首字节 / 正文与思考字数、最后一条数据几秒前）
    → `SSE 流结束：…`。前 3 个事件还会原样打印载荷，网关偷换格式一眼可见。
    看门狗是独立协程，读 socket 阻塞在 `read()` 时也照常汇报——这是区分「服务端在思考」与「真卡死」的唯一手段。
- **`AiPromptBuilder`**：纯函数层——收入明细 → 分析提示词 + Markdown 表格（应用内「问 AI」），
  月度账单 → Markdown 表格（MCP 工具用）。应用内「问 AI」的旧复制方案
  （`IncomeStatRepository.fetchAllMarkdown`）现在也委托到这里，口径只有一份。

### AI 设置页（`Route.SettingsAi`）

- 设置总览新增「AI 助手」分类卡（星芒图标 `R.drawable.ic_ai_sparkle`，绿色）。
- 页面分区：供应商预设（切换时自动填该家端点与常用模型）→ 连接（Base URL / 模型 / API Key
  密码框 + 保存 + **测试连接**，测试发一条极简提示词的请求，回包只取首行 20 字符做摘要）
  → **思考** → **MCP 服务器**分区 → 数据说明。
- **思考**（`AiThinkingMode` / `AiReasoningEffort` / `AiThinkingLevel`，见 `data/ai/AiThinking.kt`）：
  推理模型回答前先吐思维链，耗时可能几十秒，所以给一根**四档滑块**（`SliderPreference`
  风格：`BasicComponent` + `Slider`，`steps = 2` 吸附四档）——最左 `关闭`（只发
  `thinking.type=disabled`），往右依次 `low` / `high` / `max`（发
  `thinking.type=enabled` + 对应 `reasoning_effort`）。滑块离开最左端即视为「开启」，
  不再有「跟随服务端」这一档：界面上永远是一个确定状态，不存在「什么都没选但不知道发了什么」。
  `AiThinkingLevel` 是这两个字段在 UI 上的合成刻度（`of(mode, effort)` 反查档位），
  落盘仍拆成两个 Key（开关 + 强度），旧档 `default` / `medium` 读出后分别落到
  「开启 + high」与「high」。默认档 = 开启 + high。
- **不再暴露「生成参数」**：请求体不带 `temperature` / `max_tokens`。新模型（GPT-5、o 系列等）
  已不接受温度调节，部分网关还会对不认识的参数直接报错；输出长度交给模型与场景提示词掌控。
  思考相关的 `thinking` / `reasoning_effort` 见上文「思考」，按滑块档位下发。
- `SettingsViewModel` 暴露 `aiConfigDraft` 与 `testAiConnection`；两处栈（MainScreen /
  MainActivity 登录栈）都要注册 `Route.SettingsAi`（见「设置页 → 导航注册」）。
  `aiConfigDraft`（`StateFlow<AiConfigDraft>`）的初值由 `firstBlocking` **同步取真实首值**：
  DataStore 异步，若拿默认草稿垫一帧，预设下拉与思考滑块会先画错值再跳过去
  （滑块还带弹簧动画，肉眼就是「闪一下」）。DataStore 的 `data` 是热流，
  App 启动期间已有订阅者读过，这里基本是内存快照；只有冷启动直奔本页才真读一次盘。
  同一个 `firstBlocking` 也用于 `mcpEnabled` / `mcpPort`，否则开关会先显示「关」再跳到「开」。

### AI 功能接入（复用 `ui/components/ai/AiResultSheet`）

- **`AiResultSheet`** 是各 AI 场景（目前是收入分析）共用的流式结果**底栏**（`WindowBottomSheet`，不是 Dialog：
  分析报告动辄十几段，Dialog 的小窗装不下）：内部负责读配置 → 调 `buildPrompt`（suspend
  闭包，由调用方提供数据拉取+提示词）→ 流式 Markdown 渲染（mikepenz
  multiplatform-markdown-renderer 的 `StreamingMarkdownState`，重试/重开底栏靠换
  `ReferenceLinkHandler` 的 remember key 整体重建）→ 停止/重试/复制，可选 `onAdopt`
  「采用」回调。只有底栏真正打开时才会拉数据发请求。
  - 版式对齐 DeepSeek 移动端，自上而下三段：**用户侧气泡**（右对齐）→ **思考块**（灰字 +
    左侧竖线，标题可点开合）→ **正文直接铺在页面上**（无头像、无气泡卡片，可用宽度最大）。
  - 气泡内容两种形态：`queryChip` 静态文案（打开即自动发请求），或
    `composer` 槽（收入明细：时间尺度 chips + 发送，**点「发送」才发请求**，发送后 chips 锁定；
    失败/停止时解锁，可以换个尺度直接重发）。`preparingHint` 可替换「正在拉取数据…」的默认文案。
  - **思考块**：思考中标题是「正在思考」，正文一开始就定格成「已思考（用时 N 秒）」
    并自动收起成一行（用户手动开合过则不再自动收）；中途停止/失败时保持展开，
    用时照样定格。思考原文只在这里显示，不拼进正文。
  - 对话区自己滚动、动作区固定贴底，整块撑满 Sheet 高度：流式过程中高度不会随内容跳变。
    动作区的下边距 = 12dp + `WindowInsets.navigationBars`：Sheet 的 Dialog 是 edge-to-edge
    （`decorFitsSystemWindows = false`）且 `insideMargin` 纵向为 0，不自己让开就会被手势条压住。
    水平方向不再叠加内边距，直接用 Sheet 自带的 24dp（与标题对齐）。
  - **依赖必须用 m3 变体**（`multiplatform-markdown-renderer-m3`）：m2 变体内部引用
    `androidx.compose.material.MaterialTheme`，本项目只有 Material3（M2 本体只被 icons/ripple
    间接带进来），用 m2 会在弹层首帧就 `NoClassDefFoundError` 闪退。
  - 增量按 100ms 节流后再 `append`（正文与思考共用同一拍）：Markdown 每刷一次都要重解析整篇、
    思考块每改一次都要重排版，逐包刷新会把长报告的主线程压死。
  - 首包未到时思考块里给「正在等待模型响应…（Ns）」占位：秒表状态收在独立小组件里，
    每秒只重组那一行，不会拖着整段 Markdown 一起重排。
  - 颜色/排版全部显式传 Miuix 值（`markdownColor` / `markdownTypography`）：这两者的默认参数
    读 `MaterialTheme`，而项目里没有 MaterialTheme 提供者，未覆盖的项会落在 M3 默认亮色方案上，
    暗色主题下会看不清。
- **收入明细「问 AI」**（`IncomeDetailScreen`）：菜单直接开底栏（**不再有隐私确认弹窗**，
  气泡里那行说明承担告知职责，且点「发送」前不会把数据发给 AI）。打开底栏即开始**分页预取**
  （`IncomeDetailViewModel.startAiFeed()`，先复用表格已加载的页作种子，再往后翻），
  用户选时间尺度（7天/30天/3个月/6个月/全部，默认 30 天）后点「发送」：
  - 手上数据已覆盖所选窗口（`IncomeAiFeedState.covers()`）→ **立刻取消后面的翻页**，马上开始对话；
  - 还不够 → 底栏显示「正在拉取数据…（已 N 条，覆盖至 YYYY/MM/DD）」，到位后自动开跑。
  提示词由 `buildAiPromptForRange()` 按窗口过滤后组装（`AiPromptBuilder.incomeAnalysisPrompt`
  会写明「近 N 天 + 实际日期跨度 + 条数」）。关闭底栏即停掉后台翻页；菜单保留「复制提示词」走旧方案兜底。
  时间尺度的纯逻辑（窗口起始日 / 覆盖判定 / 过滤）在 `data/stats/IncomeAiRange.kt`，有单测锁定。

### AI 记录（`Route.AiHistory`）

- **定位**：只读快照回看，**不是会话**——每条 = 一次问答（问题范围 + 完整回答 + 思考过程 + 模型信息），
  没有 sessionId、不支持继续追问。这一点与 roadmap 里「不做独立 AI 聊天页 / 多会话管理」一致，
  只是补了「生成物不该关掉就没了」这一块。
- **写入**：由 `AiResultSheet` 在 `phase == Success` 后直接写盘，不经过 ViewModel ——
  将来新增场景接上底栏时自动拥有历史。要点：
  - 只在 Success 写。失败没有正文，「停止」是用户主动打断的半截内容，记进历史会污染回看语义。
  - 写盘包在 `withContext(NonCancellable)` 里：用户看到成功立刻点「关闭」会取消本次协程，
    不加这层会偶发性丢记录。
  - 底栏新增两个带默认值的参数：`scene`（场景标识）与 `historyQuery: (() -> String)?`
    （落盘那一刻才求值的摘要 lambda）。用 lambda 而非 String 是因为时间尺度在 composer 里是状态，
    提前传会拿到打开底栏时的旧值。返回空白时回退 `queryChip`。
- **数据**：`data/ai/AiHistoryStore`（独立 DataStore `ai_history`）+ `AiHistoryModels.AiHistoryEntry`。
  按 `CurrentAccount.userId` 分区（与浏览历史同构），单账号上限 50 条，超出按时间截断。
  思考过程截断到 8000 字符：它不参与复制/采用，存全量只会拖慢 DataStore 的整文件读写。
- **入口**：消息列表里的一条**标准会话行**，不是特殊卡片。做法是在 `MessagesViewModel` 里把最近一条
  记录合成一个虚拟 `MessageDialog`（`user.user_id = AI_HISTORY_DIALOG_ID`），合进 `filteredDialogs`
  的展示流，于是自动获得会话行的版式、`formatMessageTime` 时间格式、置顶 Pin 与长按菜单：
  - **按时间混排**，不固定在顶部：`mergeAiDialogInto` 按 `send_time` 插到非置顶区的正确位置
    （置顶区固定在最前，不能插进去）。
  - **可长按置顶**：走与真实会话同一条 `togglePinned` 链路，快照存在 `msgPinnedDialogCards` 里。
    置顶后快照的 desc 会冻结，所以合并时用实时数据覆盖它，否则新生成的记录不会反映到卡片上。
  - **只在有记录时才存在**：没记录就不该在会话列表里占一行。
  - **点击进 AI 记录页**（`Route.AiHistory`），不进聊天页——它没有真实 `user_id`。
  - `unread_count` 恒为 0：不参与「未读」筛选、不计入 Tab 角标；长按菜单对它只给置顶 / 取消置顶，
    没有「标为已读」。
  - 虚拟行只在展示流里，**永远不进 `_dialogs`**，所以分页合并、`markConversationRead`、
    「共 N 条对话」计数（排除它）都不受影响。
- **页面**：`AiHistoryScreen`（结构照 `HistoryScreen`：时间分组 + 一张 Card 装一组 + 整页空态 +
  顶栏清空）→ 点条目开 `AiHistoryDetailSheet`。复制 / 删除单条 / 清空都走 `AppDialog` 破坏性确认。
- **UI 与数据分层**，这是这块代码唯一的阅读入口约定：
  - `ui/viewmodel/AiGenerationEngine.kt` + `AiGenerationViewModel.kt`（**数据**）：
    引擎持有阶段 / 正文 / 思考 / 用时，负责读配置、发请求、节流、计时、成功后落盘；
    ViewModel 装配真实依赖并暴露五个事件（`onSheetOpened` / `onSend` / `onRetry` / `onStop` /
    `onSheetClosed`）。对外只暴露只读状态 + 事件，UI 改不坏状态。状态机可 JVM 单测
    （`AiGenerationEngineTest`：假时钟 + 假流，覆盖节流窗口 / 思考定格 / 落盘时机）。
  - `ui/components/ai/AiSheet.kt`（**UI**）：`AiSheetScaffold` 骨架（窗口 / 可滚动对话区 + 滚动条 /
    贴底动作区 / 导航栏让位，两个底栏共用一份）+ `AiResultSheet`（现场生成）+ `AiHistoryDetailSheet`
    （历史回看，纯展示，数据由调用方传入）。这里只持有纯视觉局部态（思考块展开态、滚动位置）。
  - 两者靠两个通道衔接：`answerDeltas`（节流后的正文增量 SharedFlow，UI 喂流式 Markdown）与
    `AiGenerationRequest.resolveHistoryQuery`（落盘那一刻才求值的问题摘要，调用方晚绑定 lambda）。
  - `AiResultSheet` 的调用面收成 6 个参数：`dialogState` / `request`（AiGenerationRequest）/
    `question`（AiQuestionSlot.Static 或 .Composer，互斥）/ `preparingHint` / `onAdopt` /
    `onSheetVisibilityChanged`（底栏开/关钩子，调用方借它启停数据预取）。
- **两个底栏为什么还放在同一个 UI 文件**：它们展示的是同一个东西，差异只有「正文从哪来」和
  「底部给哪些按钮」。也刻意**没有**合并成「一个函数 + 模式参数」——那会让 `buildPrompt` / `entry` /
  `onDelete` / `onAdopt` 变成一堆互斥的可选参数，调用方得先判断该传哪几个，更难读。
- **共享零件**：气泡 / chip 在 `AiMessageParts.kt`，思考块在 `AiThinkingBlock.kt`
  （贴底跟随 + 手势接管抽成 `Modifier.streamAutoFollow`），Markdown 渲染在 `AiMarkdown.kt`，
  间距 token（`AiSheetSpacing`）与失败/停止状态卡（`AiStatusCard`）在 `AiSheetTheme.kt`。
  生成与回看共用同一份配色字号，否则回看时的排版迟早和生成时漂移。

### MCP 本地服务器（`data/mcp/`）

- **传输**：MCP Streamable HTTP 的最小合规子集——`POST /mcp` 收 JSON-RPC 回
  `application/json`（不用 SSE 应答，规范允许）、`GET /mcp` 回 405（声明无 SSE 下行）、
  `DELETE /mcp` 关会话；不支持批量（2025-06-18 规范已废止）。协议实现在
  `McpProtocol.kt`（`McpRequestHandler`，纯逻辑可单测，见 `McpProtocolTest`）。
- **安全三道闸**：仅绑定 `127.0.0.1`；Origin 校验（浏览器请求带 Origin 时只放行本机回环，
  防 DNS rebinding；非浏览器客户端不带 Origin 直接放行）；Bearer 访问令牌（设置页生成/
  重置，SecureRandom 32 位十六进制，`AiKeyStore` 加密落盘）。initialize 之外的方法必须带
  有效 `Mcp-Session-Id`，无效回 404 让客户端自动重新握手。
- **工具集**（`McpTools.kt` + `McpDataContext` 接口）：`get_user_info` / `get_income_stats` /
  `get_monthly_summary` / `get_recent_sponsors` / `get_my_orders`，
  输出与 `AiPromptBuilder` 同源的 Markdown 表格。**隐私红线**：不提供私信类工具；
  收货地址/收件人/完整快递单号不进任何工具输出；未登录抛 `McpToolException` →
  协议层转 `isError` 结果。
- **生命周期**（`McpServerController`）：`AfdianApplication` 创建并订阅
  （开关/端口/令牌组合流）；关闭 → stop；开启且参数没变 → 不动；参数变化 → 自动重启。
  随应用进程存活，不做前台服务/常驻通知。
- **设置分区**：开关（含运行状态展示）、端口（1024-65535）、访问令牌（复制/重置，重置走
  破坏性确认弹窗）、连接指引（Cherry Studio 等 Streamable HTTP 客户端配置示例 +
  `adb reverse tcp:<port> tcp:<port>` 命令）。

## 文档页（DocsScreen）

- 路由 `Route.Docs`，入口在「我的」页底部卡（设置 / 屏蔽与拒绝名单 / 开发者 / 文档）。
  全 App「看官方文档」的入口统一收在这里：
  - **爱发电文档**：官网文档站外链 `https://guide.afdian.com`（写死域名不跟「优先线路」，见上文）；
  - **创作者指南**：官网动态，按 post id 进动态详情页——原「我的」页快捷入口
    （`QuickEntryPreset.CreatorGuide`）已删，此处是唯一收录；
  - **Webhook 与 API 文档 / 开发者功能汇总**：两篇官网开发者动态，与开发者页「文档」卡同源收录
    （那边是就地入口不撤，这边做全量聚合）。
- 版式：`SettingsPageScaffold` 骨架 + 分组「SmallTitle + 卡」，一篇文档一张卡；
  行是 `ArrowPreference` + 共用图标块 `CategoryIconTile`（32dp squircle 彩色方块，
  Monet 适配在组件内，与设置总览分类共用同一份实现，见「设置页 → 总览页」）。
- 官方文档的 post id、文档站域名/URL、文档卡四色（`DocTileBlue/Orange/Green/Purple`，
  文档页与开发者页共用）都集中在 `ui/navigation/OfficialDocs.kt`，新增收录先加常量再往页面加卡。
- 按动态 id 打开详情时入栈前做 `none { it == route }` 去重（与 MainScreen 的 `pushUnique`
  同一语义；那边是 private，页面内各自收口）。
- 已保存的快捷入口配置若还存着 `creator_guide`（已删条目），`normalizeQuickEntries` 会按
  未知值丢弃，不需要数据迁移。

## 存储空间管理页（StorageScreen）

- 入口在「设置 → 通用」；浏览历史的清理**只在这里**提供（设置页原「清除浏览历史」入口已移除）。
- 扫描器 `data/storage/StorageScanner`：以 `context.dataDir` 为根遍历一次，只累加文件字节数，按相对路径前缀归入 `StorageCategory`。
  - 遍历时跳过 `code_cache/`（编译产物）与软链接（`dataDir/lib` 指向 `/data/app/.../lib`，跟进会把 APK 原生库算进来）。
  - `Other` 分类用 `total - 其它分类之和` 兜底覆写，从构造上保证「应用总占用 == 各分类之和」。
  - `categorizeStoragePath` 用**前缀**匹配而非文件名全等：DataStore 先写 `<name>.preferences_pb.tmp` 再原子 rename，可能残留 `.tmp`。
  - `formatBytes` / `categorizeStoragePath` 是顶层 `internal` 纯函数，见 `StorageScannerTest`。
- 分类与清理动作一一对应：图片缓存 → Coil `diskCache.clear()`（同步阻塞，必须在 IO 线程，且**不能**直接删目录，目录内有 LRU journal）；
  历史记录 → 按勾选的子项分别走 `HistoryStore.clearAllAccounts()` / `SearchHistoryStore.clear()` / `AiHistoryStore.clearAllAccounts()`；
  快照缓存（`Snapshot`）→ `HomeCache.clear()` + `ProfileCache.clear()` 一起清。
- 共 6 个分类：图片缓存、历史记录、快照缓存、日志、设置与登录数据、其他。
- 两处「合并展示」的取舍不同，区别就在要不要让用户选子项：
  - 浏览记录 + 搜索关键词 + AI 记录 → **「历史记录」**（`History`）。三者都是本地历史、都能自动重建，
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

- 入口在「设置 → 通用」。页面结构：记录（两个开关）→ 状态（占用 / 文件数）→ 导出（分享 / 保存到本地）→ 清空日志 + 说明小字。
- **职责与动机**：用户遇到闪退时只有 Logcat 可看，无法向开发者提供任何线索。日志页补齐三件事：
  运行日志落盘、崩溃捕获、导出。所有文件操作集中在 `data/log/LogStore.kt`，纯函数（命名 / 淘汰 / 行格式化 / 合并）
  在 `data/log/LogFileNaming.kt`（见 `LogFileNamingTest`）。
- **写入管线**（`LogStore`）：
  - `AppLog` 保持零依赖：只多了一个 `sink` 回调注入点（`AfdianApplication.onCreate` 里接通 `LogStore::enqueue`）；
    sink 为 null（Application 之前）时只走 Logcat，不丢不崩。
  - D 级日志只在「调试日志」开关开启时落盘（与 Logcat 行为一致）；**W/E 始终落盘**——用户没开调试日志也能导出报错。
  - 「写入日志文件」开关（DataStore 键 `log_to_file`）只控制运行日志；**崩溃报告不受它控制，始终记录**——这是本功能存在的意义。
  - **两个开关（写入日志文件 / 调试日志）的默认值都是 `BuildConfig.DEBUG`**：debug 构建默认全开、
    release 默认关闭；用户手动改过后以存储值为准（默认值只在键缺失时兜底）。
    `AppLog.debugEnabled` / `LogStore.fileLoggingEnabled` 的**字段初值**也取 `DEBUG`，
    保证 DataStore 读取完成前的启动初期（崩溃多发段）日志不缺席，读出后由订阅方按真实设置校正；
    `MainActivity` / `LogViewModel` 的 collect 初值同步取 `DEBUG`，避免 debug 版首帧「先误关再读回」的闪烁。
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
  FileProvider 的 authority 用 `${applicationId}.fileprovider` 动态拼接（legacy 有 `applicationIdSuffix`，硬编码包名会崩）；
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

## 接口调试页（ApiProbeScreen）

- 入口在「设置 → 通用」（与「存储空间管理」「日志管理」同卡），**所有变体常驻**。
  页面结构：环境（构建变体）→ 操作（一键全跑 / 清空结果 / 重新读取登录态）→ 说明小字 → 探针卡片列表。
- **职责与动机**：排查「数据卡片上的数字对不上」时，需要绕过 Repository 的解析与聚合，直接看接口原文。
  典型诉求是「`/api/my/dashboard` 的 `summary.month_amount` 现在到底是多少」。因此做的是**主动探针**（点一下跑一次），
  而不是在 OkHttp 上挂拦截器自动抓包——探针可控、可复现，想查哪个查哪个。
- **清单**（`data/probe/ApiProbeModels.kt` 的 `ApiProbeCatalog.all`）：14 个**无参核心接口**，全部 GET、只需 `auth_token`。
  **分页接口一律把 `page=1` 写死，只取单页快照**——绝不复用 `CreatorStatsRepository` 的翻页聚合
  （`/api/my/sponsored-bill-filter` 会翻几十页、`/api/my/stat` 有 100 页上限），否则探针会长时间卡住。
  需要 `user_id` / `plan_id` / `post_id` 等参数的接口暂不收录，留待后续支持参数输入。
- **不解析业务模型**：探针只负责把接口原文拿回来（`ApiProbeExecutor` 用宽松 `Json` 解析），
  所以新增一个接口只需往 `ApiProbeCatalog.all` 加一条 `ApiProbeSpec`，不必再写一份 `@Serializable` 模型。
- **摘要提取**（`data/probe/ApiProbeJson.kt`，全为纯函数，见 `ApiProbeJsonTest`）：
  - `ApiProbeSpec.highlights` 写的是**相对路径**（如 `summary.month_amount`、`list[0].uv`）。
    `buildHighlights` 的解析顺序是「先按响应根试，失败再把根当 `{ec, data}` 信封取 `data` 基点重试」，
    所以写 `summary.month_amount` 能直接穿透信封命中 `{ec:200, data:{summary:{...}}}`；
    写 `data.xxx` 或根级字段（如 `balance`）也能命中。
  - `highlights` 留空时自动折叠取叶子字段前 6 条兜底（折叠后跳过 `ec` / `em` 等信封字段）。
  - 字段**不存在** → `Highlight.value == null`，UI 显示灰色「（缺失）」；字段存在但是 JSON `null` → 显示 `null`。
    两者刻意可区分（`JsonNull` 是 `JsonPrimitive` 的子类，判断顺序必须先 `JsonNull`）。
- **原始 JSON 展示**：`prettyPrintAndCap` 先美化，再按「400 行 / 8 万字符」双重截断并标注「完整响应 N 字符」。
  上屏放在**详情 Sheet**（`WindowBottomSheet`）里：Sheet 内容区自带纵向滚动，长 JSON 直接交给它；
  代码块本身只做横向滚动（`softWrap = false` + `horizontalScroll`），长行不折行、一个字段一眼看完，
  也让每行只测量一次；外层再包 `SelectionContainer` 支持长按选中。
- **列表 / 详情分工**：「一眼要看的数字」留在列表卡片（状态徽章 + 元信息 + 关键字段摘要），
  完整信息（请求元信息 + 全部关键字段 + 原始 JSON）放进点开的详情 Sheet。
  这样列表更短、更好扫视，也不存在超长 JSON 把卡片撑开的观感问题。
  Sheet 顶部右侧放复制原始 JSON 的按钮（滚动时始终可达），路径行带「重新运行」（结果实时回填 Sheet）。
- **凭据脱敏**：`maskSecrets()` 把 URL / 文本里的 `auth_token`、`access_token`、`token`、`cookie` 查询值换成 `***`，
  用于**展示与日志**（请求本身仍带真实 token）。卡片上展示的请求 URL 一定是 `auth_token=***`，
  `AppLog` 只打 id / HTTP 码 / 业务码 / 耗时 / 字节数 / 命中域名，绝不打完整 URL。
  「登录态」只显示「已登录 / 未登录」，**任何情况下都不展示 token 内容**。
- **失败即数据**：`ApiProbeResult` 显式四态（Idle / Running / Success / Failure），失败细分为
  未登录 / HTTP 异常 / 业务失败（`ec != 200`，文案取 `em`→`msg`→`message`）/ 解析失败 / 网络异常。
  执行器把所有异常兜成 `Failure`（只有 `CancellationException` 原样抛出以遵循结构化并发），
  失败时也尽量附上响应体，UI 侧无需 try/catch。**`ec` 缺失（旧接口）不算失败**，与 `requireAfdianSuccess(requireEc = false)` 语义一致。
- **并发与竞态**（`ApiProbeViewModel`）：
  - 每张卡状态完全独立，一个接口失败不影响其他；`runningIds` 做重入保护（单卡点击与全跑共用）。
  - 一键全跑用 `Semaphore(3)` 限流（纯串行会被一个慢接口堵死，放飞又容易触发限流）。
  - 用**批次号 `runEpoch`** 丢弃过期的在飞结果：`runAll` / `clearAll` 会自增批次号并取消旧作业，
    旧协程回来时批次对不上就直接丢弃，避免「取消后旧结果把新一轮的『请求中』覆盖掉」；
    协程被取消时还会把该卡拉回 `Idle`，不留「请求中」僵尸态。
- **UI 细节**：
  - 列表页结构：环境（3 行 `BasicComponent`）→ 操作（`Button` 实心「一键全跑」+ 并排的「清空结果」「刷新登录态」）→ 说明小字 → 探针卡片列表。
  - 整张卡片可点即运行（`Card(onClick = ...)` + `pressable(SinkFeedback())`，与 `CreatorCard` 一致）；
    卡片底部的「查看完整返回」行长独立可点（同样叠了 pressable + clickable），不会冒泡到整卡的运行动作。
  - 卡片排版分四段，三种运行状态共用同一套骨架（标题 → 路径 → 分隔线 → 内容），结构稳定不会跳动。
  - **卡片内的视觉层次是「字号 + 颜色」双维度拉开的**（只靠颜色会糊成一片小字墙）：
    标题 `body1` + Medium；**字段值 `body1` + 等宽 + Medium（全卡焦点，调试页看的就是这个数字）**；
    字段名 `footnote1` + 等宽 + 次要色；技术元信息 `footnote2` + 三级次色；入口 `body2` + `primary`。
  - 字段名带 `weight(1f)` + `maxLines = 1` + `Ellipsis`，值不参与权重分配：
    长路径（如 `list[0].paid_order_real_amount`）只会截尾，不会挤走值或把卡片撑高，
    而每行值的右边缘天然对齐成一列。
  - 卡片上不写「GET 」前缀（清单里全是 GET，前缀只挤占宽度；方法在详情 Sheet 里保留）；
    分隔符统一用中点 `·` 而不是全角空格，读起来才像一组并列指标。
  - 状态标签用「6dp 彩色圆点 + 主题色文字」自绘，**不用 `Badge`**——miuix 的 `Badge` 带 content 时是
    16dp 小圆点（为数字角标设计），装不下「业务失败」这类状态词；也**不套胶囊底色**——
    `primaryContainer` 是高饱和纯蓝（浅色 0xFF5D9BFF），14 张卡各顶一个会很吵，
    而中性底的 `surfaceContainerHighest` 在深色下（0xFF2D2D2D）与卡片底（0xFF242424）几乎同色、白做一层底。
  - 失败态把「失败类型 + 原因」单独成行、用错误色（它比任何字段都重要，不该埋在元信息小字里）。
  - Sheet 内所有 `Card` 必须用 `AfdianCardDefaults.overlayColors()`（弹层背景与 Card 默认色在默认色板下数值相同，
    不显式指定会「看不见」）；小节标题的 `insideMargin` 补 16dp，与卡片内容的 40dp 左边缘对齐。
  - 详情 Sheet 的可见性状态放在页面的 `remember` 里（临时 UI 状态），并额外记住「最后一次展示的卡片」，
    让 Sheet 关闭动画期间仍能渲染内容、不会闪空。列表数据仍由 ViewModel 持有。

## 内容 URL 拦截

- 拦截 `afdian.com` 和 `ifdian.net` 的公开链接：
  - `https://afdian.com/p/{post_id}` → 动态详情页。
  - `https://afdian.com/a/{slug}` 或 `https://afdian.com/@{slug}` → 创作者详情页。
- 其他 URL 交给系统浏览器。

## 通用组件

组件用法见 `DESIGN.md` 的「通用组件」一节。

- `EmptyStateView`（`ui/components/EmptyStateView.kt`）：通用空状态视图，96dp 图标（默认 `Light.Info`）+ title2 标题 + body2 引导语 + 可选 action 槽位（重试按钮）+ 可选 `iconModifier`（图标变换），组合时自带 0.8→1 缩放淡入动画（`animated = false` 可关）。
- `ErrorStateView`（同文件）：错误态预设，标题固定「加载失败」+ 内置 fillMaxWidth「重试」按钮，参数 `onRetry` / `summary` / `modifier`；图标默认是 Info 旋转 180°（miuix 无错误语义图标，`iconRotation` 可调），内部直接复用 `EmptyStateView`。
- `QuickEntry`（`ui/components/basic/QuickEntry.kt`）：圆形快捷入口（圆形底 + 图标 + 下方标题），触感反馈内聚在组件内。`containerSize` / `iconSize` 可调出紧凑档（面板网格用），`enabled = false` 可挡住重复点击；`icon` 传 null 用同尺寸 Spacer 占位。预设入口的标题/图标/目标来自 `QuickEntryPreset`，非预设的特殊动作在调用点手写 `onClick`。

## 关键文件

- `MainActivity.kt`：应用入口、版本拦截（装错 edition 的阻断）、免责声明、认证分流。
- `EditionGuard.kt`：版本拦截判定（`isWrongEdition` 纯函数 + `isBlocked()`），阈值取自 `BuildConfig.STANDARD_MIN_SDK`。
- `AfdianApplication.kt`：自定义 Application，日志管线（LogStore / sink / CrashHandler）与「写入日志文件」开关订阅的唯一装配点。
- `CrashHandler.kt`：全局未捕获异常捕获，崩溃报告落盘后交还系统 handler。
- `AppLog.kt`：全局日志门面（Logcat + 落盘 sink 注入点）。
- `ui/screens/main/`：所有主页面（Home、Discover、PostDetail、Messages、Profile、CreatorDetail、Favorites、Sponsoring、Order、Management、ThankSponsors、Settings、History、OssLicenses、Storage、Log、ApiProbe）。
- `ui/screens/auth/`：登录、免责声明、装错版本阻断页（`WrongEditionScreen`）。
- `ui/components/`：通用组件；`ui/components/data/`：数据卡片与图表组件（ChartDataCard、DonutChart）；`ui/components/home/`：首页模块组件（`IncomeStatTable.kt` 为收入明细页与首页共用的表格行组件，日期列可按需附带星期）。
- `ui/viewmodel/*.kt`：各页面 ViewModel（含 PostInteractionController 全局互动状态）。
- `data/auth/`：认证状态、Token 存储、登录请求和加密载荷。
- `data/content/`：领域接口服务（AccountService、MessageService、AffiliateService、FeedService、CreatorService、ReceivedSponsorService、ThankSponsorsService、OrderService、ManagementService），`AfadianContentService` 为兼容门面；消息解析集中在 `MessageParser`，模型见 `ContentModels` / `MessageModels`；缓存 `ProfileCache` / `HomeCache`。
- `data/network/`：`AfdianHttpClient`（优先线路与多域名回退，写请求仅连接失败才换线）、`AfdianRoute`（三档线路与有序回退链、`AfdianRouteState` 进程级当前值）、`requireAfdianSuccess`（业务码校验）、`sharedAfdianHttpClient`（全应用共享客户端）。
- `data/history/`：浏览历史（HistoryStore、HistoryModels）。
- `data/log/`：本地日志（LogStore 写入管线、LogFileNaming 纯函数：命名 / 淘汰 / 行格式化 / 合并 / 脱敏）。
- `data/stats/`：数据卡片和图表模型（CardModel、ChartModel、CreatorStatsRepository、WithdrawalSchedule；日期后缀的星期汉字统一由 `WeekdayLabel.kt` 的 `weekdayLabelOf()` 映射）。
- `data/storage/`：本地存储占用扫描（StorageScanner、StorageCategory、formatBytes）。
- `data/probe/`：接口调试页的探针清单、JSON 摘要纯函数与执行器（ApiProbeModels、ApiProbeJson、ApiProbeExecutor）。
- `data/settings/SettingsStore.kt`：DataStore 设置存储。
