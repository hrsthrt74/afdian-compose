package com.hrsthrt74.afdian.data.ai

/**
 * 一次 AI 请求所需的完整配置（API Key 已解密为明文）。
 *
 * 只在真正发起请求前由 SettingsStore 组装出来，用完即弃；
 * UI 展示用的是不含 Key 的草稿配置（见 SettingsStore.aiConfigDraft）。
 */
data class AiConfig(
    val preset: AiProviderPreset,
    val baseUrl: String,
    val model: String,
    val apiKey: String,
    /** 思考开关：显式开启或关闭（不再有「跟随服务端」这种中间态）。 */
    val thinkingMode: AiThinkingMode = DefaultAiThinkingMode,
    /** 思考强度，仅 [thinkingMode] 为 [AiThinkingMode.Enabled] 时随请求发送。 */
    val reasoningEffort: AiReasoningEffort = DefaultAiReasoningEffort,
)

/**
 * AI 配置的 UI 展示草稿：**不含 Key 明文**，只有「是否已配置」。
 * 设置页与各功能页用它判断就绪状态；需要真发请求时再走 SettingsStore.aiConfig() 拿完整配置。
 */
data class AiConfigDraft(
    val preset: AiProviderPreset,
    val baseUrl: String,
    val model: String,
    val hasKey: Boolean,
    val thinkingMode: AiThinkingMode = DefaultAiThinkingMode,
    val reasoningEffort: AiReasoningEffort = DefaultAiReasoningEffort,
) {
    /** 是否已完整到可以发起请求（Key 必填与否由预设决定，见 [isAiConfigReady]）。 */
    val isReady: Boolean
        get() = isAiConfigReady(preset, baseUrl, model, hasKey)
}

// 说明：不提供「配置草稿的缺省值」。DataStore 是异步的，用一份默认配置垫一帧，
// 设置页就会先按默认档画一次再跳到真实值（预设下拉、思考滑块都会闪），
// 所以初值统一由 SettingsViewModel 同步取真实首值（见那里的 firstBlocking）。

/**
 * 归一化 Base URL，非法输入返回 null。
 *
 * 用户常犯的三种错都在这里兜住：
 * 1. 末尾多斜杠（`https://api.x.com/v1/`）→ 去掉，拼路径时统一补；
 * 2. 把完整端点粘进来（`.../v1/chat/completions`）→ 剥掉 `/chat/completions` 后缀，
 *    本应用会自己拼这段路径；
 * 3. 忘了协议头（`api.x.com/v1`）→ 无 scheme 判为非法，让用户显式补全，
 *    不猜 http/https（猜错会静默把 Key 发到明文通道）。
 */
fun normalizeAiBaseUrl(raw: String): String? {
    var url = raw.trim()
    if (url.isEmpty()) return null
    // 剥掉用户多粘的端点路径（大小写不敏感），循环剥以防粘了两层
    while (true) {
        val lower = url.lowercase()
        val suffix = "/chat/completions"
        if (lower.length > suffix.length && lower.endsWith(suffix)) {
            url = url.dropLast(suffix.length)
        } else {
            break
        }
    }
    // 只去**末尾**的斜杠，路径中间的斜杠是合法的（如 /api/paas/v4）
    url = url.trimEnd('/')
    // 必须形如 scheme://...，且 scheme 只认 http(s)
    val schemeEnd = url.indexOf("://")
    if (schemeEnd <= 0) return null
    val scheme = url.take(schemeEnd).lowercase()
    if (scheme != "http" && scheme != "https") return null
    if (url.length <= schemeEnd + 3) return null
    return url
}

/** 归一化模型名：去首尾空白；空串原样返回（由「配置完整性」检查统一兜底）。 */
fun normalizeAiModel(raw: String): String = raw.trim()

// 说明：请求体不再带 temperature / max_tokens。
// 新一代模型（GPT-5、o 系列等）已不接受温度调节，部分网关对不认识的参数直接报错；
// 输出长度也交给模型与场景提示词自行掌控，配置里因此不再暴露「生成参数」。

/**
 * 配置是否完整到「可以发起请求」。
 *
 * - Base URL 与模型名必填；
 * - API Key 除「自定义」端点外必填（自定义端点可能指向本地服务）—— 判断依据是 Key 是否已保存，
 *   这里不接触 Key 的内容。
 */
fun isAiConfigReady(preset: AiProviderPreset, baseUrl: String, model: String, hasKey: Boolean): Boolean {
    if (baseUrl.isBlank() || model.isBlank()) return false
    if (!preset.keyOptional && !hasKey) return false
    return true
}
