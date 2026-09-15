package com.hrsthrt74.afdian.data.network

import com.hrsthrt74.afdian.AppLog
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 校验爱发电网页接口的业务响应码。
 *
 * 有些旧接口没有返回 ec，因此默认只在 ec 存在时校验；对响应契约明确的接口
 * 可传 requireEc=true，把缺失业务码也视为结构异常，避免 HTTP 200 被误判为成功。
 */
internal fun JsonObject.requireAfdianSuccess(
    tag: String,
    path: String,
    fallbackMessage: String,
    requireEc: Boolean = false,
) {
    val ecElement = this["ec"]
    if (ecElement == null) {
        if (requireEc) {
            AppLog.w(tag, "$path 缺少业务码 ec")
            error("$fallbackMessage（响应结构异常）")
        }
        return
    }

    val ec = (ecElement as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
    if (ec == null) {
        AppLog.w(tag, "$path 业务码 ec 格式异常")
        error("$fallbackMessage（响应结构异常）")
    }
    if (ec != 200) {
        val message = sequenceOf("em", "msg", "message")
            .mapNotNull { key -> (this[key] as? JsonPrimitive)?.contentOrNull }
            .firstOrNull { it.isNotBlank() }
            ?: fallbackMessage
        AppLog.w(tag, "$path 业务失败 ec=$ec")
        error(message)
    }
}
