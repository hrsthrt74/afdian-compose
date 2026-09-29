package com.hrsthrt74.afdian.data.probe

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/*
 * 本文件全部是纯函数（不依赖 Android、不依赖网络），因此可以放心地在 JVM 单元测试里覆盖。
 * 职责只有两件事：① 从任意 JSON 里抽出「关键字段摘要」；② 把原始 JSON 变成可以安全上屏的文本。
 */

/** 原始 JSON 展示上限：先按行截，再按字符截，两者同时生效。 */
private const val MAX_DISPLAY_LINES = 400
private const val MAX_DISPLAY_CHARS = 80_000

/** 摘要里数组最多展开前几项（更长的部分只用一行「共 N 项」概括）。 */
private const val ARRAY_PREVIEW_COUNT = 3

/** 缩进美化用 Json 实例；只做编码，不做解码。 */
private val prettyJson = Json { prettyPrint = true }

/** 匹配 a.b[0].c 里的下标段，例如 `[0]`、`[12]`。 */
private val INDEX_PATTERN = Regex("\\[(\\d+)]")

/**
 * 需要脱敏的查询参数。值一律替换成 `***`。
 *
 * 用于「展示」与「日志」，**绝不用在真实请求上**——
 * 请求本身必须带真实 token，服务端才认。
 */
private val SENSITIVE_QUERY = Regex(
    "([?&](?:auth_token|access_token|token|cookie)=)[^&\\s]*",
    RegexOption.IGNORE_CASE,
)

/**
 * 抹掉 URL / 字符串里所有 token 类查询参数的值。
 *
 * 例：`https://afdian.com/api/my/dashboard?auth_token=abc123` -> `...?auth_token=***`
 */
internal fun String.maskSecrets(): String =
    SENSITIVE_QUERY.replace(this) { match -> "${match.groupValues[1]}***" }

/**
 * 中国大陆手机号，两种形态都要接住：
 * - 完整 11 位：`13812345678`；
 * - 服务端已半掩：`187***2066`（前 3 位 + 星号 + 尾 4 位，星号数量不固定）。
 * 前后都不允许是数字——订单号 / 时间戳等长数字串里的片段不能被误伤。
 */
private val PHONE_PATTERN = Regex("(?<!\\d)1[3-9](?:\\d{9}|\\d\\*+\\d{4})(?!\\d)")

/**
 * 邮箱：本地部分允许出现 `*`（服务端半掩形态如 `193***@qq.com`），
 * 也覆盖未脱敏的完整邮箱。域名不算身份信息，掩码时保留。
 */
private val EMAIL_PATTERN = Regex("(?<![A-Za-z0-9._%+*-])[A-Za-z0-9._%+*-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")

/**
 * 掩掉文本里的手机号与邮箱（PII 上屏前的最后一道闸）：
 * - 手机号（完整或半掩）统一掩成 `1**********`：保留首字符 `1`、与号码等长，
 *   一眼能认出「这是个手机号」，但认不出是哪个手机号；
 * - 邮箱本地部分掩成 `***`，保留域名（如 `193***@qq.com` -> `***@qq.com`）。
 */
internal fun String.maskPii(): String =
    PHONE_PATTERN.replace(
        EMAIL_PATTERN.replace(this) { match -> "***@" + match.value.substringAfter('@') },
        "1**********",
    )

/**
 * 把任意 JSON 元素扁平化成「路径 → 叶子值」列表，用于摘要展示与缺省兜底。
 *
 * 处理规则：
 * - [JsonObject]：逐字段递归，路径用 `.` 连接；
 * - [JsonArray]：只展开前 [arrayPreviewCount] 项，路径写成 `key[0]`，
 *   并额外补一条 `key[…] = 共 N 项`，避免大数组把摘要刷屏；
 * - [JsonNull]：输出 `"null"`。注意 [JsonNull] 是 [JsonPrimitive] 的子类，
 *   **必须先判断 JsonNull**，否则会落进 primitive 分支（虽然结果相近，但语义会漂）；
 * - 深度达到 [maxDepth]：折叠成 `{N 个字段}` / `[N 项]`，不再深入；
 * - 根就是数组 / 基本类型：根前缀为空串，第一层路径即 `[0]` / `""`。
 *
 * @param prefix 当前递归到的路径前缀（调用方一般不用传）
 * @param out 结果累加容器（递归内部使用，调用方一般不用传）
 */
internal fun flattenJsonLeaves(
    element: JsonElement,
    maxDepth: Int = 3,
    arrayPreviewCount: Int = ARRAY_PREVIEW_COUNT,
    prefix: String = "",
    depth: Int = 0,
    out: MutableList<Pair<String, String>> = mutableListOf(),
): List<Pair<String, String>> {
    when (element) {
        // JSON null：叶子，显式输出 "null" 以便与「字段缺失」区分
        is JsonNull -> out += prefix to "null"

        is JsonObject -> {
            if (depth >= maxDepth) {
                // 超深：只报告字段个数，不展开
                out += prefix to "{${element.size} 个字段}"
            } else {
                element.forEach { (key, value) ->
                    flattenJsonLeaves(
                        element = value,
                        maxDepth = maxDepth,
                        arrayPreviewCount = arrayPreviewCount,
                        prefix = if (prefix.isEmpty()) key else "$prefix.$key",
                        depth = depth + 1,
                        out = out,
                    )
                }
            }
        }

        is JsonArray -> {
            if (depth >= maxDepth) {
                out += prefix to "[${element.size} 项]"
            } else {
                element.take(arrayPreviewCount).forEachIndexed { index, value ->
                    flattenJsonLeaves(
                        element = value,
                        maxDepth = maxDepth,
                        arrayPreviewCount = arrayPreviewCount,
                        prefix = "$prefix[$index]",
                        depth = depth + 1,
                        out = out,
                    )
                }
                // 超出预览条数时补一行总数，否则用户会以为数组只有 3 项
                if (element.size > arrayPreviewCount) {
                    out += "$prefix[…]" to "共 ${element.size} 项"
                }
            }
        }

        is JsonPrimitive -> out += prefix to (element.contentOrNull ?: element.toString()).maskPii()
    }
    return out
}

/**
 * 按 `a.b[0].c` 形式的路径解析子元素。任一段缺失、或中间类型不匹配时返回 null。
 *
 * 路径段形如 `list[0]`：先按名字 `list` 取字段，再按 `[0]` 逐层下标取值。
 * 首段也可以只有下标（如 `[0].x`），此时跳过名字查找。
 */
internal fun resolveJsonPath(root: JsonElement?, path: String): JsonElement? {
    if (root == null || path.isBlank()) return root
    var current: JsonElement = root
    for (token in path.split('.')) {
        // 段内先取名字部分（`list[0]` -> `list`；`[0]` -> 空串）
        val name = token.substringBefore('[')
        if (name.isNotEmpty()) {
            current = (current as? JsonObject)?.get(name) ?: return null
        }
        // 再按顺序应用所有下标（支持 `[0][1]` 这种连续下标）
        var matchedIndex = false
        INDEX_PATTERN.findAll(token).forEach { match ->
            matchedIndex = true
            val array = current as? JsonArray ?: return null
            val index = match.groupValues[1].toIntOrNull() ?: return null
            current = array.getOrNull(index) ?: return null
        }
        if (!matchedIndex && name.isEmpty()) return null
    }
    return current
}

/**
 * 把解析到的元素变成一行可读文本：
 * - 基本类型 → 原文（JSON null 是 `"null"`）
 * - 对象 → `{N 个字段}`
 * - 数组 → `[N 项]`（恰好覆盖「只想知道个数」的场景）
 * - 元素为 null（路径没解析到）→ null
 */
internal fun describeJsonValue(element: JsonElement?): String? = when (element) {
    null -> null
    is JsonNull -> "null"
    is JsonObject -> "{${element.size} 个字段}"
    is JsonArray -> "[${element.size} 项]"
    // 叶子值上屏前掩手机号：摘要可能直接命中 mobile / phone 这类字段
    is JsonPrimitive -> (element.contentOrNull ?: element.toString()).maskPii()
}

/**
 * 生成卡片摘要。
 *
 * 解析顺序（关键）：先按**响应根**解析；失败再把根当成 `{ec, data}` 信封，
 * 取 `data` 对象作基点重试一次。这样 spec 里写 `summary.month_amount` 能命中
 * `{ec:200, data:{summary:{month_amount:"..."}}}`，写 `data.xxx` 或根级字段也能命中。
 *
 * [ApiProbeSpec.highlights] 为空时，自动折叠取叶子字段前 [fallbackCount] 条兜底。
 */
internal fun buildHighlights(
    root: JsonElement?,
    spec: ApiProbeSpec,
    fallbackCount: Int = 6,
): List<Highlight> {
    if (root == null) return emptyList()
    val dataBase = (root as? JsonObject)?.get("data")

    if (spec.highlights.isNotEmpty()) {
        return spec.highlights.map { path ->
            // 先按根试，再按 data 试；都没解析到就标记为缺失（value = null）
            val resolved = resolveJsonPath(root, path) ?: resolveJsonPath(dataBase, path)
            Highlight(path = path, value = describeJsonValue(resolved))
        }
    }

    // 未声明重点字段：折叠一层后取叶子兜底，跳过空路径（根是基本类型时会出现空路径）
    return flattenJsonLeaves(dataBase ?: root, maxDepth = 2)
        .filter { it.first.isNotBlank() }
        .take(fallbackCount)
        .map { Highlight(path = it.first, value = it.second) }
}

/**
 * Pretty-print 响应体并做双重截断，防止超大 JSON 把 Compose 文本布局拖死。
 *
 * 截断规则：最多 [MAX_DISPLAY_LINES] 行、最多 [MAX_DISPLAY_CHARS] 字符，两者取更严的一方；
 * 发生截断时在末尾追加一行说明，并把截断前的字符数告知用户。
 *
 * @param text 原始响应文本（解析失败时的兜底展示内容）
 * @param parsed 已解析的 JSON 元素；为 null（不是合法 JSON）时直接展示 [text]
 * @return (展示文本, 是否被截断)
 */
internal fun prettyPrintAndCap(text: String, parsed: JsonElement?): Pair<String, Boolean> {
    // 解析成功才美化；美化失败（理论上不会发生）退回原文，保证永不因格式化而丢内容。
    // 手机号掩码统一在这里做：掩后的文本才是上屏内容，后面的字符数统计与展示保持一致。
    val source = if (parsed != null) {
        runCatching { prettyJson.encodeToString(JsonElement.serializer(), parsed) }.getOrElse { text }.maskPii()
    } else {
        text.maskPii()
    }

    val byLines = source.lineSequence().take(MAX_DISPLAY_LINES).joinToString("\n")
    val capped = if (byLines.length > MAX_DISPLAY_CHARS) byLines.take(MAX_DISPLAY_CHARS) else byLines
    val truncated = capped.length != source.length
    val suffix = if (truncated) "\n…（已截断，完整响应 ${source.length} 字符）" else ""
    return (capped + suffix) to truncated
}
