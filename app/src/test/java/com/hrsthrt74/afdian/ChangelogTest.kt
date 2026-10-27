package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.content.Changelog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 更新日志解析的回归测试。
 *
 * 锁住四件事：
 * 1. `## 版本 · 日期` 的两种写法（带日期 / 不带日期）都能认；
 * 2. 「未发布」小节被正确识别（`isUnreleased`），日期为空；
 * 3. 行内 markdown（粗体、反引号、链接）会被剥掉，界面上不再出现原始符号；
 * 4. 仓库里的真实 CHANGELOG.md 能被完整解析 —— 格式一旦被改坏，这里先炸，
 *    而不是等用户在「关于 → 更新日志」看到一片空白。
 */
class ChangelogTest {

    @Test
    fun parsesVersionsWithAndWithoutDate() {
        val notes = Changelog.parse(
            """
            # 更新日志

            ## 未发布

            - 还在开发中的改动

            ## 1.0.2 · 2026-10-27

            - 第一条
            - 第二条

            ## 1.0.1

            - 更早的一条
            """.trimIndent(),
        )

        assertEquals(3, notes.size)
        assertEquals("未发布", notes[0].version)
        assertNull(notes[0].date)
        assertTrue(notes[0].isUnreleased)

        assertEquals("1.0.2", notes[1].version)
        // CHANGELOG 里写 ISO（2026-10-27），展示层统一成斜杠
        assertEquals("2026/10/27", notes[1].date)
        assertEquals(listOf("第一条", "第二条"), notes[1].items)

        // 日期可省：老版本补日期前也不会解析失败
        assertEquals("1.0.1", notes[2].version)
        assertNull(notes[2].date)
    }

    @Test
    fun stripsInlineMarkdown() {
        val notes = Changelog.parse(
            """
            ## 1.0.2 · 2026-10-27

            - 新增 **动态背景** 与 `shader` 支持
            - 修复 [文档地址](https://example.com/doc) 打不开
            - 去掉 ~~废弃~~ 的入口
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                "新增 动态背景 与 shader 支持",
                "修复 文档地址 打不开",
                "去掉 废弃 的入口",
            ),
            notes.single().items,
        )
    }

    @Test
    fun dropsVersionWithoutItems() {
        // 空小节不产出：否则界面上会出现一个只有版本号、下面什么都没有的卡片
        val notes = Changelog.parse(
            """
            ## 1.0.2 · 2026-10-27

            ## 1.0.1 · 2026-10-26

            - 有内容
            """.trimIndent(),
        )

        assertEquals(listOf("1.0.1"), notes.map { it.version })
    }

    @Test
    fun normalizesDateToSlashWithPadding() {
        // 展示层统一 `YYYY/MM/DD`：分隔符换成斜杠、月日补零。
        // 不补零的话同一列日期宽窄不一，右侧对齐会跳
        val notes = Changelog.parse(
            """
            ## 1.0.2 · 2026-1-5

            - 一条

            ## 1.0.1 · 2026.10.6

            - 另一条
            """.trimIndent(),
        )

        assertEquals(listOf("2026/01/05", "2026/10/06"), notes.map { it.date })
    }

    @Test
    fun realChangelogParses() {
        // 单元测试的工作目录是 app/，仓库根的 CHANGELOG.md 在上一层
        val file = File("../CHANGELOG.md")
        assumeTrue("找不到仓库根的 CHANGELOG.md，跳过真实文件校验", file.exists())

        val notes = Changelog.parse(file.readText())

        assertTrue("真实 CHANGELOG 应当至少解析出一个版本", notes.isNotEmpty())
        // 每一节都得有版本号、有内容；日期要么为空（未发布），要么是 YYYY-MM-DD
        notes.forEach { note ->
            assertTrue("版本号不应为空", note.version.isNotBlank())
            assertTrue("${note.version} 没有解析出条目", note.items.isNotEmpty())
            note.date?.let { assertTrue("日期格式异常：$it", Regex("""\d{4}/\d{2}/\d{2}""").matches(it)) }
        }
        // 发布过的版本必须带日期，否则「更新日志」页右侧会空一块
        notes.filterNot { it.isUnreleased }.forEach { note ->
            assertTrue("${note.version} 缺少日期，请写成 `## 版本 · YYYY-MM-DD`", note.date != null)
        }
    }
}
