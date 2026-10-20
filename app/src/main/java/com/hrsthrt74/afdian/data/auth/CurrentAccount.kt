package com.hrsthrt74.afdian.data.auth

import com.hrsthrt74.afdian.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 当前已登录账号的身份（user_id）。
 *
 * 为什么需要它：浏览历史、搜索历史这类「按账号隔离」的本地数据需要一个稳定的归属键。
 * token 每次登录都会重新签发、不能当账号标识；user_id 才是账号的稳定身份，
 * 因此用它做分区键 —— 同一账号退出再登录，历史依然能读回来。
 *
 * 生命周期：登录后由 MainViewModel 从资料接口（或资料缓存）写入，登出时清空。
 * 只存在内存里，不落盘：冷启动时会在资料回填/网络刷新后重新填入，
 * 在填入之前读到空历史，比错误展示上一个账号的记录要安全。
 */
object CurrentAccount {
    private val _userId = MutableStateFlow<String?>(null)
    val userId: StateFlow<String?> = _userId.asStateFlow()

    /**
     * 更新当前账号；传 null 表示登出。
     *
     * 只记录「有没有账号」到日志里，不打印 user_id 本身。
     */
    fun update(userId: String?) {
        val normalized = userId?.takeIf { it.isNotBlank() }
        if (_userId.value == normalized) return
        _userId.value = normalized
        AppLog.d("CurrentAccount", if (normalized == null) "已清空当前账号" else "已绑定当前账号")
    }
}
