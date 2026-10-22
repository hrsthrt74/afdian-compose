# 最佳实践

不随任务默认加载，遇到对应场景时再读。记录「踩过坑才得出」的通用做法，
每条都注明来源案例，方便回溯上下文。

## 嵌套滚动 / 顶栏分段收拢

来源案例：`DiscoverScreen` 搜索框随滚动收归 0（假搜索栏 + 分段收拢，2026-09）。

### Scaffold contentPadding 守恒定律（「滚动变快」问题）

- Miuix/Compose `Scaffold` 的 `contentPadding.top` **实时等于 topBar 布局高度**
  （`ScaffoldLayout` 测量 topBar 后写入 `paddingHolder`，body 在同一次 measure 里读取）。
- 收拢手势被 nestedScroll 消费的每一 px，只要让栏变矮了，内容就会跟着位移同样距离；
  child（LazyColumn）再滚的量叠加其上。因此：

  ```text
  内容位移 / 手指位移 ≤1
  （= 各段「消耗 px」×「每 px 回收高度」之和，必须 ≤1）
  ```

- **要收掉的高度必须拥有等量的独立滚动行程预算**。把 52dp 的搜索框高度
  硬塞进大标题仅 ~40dp 的 `heightOffsetLimit` 行程里，收缩速率就 >1，
  表现为「滚动变快」。解法 = 每段组件用自己的 `NestedScrollConnection`
  记账、吃自己的行程，串行走完。
- 对照实验可用：原版 scaleY 压缩不改布局高度 → padding 不缩 → 手感恒 1:1；
  一旦改成「真实高度收归 0」就必须重新做行程预算。

### consumed 符号坑（单帧 3 倍速）

- `onPreScroll` / `onPostScroll` 的**返回值（consumed）必须与 available 同号**。
  父链扣减公式是 `available - parentConsumed`（见 `NestedScrollNode.onPreScroll`）：
  向上滚动 `available.y < 0` 时若返回**正数**，外层连接拿到
  `-δ - (+δ) = -2δ` 的双倍增量 → 上层栏体被双倍喂食 → padding 单帧缩 3δ。
- 症状特征：**展开正常、收拢快**（`y > 0` 分支符号恰好是对的）。
  凡在收拢方向新增连接，先检查 `Offset(0f, -used)` 而不是 `Offset(0f, used)`。

### nestedScroll 链序 = 分段执行顺序

`NestedScrollNode` 的分发顺序（androidx.compose.ui 实测源码）：

| 事件 | 顺序 |
|---|---|
| `onPreScroll` / `onPreFling` | **外层先**（parent 先于 self，dispatcher 从最内节点向 root 走） |
| `onPostScroll` / `onPostFling` | **内层先**（self 先于 parent） |

- 想要「收拢顺序 A→B→内容」：A 的 `nestedScroll` 放在链的**最前（最外层）**，
  scrollBehavior 放中间，child 天然最后。
- 展开（post 方向）自动得到严格逆序 B→A（栈式），无需另写逻辑。
- 本项目已挂载顺序示例（DiscoverScreen LazyColumn）：

  ```kotlin
  Modifier.nestedScroll(searchCollapseConnection)      // 外：先收搜索框
      .nestedScroll(scrollBehavior.nestedScrollConnection) // 内：再收大标题
      .scrollEndHaptic()                               // 最内：只观察不消费
  ```

### postFling 吸附要跟动量方向

- 收拢进度停在半路时，松手吸附目标应优先看 `available.y` 符号：
  上甩 → 吸到 1（顺势收完），下拉 → 吸到 0，慢放 → 就近 0/1。
- 只按「就近 0.5」吸附会在上甩时**反向回弹**（半路弹回展开），
  观感就是莫名「跳/快」。
- 吸附动画（`animate + spring`）会驱动 padding 变化 → 内容在无手指时移动，
  弹簧宜短促；`fraction` 在 0/1 端点时直接 `Velocity.Zero` 返回，勿打扰
  scrollBehavior 自己的 `settleAppBar`。

## 高度动画：改高度 vs 压缩 vs 裁切

来源案例：同上（搜索框 scaleY → 真实高度归 0 的演进）。

- **scaleY（graphicsLayer）**：只改视觉不改布局。栏占位不缩、padding 不动、
  手感永远 1:1，但内容被压扁且最矮只能到「比例下限」，空间收不回来。
- **真实高度（自定义 layout / `.height()`）**：布局与视觉一起收，空间真正归还，
  必须按上面的行程预算设计，否则滚动变快。
- **clip 兜底**：内容按完整尺寸测量、超出部分裁掉。用户明确不要「裁切感」时，
  改为让**背景形状本身按当前高度重画**（见下条），内容在过小前淡出，天然不越界。
- 顺序：能直接改高度就直接改 → 内容撑不住就自绘组件按当前高度作画 →
  最后才考虑 clip。

### miuix 组件内部写死最小高度

- `InputField` 内部 `heightIn(min = SearchBarDefaults.InputFieldMinHeight)`（45dp），
  外部强压高度内容会溢出，**不能**当收缩动画的载体。
- 替代：按同样视觉规格自绘假组件（胶囊 `CircleShape` 背景 + `MiuixIcons.Basic.Search`
  + 17sp/Medium 占位文案，颜色取 `surfaceContainerHigh` / `onSurfaceContainerHigh`），
  高度随进度重画；内容 alpha 在胶囊高度低于安全阈值（如 30dp）前于
  `graphicsLayer {}` 里淡出 → 无需 clip。
- `SearchBarDefaults.InputFieldMinHeight / InputFieldFontSize` 是 public 的，
  自绘时直接引用保持与真组件一致。

### 滚动驱动的 UI 不要在组合期读进度（miuix 大标题写法）

- 参考 `TopAppBar`：`Modifier.offset { scrolledOffset() }`、`graphicsLayer { alpha = … }`
  把状态读数放进 **layout/draw 阶段**，进度变化只触发布局/绘制，不重组子树。
- 落地写法：外层用生产者 lambda 传进度 `fraction: () -> Float`，
  高度在 `.layout { }` 里算、内容淡出在 `.graphicsLayer { }` 里算；
  组合期只留与滚动无关的常量。

## LazyColumn 的 key 锚点与异步初始化

来源案例：首页「快捷方式」模块不在首位时，冷启动列表自己往下滚。

- `item(key = …)` 不只是给动画用的：**LazyList 会把「首个可见项」的 key 钉在视口顶部**。
  item 集合或顺序变化后，它按 key 找回新下标，让同一项继续待在顶部。
- 所以「先按默认值铺一版，再切成真值」在带 key 的列表里是有代价的：
  首帧那个位置的项一挪位，列表就整体跟着滚过去。
- 首页踩的正是这条：首帧按默认顺序渲染，快捷方式模块的入口还没读到、
  只渲染出 0 高度的空壳并占了 index 0；真值把它排到第三位后，列表跳到第三位。
- 对策：**决定列表形状的异步配置到齐之前，一个 item 都别铺**（包括尾部的常驻按钮）；
  就绪后再校准一次 `scrollToItem(0)`。别用「默认值先顶着」，那只是把跳动藏到后面。
- 判断「形状」的依据不止顺序：模块启用集合、模块自己的可见子项（如快捷方式入口）同样算。

## 验证

- 纯 Kotlin 改动：`./gradlew.bat :app:compileInternalDebugKotlin` 快速验类型。
- 提交前按 `CONTEXT.md` 跑 `testInternalDebugUnitTest` + `assembleInternalDebug`。
- 手感问题（倍速/回弹/顺序）没法靠单测抓，推演模型：
  「每帧 consumed 账本 = available 总额守恒 + padding 收缩率」列一遍即可定位。
