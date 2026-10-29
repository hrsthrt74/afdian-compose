package com.hrsthrt74.afdian.data.ai

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 消息页把「AI 记录」伪装成一条会话时使用的固定 id。
 *
 * 用下划线包起来的形式，保证不会和真实的爱发电 `user_id`（32 位十六进制）撞车；
 * 它是本地虚拟条目，永远不会被发给服务端。
 */
const val AI_HISTORY_DIALOG_ID = "__ai_history__"

/** 虚拟会话的展示名：与会话列表里其他行的名字同一层级。 */
const val AI_HISTORY_DIALOG_TITLE = "AI 记录"

/**
 * 一条 AI 问答快照（只读回看用）。
 *
 * 刻意只存「一次问答的完整产物」，不建模会话：本项目的 AI 是嵌在功能里的单轮问答，
 * 不存在多轮上下文，所以没有 sessionId / 消息列表，也没有 user、assistant 的角色区分。
 *
 * 字段取舍：
 * - [answer] 存完整 Markdown 正文，不截断 —— 它是用户唯一的产物，截了就没法原样回看；
 * - [reasoning] 思考过程只作辅助信息，超长时截断（见 [MAX_REASONING_CHARS]）；
 * - [model] / [provider] 一并记下，回看时能知道这份分析是哪家模型出的。
 */
@Serializable
data class AiHistoryEntry(
    // 记录唯一标识，删除单条时按它匹配
    val id: String,
    // 场景标识：目前只有「收入分析」一种，用字符串而不是 enum，
    // 将来新增场景直接加常量即可，旧数据不会因为枚举缺项而解析失败
    val scene: String = SCENE_INCOME,
    // 生成时底栏的标题，如「AI 收入分析」，详情弹层直接拿来当标题
    val title: String,
    // 问题摘要：记录「问的是哪份数据」，如「近7天」，AI 记录列表条目与详情弹层用它
    val query: String,
    // 完整回答（Markdown 原文）
    val answer: String,
    // 思考过程原文，可能为空（模型未开思考 / 该模型不返回推理内容）
    val reasoning: String = "",
    // 思考用时（秒），详情里渲染成「已思考（用时 N 秒）」；0 表示没有思考过程
    val thinkingSeconds: Int = 0,
    // 生成时使用的模型名，如 deepseek-chat
    val model: String = "",
    // 生成时使用的供应商标识（AiProviderPreset.storageValue）
    val provider: String = "",
    // 记录创建时间戳（毫秒），列表按它倒序与分组
    val createdAt: Long,
) {
    companion object {
        /** 收入明细页「问 AI」生成的收入分析。 */
        const val SCENE_INCOME = "income_analysis"

        /**
         * 思考过程的存盘上限（字符）。
         *
         * 推理模型的原生思考长度完全不可控（见过几万字符的），而它既不参与复制也不参与「采用」，
         * 存全量只会让 DataStore 的整文件读写越来越慢。8k 字符足够还原当时的推理脉络。
         */
        const val MAX_REASONING_CHARS = 8_000

        /** 构造一条快照：自动补 id 与时间戳，并对思考过程做长度截断。 */
        fun create(
            scene: String,
            title: String,
            query: String,
            answer: String,
            reasoning: String = "",
            thinkingSeconds: Int = 0,
            model: String = "",
            provider: String = "",
            id: String = UUID.randomUUID().toString(),
            now: Long = System.currentTimeMillis(),
        ): AiHistoryEntry = AiHistoryEntry(
            id = id,
            scene = scene,
            title = title,
            query = query,
            answer = answer,
            reasoning = reasoning.take(MAX_REASONING_CHARS),
            thinkingSeconds = thinkingSeconds,
            model = model,
            provider = provider,
            createdAt = now,
        )
    }
}

/** 卡片预览最多取多少字：消息行只有一行（maxLines = 1），再长也只是被省略号吃掉。 */
private const val PREVIEW_MAX_CHARS = 160

// 预览清洗用的正则：只处理「一眼能看出是 Markdown」的记号，不做完整解析。
// 目标是让对方看到一句像人话的摘要，不是还原排版。
private val CODE_FENCE = Regex("```[^`]*```", RegexOption.DOT_MATCHES_ALL)
private val IMAGE_MD = Regex("!\\[[^\\]]*]\\([^)]*\\)")
private val LINK_MD = Regex("\\[([^\\]]+)]\\([^)]*\\)")
private val INLINE_CODE = Regex("`([^`]*)`")
private val BOLD_MD = Regex("\\*\\*(.+?)\\*\\*|__(.+?)__")
private val ITALIC_MD = Regex("\\*([^*]+)\\*")
private val BLOCK_PREFIX = Regex("^(#{1,6}\\s+|>\\s*|[-*+]\\s+|\\d+[.)]\\s+)")
private val TABLE_RULE = Regex("^\\|?[\\s:|-]{3,}\\|?$")
private val ENTITY_SPACES = Regex("[ \\t\\u00A0]{2,}")

/**
 * 把一条 AI 回答的 Markdown 原文压成「一行纯文本预览」，给消息页的「AI 记录」卡片当摘要。
 *
 * 为什么需要清洗而不是直接取 `answer`：
 * 正文是给 Markdown 渲染器看的（`## 收入概览`、`**结论**`、表格竖线、列表短横），
 * 原样塞进一行 Text 会满屏 `#`、`|`、`-`，比空白更难读。
 *
 * 只剥记号、不重排语义：标题/列表/引用都只去掉前缀，正文原样保留；
 * 表格行把竖线换成空格（单元格之间还留着空隙），分隔行（`|---|`）整行丢掉。
 *
 * @param answer 完整回答（Markdown 原文）
 * @param maxChars 预览字符上限，超出直接截断（调用方那一行本来也会省略号，这里只为别把长文带进卡片）
 * @return 单行纯文本；原文为空或全是记号时返回空串，由调用方决定兜底文案
 */
fun aiAnswerPreview(answer: String, maxChars: Int = PREVIEW_MAX_CHARS): String {
    if (answer.isBlank()) return ""

    val body = CODE_FENCE.replace(answer, " ")
    val lines = body.lineSequence()
        .map { it.trim() }
        // 代码围栏残留、表格分隔行这类「纯结构」行没有阅读价值，整行丢掉
        .filter { it.isNotEmpty() && !it.startsWith("```") && !TABLE_RULE.matches(it) }
        .map { line -> BLOCK_PREFIX.replace(line, "") }
        .map { line ->
            line
                .replace(IMAGE_MD, " ")
                .replace(LINK_MD, "$1")
                .replace(INLINE_CODE, "$1")
                .replace(BOLD_MD) { it.groupValues[1].ifEmpty { it.groupValues[2] } }
                .replace(ITALIC_MD, "$1")
                // 表格竖线：留着会像乱码，换成空格后单元格还能靠空白分开
                .replace('|', ' ')
                .replace(ENTITY_SPACES, " ")
                .trim()
        }
        .filter { it.isNotEmpty() }
        .joinToString(" ")

    return lines.take(maxChars)
}
