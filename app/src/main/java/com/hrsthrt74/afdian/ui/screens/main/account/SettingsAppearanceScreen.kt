package com.hrsthrt74.afdian.ui.screens.main.account

import android.app.Application
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hrsthrt74.afdian.BuildConfig
import com.hrsthrt74.afdian.R
import com.hrsthrt74.afdian.data.settings.DATACARD_FONT_WIDTH_MAX
import com.hrsthrt74.afdian.data.settings.DATACARD_FONT_WIDTH_MIN
import com.hrsthrt74.afdian.data.settings.DATACARD_FONT_WIDTH_SLIDER_STEPS
import com.hrsthrt74.afdian.data.settings.ThemeColorPreset
import com.hrsthrt74.afdian.ui.components.DataCard
import com.hrsthrt74.afdian.ui.components.basic.AppDialog
import com.hrsthrt74.afdian.ui.components.basic.SettingItemIcon
import com.hrsthrt74.afdian.ui.components.basic.rememberDialogState
import com.hrsthrt74.afdian.ui.theme.DATACARD_VALUE_FONT_FEATURE_SETTINGS
import com.hrsthrt74.afdian.ui.theme.LocalBottomBarStyle
import com.hrsthrt74.afdian.ui.theme.LocalCustomThemeColor
import com.hrsthrt74.afdian.ui.theme.LocalDataCardFontFamily
import com.hrsthrt74.afdian.ui.theme.LocalThemeColor
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.theme.ThemeColorTransition
import com.hrsthrt74.afdian.ui.theme.afdianDarkColorScheme
import com.hrsthrt74.afdian.ui.theme.afdianLightColorScheme
import com.hrsthrt74.afdian.ui.theme.isDarkColorScheme
import com.hrsthrt74.afdian.ui.theme.rememberMiSansCondensedFontFamily
import com.hrsthrt74.afdian.ui.util.AfdianCardDefaults
import com.hrsthrt74.afdian.ui.viewmodel.SettingsViewModel
import com.kyant.shapes.Capsule
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Help
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable
import kotlin.math.roundToInt

// 配色模式选项，索引与 SettingsStore 的 0/1/2 约定一致
private val colorModeNames = listOf("跟随系统", "浅色", "深色")

// 主题选项，索引 0=MIUIX 1=Monet（对应 SettingsStore 的 monetEnabled）
private val themeStyleNames = listOf("MIUIX", "Monet")

/**
 * 「跟随壁纸」那一项的颜色球用的色轮。
 *
 * 这一档没有固定色值（颜色由系统壁纸决定），用一圈彩虹来区别于其它纯色球，
 * 暗示「这里出来的是任意颜色」。
 */
private val SystemWallpaperBrush = Brush.sweepGradient(
    colors = listOf(
        Color(0xFFE53935), Color(0xFFFFB300), Color(0xFF43A047),
        Color(0xFF00ACC1), Color(0xFF3949AB), Color(0xFF8E24AA), Color(0xFFE53935),
    ),
)

/** 色球外框直径（含选中描边）。描边画在外框上，里面的球保持同一尺寸，切换选中不会跳动。 */
private val ThemeColorBallFrameSize = 36.dp

/** 色球本体直径。比外框小 8dp，选中时描边与球之间留空隙，看起来是「环」而不是「粗边」。 */
private val ThemeColorBallSize = 28.dp

/**
 * 「彩虹」球上那颗 Help 图标的边长。
 *
 * 刻意比球体小一圈（28dp 球 + 18dp 图标）：Demibold 的 Help 是一颗实心圆，
 * 图标和球体一样大时会把球色整片盖住，看着就像「这颗球比别的小一圈」。
 */
private val ThemeColorBallIconSize = 18.dp

/**
 * 主题色选择器里的单颗色球。
 *
 * [color] 为 null 表示「跟随壁纸」，此时画 [SystemWallpaperBrush] 色轮。
 * [selected] 时在外框上描一圈主题色，与球之间留空隙。
 * [showAddIcon] 只在「自定义」档被选中时为 true：球上叠一个「+」，提示再点一次可以调色。
 * [icon] 非空时代替色球本身（彩虹档：没有固定颜色可画，球底用 [iconBallColor]、上面叠图标）。
 * [iconTint] 是 [icon] 的颜色，[iconBallColor] 是图标底下那颗球的颜色。
 */
@Composable
private fun ThemeColorBall(
    color: Color?,
    selected: Boolean,
    showAddIcon: Boolean = false,
    icon: ImageVector? = null,
    iconTint: Color = MiuixTheme.colorScheme.primary,
    iconBallColor: Color = MiuixTheme.colorScheme.secondaryContainer,
) {
    // 外框：选中时描边，未选中时只有占位（保证两种状态下球心位置一致）
    Box(
        modifier = Modifier
            .size(ThemeColorBallFrameSize)
            .then(
                if (selected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MiuixTheme.colorScheme.primary,
                        shape = CircleShape,
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 球体：固定色值铺纯色，跟随壁纸铺色环；彩虹档铺一层指定球底再叠图标
        val ballModifier = Modifier.size(ThemeColorBallSize)
        when {
            icon != null -> {
                // 球底
                Box(modifier = ballModifier.background(color = iconBallColor, shape = CircleShape))
                // 球上的图标
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(ThemeColorBallIconSize),
                )
            }
            color != null -> Box(modifier = ballModifier.background(color = color, shape = CircleShape))
            else -> Box(modifier = ballModifier.background(brush = SystemWallpaperBrush, shape = CircleShape))
        }
        // 「+」图标：只在自定义档被选中时出现
        if (showAddIcon) {
            Icon(
                imageVector = MiuixIcons.Demibold.Add,
                contentDescription = "修改自定义颜色",
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * 主题色选择器：横向滚动的「上面色球、下面名字」列表，选中项色球加描边。
 *
 * 用横向列表而不是下拉：颜色本身就是内容，全部铺开比收进菜单更容易比对；
 * 条目数量是固定的几个预设，横向一屏基本能看全，剩下的滑一下就到。
 *
 * 第一项是「自定义」：球用用户上次调好的颜色，**已选中时**球上出现「+」，
 * 再点一次才弹出调色盘（第一次点只是切到自定义档，不会突然弹窗）。
 *
 * 点击刻意不带水波纹：波纹会盖住色球本身，而选中态已经由描边表达清楚了。
 *
 * @param presets 当前主题下可选的档位（见 [ThemeColorPreset.selectable]）
 * @param selected 当前生效的档位（读 [LocalThemeColor]，不是 SettingsViewModel 的流）
 * @param customColor 自定义档的具体色值（读 [LocalCustomThemeColor]），给那颗球上色
 * @param onSelect 选中回调
 * @param onCustomColorChange 调色盘确认后的回调（保存自定义色值）
 */
@Composable
private fun ThemeColorPicker(
    presets: List<ThemeColorPreset>,
    selected: ThemeColorPreset,
    customColor: Color,
    onSelect: (ThemeColorPreset) -> Unit,
    onCustomColorChange: (Color) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    // 调色盘弹窗：草稿色只在点「确定」时落盘，拨调色盘的过程不写 DataStore
    val colorDialogState = rememberDialogState()
    var draftColor by remember { mutableStateOf(customColor) }
    // 彩虹档的警告弹窗：确认之后才真正启用这个配色
    val rainbowDialogState = rememberDialogState()
    // 彩虹那颗的球色 / 图标色：用项目里「浮层内 Card」的容器色 + 内容色（overlayColors），
    // 于是它看起来就是一颗「浮层里的卡片」配色，与弹层里的自定义内容同一套颜色
    val rainbowCardColors = AfdianCardDefaults.overlayColors()
    val rainbowBallColor = rainbowCardColors.color
    val rainbowIconColor = rainbowCardColors.contentColor

    // 上下各留 12dp：与相邻 miuix 偏好项的内边距对齐（上方是「主题」下拉、下方是卡片底缘）
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        // 标题：与 miuix 偏好项的标题同一套排版（16dp 内边距 + headline1 字号 + Medium）
//        Text(
//            text = "主题色",
//            fontSize = MiuixTheme.textStyles.headline1.fontSize,
//            fontWeight = FontWeight.Medium,
//            color = MiuixTheme.colorScheme.onBackground,
//            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
//        )
        LazyRow(
            // 标题与卡片右缘各留 16dp，滑到尽头时内容不会贴边
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),    // 稍微大点
        ) {
            items(presets, key = { it.storageValue }) { preset ->
                val isSelected = preset == selected
                val isCustom = preset == ThemeColorPreset.Custom
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        // 按压下沉：与项目里其他可点卡片同一套反馈（SinkFeedback）
                        .pressable(interactionSource = null, indication = SinkFeedback(), delay = null)
                        // 整块（色球 + 名字）都是热区，并给出单选语义。
                        .selectable(
                            selected = isSelected,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.RadioButton,
                            onClick = {
                                // 触感反馈内聚在条目里，与页面其他选择项一致
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (preset == ThemeColorPreset.Rainbow) {
                                    // 彩虹档先弹警告：性能代价写在弹窗里，确认后才真的切过去
                                    rainbowDialogState.show()
                                } else if (isCustom && isSelected) {
                                    // 已经选中「自定义」→ 再点一次才开调色盘，并从当前色开始
                                    draftColor = customColor
                                    colorDialogState.show()
                                } else {
                                    onSelect(preset)
                                }
                            },
                        )
                        .padding(vertical = 4.dp),
                ) {
                    // 色球（选中加描边；自定义档选中时球上叠「+」；
                    // 彩虹档的球用浮层内容器色，上面叠一个同系内容色的 Help 图标）
                    ThemeColorBall(
                        color = if (isCustom) customColor else preset.color,
                        selected = isSelected,
                        showAddIcon = isCustom && isSelected,
                        icon = if (preset == ThemeColorPreset.Rainbow) MiuixIcons.Demibold.Help else null,
                        iconTint = rainbowIconColor,
                        iconBallColor = rainbowBallColor,
                    )
                    // 名称：选中时用主色，其余用次要文字色
                    Text(
                        text = preset.label,
                        style = MiuixTheme.textStyles.footnote2,
                        color = if (isSelected) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        },
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    // 调色盘弹窗：Miuix 的 ColorPalette（与官方 demo 同一用法），点选即时预览、确定才生效
    AppDialog(
        state = colorDialogState,
        title = "自定义主题色",
        confirmText = "确定",
        onConfirm = {
            // 先保存色值再切档：主题换色的那一刻用的已经是新颜色
            onCustomColorChange(draftColor)
            onSelect(ThemeColorPreset.Custom)
        },
    ) {
        // 顶部色带：用草稿色试算「真选了它」会得到的色板，平分成三段预览
        val previewColors = rememberPreviewColors(draftColor)
        Row(modifier = Modifier.fillMaxWidth().height(32.dp).clip(Capsule())) {
            // 主色
            Box(Modifier.weight(1f).fillMaxHeight().background(previewColors.primary))
            // 主色变体
            Box(Modifier.weight(1f).fillMaxHeight().background(previewColors.primaryVariant))
            // 强调容器（下拉选中项的底色）
            Box(Modifier.weight(1f).fillMaxHeight().background(previewColors.tertiaryContainer))
        }
        Spacer(Modifier.height(12.dp))
        // 调色盘本体：关掉它自带的单色预览条，改用上面那三段
        ColorPalette(
            color = draftColor,
            onColorChanged = { draftColor = it },
            includeGrayColumn = false,
            showPreview = false,
        )
        // 内容与按钮之间留一点下边距，别贴着按钮
        Spacer(modifier = Modifier.height(8.dp))
    }

    // 彩虹档的警告弹窗：说清楚这是娱乐向配色、性能可能下降，确认后才切换并给个提示
    AppDialog(
        state = rainbowDialogState,
        title = "彩虹主题色",
        summary = "此配色仅供娱乐，可能会导致性能大幅下降，请勿反馈此配色相关的问题~",
        confirmText = "确定",
        onConfirm = {
            onSelect(ThemeColorPreset.Rainbow)
            Toast.makeText(context, "Enjoy~", Toast.LENGTH_SHORT).show()
        },
    )
}

/**
 * 用草稿色试算一套色板，供调色盘顶部的三段色带预览。
 *
 * 与 [com.hrsthrt74.afdian.ui.theme.AfdianTheme] 用同一套映射：MIUIX 主题走
 * `afdianLight/DarkColorScheme`，Monet 主题走种子色生成（`ThemeController` 在
 * `currentColors()` 内部按当前模式算）。这里只算不用，也不改全局主题。
 *
 * @param color 调色盘当前草稿色
 */
@Composable
private fun rememberPreviewColors(color: Color): Colors {
    // 跟随全局主题的深浅：深色主题下预览深色色板，免得色带比对话框还亮
    val dark = isDarkColorScheme(MiuixTheme.colorScheme)
    // 是否 Monet 同样读全局主题（ThemeColorTransition 只换色板、不改模式，读到的仍是真值）
    val monet = MiuixTheme.isDynamicColor
    val controller = remember(color, dark, monet) {
        if (monet) {
            ThemeController(
                colorSchemeMode = if (dark) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight,
                keyColor = color,
            )
        } else {
            ThemeController(
                colorSchemeMode = if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light,
                lightColors = afdianLightColorScheme(color),
                darkColors = afdianDarkColorScheme(color),
            )
        }
    }
    return controller.currentColors()
}

/**
 * 设置 → 外观。
 *
 * 页内含原设置页的三组内容：
 * - **外观**：底栏样式、配色模式、主题（MIUIX / Monet）、主题色、模糊、渐进模糊；
 * - **动画**：高级过渡动画、手势返回、预测性返回；
 * - **数据卡片**：两张预览卡、数字滚动、单位开关、数值窄字体与字体宽度。
 *
 * 本页是唯一能改配色的页面，因此 [ThemeColorTransition] 只包在这里：
 * 切换深浅模式 / 主题 / 主题色时整页渐变过去，而不是瞬间跳色；总览页不再参与过渡。
 */
@Composable
fun SettingsAppearanceScreen(
    onBack: () -> Unit,
    backStack: NavBackStack,
    viewModel: SettingsViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as Application
        )
    )
) {
    ThemeColorTransition {
        SettingsAppearanceContent(onBack = onBack, backStack = backStack, viewModel = viewModel)
    }
}

@Composable
private fun SettingsAppearanceContent(
    onBack: () -> Unit,
    backStack: NavBackStack,
    viewModel: SettingsViewModel,
) {
    // 本页用到的设置项
    val colorMode by viewModel.colorMode.collectAsState()
    // 主题相关 UI（下拉选中项、主题色高亮）一律以「全局主题真正生效的状态」为准，
    // 而不是读页面自己的 SettingsViewModel 流：VM 是进入设置页时才创建的，初值固定是默认档，
    // DataStore 读回来之前会先按默认值渲染一帧，于是下拉/色球会先亮错一项再跳回来 —— 就是那个「闪一下」。
    // 全局主题在 App 启动时就按真值建好了，抄它的结果最稳。
    // 注意 ThemeColorTransition 里那层 MiuixTheme(colors = ...) 只换色板、不下发 colorSchemeMode，
    // 所以这里读到的仍是外层全局主题的真实模式，不会被过渡色板顶替。
    val monetActive = MiuixTheme.isDynamicColor
    // 当前生效的主题色（「跟随壁纸」在 MIUIX / 低版本上已被解析成 MIUIX 蓝）
    val selectedThemeColor = LocalThemeColor.current
    // 自定义档的具体色值：选择器里「自定义」那颗球用它上色
    val customThemeColor = LocalCustomThemeColor.current
    // 系统壁纸取色需要 Android 12 / API 31 以上；低版本要在选择器里藏掉「跟随壁纸」
    val systemWallpaperSupported = remember { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
    val advancedTransition by viewModel.advancedTransition.collectAsState()
    val gestureBack by viewModel.gestureBack.collectAsState()
    val predictiveBack by viewModel.predictiveBack.collectAsState()
    // 底栏样式：读根组合下发的组合局部，而不是 VM 的流 —— VM 初值恒为默认档（常规），
    // 选了悬浮再进本页会先闪一下常规。理由与上面主题那一段完全一样。
    val bottomBarStyle = LocalBottomBarStyle.current
    val showIncomeUnit by viewModel.showIncomeUnit.collectAsState()
    val showCountUnit by viewModel.showCountUnit.collectAsState()
    val rollingNumberEnabled by viewModel.rollingNumberEnabled.collectAsState()
    val dataCardCustomFontEnabled by viewModel.dataCardCustomFontEnabled.collectAsState()
    val dataCardFontWidth by viewModel.dataCardFontWidth.collectAsState()
    val themeSettings = LocalThemeSettings.current

    // 字体宽度拖动的本地草稿：只在松手时落盘，避免拖动过程每一帧都写一次 DataStore。
    // 以已落盘的 dataCardFontWidth 为 key：首次读出或别处改动时重新同步到真实值。
    var draftFontWidth by remember(dataCardFontWidth) {
        mutableFloatStateOf(dataCardFontWidth.toFloat())
    }
    // 预览卡专用字体族：直接跟随草稿宽度构建，于是拖动滑块时两张预览卡会实时变宽变窄。
    // 它只覆盖设置页这一小片子树（见下方嵌套 Provider），不影响全局下发值。
    val previewFontFamily = if (dataCardCustomFontEnabled) {
        rememberMiSansCondensedFontFamily(draftFontWidth.roundToInt())
    } else {
        null
    }
    // 「数值窄字体」这一项 summary 专用的字体族：**恒用** MiSans Condensed、恒跟随草稿宽度，
    // 与上面的开关状态无关 —— 于是这段描述本身就是一条活字体样本：
    // 开关关着也能提前看到字体长什么样，拖动滑块时样例同步变宽变窄。
    // 必须和 previewFontFamily 分开构建：后者在开关关闭时得是 null（预览卡要回到系统字体），
    // 而这里要的正是「始终」。
    // 宽度相同时 remember 会命中复用，与 previewFontFamily 各持一份对象也不会重复解析字体，
    // 因为 Typeface 缓存在字体解析器里按 (resId, weight, style, variationSettings) 命中。
    val summaryFontFamily = rememberMiSansCondensedFontFamily(draftFontWidth.roundToInt())

    // 预览卡的重播令牌：0 = 首次渲染不播放，数字直接落位。
    // 自增会让 key(replayToken) 重建预览卡，从而重新触发一次完整滚动
    //（滚轮用 rememberSaveable 记住「已播过」，只有重建才能重播）。
    var replayToken by remember { mutableStateOf(0) }
    // 唯一的触发源是「把开关从关打到开」：让用户立刻看到刚打开的效果，
    // 而刚进设置页时不会自己播（动画只有约 460ms，自动播很容易被错过）。
    // 与「上一次的值」比较而不是直接触发，正是为了滤掉首次组合。
    var lastRollingNumberEnabled by remember { mutableStateOf(rollingNumberEnabled) }
    LaunchedEffect(rollingNumberEnabled) {
        if (rollingNumberEnabled && !lastRollingNumberEnabled) replayToken++
        lastRollingNumberEnabled = rollingNumberEnabled
    }

    val haptic = LocalHapticFeedback.current

    SettingsPageScaffold(
        title = "外观",
        onBack = onBack,
        backStack = backStack,
    ) {
        item { SmallTitle(text = "底栏样式", modifier = Modifier.padding(start = 4.dp)) }
        item {
            // 底栏样式：两张实时预览卡，卡片里画的是真 navbar（等比缩小、不可交互），
            // 切换后由根组合回推 LocalBottomBarStyle，主界面底栏立即换样式
            BottomBarStyleSelector(
                style = bottomBarStyle,
                onSelect = viewModel::setBottomBarStyle,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        item { SmallTitle(text = "配色", modifier = Modifier.padding(start = 4.dp)) }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                // 配色模式：只决定明暗（跟随系统/浅色/深色），与「主题」正交
                OverlayDropdownPreference(
                    title = "配色模式",
                    startAction = { SettingItemIcon(ImageVector.vectorResource(R.drawable.ic_dark)) },
                    items = colorModeNames,
                    selectedIndex = colorMode.coerceIn(0, colorModeNames.lastIndex),
                    onSelectedIndexChange = viewModel::setColorMode
                )
                // 主题：MIUIX（Miuix 静态色板，默认）/ Monet（按壁纸或主题色生成整套色板）
                OverlayDropdownPreference(
                    title = "配色主题",
                    startAction = { SettingItemIcon(MiuixIcons.Background) },
                    items = themeStyleNames,
                    selectedIndex = if (monetActive) 1 else 0,
                    onSelectedIndexChange = { index -> viewModel.setMonetEnabled(index == 1) },
                )
                // 主题色：两种主题共用一套档位。MIUIX 下「跟随壁纸」只让主色随壁纸，
                // 所以两种主题都提供这一档；低版本设备没有系统取色能力才隐藏。
                // 当前高亮的是全局主题真正生效的那一档
                val selectableColors = ThemeColorPreset.selectable(
                    systemWallpaperSupported = systemWallpaperSupported,
                )
                ThemeColorPicker(
                    presets = selectableColors,
                    selected = selectedThemeColor,
                    customColor = customThemeColor,
                    onSelect = viewModel::setThemeColor,
                    onCustomColorChange = viewModel::setCustomThemeColor,
                )
            }
        }

        if (BuildConfig.SUPPORT_BLUR) {
            item { SmallTitle(text = "模糊", modifier = Modifier.padding(start = 4.dp)) }
        }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)){
                // legacy 低配版不支持模糊（SUPPORT_BLUR=false），开关直接隐藏，
                // 避免用户打开一个永远不会生效的选项
                if (BuildConfig.SUPPORT_BLUR) {
                    SwitchPreference(
                        checked = themeSettings.enableBlur,
                        onCheckedChange = viewModel::setEnableBlur,
                        title = "模糊效果",
                        summary = "适用于顶栏、底栏、数字滚动等",
                        startAction = { SettingItemIcon(ImageVector.vectorResource(R.drawable.ic_blur)) },
                    )
                    // 渐进模糊是模糊效果的子选项，总开关关掉时一起隐藏，避免出现无效选项
                    AnimatedVisibility(visible = themeSettings.enableBlur) {
                        SwitchPreference(
                            checked = themeSettings.progressiveBlur,
                            onCheckedChange = viewModel::setProgressiveBlur,
                            title = "渐进模糊",
                            startAction = { SettingItemIcon() },
                        )
                    }
                }
            }
        }

        item { SmallTitle(text = "动画", modifier = Modifier.padding(start = 4.dp)) }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                SwitchPreference(
                    checked = advancedTransition,
                    onCheckedChange = viewModel::setAdvancedTransition,
                    title = "高级过渡动画（实验性）",
                    summary = "创作者主页以卡片展开的方式打开和返回",
                    startAction = { SettingItemIcon(ImageVector.vectorResource(R.drawable.ic_size)) },
                )
                SwitchPreference(
                    checked = gestureBack,
                    onCheckedChange = viewModel::setGestureBack,
                    title = "手势返回",
                    summary = "页面任意位置向右滑动以返回",
                    startAction = { SettingItemIcon(ImageVector.vectorResource(R.drawable.ic_back_alt)) },
                )
                // legacy 低配版不支持系统预测性返回（SUPPORT_PREDICTIVE_BACK=false），
                // 开关直接隐藏，避免出现永不生效的选项
                if (BuildConfig.SUPPORT_PREDICTIVE_BACK) {
                    SwitchPreference(
                        checked = predictiveBack,
                        onCheckedChange = viewModel::setPredictiveBack,
                        title = "预测性返回",
                        startAction = { SettingItemIcon(ImageVector.vectorResource(R.drawable.ic_pred_back)) },
                    )
                }
            }
        }

        item { SmallTitle(text = "数据卡片", modifier = Modifier.padding(start = 4.dp)) }
        item {
            // 这两张预览卡身兼三职：
            //   1) 切换下面两个单位开关时，单位会立即出现/消失；
            //   2) 兼作数字滚动动画的预览 —— 进页面时直接落位、不播放（动画只有约 460ms，
            //      自动播很容易被错过），只有把「数字滚动动画」开关从关打到开时才播一次。
            //      令牌一变就重建预览卡，滚轮随之从 0 重新滚一遍。
            //   3) 兼作数值字体的预览 —— 开着字体开关时，下面拖宽度滑块能实时看到变化。
            // 字体 Provider 刻意套在 key(replayToken) 外面：宽度变化只该让数字「重新量算」，
            // 不该触发滚轮重播（重播只属于「数字滚动动画」开关从关到开那一下）。
            CompositionLocalProvider(LocalDataCardFontFamily provides previewFontFamily) {
                key(replayToken) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DataCard(
                            title = "收入预览",
                            value = "1280.00",
                            unit = "元".takeIf { showIncomeUnit },
                            modifier = Modifier.weight(1f),
                            // 开关关掉时这里回退成普通文本，顺带预览了「关闭后」的效果
                            animateValue = rollingNumberEnabled,
                            autoPlay = replayToken > 0,
                        )
                        DataCard(
                            title = "人数预览",
                            value = "256",
                            unit = "人".takeIf { showCountUnit },
                            modifier = Modifier.weight(1f),
                            animateValue = rollingNumberEnabled,
                            autoPlay = replayToken > 0,
                        )
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp)) {
                // 数字动画总开关：关闭后所有数据卡的数字回退为普通文本显示
                SwitchPreference(
                    checked = rollingNumberEnabled,
                    onCheckedChange = viewModel::setRollingNumberEnabled,
                    title = "数字滚动动画",
                    startAction = { SettingItemIcon(ImageVector.vectorResource(R.drawable.ic_status)) },
                    // 尾部补一个「重播」按钮：自增 replayToken 会让上方两张预览卡重建
                    // （见 key(replayToken)），从而重新滚一遍，方便反复看效果
                    endActions = {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                replayToken++
                            },
                            minWidth = 28.dp,
                            minHeight = 28.dp,
                            enabled = rollingNumberEnabled,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Demibold.Play,
                                contentDescription = "重播数字滚动动画",
                                tint = if (rollingNumberEnabled) { MiuixTheme.colorScheme.onSurfaceVariantActions }
                                       else { MiuixTheme.colorScheme.disabledOnSecondaryVariant },
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                )
                SwitchPreference(
                    checked = showIncomeUnit,
                    onCheckedChange = viewModel::setShowIncomeUnit,
                    title = "显示收入单位",
                    startAction = { SettingItemIcon() },
                )
                SwitchPreference(
                    checked = showCountUnit,
                    onCheckedChange = viewModel::setShowCountUnit,
                    title = "显示人次单位",
                    startAction = { SettingItemIcon() },
                )
                // 数值窄体字体：默认关闭，关闭时数值完全沿用系统默认字体。
                // 用自写的 CondensedFontSwitchPreference（而不是 miuix 的 SwitchPreference）：
                // 只有它能把 MiSans Condensed 注入到 summary 上，让描述文字自己变成字体样本。
                CondensedFontSwitchPreference(
                    checked = dataCardCustomFontEnabled,
                    onCheckedChange = viewModel::setDataCardCustomFontEnabled,
                    title = "数字窄字体",
                    summary = "MiSans Condensed 2T",
                    summaryFontFamily = summaryFontFamily,
                )
                // 拖动走本地草稿（预览卡实时变宽），松手才落盘。
                AnimatedVisibility(visible = dataCardCustomFontEnabled) {
                    SliderPreference(
                        title = "字体宽度",
                        value = draftFontWidth,
                        onValueChange = { draftFontWidth = it },
                        onValueChangeFinished = {
                            val picked = draftFontWidth.roundToInt()
                            if (picked != dataCardFontWidth) viewModel.setDataCardFontWidth(picked)
                        },
                        valueRange = DATACARD_FONT_WIDTH_MIN.toFloat()..DATACARD_FONT_WIDTH_MAX.toFloat(),
                        // 每 10 个单位一档（10–100 共 10 档）。steps 是「两端点之间的档位数」，
                        // 所以是 档位数 - 2 = 8，换算见常量注释。
                        steps = DATACARD_FONT_WIDTH_SLIDER_STEPS,
                        // 当前档位显示在标题行右侧，拖动时能直接读出数值
                        valueText = draftFontWidth.roundToInt().toString(),
                        // 10 个档位全部画点、逐档震动。
                        // **这里刻意不传 keyPoints**：miuix 只要拿到非空 keyPoints，就会把震动用的
                        // 「关键点集合」（allKeyPointFractions）整个换成 keyPoints。而 keyPoints 一旦
                        // 覆盖了全部档位，边沿触发（"从非关键点进入关键点才震"）就再也触发不了 ——
                        // 吸附后的值每一帧都停在关键点上，Step 会整体失效、只剩两端 Edge。
                        // 「每档都震」因此要走 steps + showKeyPoints，而不是 keyPoints。
                        showKeyPoints = true,
                        hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                    )
                }
            }
        }
    }
}

/**
 * 「数值窄字体」那一项专用的开关偏好项。
 *
 * **为什么自己写一个**：miuix 的 `SwitchPreference` 只收 `summary: String`，
 * 内部固定用主题字体渲染，没有任何入口能把 `FontFamily` 注入到 summary；
 * 而这里要的正是「让描述文字自己变成 MiSans Condensed 的样本」。
 *
 * **怎么写的**：结构完全照抄 miuix 源码 `miuix-preference/.../SwitchPreference.kt` ——
 * 同一个 `BasicComponent` + 右侧 `Switch` + `Role.Switch` + 整行点击翻转，
 * 连 `rememberUpdatedState` 包一层回调的细节也一致。
 * 差别只有一处：改用 `BasicComponent` 的 content 插槽重载
 * （`miuix-ui/.../basic/Component.kt` 里 `content: @Composable ColumnScope.() -> Unit` 那个版本），
 * title / summary 由我们自己画，于是 summary 想用什么 TextStyle 都行。
 * 行高、内边距、右侧区域宽度上限（60% 硬上限）、无障碍语义仍由 BasicComponent 负责，
 * 所以外观与相邻的原生偏好项不会有任何偏差。
 *
 * 不做成通用组件的理由：只有外观页这一处调用，多一层参数化只是噪音。
 *
 * @param checked 开关状态
 * @param onCheckedChange 开关状态变化回调
 * @param title 标题（字号/字重与 miuix 一致：headline1 字号 + Medium）
 * @param summary 描述文字，用 [summaryFontFamily] 渲染
 * @param summaryFontFamily summary 使用的字体族（调用方按当前宽度构建，随滑块变化）
 * @param modifier 修饰符
 * @param enabled 是否可用
 */
@Composable
private fun CondensedFontSwitchPreference(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    summary: String,
    summaryFontFamily: FontFamily,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // 与 miuix 一致：用 rememberUpdatedState 包一层，避免每次重组都把新 lambda 捕获进 clickable
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
    // 标题 / 描述配色：miuix 取的是 BasicComponentDefaults.titleColor()/summaryColor() 的默认值，
    // 这里直接读同一组色板。它们内部用的 BasicComponentColors.color(enabled) 是 internal，
    // 跨模块不可见，所以按 enabled 自己选一遍。
    val titleColor = if (enabled) MiuixTheme.colorScheme.onBackground
                     else MiuixTheme.colorScheme.disabledOnSecondaryVariant
    val summaryColor = if (enabled) MiuixTheme.colorScheme.onSurfaceVariantSummary
                       else MiuixTheme.colorScheme.disabledOnSecondaryVariant

    BasicComponent(
        modifier = modifier,
        startAction = { SettingItemIcon(ImageVector.vectorResource(R.drawable.ic_cond_num)) },
        // 右侧：开关本体（对应 miuix 的 SwitchPreferenceEndActions）
        endActions = {
            Switch(
                checked = checked,
                onCheckedChange = currentOnCheckedChange,
                enabled = enabled,
            )
        },
        // 整行点击翻转，语义角色为 Switch
        onClick = { currentOnCheckedChange(!checked) },
        role = Role.Switch,
        enabled = enabled,
    ) {
        // 标题
        Text(
            text = title,
            fontSize = MiuixTheme.textStyles.headline1.fontSize,
            fontWeight = FontWeight.Medium,
            color = titleColor,
        )
        // 描述：本组件存在的意义 —— 换成 MiSans Condensed。
        // fontFeatureSettings 一并关掉 calt：这个字体的 calt 会把小数点错换成冒号字形
        //（详见 DATACARD_VALUE_FONT_FEATURE_SETTINGS 的注释）。当前文案里没有小数点，
        // 但留着这行，将来把文案换成数字样例（如 1280.00）时不会踩坑。
        Text(
            text = summary,
            color = summaryColor,
            style = MiuixTheme.textStyles.body2.copy(
                fontFamily = summaryFontFamily,
                fontFeatureSettings = DATACARD_VALUE_FONT_FEATURE_SETTINGS,
            ),
        )
    }
}
