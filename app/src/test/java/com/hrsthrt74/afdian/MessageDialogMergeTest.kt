package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.data.content.MessageDialog
import com.hrsthrt74.afdian.data.content.mergeDialogs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 会话列表合并的回归测试。
 *
 * 背景：`/api/message/dialogs` 是页码分页 + 按最新消息时间倒序，翻页期间来了新消息就会
 * 产生跨页重叠；重叠的 `user_id` 会让 LazyColumn 的 `items(key = { it.id })` 直接抛
 * "Key ... was already used" 闪退。这里钉住合并后的两条不变式：
 * 1. id 唯一且非空；
 * 2. 同 id 保留先出现的一条（existing 是较新的快照，不能被旧页覆盖）。
 */
class MessageDialogMergeTest {

    /** 造一条会话；[userId] 为空串即模拟「响应缺 user 对象」的坏数据。 */
    private fun dialog(userId: String, unread: Int = 0, latestMsgId: Int? = null) = MessageDialog(
        latest_msg_id = latestMsgId,
        unread_count = unread,
        user = AfadianUser(user_id = userId),
    )

    @Test
    fun mergeAppendsOnlyNewDialogsAcrossPages() {
        // page 2 的第一条（b）因为来了新消息下沉过，已经在 page 1 里出现过
        val merged = mergeDialogs(listOf(dialog("a"), dialog("b")), listOf(dialog("b"), dialog("c")))

        assertEquals(listOf("a", "b", "c"), merged.map { it.id })
    }

    @Test
    fun mergeDropsDuplicatesInsideSinglePage() {
        // 首页内部自重复（服务端异常）也要归一
        val merged = mergeDialogs(emptyList(), listOf(dialog("a"), dialog("a"), dialog("b")))

        assertEquals(listOf("a", "b"), merged.map { it.id })
    }

    @Test
    fun mergeDropsBlankUserIdDialogs() {
        // user_id 为空的行既打不开会话也标不了已读，直接丢弃（多个空 id 曾是最直接的空 key 崩溃）
        val merged = mergeDialogs(
            emptyList(),
            listOf(dialog(""), dialog("a"), dialog("   "), dialog("", latestMsgId = 100)),
        )

        assertEquals(listOf("a"), merged.map { it.id })
    }

    @Test
    fun mergeKeepsFirstOccurrenceFields() {
        // 两条同 id：existing 是已在屏幕上、未读已清掉的快照；incoming 是旧页里未读仍为 5 的旧数据
        val fresh = dialog("a", unread = 0)
        val stale = dialog("a", unread = 5)

        val merged = mergeDialogs(listOf(fresh), listOf(stale))

        assertEquals(1, merged.size)
        assertEquals(0, merged.single().unread_count)
        // 断言同一个实例，证明不是「先丢后建」而是原样保留
        assertSame(fresh, merged.single())
    }

    @Test
    fun mergeHandlesEmptyInputs() {
        assertTrue(mergeDialogs(emptyList(), emptyList()).isEmpty())

        val existing = listOf(dialog("a"), dialog("b"))
        // 空页（翻到底后再触底）必须原样返回，不能把已有列表抹掉
        assertEquals(existing, mergeDialogs(existing, emptyList()))
    }

    @Test
    fun mergedIdsAreUniqueAndNonBlank() {
        // 混合场景：跨页重叠 + 页内自重复 + 空 id，一次性验证列表 key 不变式
        val merged = mergeDialogs(
            listOf(dialog("a"), dialog("b")),
            listOf(dialog("b"), dialog(""), dialog("c"), dialog("c")),
        )
        val ids = merged.map { it.id }

        assertEquals(listOf("a", "b", "c"), ids)
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.none { it.isBlank() })
    }
}
