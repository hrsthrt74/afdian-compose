package com.hrsthrt74.afdian.ui.components.basic

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.AppLog
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.PlainTooltip
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TooltipAnchorPosition
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.basic.TooltipDefaults
import top.yukonga.miuix.kmp.basic.rememberTooltipState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 组件日志 TAG */
private const val TooltipInfoButtonLogTag = "TooltipInfoButton"

/**
 * 说明按钮的预设样式：把图标大小、字重与热区留白绑定成套，
 * 与 [CopyButtonStyle] 同构，避免调用处分别传三个参数导致风格脱节。
 *
 * 各档位的图标与热区留白成对设计（取值与 [CopyButtonStyle] 一致）：
 * - [Small]：16.dp 图标配 [MiuixIconWeight.Demibold]，图标缩小后笔画少，用粗字重补视觉分量；
 * - [Tiny]：同 [Small] 的图标，但留白收到 0.dp，热区就是图标本身，行内/表格里不会顶高所在行；
 * - [Default]：24.dp 图标配 [MiuixIconWeight.Regular]，与 Miuix Icon 默认观感一致。
 *
 * 热区都是 `iconSize + touchTargetPadding`：Default 档 40.dp 正好等于
 * Miuix IconButton 的默认热区，Small 档 28.dp 适合行内紧凑场景，
 * Tiny 档 16.dp 适合「表格局部行」这类行高敏感、且该行元素间距充裕的窄位置。
 */
enum class TooltipInfoButtonStyle(
    /** 图标绘制尺寸 */
    val iconSize: Dp,

    /** 图标字重 */
    val iconWeight: MiuixIconWeight,

    /** 图标与热区边缘的留白：热区 = iconSize + 此值 */
    val touchTargetPadding: Dp,
) {
    /** 小号：行内紧凑场景（图标小，留白也收窄，占位更紧凑） */
    Small(16.dp, MiuixIconWeight.Demibold, 12.dp),

    /** 极小：行高敏感的位置（表格单元格、标签行尾），热区与图标同大，不撑高所在行 */
    Tiny(16.dp, MiuixIconWeight.Demibold, 4.dp),

    /** 默认：与 Miuix Icon 默认尺寸一致（24.dp + 16.dp = 40.dp） */
    Default(24.dp, MiuixIconWeight.Regular, 16.dp),
}

/** 按字重取「信息」图标：注意 MiuixIcons.Info 本身即 Regular 档 */
private val MiuixIconWeight.infoIcon: ImageVector
    get() = when (this) {
        MiuixIconWeight.Light -> MiuixIcons.Light.Info
        MiuixIconWeight.Normal -> MiuixIcons.Normal.Info
        MiuixIconWeight.Regular -> MiuixIcons.Regular.Info
        MiuixIconWeight.Medium -> MiuixIcons.Medium.Info
        MiuixIconWeight.Demibold -> MiuixIcons.Demibold.Info
    }

/**
 * 说明按钮：Miuix 的 ⓘ 图标，**点击**和**长按**都能弹出 Miuix 提示气泡。
 *
 * 用于「这里有一句话要说清楚，但不想占版面」的场景：正文旁边一个 ⓘ，
 * 点一下（或长按）就把补充说明弹出来。
 *
 * 与 Miuix 自带 `TooltipBox` 的差别：
 * - 默认只有长按（触屏）/ 悬停（鼠标）才弹，这里在 `onClick` 里补上
 *   `TooltipState.show()`，让点按也能触发（长按路径仍由 Miuix 自己接管，
 *   它会吞掉长按后的抬起事件，所以长按不会顺带触发一次 [onClick]）；
 * - 传 `focusable = true`：气泡是一个能拿到输入焦点的独立窗口，
 *   点击气泡外部（或返回键）会走 `onDismissRequest` 关闭，配合常驻模式才有意义。
 *
 * 行为细节：
 * - 再次点击按钮会**收起**已展开的气泡（切换语义），不会出现「点了没反应又连点几下」；
 * - 默认常驻：点按触发时若沿用 Miuix 的短时自动消失（1.5s），用户根本读不完；
 * - 图标固定为 Miuix 的 ⓘ，尺寸/字重由 [style] 成套给出，调用处不必自己拼。
 *
 * 状态归属：只有瞬时展示状态（气泡是否展开）在组件内部，
 * 业务状态（是否加载中、是否选中……）仍由调用方传入。
 *
 * 使用示例：
 * ```kotlin
 * // 纯提示（点击只切气泡，没有别的动作）：不传 onClick
 * TooltipInfoButton(tooltipText = "该数据每 15 分钟同步一次")
 *
 * // 默认档 + 额外动作：40.dp 热区，用在弹层标题栏、卡片头部
 * TooltipInfoButton(
 *     tooltipText = "如果你有分销需求，利用无折扣优惠码，可以知道分销渠道的销量。",
 *     onClick = { showDetail() },
 * )
 *
 * // 小号档：28.dp 热区，用在正文行内
 * TooltipInfoButton(
 *     tooltipText = "该数据每 15 分钟同步一次",
 *     style = TooltipInfoButtonStyle.Small,
 * )
 *
 * // 自定义图标颜色：用在警示/强调语境，让 ⓘ 跟着该语境的主色走
 * TooltipInfoButton(
 *     tooltipText = "此操作不可撤销",
 *     iconTint = MiuixTheme.colorScheme.error,
 * )
 * ```
 *
 * @param tooltipText 气泡文案，同时也是图标的无障碍描述默认值
 * @param onClick 点击后的额外动作；点击本身已经用来切换气泡，纯提示场景可以不传
 * @param modifier 外部修饰符（例如行内排版需要的 `Modifier.padding(...)`）
 * @param enabled 是否可点；false 时点击不响应，也不弹气泡
 * @param style 预设样式（图标大小 + 字重 + 热区留白成套），默认 [TooltipInfoButtonStyle.Default]
 * @param iconTint 图标颜色，默认 [MiuixTheme.colorScheme.onSurfaceVariantActions]（低调的附注色）
 * @param positioning 气泡相对按钮的方位，默认在上方；空间不足时 Miuix 会自动翻转到另一侧
 * @param persistent 是否常驻：true 一直显示到点外部/返回键/再次点击，false 沿用 Miuix 默认的短时自动消失
 * @param contentDescription 无障碍描述，默认取 [tooltipText]（两者一致时不必重复传）
 */
@Composable
fun TooltipInfoButton(
    tooltipText: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
    style: TooltipInfoButtonStyle = TooltipInfoButtonStyle.Default,
    iconTint: Color = MiuixTheme.colorScheme.onSurfaceVariantActions,
    positioning: TooltipAnchorPosition = TooltipAnchorPosition.Above,
    persistent: Boolean = true,
    contentDescription: String = tooltipText,
) {
    // 常驻气泡：点击触发时不会被 1.5s 超时秒关，用户能读完再点外部关闭
    val tooltipState = rememberTooltipState(isPersistent = persistent)
    // show() 是挂起函数（内部靠 MutatorMutex 排队，要等被关闭才返回），必须用协程调起
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // 点击热区随预设同步缩放：Miuix IconButton 默认 minWidth/minHeight 是 40.dp，
    // 恰好等于 Default 档「24.dp 图标 + 16.dp 留白」；Small 档留白更窄，占位更紧凑
    val minTouchTarget = style.iconSize + style.touchTargetPadding

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning),
        tooltip = {
            // 反色气泡（浅色主题下是深色小气泡），与 Miuix 其他浮层风格一致
            PlainTooltip {
                Text(
                    text = tooltipText,
                    style = MiuixTheme.textStyles.footnote1,
                )
            }
        },
        state = tooltipState,
        focusable = true,
        // 保留长按/悬停触发，否则点击这条路自己接管后就丢了长按
        enableUserInput = enabled,
    ) {
        IconButton(
            onClick = {
                if (tooltipState.isVisible) {
                    // 已展开：再点一次是收起，避免重复点击把气泡反复重开
                    AppLog.d(TooltipInfoButtonLogTag, "点击收起气泡：$tooltipText")
                    tooltipState.dismiss()
                } else {
                    AppLog.d(TooltipInfoButtonLogTag, "点击展开气泡：$tooltipText")
                    // 先震动再弹：与项目其他「点击触发浮层」的交互一致
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch { tooltipState.show() }
                }
                onClick()
            },
            modifier = modifier,
            enabled = enabled,
            minWidth = minTouchTarget,
            minHeight = minTouchTarget,
        ) {
            // ⓘ 图标：尺寸与字重都由预设成套给出，调用处不需要再传；
            // 颜色默认 onSurfaceVariantActions——ⓘ 默认只是「附注」而不是主操作，
            // 用动作色的浅档保证放进任何一层卡片/表格都不会抢正文内容的视觉层级；
            // 需要在警示/强调场景里突出时，调用处用 iconTint 覆盖即可
            Icon(
                imageVector = style.iconWeight.infoIcon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(style.iconSize),
            )
        }
    }
}