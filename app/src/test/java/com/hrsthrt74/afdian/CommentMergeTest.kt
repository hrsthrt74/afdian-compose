package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.content.PostComment
import com.hrsthrt74.afdian.data.content.mergeComments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 评论列表合并的回归测试。
 *
 * 背景：服务端的 `hot_list` 与 `list` 会下发同一条评论；而热评本质是「历史高赞的老评论」，
 * 翻页翻到它所在的年代时，同一个 `comment_id` 还会在后面的页里再出现一次。
 * 重复 id 会让详情页的 `items(key = { it.comment_id })` 抛
 * "Key ... was already used" 直接闪退，所以合并必须保证两条不变式：
 * 1. `comment_id` 唯一且非空；
 * 2. 同 id 保留先出现的那一条 —— 热评已经在顶部，后来的重复项直接丢掉，位置也不能变。
 */
class CommentMergeTest {

    /** 造一条评论；[likeCount] 用来区分同 id 的不同快照，[id] 为空串即模拟坏数据。 */
    private fun comment(id: String, likeCount: Int = 0) =
        PostComment(comment_id = id, content = "内容-$id", like_count = likeCount)

    @Test
    fun mergeAppendsOnlyNewCommentsAcrossPages() {
        // 热评（a、b）已经在屏幕上，翻到较早的一页时 list 里又出现了 a、b
        val merged = mergeComments(
            existing = listOf(comment("a"), comment("b")),
            incoming = listOf(comment("a"), comment("b"), comment("c")),
        )

        // 重复的两条被丢掉，只追加真正没见过的 c
        assertEquals(listOf("a", "b", "c"), merged.map { it.comment_id })
    }

    @Test
    fun hotCommentStaysOnTopAfterNextPage() {
        // 首屏解析结果：热评 hot1 置顶 + 最新一条 n1
        val first = listOf(comment("hot1"), comment("n1"))

        // 下一页（更早）里又下发了一次 hot1
        val merged = mergeComments(first, listOf(comment("hot1"), comment("n2")))

        // 热评仍然在第一位，且只出现一次
        assertEquals(listOf("hot1", "n1", "n2"), merged.map { it.comment_id })
    }

    @Test
    fun mergeDropsDuplicatesInsideSinglePage() {
        // 同一页内部自重复（服务端异常）也要归一
        val merged = mergeComments(emptyList(), listOf(comment("a"), comment("a"), comment("b")))

        assertEquals(listOf("a", "b"), merged.map { it.comment_id })
    }

    @Test
    fun mergeDropsBlankCommentIds() {
        // comment_id 为空的行在 LazyColumn 里就是重复 key，直接丢弃
        val merged = mergeComments(
            existing = listOf(comment(""), comment("  ")),
            incoming = listOf(comment("a"), comment("")),
        )

        assertEquals(listOf("a"), merged.map { it.comment_id })
    }

    @Test
    fun mergeKeepsFirstOccurrenceFields() {
        // 同 id 两条：existing 是列表里当前展示的那条（点赞数已是新的），incoming 是旧页快照
        val shown = comment("a", likeCount = 9)
        val stale = comment("a", likeCount = 1)

        val merged = mergeComments(listOf(shown), listOf(stale))

        assertEquals(1, merged.size)
        assertEquals(9, merged.single().like_count)
        // 断言同一个实例，证明不是「先丢后建」而是原样保留
        assertSame(shown, merged.single())
    }

    @Test
    fun mergeHandlesEmptyInputs() {
        assertTrue(mergeComments(emptyList(), emptyList()).isEmpty())

        val existing = listOf(comment("a"), comment("b"))
        // 空页（翻到底后再触底）必须原样返回，不能把已有列表抹掉
        assertEquals(existing, mergeComments(existing, emptyList()))
    }

    @Test
    fun mergedIdsAreUniqueAndNonBlank() {
        // 混合场景：跨页重叠 + 页内自重复 + 空 id，一次性验证列表 key 不变式
        val merged = mergeComments(
            existing = listOf(comment("hot"), comment("n1")),
            incoming = listOf(comment("hot"), comment(""), comment("n1"), comment("n2"), comment("n2")),
        )
        val ids = merged.map { it.comment_id }

        assertEquals(listOf("hot", "n1", "n2"), ids)
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.none { it.isBlank() })
    }
}
