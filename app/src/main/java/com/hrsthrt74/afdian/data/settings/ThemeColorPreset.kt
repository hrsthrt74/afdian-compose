package com.hrsthrt74.afdian.data.settings

import androidx.compose.ui.graphics.Color
import com.hrsthrt74.afdian.ui.theme.AfdianBrandColor
import com.hrsthrt74.afdian.ui.theme.MiuixBrandBlue

/**
 * 主题色预设 —— 一套颜色同时服务「MIUIX」「Monet」两种主题（见设置 → 外观）。
 *
 * 两种主题下同一个色值的含义：
 * - **主题 = MIUIX**：把色值当作主色，沿用 Miuix 默认色板的派生规律染整套色板
 *   （[com.hrsthrt74.afdian.ui.theme.afdianLightColorScheme] 等）。选 [Miuix] 例外，
 *   直接用 Miuix 官方默认色板（那样才是原汁原味的 MIUIX 观感）；
 * - **主题 = Monet**：把色值当种子交给 Miuix，由 Monet 算法本地生成整套色板；
 *   选 [System] 时种子交给系统壁纸。
 *
 * 枚举顺序 = 外观页横向选择器的展示顺序
 * （自定义 → 跟随壁纸 → MIUIX 蓝 → 爱发电紫 → 其余预设 → 彩虹）。
 * 持久化的是 [storageValue] 字符串而不是下标，调整顺序不会让已保存的选择错位。
 *
 * @property storageValue 写入 DataStore 的字符串值，只增不改。
 * @property label 选择器里展示在色球下方的名字。
 * @property color 色球颜色，也是 MIUIX 主题的主色 / Monet 主题的种子色；
 *   null 表示色值不写死在这里（[Custom] 由用户自选，[System] 取系统壁纸，
 *   [Rainbow] 由时间算），这些档位的实际色值由 `AfdianTheme` 解析后一并使用。
 */
enum class ThemeColorPreset(
    val storageValue: String,
    val label: String,
    val color: Color?,
) {
    /**
     * 自定义：色值不写死在这里，由用户在外观页的调色盘里挑，
     * 存 `SettingsStore.customThemeColor`（ARGB Int）。
     *
     * 选择器里排在第一位：它的色球显示当前自定义色，选中时球上叠一个「+」，
     * 再点一次就打开调色盘。其余档位的 [color] 都是固定色。
     */
    Custom("custom", "自定义", null),

    /**
     * 跟随系统壁纸：从系统 Material You 配色里取色。
     *
     * 两种主题都提供（只要求 Android 12 / API 31 以上，低版本由 [selectable] 过滤掉）：
     * - Monet 主题下整套色板都交给系统壁纸色；
     * - MIUIX 主题下**只有主色**跟随壁纸（见 `AfdianTheme` 的 `wallpaperAccent`），
     *   背景 / 卡片 / 文字仍是 Miuix 静态色板。
     */
    System("system", "跟随壁纸", null),

    /**
     * MIUIX 蓝：Miuix 默认色板的主色，也是新装的默认档位。
     *
     * MIUIX 主题下选它 = 完全回到 Miuix 官方默认色板；Monet 主题下则拿它当种子。
     */
    Miuix("miuix", "MIUIX 蓝", MiuixBrandBlue),

    /**
     * 爱发电紫：全 App 唯一的品牌紫 [AfdianBrandColor]。
     *
     * MIUIX 主题下走固定色板（主色就是纯 #946CE6）；Monet 主题下把品牌紫当种子生成，
     * 整套色板都带紫调、观感更统一，但主色会比原色略深一档 —— 这是 Monet 算法的正常表现。
     */
    Afdian("afdian", "爱发电紫", AfdianBrandColor),

    Green("green", "绿色", Color(0xFF36D167)),
    Yellow("yellow", "黄色", Color(0xFFFFB21D)),
    Orange("orange", "橙色", Color(0xFFFF5722)),
    Pink("pink", "粉色", Color(0xFFE91E63)),
    Teal("teal", "青色", Color(0xFF00BCD4)),

    /**
     * 彩虹：色相连着走，主题色会自己缓慢轮换。
     *
     * 具体色值由 `AfdianTheme` 按时间算（[color] 为 null），不走 DataStore。
     * 选择器里它的色球不画固定颜色，改用浮层内容器色 + 一个同系 Help 图标。
     */
    Rainbow("rainbow", "彩虹", null),
    ;

    companion object {
        /** 默认档位：MIUIX 蓝（新装用户看到的就是 Miuix 官方默认配色）。 */
        val Default = Miuix

        /**
         * 自定义档的默认色值：50% 灰。
         *
         * 用户第一次选「自定义」、还没在调色盘里挑过色时用它 —— 中性灰不偏向任何一种色相，
         * 打开调色盘时选中的是最中间的那个点，不会一上来就诱导成某个颜色。
         */
        val DefaultCustomColor = Color(0xFF808080)

        /**
         * 旧版本的种子色字符串 → 新档位。
         *
         * 旧版下拉里有一档「蓝色」（#3482FF），它和 MIUIX 蓝是同一个色值，统一并到 [Miuix]；
         * 其余旧值（system / afdian / green / yellow / orange / pink / teal）沿用原字符串。
         */
        private val LegacyAliases = mapOf("blue" to Miuix)

        /**
         * 从 DataStore 读出的字符串还原成枚举。
         * 未知值或历史脏数据一律回落 [Default]，保证升级、降级都不会崩。
         */
        fun fromStorage(value: String?): ThemeColorPreset =
            entries.firstOrNull { it.storageValue == value }
                ?: LegacyAliases[value]
                ?: Default

        /**
         * 当前设置下真正可选的档位。
         *
         * 两种主题都提供 [System]：MIUIX 主题下它只让**主色**跟随壁纸（其余中性色仍用 Miuix 色板），
         * Monet 主题下则是整套 Monet 色板跟随壁纸。
         * 系统取色要 Android 12 / API 31 以上，低版本隐藏该项，避免用户选到一个永远不生效的档位。
         *
         * @param systemWallpaperSupported 设备是否支持系统壁纸取色。
         */
        fun selectable(systemWallpaperSupported: Boolean): List<ThemeColorPreset> =
            entries.filter { it != System || systemWallpaperSupported }
    }
}

/**
 * 把读到的预设解析成「本机上真正生效」的那一档。
 *
 * 唯一的兜底：设备不支持系统取色（Android 11 及以下）却存着 [ThemeColorPreset.System] 时，
 * 退回 [ThemeColorPreset.Miuix] —— 让选择器里高亮的色球与主题实际用的颜色保持一致，
 * 不会出现「显示跟随壁纸、实际是 MIUIX 蓝」的错位。
 *
 * @param systemWallpaperSupported 设备是否支持系统壁纸取色（Android 12 / API 31 及以上）。
 */
fun ThemeColorPreset.resolveForTheme(
    systemWallpaperSupported: Boolean,
): ThemeColorPreset =
    if (this == ThemeColorPreset.System && !systemWallpaperSupported) ThemeColorPreset.Miuix else this
