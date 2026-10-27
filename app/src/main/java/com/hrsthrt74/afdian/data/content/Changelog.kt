package com.hrsthrt74.afdian.data.content

import android.content.Context
import com.hrsthrt74.afdian.AppLog

/**
 * 一条版本更新记录。
 *
 * @param version 版本号原文（如 `1.0.2`）；「未发布」那一节也用这个字段承载
 * @param date 展示用的发布日期，统一成 `YYYY/MM/DD`；「未发布」没有日期，为 null
 * @param items 该版本的条目，已剥掉行内 markdown 标记
 */
data class ReleaseNote(
    val version: String,
    val date: String?,
    val items: List<String>,
) {
    /** 是否为「尚未发布」那一节。正式包会整节过滤掉。 */
    val isUnreleased: Boolean get() = version == Changelog.UNRELEASED_VERSION
}

/**
 * 更新日志的读取与解析。
 *
 * 数据源是仓库根的 `CHANGELOG.md`，构建时由 Gradle 复制进 assets（见 app/build.gradle.kts），
 * 应用内不另存一份 —— 发版时只改 markdown，应用里的记录与 GitHub 上的天然一致。
 *
 * 只实现项目自己在用的两种语法：`## 版本 · 日期` 与 `- 条目`。
 * 不引 Markdown 引擎：为了一个只读信息页装几十上百 KB、还要处理链接点击与代码块主题色，
 * 不划算；而 CHANGELOG 的格式本来就是我们自己定的。
 * 看不懂的行直接忽略 —— 宁可少显示一行，也不要让界面上冒出半截 `**` 之类的原始符号。
 */
object Changelog {

    /** 「未发布」小节的固定标题。 */
    const val UNRELEASED_VERSION = "未发布"

    private const val ASSET_NAME = "CHANGELOG.md"
    private const val LOG_TAG = "Changelog"

    /** 二级标题：`## 1.0.2 · 2026-10-27`。日期可省，分隔符容忍 `|`，日期里的分隔符容忍 `/` 与 `.`。 */
    private val headerPattern = Regex("""^##\s+(.+?)(?:\s*[·|]\s*(\d{4}[-/.]\d{1,2}[-/.]\d{1,2}))?\s*$""")

    /** 列表项：`- xxx`，兼容 `* xxx`。 */
    private val itemPattern = Regex("""^[-*]\s+(.+)$""")

    /** 行内链接 `[文字](url)`：只留文字。列表里点不了链接，留着方括号反而碍眼。 */
    private val inlineLinkPattern = Regex("""\[([^\]]+)]\([^)]*\)""")

    /** 日期的年/月/日三段，分隔符容忍 `-` `/` `.`。 */
    private val datePattern = Regex("""(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})""")

    /**
     * 读取并解析打包进 assets 的 [ASSET_NAME]。
     *
     * 失败时返回空列表而不是抛异常：更新日志是只读展示，读不到就显示「暂无记录」，
     * 没必要让一个信息页把用户拦住。
     */
    fun load(context: Context): List<ReleaseNote> = runCatching {
        context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
    }.mapCatching(::parse)
        .onFailure { AppLog.e(LOG_TAG, "读取 assets/$ASSET_NAME 失败", it) }
        .getOrDefault(emptyList())

    /**
     * 把 markdown 文本解析成版本列表（按文件中的顺序，最新的在前）。
     *
     * 纯函数、不依赖 Android，可直接被 JVM 单元测试覆盖 —— 解析是这个功能里唯一
     * 有分支逻辑的部分，界面只是把结果摆出来。
     */
    fun parse(markdown: String): List<ReleaseNote> {
        val notes = mutableListOf<ReleaseNote>()
        var version: String? = null
        var date: String? = null
        var items = mutableListOf<String>()

        // 遇到下一个标题（或文件结束）时结算上一个版本
        fun flush() {
            val currentVersion = version ?: return
            // 没有任何条目的版本不产出：一个空标题会把读者的注意力白占一行
            if (items.isNotEmpty()) notes += ReleaseNote(currentVersion, date, items.toList())
        }

        markdown.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            val header = headerPattern.find(line)
            if (header != null) {
                flush()
                version = header.groupValues[1].trim()
                date = header.groupValues[2].takeIf { it.isNotEmpty() }?.let(::formatDate)
                items = mutableListOf()
                return@forEach
            }
            val item = itemPattern.find(line)
            if (item != null && version != null) {
                items += stripInlineMarkup(item.groupValues[1])
            }
        }
        flush()
        return notes
    }

    /** 去掉行内 markdown 标记，保证界面上不出现原始符号。 */
    private fun stripInlineMarkup(text: String): String =
        inlineLinkPattern.replace(text, "$1")
            .replace("**", "")
            .replace("__", "")
            .replace("`", "")
            .replace("~~", "")
            .trim()

    /**
     * 把 `2026-10-27` 统一成 `2026/10/27`。
     *
     * CHANGELOG 里写 ISO 格式（`YYYY-MM-DD`）—— 那是 Git/GitHub 上的通行写法，便于脚本处理；
     * 界面上按中文习惯用斜杠分隔。转换放在解析层而不是两个页面各做一次，
     * 免得哪天新增一个展示位又忘了格式化。
     * 月/日补零，`2026-1-5` 也会变成 `2026/01/05`，同一列日期宽度才一致。
     */
    private fun formatDate(raw: String): String {
        val match = datePattern.matchEntire(raw) ?: return raw
        val (year, month, day) = match.destructured
        return "$year/${month.padStart(2, '0')}/${day.padStart(2, '0')}"
    }
}
