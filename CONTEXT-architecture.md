# 架构

## 目录结构

```
app/src/main/java/com/hrsthrt74/afdian/
├── data/
│   ├── auth/          # 认证（Token 存储、登录加密）
│   ├── content/       # 内容接口（动态、创作者、收藏、订单、管理、感谢页）
│   ├── history/       # 浏览历史（本地存储、头像缓存）
│   ├── settings/      # DataStore 设置（首页模块、管理布局、主题等）
│   └── stats/         # 数据卡片模型、图表模型、提现日程
├── ui/
│   ├── components/    # 通用 UI 组件
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

## 路由结构

底部导航 4 个 Tab：首页、发现、消息、我的。

二级路由（覆盖全屏，由外层 NavBackStack 管理）：
- `CreatorDetail` / `PostDetail` / `PostDetailById` / `MessageConversation`
- `Favorites` / `Sponsoring` / `Orders` / `Management` / `ThankSponsors` / `Settings` / `History`

## 导航模式

- 底部导航栏使用 Miuix `NavigationBar` + `HorizontalPager`（四个 Tab 常驻，beyondViewportPageCount = 3）
- 页面路由使用 `miuix-nav` 的 `NavDisplay` + `NavBackStack`
- 「我的」页的子路由（收藏/我发电的/订单/管理/设置）提升到外层栈，push 时盖住底部导航栏
- 页面过渡使用 `CreatorDetailTransition`（卡片展开动画），由设置开关控制

## TabRow 固定头部模式（Favorites/Order/Management 均已应用）

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
