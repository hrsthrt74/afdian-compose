package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.data.content.RecommendedPost
import com.hrsthrt74.afdian.data.history.HistoryEntry
import com.hrsthrt74.afdian.ui.components.relativeTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** 历史记录的纯逻辑测试：不依赖 Android Context 或网络。 */
class HistoryTest {
    @Test
    fun differentCreatorsHaveDifferentKeys() {
        val first = HistoryEntry.from(AfadianUser(user_id = "creator-a", name = "甲"))
        val second = HistoryEntry.from(AfadianUser(user_id = "creator-b", name = "乙"))

        assertNotEquals(first.key, second.key)
        assertEquals("creator:creator-a", first.key)
        assertEquals("creator:creator-b", second.key)
    }

    @Test
    fun postsUsePostIdAsKey() {
        val entry = HistoryEntry.from(RecommendedPost(post_id = "post-a", title = "测试动态"))

        assertEquals("post:post-a", entry.key)
        assertEquals("动态详情", entry.title)
    }

    @Test
    fun conversationsUseUserIdAsKey() {
        val entry = HistoryEntry.fromConversation("user-a", "阿花", "https://avatar")

        assertEquals("message:user-a", entry.key)
        assertEquals("阿花", entry.title)
        assertEquals("私信对话", entry.summary)
    }

    @Test
    fun relativeTimeUsesChineseLabels() {
        val now = 10_000_000L

        assertEquals("刚刚", relativeTime(now - 30_000L, now))
        assertEquals("2分钟前", relativeTime(now - 120_000L, now))
        assertEquals("3小时前", relativeTime(now - 3 * 3_600_000L, now))
    }
}
