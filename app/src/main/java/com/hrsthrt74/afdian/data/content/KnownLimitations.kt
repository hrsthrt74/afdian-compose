package com.hrsthrt74.afdian.data.content

/**
 * 一条「尚未完成」的功能说明。
 *
 * @param title 功能名（短，用于列表标题）
 * @param detail 当前状态说明（稍长，用于列表正文与弹窗正文）
 */
data class KnownIssue(
    val title: String,
    val detail: String,
)

/**
 * 「当前功能尚未全部完成」的文案单一来源。
 *
 * 为什么放在数据层而不是写死在各个界面里：
 * - 设置页的「功能完成度」页与 README 的「已知限制」两处要说的是同一件事，
 *   各写一份必然会漂移 —— 某天补完了一个功能，只改了两处中的一处，
 *   用户就会看到应用内说没做、文档说做了这种自相矛盾；
 * - 这里只放**纯文案**，不依赖任何 Android 组件，便于在 JVM 单元测试里断言。
 *
 * 补完一个功能时：把对应条目从这里删掉，应用内与文档就同时生效。
 */
object KnownLimitations {

    /** 页面顶部的一句话引导 */
    const val NOTICE_SUMMARY = "本应用仍在开发中，以下功能目前还不可用。"

    /**
     * 全部未完成项。
     *
     * 顺序即展示顺序：先列「用户日常最容易撞上的」（商品编辑、感谢页），
     * 再列「边缘功能」（众筹、合集、评论、转发）。
     */
    val issues: List<KnownIssue> = listOf(
        KnownIssue(
            title = "商品改库存",
            detail = "商品管理页的「改库存」入口暂未开放，底层编辑弹层已实现，等待入口接入。",
        ),
        KnownIssue(
            title = "感谢页分享 / 月份跳转",
            detail = "赞助者感谢页顶部的「分享」与「月份跳转」暂不可用。",
        ),
        KnownIssue(
            title = "众筹卡片",
            detail = "众筹卡片仅作展示，点击不会跳转（暂无众筹详情页）。",
        ),
        KnownIssue(
            title = "动态合集",
            detail = "动态合集暂无独立页面，含合集链接的跳转会直接打开其中某一条动态。",
        ),
        KnownIssue(
            title = "评论只读",
            detail = "评论区暂不支持发布、回复或删除评论。",
        ),
        KnownIssue(
            title = "消息转发",
            detail = "消息气泡菜单里的「转发」暂不可用。",
        ),
    )
}
