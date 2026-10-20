package com.hrsthrt74.afdian.data.auth

import java.util.concurrent.atomic.AtomicLong

/**
 * 进程级会话代次。
 *
 * 每次登录、切换账号、登出都会 [next]；会话级 ViewModel 在构造时记下自己的代次，
 * 任何异步结果写回前先比对 [current]：
 *
 * - 旧会话的慢请求（首页刷新、资料预取、点赞播种）不会把上一个账号的数据写进新账号；
 * - A→B→A 快速连切时，只有最后一代的结果会被采纳。
 *
 * 用 AtomicLong 而不是普通变量：读写分散在主线程与 IO 线程。
 */
object SessionEpoch {
    private val counter = AtomicLong(0L)

    /** 当前代次。 */
    fun current(): Long = counter.get()

    /** 开启新的一代（登录 / 切换 / 登出），返回新代次。 */
    fun next(): Long = counter.incrementAndGet()
}
