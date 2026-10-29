package com.hrsthrt74.afdian.data.ai

/**
 * 思考开关：推理模型在给出正文前先输出一段思维链（`reasoning_content`）。
 *
 * 各家 OpenAI 兼容端点的写法基本一致（DeepSeek / 小米 MiMo 等）：
 * `{"thinking": {"type": "enabled" | "disabled"}}`，强度用顶层 `reasoning_effort`。
 *
 * 这里只有「开 / 关」两态——不再提供「跟随服务端」：设置项永远是用户看得见的一个确定状态，
 * 不会出现「界面上什么都没选、实际却不知道发了什么」的模糊地带。
 * 强度档位见 [AiReasoningEffort]，两者在设置页合成一根四档滑块（见 [AiThinkingLevel]）。
 *
 * @param storageValue DataStore 持久化值
 * @param title 展示名
 */
enum class AiThinkingMode(
    val storageValue: String,
    val title: String,
) {
    /** 显式要求思考：带 `thinking.type=enabled` 与 `reasoning_effort`。 */
    Enabled("enabled", "开启"),

    /** 显式关闭思考：带 `thinking.type=disabled`，响应明显更快，适合纯数据整理类任务。 */
    Disabled("disabled", "关闭"),
    ;

    companion object {
        fun fromStorage(value: String?): AiThinkingMode? =
            entries.firstOrNull { it.storageValue == value }
    }
}

/**
 * 思考强度（`reasoning_effort`）。
 *
 * 取值按 DeepSeek 文档给的映射选：它接受 low / high / max，
 * 我们原样透传，实际生效强度由厂商自己映射。
 *
 * @param storageValue 请求体里的字面值
 * @param title 展示名
 */
enum class AiReasoningEffort(
    val storageValue: String,
    val title: String,
) {
    Low("low", "Low"),
    High("high", "High"),
    Max("max", "Max"),
    ;

    companion object {
        fun fromStorage(value: String?): AiReasoningEffort? =
            entries.firstOrNull { it.storageValue == value }
    }
}

/**
 * 设置页「思考」滑块的一个档位：把「开关 + 强度」合并成一根滑块的离散刻度。
 *
 * 顺序即滑块从左到右的顺序：0 = [Off]（拉到最左，关闭），
 * 之后依次是 low / high / max——滑块一旦离开最左端就是「开启 + 对应强度」，
 * 所以界面上不需要再单独放一个开关。
 *
 * @param mode 该档位对应的思考开关
 * @param effort 该档位对应的思考强度；[Off] 为 null（关闭时不发强度）
 */
enum class AiThinkingLevel(
    val mode: AiThinkingMode,
    val effort: AiReasoningEffort?,
) {
    Off(AiThinkingMode.Disabled, null),
    Low(AiThinkingMode.Enabled, AiReasoningEffort.Low),
    High(AiThinkingMode.Enabled, AiReasoningEffort.High),
    Max(AiThinkingMode.Enabled, AiReasoningEffort.Max),
    ;

    /** 滑块刻度文案（关闭 / Low / High / Max）。 */
    val label: String
        get() = effort?.title ?: "关闭"

    companion object {
        /**
         * 由存档的两个字段反查滑块档位。
         *
         * 历史数据兜底：老版本存过 `default`（跟随服务端）或 `medium`（已废弃的强度），
         * 反查不到时按开关态落档——关闭就是 [Off]，开启就是 [High]，
         * 与 DeepSeek 服务端默认行为最接近。
         */
        fun of(mode: AiThinkingMode, effort: AiReasoningEffort?): AiThinkingLevel =
            entries.firstOrNull { it.mode == mode && it.effort == effort }
                ?: if (mode == AiThinkingMode.Disabled) Off else DefaultAiThinkingLevel
    }
}

/** 思考开关默认档：开启（多数推理模型默认就想开，关闭反而要多发一个字段）。 */
val DefaultAiThinkingMode = AiThinkingMode.Enabled

/** 思考强度默认档：高（DeepSeek 的默认 effort 就是 high，选它最接近「不干预」）。 */
val DefaultAiReasoningEffort = AiReasoningEffort.High

/** 滑块默认档：开启 + 高强度（与上面两个默认值同源）。 */
val DefaultAiThinkingLevel = AiThinkingLevel.High
