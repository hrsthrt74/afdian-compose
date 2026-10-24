package com.hrsthrt74.afdian.data.update

/**
 * 检查更新的通道。
 *
 * - [Stable]：GitHub Releases 里的正式版。附件可匿名直链下载，App 内直接下载并拉起安装器。
 * - [Actions]：master 每次 push 由 GitHub Actions 产出的构建。artifact 下载需要登录 GitHub，
 *   所以 App 只负责「发现新构建 + 打开对应的运行页面」，不做站内下载。
 *
 * 通道是**设备级**偏好（不随账号切换），存在 SettingsStore 的 `update_channel`。
 */
enum class UpdateChannel(val storageValue: String) {

    Stable("stable"),
    Actions("actions"),
    ;

    companion object {

        /**
         * 从存储值还原通道。
         *
         * 未知/缺失值一律回落到 [Stable]：宁可少提示，也不能把用户推进一个
         * 他自己都没选过的通道（旧版本升级上来时这个键本来就不存在）。
         */
        fun fromStorage(raw: String?): UpdateChannel =
            entries.firstOrNull { it.storageValue == raw } ?: Stable
    }
}
