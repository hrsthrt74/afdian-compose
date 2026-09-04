# 设计规范

本文件记录项目特有的 UI 约定和查阅指引。Miuix 组件用法、颜色语义、字体排版等通用规范请直接查阅 miuix-skill 。

## 配色

直接使用 miuix-skill 定义的配色即可。

## 本项目 UI 模式

### 卡片

使用 miuix 的 Card 来包裹元素时，请注意：Card 的职能只有：提供背景颜色、提供预设的圆角裁切。

对于 Card 的边距，请不要给 Card 加边距，而是让 Card 只做好自己的职能，padding 由内部的 Box / Column / … 负责。

### Dialog

所有 Dialog 统一用 `AppDialog` + `DialogState`，不再自写 `WindowDialog`。

```kotlin
val dialogState = rememberDialogState()
// 打开
dialogState.show()
// AppDialog 内部自动处理关闭，页面无需手动写 onDismissRequest
```

破坏性操作用 `destructive = true`，确认按钮变红。

### 头像

需要头像时使用 `Avatar` 组件。

### 占位元素

图片加载前/失败的占位背景色统一用 `MiuixTheme.colorScheme.disabledSecondary`。

### 空状态

空状态提示文本统一样式：

```kotlin
Text(
    text = "...",
    style = MiuixTheme.textStyles.footnote1,
    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    textAlign = TextAlign.Center,
    modifier = Modifier.fillMaxWidth().padding(16.dp),
)
```

### 通用组件

需要以下能力时直接用封装好的组件，不要自己重写：

- 加载中 → `LoadingComponent`
- 动态卡片 → `PostCard`
- 创作者卡（长条形） → `CreatorListItem`
- 创作者卡（矩形） → `CreatorLargeCard` / `CreatorSmallCard`
- and more in ui/components/

### 点击按压下沉反馈

对于 Card 包裹内容的场景，请只给 Card 添加此 modifier。

```kotlin
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable

Modifier.pressable(interactionSource = null, indication = SinkFeedback(), delay = null)
```

modifier.clickable 请只放在内部元素（`Box`/`Column`）上，`Card` 只做容器。

### Overscroll 触觉反馈

对于可滑动的 `LazyColumn` 等元素，请总是添加：

```kotlin
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
Modifier.scrollEndHaptic()
```

scrollEndHaptic 不需要传参。

### 模糊效果

- `rememberBlurBackdrop(enabled)` 返回 `LayerBackdrop?`，`null` 表示模糊关闭或设备不支持。
- `blurBarColor(backdrop)`：模糊开启时顶栏必须透明，否则遮住模糊层。
- 采样层 `layerBackdrop(backdrop)` 只包滚动内容，不包带 `textureBlur` 的栏，否则会原生层崩溃。

### 状态管理

- 业务状态放 ViewModel，临时 UI 状态用 `remember`。
- 加载中、空状态、错误、重试显式建模，不要用一个 Boolean 含糊表达。
