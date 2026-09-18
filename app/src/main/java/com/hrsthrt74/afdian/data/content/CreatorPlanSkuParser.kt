package com.hrsthrt74.afdian.data.content

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

private val creatorPlanSkuJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
}

/** 解析 get-plan-skus 的 data；接口偶尔省略 data 时返回空结果而不是让页面崩溃。 */
internal fun parseCreatorPlanSkus(root: JsonObject): CreatorPlanSkus {
    val data = root["data"]?.jsonObject ?: return CreatorPlanSkus()
    return runCatching {
        creatorPlanSkuJson.decodeFromJsonElement(CreatorPlanSkus.serializer(), data)
    }.getOrElse {
        CreatorPlanSkus()
    }
}
