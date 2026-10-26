package com.hrsthrt74.afdian.ui.screens.auth

import java.net.URLDecoder

/**
 * 网页端登录凭据的解析。
 *
 * 爱发电网页端（`/login`）登录成功后，由前端的 `set_auth_token()` 用 js-cookie
 * 写入名为 `auth_token` 的 cookie（有效期 360 天）。这与 App 自己走
 * `/api/passport/login` 拿到的凭据是**同一种东西**，所以网页登录拿到的值
 * 可以直接交给现有的登录提交流程，不需要任何格式转换。
 *
 * 这里只放纯函数：真正读 cookie 的动作依赖 Android 的 `CookieManager`，
 * 留在 UI 层；而「从一段 cookie 文本里挑出 auth_token」是纯字符串处理，
 * 做成顶层函数才能被 JVM 单元测试直接覆盖（参考 `MessageParser` 的做法）。
 */

/**
 * 从 Cookie 头文本里取出 `auth_token` 的值。
 *
 * 输入形如 `auth_token=abc123; other=1`（Cookie 头的标准格式），
 * 输出 `abc123`；找不到该 cookie、值为空、或输入为 null 时返回 null。
 *
 * 刻意不用 `split("=")` 直接取第二段：token 里可能含 `=`（Base64 变体的尾随填充），
 * 按第一个 `=` 切分才能保住完整值。也不做 URL 解码 —— cookie 的值在
 * `CookieManager.getCookie()` 返回时已经是原始值，解码反而可能引入偏差。
 *
 * @param cookieHeader Cookie 头文本，允许为 null
 * @return auth_token 的值；不存在或为空时返回 null
 */
internal fun parseAuthToken(cookieHeader: String?): String? {
    if (cookieHeader.isNullOrBlank()) return null
    return cookieHeader.split(';')
        .mapNotNull { part ->
            val separator = part.indexOf('=')
            // 没有 '=' 的片段不是有效的键值对；'=' 在开头说明键名为空，同样跳过
            if (separator <= 0) return@mapNotNull null
            val name = part.substring(0, separator).trim()
            if (name != AUTH_TOKEN_COOKIE_NAME) return@mapNotNull null
            // 值可能为空（空 cookie 等价于删除），空值不算命中
            part.substring(separator + 1).trim().takeIf { it.isNotEmpty() }
        }
        .firstOrNull()
}

/**
 * 从网页地址的 query 参数里取出 `atn` 的值。
 *
 * ## 为什么需要这条路径
 *
 * 网页端在跨站跳转时会把登录凭据放进 URL 的 `atn` 参数
 * （`build_show_host_login_url` 里 `searchParams.set("atn", token)`），
 * 落地页再用 `set_auth_token(atn)` 把它写回 cookie。
 *
 * 这意味着两种可能：
 * - 凭据最终总会落到 cookie 上（正常路径）；
 * - 页面停在「带 atn 但还没写 cookie」的瞬间（跳转与写 cookie 之间）。
 *
 * 后者如果只查 cookie 就会漏判。所以这里同时解析 `atn`，
 * 让「URL 里已有凭据」也算登录成功。
 *
 * @param url 当前网页地址，允许为 null
 * @return atn 参数的值；不存在或为空时返回 null
 */
internal fun parseAuthTokenFromUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    // 只看 query 部分：hash 里的内容网页端会自己清掉，不作数
    val query = url.substringAfter('?', "").substringBefore('#')
    if (query.isEmpty()) return null
    return query.split('&')
        .mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            if (part.substring(0, separator).trim() != AUTH_TOKEN_QUERY_NAME) return@mapNotNull null
            val raw = part.substring(separator + 1).trim()
            if (raw.isEmpty()) return@mapNotNull null
            // query 里的值是 URL 编码的，解码后才与 cookie 里的原始值一致
            runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
        }
        .firstOrNull()
        ?.takeIf { it.isNotEmpty() }
}

/**
 * 爱发电网页端写入的登录凭据 cookie 名。
 *
 * 与网页端 `app.js` 里 `this.$cookies.set("auth_token", ...)` 保持一致。
 */
internal const val AUTH_TOKEN_COOKIE_NAME = "auth_token"

/**
 * 网页端跨站跳转时携带登录凭据的 query 参数名。
 *
 * 与网页端 `app.js` 里 `searchParams.set("atn", token)` 保持一致。
 */
internal const val AUTH_TOKEN_QUERY_NAME = "atn"
