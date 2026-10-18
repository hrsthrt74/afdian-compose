package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.data.content.MessageDialog
import com.hrsthrt74.afdian.data.content.PinnedDialogCard
import com.hrsthrt74.afdian.data.content.mergePinnedIntoDialogs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 会话置顶合并的回归测试。
 *
 * 背景：`/api/message/dialogs` 是页码分页，置顶的老会话可能落在后面的分页里、首屏拿不到。
 * 所以置顶存的是「卡片快照」，列表 = 置顶快照（服务端已加载就用服务端那一行）+ 其余服务端会话。
 * 这里钉住四条不变式：
 * 1. 置顶会话永远在最前，即使服务端还没加载到（用快照补位，不允许「有配置没入口」）；
 * 2. 置顶顺序 = 置顶动作顺序（新置顶的在最前）；
 * 3. 未置顶部分严格保持服务端顺序，同一会话不重复出现；
 * 4. 没有置顶时原样返回同一个列表实例。
 */
class ConversationPinTest {

    /** 造一条服务端会话。 */
    private fun dialog(userId: String, unread: Int = 0, desc: String? = null) = MessageDialog(
        unread_count = unread,
        user = AfadianUser(user_id = userId, name = "名字-$userId"),
        desc = desc,
    )

    /** 造一条本地置顶快照。 */
    private fun card(userId: String, unread: Int = 0, desc: String? = null) = PinnedDialogCard(
        userId = userId,
        name = "快照-$userId",
        desc = desc,
        unreadCount = unread,
    )

    @Test
    fun noPinnedReturnsSameList() {
        val dialogs = listOf(dialog("a"), dialog("b"))

        val result = mergePinnedIntoDialogs(dialogs, emptyList())

        // 同一实例：没有置顶时不该造新列表，避免每次流发射都触发一次列表差量
        assertSame(dialogs, result)
    }

    @Test
    fun pinnedDialogOutsideLoadedPagesIsStillShown() {
        // old 不在已加载的两条里（模拟置顶的是第 5 页的老会话）
        val result = mergePinnedIntoDialogs(
            dialogs = listOf(dialog("a"), dialog("b")),
            pinned = listOf(card("old", unread = 2, desc = "最后一条消息")),
        )

        assertEquals(listOf("old", "a", "b"), result.map { it.id })
        // 快照补位的卡片要能正常渲染：昵称、预览、未读都来自快照
        assertEquals("快照-old", result.first().user.name)
        assertEquals("最后一条消息", result.first().desc)
        assertEquals(2, result.first().unread_count)
    }

    @Test
    fun pinnedDialogAlreadyLoadedUsesServerRow() {
        val serverRow = dialog("a", unread = 0, desc = "新消息")
        val result = mergePinnedIntoDialogs(
            dialogs = listOf(serverRow, dialog("b")),
            pinned = listOf(card("a", unread = 5, desc = "旧预览")),
        )

        // 服务端数据优先：未读、预览都以服务端为准（快照只在拉不到时补位）
        assertSame(serverRow, result.first())
        assertEquals("新消息", result.first().desc)
        assertEquals(0, result.first().unread_count)
    }

    @Test
    fun pinnedKeepPinOrder() {
        // 置顶顺序 b 先、a 后（新置顶的排在列表最前）
        val result = mergePinnedIntoDialogs(
            dialogs = listOf(dialog("a"), dialog("b"), dialog("c")),
            pinned = listOf(card("b"), card("a")),
        )

        assertEquals(listOf("b", "a", "c"), result.map { it.id })
    }

    @Test
    fun unpinnedKeepServerOrder() {
        val result = mergePinnedIntoDialogs(
            dialogs = listOf(dialog("a"), dialog("b"), dialog("c"), dialog("d")),
            pinned = listOf(card("c")),
        )

        assertEquals(listOf("c", "a", "b", "d"), result.map { it.id })
    }

    @Test
    fun pinnedDialogNeverAppearsTwice() {
        val result = mergePinnedIntoDialogs(
            dialogs = listOf(dialog("a"), dialog("b")),
            pinned = listOf(card("a")),
        )
        val ids = result.map { it.id }

        assertEquals(listOf("a", "b"), ids)
        // LazyColumn 的 key 不变式：同一会话不能既来自快照又来自服务端
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.none { it.isBlank() })
    }

    @Test
    fun blankPinnedUserIdIsSkipped() {
        val result = mergePinnedIntoDialogs(
            dialogs = listOf(dialog("a")),
            pinned = listOf(card(""), card("a")),
        )

        assertEquals(listOf("a"), result.map { it.id })
    }

    @Test
    fun duplicatePinnedEntriesAreCollapsed() {
        // DataStore 被手工改坏时可能配出两条同 id：只保留第一条，不能让列表撞重复 key
        val result = mergePinnedIntoDialogs(emptyList(), listOf(card("a"), card("a")))

        assertEquals(listOf("a"), result.map { it.id })
    }

    @Test
    fun emptyServerListStillShowsPinned() {
        // 首屏还在加载 / 加载失败时，置顶卡片依然要有入口
        val result = mergePinnedIntoDialogs(emptyList(), listOf(card("old")))

        assertEquals(listOf("old"), result.map { it.id })
    }
}
