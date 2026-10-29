package com.hrsthrt74.afdian.data.ai

/**
 * AI 供应商预设（BYOK）。
 *
 * 当前仅兼容 OpenAI 协议（`POST {baseUrl}/chat/completions` + SSE 流式），
 * 供应商预设包含 Base URL、API Key、模型名。
 * 选中后由设置页自动填充，用户仍可手动修改。
 *
 * 声明顺序即设置页下拉的展示顺序。
 *
 * @param storageValue DataStore 持久化值；后续新增档位可兼容旧配置
 * @param title 展示名
 * @param defaultBaseUrl 该家官方 OpenAI 兼容端点（不含 `/chat/completions` 路径部分）；
 *        [Custom] 为空串，由用户完整填写
 * @param defaultModel 常用模型建议，选中预设时填入输入框作起始值
 */
enum class AiProviderPreset(
    val storageValue: String,
    val title: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
) {
    DeepSeek("deepseek", "DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
    // TODO: 以下供应商没有长期不变的模型名（e.g. deepseek-chat），发布新模型后需要手动换成新名字
    XiaomiMiMo("mimo", "Xiaomi MiMo", "https://api.xiaomimimo.com/v1", "mimo-v2.6-flash"),
    Zhipu("zhipu", "智谱", "https://open.bigmodel.cn/api/paas/v4", "glm-5.3-flash"),
    Moonshot("moonshot", "Kimi", "https://api.moonshot.cn/v1", "kimi-k3.1-pro"),
    Custom("custom", "自定义", "", ""),
    ;

    /** 本档位是否允许不填 API Key（自定义端点可能指向本地推理服务，普遍不校验鉴权）。 */
    val keyOptional: Boolean
        get() = this == Custom

    companion object {
        fun fromStorage(value: String?): AiProviderPreset? =
            entries.firstOrNull { it.storageValue == value }
    }
}
