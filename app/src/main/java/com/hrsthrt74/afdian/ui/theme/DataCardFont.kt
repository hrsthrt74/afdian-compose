package com.hrsthrt74.afdian.ui.theme

import androidx.annotation.FontRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.hrsthrt74.afdian.R
import com.hrsthrt74.afdian.data.settings.normalizeDataCardFontWidth

/**
 * 数据卡数值字体的组合局部。
 *
 * - `null` = 使用系统默认字体（设置页开关关闭，或未提供）；
 * - 非 null = 数据卡的**数值文字**改用这个 FontFamily（MiSans Condensed，已按 wdth 轴实例化）。
 *
 * 只影响数值文字：标题 / 单位 / 副标题仍走原来的 Miuix 样式（系统字体）。
 *
 * 用 CompositionLocal 而不是「进程级可变变量」的原因与
 * [com.hrsthrt74.afdian.ui.components.LocalRollingNumberEnabled] 相同：
 * 它是正常的组合输入，谁读谁订阅，值一变必然重组换字体，不依赖任何手动写入时机。
 *
 * 由 MainActivity 在根组合按持久化设置下发；设置页预览卡再用一层嵌套 Provider
 * 覆盖成「拖动中的草稿值」，实现实时预览而不影响全局。
 */
val LocalDataCardFontFamily = compositionLocalOf<FontFamily?> { null }

/**
 * 数值字体在 `res/font` 下的资源 id（`app/src/main/res/font/misans_condensed.ttf`）。
 *
 * 为什么用 `res/font` 而不是 assets：
 * - 资源 id 由 R 类生成，引用处是编译期常量，重命名/移动文件时构建期就会报编译错误；
 *   assets 版写的是字符串路径，文件被改名/挪走只会在运行时悄悄回退成系统字体。
 * - `Font(resId, ...)` 这条重载默认就是 `FontLoadingStrategy.Blocking`，同样在组合期就
 *   同步加载完（见下方注释），滚轮量算不会先按兜底字体测一遍再跳一次。
 *
 * 可变轴照旧生效：Compose 内部把 variationSettings 交给
 * `Typeface.Builder(...).setFontVariationSettings(...)`（API 26+）。
 *
 * 注意：`res/font` 下的 `.ttf` 需要靠 `androidResources { noCompress += "ttf" }` 保持不压缩，
 * 否则 `Typeface.Builder` 只能把整个字体读进内存、无法 mmap。
 */
@FontRes
val DATACARD_VALUE_FONT_RES_ID: Int = R.font.misans_condensed

/**
 * 数值字体要显式关闭的 OpenType 特性：`calt`（上下文替换）。
 *
 * **为什么必须关掉**：这个字体的 `calt` 规则是「`.` 或 `:` 两侧都是字母/数字时，
 * 把它们统一换成同一个字形 gid157」。而 gid157 实际是**垂直居中的冒号**（两个轮廓，
 * 位于数字行中部）—— 也就是说字体给「小数点」的替换目标填错了字形。
 * 结果就是数字之间的小数点被渲染成冒号（`1280.00` 看着像 `1280:00`）。
 * 这是字体文件自身的缺陷（应该给居中的单点才对），无法从调用侧换对字形，
 * 只能在排版时禁掉这条特性，让小数点回到字体原始的基线圆点。
 *
 * `calt` 在本字体里只做上面那条替换（另有一条 `-` 在字母间居中，对我们无关），
 * 所以关掉它不会损失任何数字排版能力。注意必须**显式**关闭：Android 的排版引擎
 * 默认开启 `calt`，不关就会命中这条错误替换。
 *
 * 写法是 Android/CSS 风格的特性串；`FontFeatureSpan` 会把它交给 `TextPaint.fontFeatureSettings`，
 * 量算与绘制两条路径都会生效（滚轮路径用的也是同一份排版结果）。
 */
const val DATACARD_VALUE_FONT_FEATURE_SETTINGS = "\"calt\" 0"

/**
 * 按 wdth（宽度）轴构建 MiSans Condensed 字体族。
 *
 * 几个刻意的取舍：
 * - **显式给出 wght = 400（常规）**：字体的自带默认字重是 330，比系统常规（400）细，
 *   直接用会显得数字比卡片里其它文字「发虚」。这里把常规档位对齐到 400，
 *   于是数字的粗细与系统/主题的常规文字一致；用户若在系统里开了「粗体文字」，
 *   Compose 会自动把系统的 fontWeightAdjustment 叠到 `wght` 上（见
 *   `PlatformFontVariationSettings.toAndroidString`：我们自己给了 `wght`，它就是在这个值上做加法），
 *   粗细随之跟随系统设置变化 —— 也就是「不用另设开关，系统怎么调就怎么显示」。
 * - **weight 保持 Normal**：与 wght=400 一致，避免 FontSynthesis 再去做伪粗体（那会在窄体上糊成一团）。
 * - **remember(safeWidth)**：Font 的相等包含 variationSettings、FontFamily 按 fonts 结构相等，
 *   因此同一宽度会命中复用、不同宽度自动让下游（Text / 滚轮量算）重新排版并派生新的 Typeface。
 *   不 remember 的话每次重组都会新建对象，白白穿透字体解析缓存。
 *
 * 注意该字体文件是**数字子集**（约 182 个字形：0-9 与 . - , %，另含少量 CJK 省份简称），
 * 不含「万」「元」「人」「次」等字：数值里若混入汉字，Compose 会逐字形回退到系统字体，
 * 不会缺字或错位，只是风格略有混搭（单位文字本就独立用 unitStyle，不受影响）。
 *
 * @param width wdth 轴的值，10–100 且每 10 一档；超范围或非档位值会被归一化
 *   （见 [normalizeDataCardFontWidth]）
 */
@Composable
fun rememberMiSansCondensedFontFamily(width: Int): FontFamily {
    val safeWidth = normalizeDataCardFontWidth(width)
    return remember(safeWidth) {
        FontFamily(
            Font(
                // res/font/misans_condensed.ttf：可变字体，见 DATACARD_VALUE_FONT_RES_ID 的注释。
                resId = DATACARD_VALUE_FONT_RES_ID,
                weight = FontWeight.Normal,
                // 显式声明 Blocking：数值要先量算、再绘制（滚轮路径还要做数字宽度比），
                // 异步加载会让首帧先按兜底字体排一遍、字体到位后再重排一次，表现为数字跳一下。
                loadingStrategy = FontLoadingStrategy.Blocking,
                variationSettings = FontVariation.Settings(
                    // 常规字重（400）：系统的 fontWeightAdjustment 会在此基础上叠加
                    FontWeight.Normal,
                    FontStyle.Normal,
                    // 宽度轴：值越小越窄，100 为字体自带默认
                    FontVariation.Setting("wdth", safeWidth.toFloat()),
                ),
            ),
        )
    }
}
