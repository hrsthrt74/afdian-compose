package com.hrsthrt74.afdian.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 爱发电品牌紫 —— 全 App 唯一的色值出处。
 * MainActivity / AfdianColors 都从这里引用，换品牌色只改这一行。
 */
val AfdianBrandColor = Color(0xFF946CE6)

/**
 * Miuix 官方默认色板的主色（`lightColorScheme()` 的 primary）。
 *
 * 同时充当主题色预设里的「MIUIX 蓝」：MIUIX 主题下选它就直接用官方默认色板，
 * Monet 主题下则拿它当种子色，这样两种主题下同一个档位得到的蓝是同一个蓝。
 */
val MiuixBrandBlue = Color(0xFF3482FF)

// 深色模式的页面底色：Miuix darkColorScheme 的默认 background，
// 深色下的禁用态等「面」颜色以它为混合基准，保证与背景协调
private val DarkCanvas = Color(0xFF242424)

/**
 * 主色变体 [Colors.primaryVariant] 的派生规律：同色系里更深、更沉的一档。
 *
 * Miuix 官方两套默认色板在这一档上并不一致（见 miuix 仓库 `docs/zh_CN/guide/colors.md`）：
 * - 浅色：`#3482FF`，与 primary 完全相同（官方把变体等同于主色）；
 * - 深色：primary `#277AF7` → primaryVariant `#0073DD`，明度约为主色的 0.89、更沉一点。
 * 生成式色板（Monet / 给 ThemeController 传 keyColor）又是第三套：
 * 变体取 `primaryFixed`（主色 tone 90 的浅色调，见 miuix `MonetMapping.kt`）。
 *
 * 本项目的色板是从任意强调色现场算出来的，没有现成的「深一档」可抄；
 * 若照抄浅色那套「变体 = 主色」，变体在任何主题下都看不出层次 ——
 * 设置页调色盘顶部的三段预览会直接出现两段同色。所以统一按深色那档的比例压暗。
 *
 * 实现上向黑色插值：Compose 的 [lerp] 是在 Oklab（感知均匀空间）里插值的，
 * 所以「向黑 15%」相当于把明度整体压低一档、色相基本不动，比逐通道乘 RGB
 * 更不容易发灰发脏（逐通道乘会同时压低饱和度）。
 */
private fun primaryVariantOf(accent: Color): Color = lerp(accent, Color.Black, 0.15f)

/**
 * 生成「爱发电主题」浅色色板。
 *
 * Miuix 默认色板里除 [top.yukonga.miuix.kmp.theme.lightColorScheme] 的 primary 外，
 * Switch/Button/Slider 的禁用态、强调容器、下拉选中项等仍是官方蓝系；
 * 这里按官方默认值的派生规律（主色向白色按不同比例插值）全部染成品牌色系。
 *
 * 系数来源：对官方 light 默认色值逐一反推验证得出，例如
 * primary #3482FF → disabledPrimary #C2D9FF 恰为 lerp(主色, 白, 0.70)。
 */
fun afdianLightColorScheme(accent: Color = AfdianBrandColor): Colors = lightColorScheme(
    // 主操作色（Switch 开启、主按钮、Slider）
    primary = accent,
    // 主色上的文字/图标，官方即为白
    onPrimary = Color.White,
    // 主色变体（Card 强调场景的底色）：同色系更深的一档，不能等于主色
    primaryVariant = primaryVariantOf(accent),
    onPrimaryVariant = lerp(accent, Color.White, 0.60f),
    // 强调容器：官方 #5D9BFF = lerp(primary, 白, 0.20)
    primaryContainer = lerp(accent, Color.White, 0.20f),
    onPrimaryContainer = Color.White,
    // Switch 禁用轨道：官方 #C2D9FF = lerp(primary, 白, 0.70)
    disabledPrimary = lerp(accent, Color.White, 0.70f),
    disabledOnPrimary = lerp(Color.White, accent, 0.06f),
    // Button 禁用：官方与 disabledPrimary 同值
    disabledPrimaryButton = lerp(accent, Color.White, 0.70f),
    disabledOnPrimaryButton = Color.White,
    // Slider/进度条禁用：官方略深于 0.70，取 0.65 更贴近观感
    disabledPrimarySlider = lerp(accent, Color.White, 0.65f),
    // Slider 刻度点前景：官方为浅蓝高光，按约 1/3 提亮
    sliderKeyPointForeground = lerp(accent, Color.White, 0.33f),
    // 下拉菜单选中项：官方浅蓝底 #EAF2FF = lerp(primary, 白, 0.90)，文字即主色
    tertiaryContainer = lerp(accent, Color.White, 0.90f),
    onTertiaryContainer = accent,
)

/**
 * 生成「爱发电主题」深色色板。
 *
 * 深色下官方禁用色的派生规律是主色向深底 [#242424] 按 0.70 插值
 * （primary #277AF7 → disabledPrimary #253E64 完全吻合）；
 * 容器类则小幅向白提亮以保持层次。
 */
fun afdianDarkColorScheme(accent: Color = AfdianBrandColor): Colors = darkColorScheme(
    primary = accent,
    onPrimary = Color.White,
    // 主色变体：深色下官方同样比主色更深（#277AF7 → #0073DD），这里沿用同一规律
    primaryVariant = primaryVariantOf(accent),
    onPrimaryVariant = lerp(accent, Color.White, 0.55f),
    primaryContainer = lerp(accent, Color.White, 0.10f),
    onPrimaryContainer = Color.White,
    // Switch/Button 禁用：lerp(主色, 深底, 0.70)，与官方派生规律一致
    disabledPrimary = lerp(accent, DarkCanvas, 0.70f),
    disabledOnPrimary = lerp(DarkCanvas, Color.White, 0.38f),
    disabledPrimaryButton = lerp(accent, DarkCanvas, 0.70f),
    disabledOnPrimaryButton = lerp(DarkCanvas, Color.White, 0.38f),
    // Slider/进度条禁用比开关禁用更暗沉一档，在 0.70 基础上再压向深底
    disabledPrimarySlider = lerp(lerp(accent, DarkCanvas, 0.70f), DarkCanvas, 0.25f),
    sliderKeyPointForeground = lerp(accent, Color.White, 0.14f),
    // 下拉选中项：深色下官方为暗蓝底 + 提亮文字，这里改为深紫底 + 提亮紫字
    tertiaryContainer = lerp(DarkCanvas, accent, 0.22f),
    onTertiaryContainer = lerp(accent, Color.White, 0.14f),
)
