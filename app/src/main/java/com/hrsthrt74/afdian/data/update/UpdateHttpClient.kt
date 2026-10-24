package com.hrsthrt74.afdian.data.update

import com.hrsthrt74.afdian.data.network.sharedAfdianHttpClient
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * 更新模块专用的 OkHttp 客户端。
 *
 * 从共享客户端派生：复用同一个连接池、线程池与 DNS 缓存，只覆盖超时。
 * 为什么不直接改 [sharedAfdianHttpClient]：爱发电接口的正常响应都在秒级，
 * 把全局读超时拉到 30 秒只会让真正的网络异常更晚暴露。
 *
 * 20~40MB 的安装包在弱网下，OkHttp 默认 10 秒读超时（按「一次读操作」计算）
 * 很容易在两次数据块之间超时，下载会被误判为失败。
 */
internal val updateHttpClient: OkHttpClient by lazy {
    sharedAfdianHttpClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
}
