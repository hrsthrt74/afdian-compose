package com.hrsthrt74.afdian.data.settings

import androidx.compose.ui.graphics.Color
import com.hrsthrt74.afdian.ui.theme.AfdianBrandColor

/**
 * Monet 配色模式下的种子色（Key Color）预设。
 *
 * 开启「Monet 配色」后，Miuix 会用这里选中的色值作为种子，本地生成整套色板
 * （主色、容器色、背景、卡片、分割线等）；选 [System] 时种子色交给系统壁纸。
 *
 * 枚举顺序 = 下拉里的展示顺序（「跟随系统壁纸」在首位，「爱发电主题色」紧随其后，
 * 其余是官方 demo 的预设色）。顺序不参与持久化，见下面的说明。
 *
 * 注意持久化的是 [storageValue] 字符串而不是下标：这样调整展示顺序时，
 * 用户已保存的选择不会被错位成另一个颜色。
 *
 * @property storageValue 写入 DataStore 的字符串值，只增不改。
 * @property label 下拉里展示的中文名字。
 * @property color 种子色；null 表示「跟随系统壁纸」，此时由 Miuix 自行取系统配色。
 */
enum class KeyColorPreset(
    val storageValue: String,
    val label: String,
    val color: Color?,
) {
    /**
     * 跟随系统壁纸：由 Miuix 读取系统 Material You 配色。
     * 系统取色要 Android 12（API 31）以上才有效，低版本按 [selectable] 隐藏本项。
     */
    System("system", "跟随壁纸", null),

    /**
     * 爱发电主题色：种子色即全 App 唯一的品牌紫 [AfdianBrandColor]。
     *
     * 与「使用爱发电主题色」开关的区别：开关用的是固定色板（主色就是纯 #946CE6），
     * 这里是把品牌紫当种子交给 Monet 生成，整套色板都会带上紫调，观感更统一，
     * 但主色会比原色略深一档 —— 这是 Monet 算法的正常表现。
     */
    Afdian("afdian", "爱发电紫", AfdianBrandColor),

    Blue("blue", "蓝色", Color(0xFF3482FF)),
    Green("green", "绿色", Color(0xFF36D167)),
    Yellow("yellow", "黄色", Color(0xFFFFB21D)),
    Orange("orange", "橙色", Color(0xFFFF5722)),
    Pink("pink", "粉色", Color(0xFFE91E63)),
    Teal("teal", "青色", Color(0xFF00BCD4)),
    ;

    companion object {
        /** 默认档位：跟随系统壁纸，也是「Monet 配色」刚打开时的选项。 */
        val Default = System

        /**
         * 从 DataStore 读出的字符串还原成枚举。
         * 未知值或历史脏数据一律回落 [Default]，保证升级、降级都不会崩。
         */
        fun fromStorage(value: String?): KeyColorPreset =
            entries.firstOrNull { it.storageValue == value } ?: Default

        /**
         * 当前设备真正可选的档位。
         *
         * @param systemWallpaperSupported 设备是否支持系统壁纸取色（Android 12 / API 31 及以上）。
         *   不支持时过滤掉 [System]，避免用户选到一个永远不生效、只会回退成默认紫的选项。
         */
        fun selectable(systemWallpaperSupported: Boolean): List<KeyColorPreset> =
            entries.filter { it != System || systemWallpaperSupported }
    }
}

/**
 * 把读到的预设解析成「本机上真正生效」的那一档。
 *
 * 场景：用户在支持系统取色的设备上选了「跟随系统壁纸」，之后换到 Android 11 及以下的设备；
 * 此时 [KeyColorPreset.System] 不可用，统一退回 [KeyColorPreset.Blue]，
 * 让下拉里显示的颜色与主题实际用的颜色保持一致（否则会出现「显示蓝色、实际紫色」的错位）。
 *
 * @param systemWallpaperSupported 设备是否支持系统壁纸取色（Android 12 / API 31 及以上）。
 */
fun KeyColorPreset.resolveForSdk(systemWallpaperSupported: Boolean): KeyColorPreset =
    if (this == KeyColorPreset.System && !systemWallpaperSupported) KeyColorPreset.Blue else this
