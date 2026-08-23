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

// 深色模式的页面底色：Miuix darkColorScheme 的默认 background，
// 深色下的禁用态等「面」颜色以它为混合基准，保证与背景协调
private val DarkCanvas = Color(0xFF242424)

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
    // 主色变体（Card 强调场景），官方与 primary 同色
    primaryVariant = accent,
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
    // 官方深色变体更暗更饱和；本项目 Card 走 surfaceContainer 不受影响，直接用主色最安全
    primaryVariant = accent,
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
