package com.hrsthrt74.afdian.data.network

import com.hrsthrt74.afdian.AppLog

/**
 * 爱发电的三个可用域名。
 *
 * 写成文件私有常量而不是把字面量散在枚举里：三条链要反复引用同一批字符串，
 * 拼错一个字符的后果是「某一档永远落到 DNS 失败」，而编译器不会提醒。
 */
private const val HOST_IFDIAN = "ifdian.net"
private const val HOST_AFDIAN = "afdian.com"
private const val HOST_WWW_AFDIAN = "www.afdian.com"

/**
 * 设置页可选的「优先线路」。
 *
 * [chain] 是完整的尝试顺序（第 0 项为首选），不是「首选 + 单个备选」两段式：
 * 三档的末位候选并不相同（前两档的末位是 www，www 档的末位是 afdian.com），
 * 把顺序直接写死在枚举里，网络层就只剩「按链遍历」一件事，不需要再推导优先级。
 *
 * 设备级偏好，存 [storageValue] 而不是 ordinal：日后插入新档位时老用户的配置不会错位。
 */
enum class AfdianRoute(
    val storageValue: String,
    val chain: List<String>,
) {
    // 默认线路，与改动前「优先 ifdian.net」的行为一致
    IfdianNet("ifdian", listOf(HOST_IFDIAN, HOST_AFDIAN, HOST_WWW_AFDIAN)),
    AfdianCom("afdian", listOf(HOST_AFDIAN, HOST_IFDIAN, HOST_WWW_AFDIAN)),
    AfdianWww("www", listOf(HOST_WWW_AFDIAN, HOST_IFDIAN, HOST_AFDIAN)),
    ;

    /** 首选域名，即 [chain] 第一项；设置页下拉直接显示它。 */
    val host: String get() = chain.first()

    companion object {

        /**
         * 默认线路。
         *
         * [fromStorage] 的兜底值也用它，两处必须同源 —— 否则设置页首帧显示的值
         * 与真正生效的值会打架（参考 `SettingsViewModel` 里 stateIn 初值的既有约定）。
         */
        val Default: AfdianRoute = IfdianNet

        /**
         * 需要被 HTTP 层接管的所有域名。
         *
         * 用集合精确匹配，不用 `endsWith` / `contains`：后者会把
         * `www.afdian.com.evil.com`、`evil-ifdian.net` 这类域名也当成自家域名去改写。
         */
        private val HOSTS: Set<String> = entries.flatMap { it.chain }.toSet()

        /** 该 host 是否属于爱发电（含 www 变体）；不属于的请求原样放行。 */
        fun isAfdianHost(host: String): Boolean = host in HOSTS

        /**
         * 从存储值还原线路。
         *
         * 未知/缺失值一律回落 [Default]：用户从没选过（旧版本升级上来时这个键本来就不存在），
         * 不能把他推到一条自己没选过的线路上。与 `UpdateChannel.fromStorage` 同一套约定。
         */
        fun fromStorage(raw: String?): AfdianRoute =
            entries.firstOrNull { it.storageValue == raw } ?: Default
    }
}

/**
 * 进程级「当前优先线路」。
 *
 * 为什么不能放在各 Service 的实例字段里：Service 的寿命跟着 ViewModel，切换线路后
 * 只有新建的实例能读到新值，前台页面里活着的旧实例仍走老线路 —— 用户会觉得「切了没用」。
 * 线路本质是设备级的进程状态，所以放这里，由 [AfdianHttpClient.execute] 每次请求现读。
 *
 * 用 `@Volatile var` 而不是 StateFlow：网络线程每发一次请求都要读，裸 `var` 读取代价最低，
 * 也不必为此把网络层改成协程（与 `AppLog.debugEnabled`、`LogStore.fileLoggingEnabled` 同构）。
 */
internal object AfdianRouteState {

    /** 当前优先线路；只有 [update] 能改。 */
    @Volatile
    var preferred: AfdianRoute = AfdianRoute.Default
        private set

    /**
     * 写入当前线路。
     *
     * 只由 `AfdianApplication` 的 DataStore 订阅调用；值没变就直接返回，
     * 避免每次订阅回推同一档时都打一条日志。
     */
    fun update(route: AfdianRoute) {
        if (preferred == route) return
        preferred = route
        AppLog.d(
            "AfdianRoute",
            "优先线路切换为 ${route.host}，回退链 ${route.chain.joinToString(" -> ")}",
        )
    }
}

/**
 * 用当前优先线路的域名拼一个站内网页绝对地址。
 *
 * 供 App 自己构造的外跳、分享与图片地址使用，好处有三：
 * 1. 与网络层读同一个全局值，保证「接口打哪条线路，页面就开哪条线路」；
 * 2. 拼 host 的地方只有这一处，不会有人漏 `https://` 或写出双斜杠；
 * 3. 切线路后立即生效，不需要重启或重建页面。
 *
 * **服务端下发的链接不要经过这里**（消息卡片、富文本正文里的 URL）：
 * 那种链接的 host 由服务端决定，改写到别的域名可能指向不存在的页面。
 *
 * @param path 站内路径，必须以 "/" 开头。
 */
internal fun afdianWebUrl(path: String): String = "https://${AfdianRouteState.preferred.host}$path"
