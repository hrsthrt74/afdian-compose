package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.content.PostComment
import com.hrsthrt74.afdian.data.content.displayLikeCount
import com.hrsthrt74.afdian.data.content.isPostAuthor
import com.hrsthrt74.afdian.data.content.parseCommentPage
import com.hrsthrt74.afdian.data.content.replyTargetName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 评论解析单测：覆盖 hot_list 前置合并、游标取值、has_more、嵌套 sub_comments、
 * 坏条目跳过、楼中楼「回复对象」的判定，以及点赞数乐观补偿口径。
 *
 * 样本取自 `GET /api/comment/get-list` / `get-sub-list` 的真实响应结构。
 */
class CommentParserTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(raw: String) = parseCommentPage(json.parseToJsonElement(raw).jsonObject)

    /**
     * 解析一个「母评论 + 一条楼中楼」的响应片段，返回 `(母评论, 楼中楼)`。
     *
     * 走真实解析路径而不是直接构造模型：既覆盖 `reply_user` 的反序列化，
     * 也顺带验证「被回复者」这条链路端到端是通的。
     *
     * @param subExtraFields 追加到楼中楼条目上的原始 JSON 片段（用来注入 reply_user 等）
     */
    private fun parseThread(subExtraFields: String): Pair<PostComment, PostComment> {
        val page = parse(
            """
            {"ec":200,"em":"ok","data":{
              "list":[
                {"comment_id":"root","user_id":"u1","content":"主评论","publish_sn":100,
                 "user":{"user_id":"u1","name":"楼主"}},
                {"comment_id":"sub","user_id":"u2","content":"同问","publish_sn":101,
                 "user":{"user_id":"u2","name":"路人"}
                 $subExtraFields}
              ],
              "has_more":0
            }}
            """.trimIndent()
        )
        return page.comments[0] to page.comments[1]
    }

    @Test
    fun parsesListAndFlagsHasMore() {
        val page = parse(
            """
            {"ec":200,"em":"ok","data":{
              "list":[
                {"comment_id":"a","post_id":"p1","content":"第一条","like_count":0,
                 "publish_time":1791983005,"publish_sn":17919830056364,"has_like":null,
                 "user":{"user_id":"u1","name":"张三","avatar":"https://x/a.png"}},
                {"comment_id":"b","post_id":"p1","content":"第二条","like_count":2,
                 "publish_time":1791982975,"publish_sn":17919829756884,"has_like":1,
                 "user":{"user_id":"u2","name":"李四"}}
              ],
              "has_more":1
            }}
            """.trimIndent()
        )

        assertEquals(listOf("a", "b"), page.comments.map { it.comment_id })
        assertTrue(page.hasMore)
        assertEquals("17919829756884", page.nextPublishSn)
    }

    @Test
    fun hotListIsPrependedAndFlagged() {
        val page = parse(
            """
            {"ec":200,"em":"ok","data":{
              "hot_list":[
                {"comment_id":"hot1","content":"热评","publish_sn":17919830056364,
                 "user":{"user_id":"u1","name":"热评人"}}
              ],
              "list":[
                {"comment_id":"n1","content":"普通","publish_sn":17919829756884,
                 "user":{"user_id":"u2","name":"普通人"}}
              ],
              "has_more":0
            }}
            """.trimIndent()
        )

        // 官方网页端就是 hot_list 拼在 list 前面
        assertEquals(listOf("hot1", "n1"), page.comments.map { it.comment_id })
        assertFalse(page.hasMore)
        // 游标取合并后的末条（即 list 的末条）
        assertEquals("17919829756884", page.nextPublishSn)
    }

    @Test
    fun hotListComesBeforeListAndKeepsSubReplies() {
        // 热门评论同样是完整的 PostComment：带 user / like_count / sub_comments，
        // 复用 CommentCard 渲染时不用区分数据结构
        val page = parse(
            """
            {"ec":200,"em":"ok","data":{
              "hot_list":[
                {"comment_id":"hot1","content":"热评","publish_sn":17906410882509,
                 "like_count":7,"has_like":null,
                 "sub_comment_has_more":1,
                 "sub_comments":[
                   {"comment_id":"hotsub","content":"热评下的回复","publish_sn":17910841166226,
                    "reply_user":{"user_id":"u9","name":"被回复的人"},
                    "user":{"user_id":"u8","name":"回复者"}}
                 ],
                 "user":{"user_id":"u7","name":"热评人"}}
              ],
              "list":[
                {"comment_id":"n1","content":"普通","publish_sn":17913515053910,
                 "user":{"user_id":"u2","name":"普通人"}}
              ],
              "has_more":0
            }}
            """.trimIndent()
        )

        // 合并顺序：hot 在前，list 在后
        assertEquals(listOf("hot1", "n1"), page.comments.map { it.comment_id })
        // 游标仍取「合并后最后一条」= list 的末条，不受 hot 影响
        assertEquals("17913515053910", page.nextPublishSn)

        val hot = page.comments.first()
        assertEquals(7, hot.like_count)
        assertEquals(1, hot.sub_comment_has_more)
        assertEquals("回复者", hot.sub_comments.single().user.name)
    }

    @Test
    fun missingHotListKeyIsTolerated() {
        // 不传 hot=1 时服务端连 hot_list 这个 key 都不返回，必须当空处理而不是抛异常
        val page = parse(
            """
            {"ec":200,"em":"ok","data":{
              "list":[{"comment_id":"a","content":"只有普通评论","publish_sn":1}],
              "has_more":0
            }}
            """.trimIndent()
        )

        assertEquals(listOf("a"), page.comments.map { it.comment_id })
    }

    @Test
    fun duplicatedHotCommentIsNotRenderedTwice() {
        val page = parse(
            """
            {"ec":200,"em":"ok","data":{
              "hot_list":[{"comment_id":"dup","content":"x","publish_sn":1}],
              "list":[{"comment_id":"dup","content":"x","publish_sn":1}],
              "has_more":1
            }}
            """.trimIndent()
        )

        assertEquals(listOf("dup"), page.comments.map { it.comment_id })
    }

    @Test
    fun emptyListStopsPagination() {
        // list 为空但 has_more=1 时不能再翻页，否则游标不前进会死循环
        val page = parse(
            """
            {"ec":200,"em":"ok","data":{
              "hot_list":[{"comment_id":"hot1","content":"热评","publish_sn":5}],
              "list":[],
              "has_more":1
            }}
            """.trimIndent()
        )

        assertEquals(1, page.comments.size)
        assertFalse(page.hasMore)
    }

    @Test
    fun parsesNestedSubComments() {
        val page = parse(
            """
            {"ec":200,"em":"ok","data":{
              "list":[
                {"comment_id":"root","content":"主评论","publish_sn":100,
                 "sub_comment_has_more":1,
                 "sub_comments":[
                   {"comment_id":"sub1","content":"回复1","publish_sn":101,
                    "like_count":1,"has_like":1,"user":{"user_id":"u3","name":"回复者"}}
                 ],
                 "user":{"user_id":"u1","name":"作者"}}
              ],
              "has_more":0
            }}
            """.trimIndent()
        )

        val root = page.comments.single()
        assertEquals(1, root.sub_comment_has_more)
        val sub = root.sub_comments.single()
        assertEquals("回复1", sub.content)
        assertEquals("回复者", sub.user.name)
        assertEquals(1, sub.like_count)
        assertEquals(1, sub.has_like)
    }

    @Test
    fun missingUserFallsBackToDefault() {
        // user 缺失时不能抛异常，页面会用「匿名用户」兜底
        val page = parse(
            """
            {"ec":200,"em":"ok","data":{
              "list":[{"comment_id":"a","content":"无用户信息","publish_sn":1}],
              "has_more":0
            }}
            """.trimIndent()
        )

        val comment = page.comments.single()
        assertEquals("", comment.user.user_id)
        assertEquals("", comment.user.name)
    }

    @Test
    fun malformedEntriesAreSkipped() {
        val page = parse(
            """
            {"ec":200,"em":"ok","data":{
              "list":[
                "不是对象",
                {"content":"缺 comment_id","publish_sn":1},
                {"comment_id":"good","content":"正常","publish_sn":2}
              ],
              "has_more":0
            }}
            """.trimIndent()
        )

        // 脏条目跳过，不影响后面正常条目
        assertEquals(listOf("good"), page.comments.map { it.comment_id })
    }

    @Test
    fun missingDataYieldsEmptyPage() {
        val page = parse("""{"ec":200,"em":"ok"}""")
        assertTrue(page.comments.isEmpty())
        assertFalse(page.hasMore)
        assertEquals("", page.nextPublishSn)
    }

    @Test
    fun subCommentListUsesSameShape() {
        // get-sub-list 返回结构与主列表一致，只是没有 hot_list
        val page = parse(
            """
            {"ec":200,"em":"ok","data":{
              "list":[
                {"comment_id":"s1","content":"回复1","publish_sn":11},
                {"comment_id":"s2","content":"回复2","publish_sn":10}
              ],
              "has_more":0
            }}
            """.trimIndent()
        )

        assertEquals(listOf("s1", "s2"), page.comments.map { it.comment_id })
        assertEquals("10", page.nextPublishSn)
    }

    @Test
    fun parsesReplyUserOnSubComment() {
        // 楼中楼回复带 reply_user（被回复者），页面据此在正文前渲染「↩ 某人」
        val (root, sub) = parseThread(
            ""","reply_user":{"user_id":"u3","name":"被回复的人"}"""
        )

        assertEquals("路人", sub.user.name)
        assertEquals("被回复的人", sub.reply_user.name)
        assertEquals("u3", sub.reply_user.user_id)
        // 母评论本身没有 reply_user
        assertTrue(root.reply_user.name.isBlank())
    }

    @Test
    fun replyTargetHiddenWhenReplyingToRootAuthor() {
        // 回楼主的不标回复目标：楼中楼本来就挂在母评论下，楼主昵称在卡片顶部已经有了
        val (root, sub) = parseThread(
            ""","reply_user":{"user_id":"u1","name":"楼主"}"""
        )

        assertNull(replyTargetName(root, sub))
    }

    @Test
    fun replyTargetShownWhenReplyingToAnotherSubCommenter() {
        val (root, sub) = parseThread(
            ""","reply_user":{"user_id":"u3","name":"另一个路人"}"""
        )

        assertEquals("另一个路人", replyTargetName(root, sub))
    }

    @Test
    fun replyTargetHiddenWhenServerOmitsReplyUser() {
        val (root, sub) = parseThread("")

        assertNull(replyTargetName(root, sub))
    }

    @Test
    fun replyTargetHiddenWhenReplyUserHasNoName() {
        // 只有 id 没有昵称时标出来也没信息量，直接不标
        val (root, sub) = parseThread(""","reply_user":{"user_id":"u3","name":""}""")

        assertNull(replyTargetName(root, sub))
    }

    @Test
    fun postAuthorIsTaggedWhenCommenterMatchesPostOwner() {
        val (_, authorReply) = parseThread(
            ""","reply_user":{"user_id":"u9","name":"某人"}"""
        )
        // 母评论作者是 u1；把评论者换成 u1 模拟「作者在评论区回复」
        val asAuthor = authorReply.copy(user = authorReply.reply_user.copy(user_id = "u1", name = "楼主"))
        assertTrue(isPostAuthor("u1", asAuthor))
    }

    @Test
    fun postAuthorNotTaggedForOtherCommenters() {
        val (_, sub) = parseThread("")

        assertFalse(isPostAuthor("u1", sub))
    }

    @Test
    fun postAuthorNotTaggedWhenAuthorIdIsBlank() {
        // 动态作者 user_id 缺失时不能把「双方都是空」当成命中，
        // 否则所有匿名评论都会被标成「作者」
        val (_, sub) = parseThread("")

        assertFalse(isPostAuthor("", sub))
    }

    @Test
    fun displayLikeCountCompensatesAgainstServerState() {
        // 服务端 has_like=0，本地刚点赞 → 显示 +1
        assertEquals(6, displayLikeCount(serverCount = 5, likedNow = true, serverLiked = false))
        // 服务端已赞，本地取消 → 显示 -1
        assertEquals(4, displayLikeCount(serverCount = 5, likedNow = false, serverLiked = true))
        // 与服务端一致 → 原值
        assertEquals(5, displayLikeCount(serverCount = 5, likedNow = true, serverLiked = true))
        assertEquals(5, displayLikeCount(serverCount = 5, likedNow = false, serverLiked = false))
        // 服务端没给计数时按 0 起算
        assertEquals(1, displayLikeCount(serverCount = null, likedNow = true, serverLiked = false))
    }
}
