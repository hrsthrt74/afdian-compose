package com.hrsthrt74.afdian.ui.navigation

import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.data.content.RecommendedPost
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

@Serializable
sealed interface Route : NavKey {
    @Serializable data object MainTabs : Route

    @Serializable data object Messages : Route
    @Serializable data object Profile : Route
    @Serializable data object History : Route

    @Serializable data class CreatorDetail(val user: AfadianUser) : Route
    // 动态详情：路由里携带列表页已有的 post 数据，详情页可先秒显再拉全文
    @Serializable data class PostDetail(val post: RecommendedPost) : Route
    // 按动态 id 打开详情（如「我的」页的「创作者指南」入口，只持有 id 时进入）
    @Serializable data class PostDetailById(val postId: String) : Route
    @Serializable data class MessageConversation(val userId: String, val userName: String, val avatarUrl: String? = null) : Route

    @Serializable data object Favorites : Route
    @Serializable data object Sponsoring : Route
    @Serializable data object Orders : Route
    @Serializable data object Management : Route
    // 收入明细页（创作者后台 /api/my/stat，按日/周/月统计表格）
    @Serializable data object IncomeDetail : Route
    // 每月汇总页（创作者后台 /api/my/income，按年分组的月度账单，可切换抽成前/后金额）
    @Serializable data object MonthlySummary : Route
    // 独立收到发电订单列表，暂未接入「我的」页入口
    @Serializable data object ReceivedSponsors : Route
    // 赞助者管理页（创作者后台 who-sponsored-me），入口在「我的」页创作者分组
    @Serializable data object SponsorManage : Route
    // 赞助者感谢页（创作者后台 get-thank-sponsors）；年月由「每月汇总」页按行带入
    @Serializable data class ThankSponsors(val year: Int, val month: Int) : Route
    // 优惠码页（创作者后台 creator/list-affiliate），入口后续再接
    @Serializable data object Affiliates : Route
    @Serializable data object Settings : Route
    // 开源许可页：AboutLibraries 收集的第三方依赖与许可证，入口在设置页「退出登录」上方
    @Serializable data object OssLicenses : Route
    // 存储空间管理页：本地缓存/数据占用总览与分类清理，入口在设置页「其他」分组
    @Serializable data object Storage : Route
    // 日志页：运行日志与崩溃报告的记录开关、占用查看、导出与清空，入口在设置页「其他」分组
    @Serializable data object Log : Route
    // 接口调试页：主动探针逐个运行核心无参接口并查看原始返回，入口在设置页「其他」分组
    @Serializable data object ApiProbe : Route
    // 屏蔽 / 拒绝名单页（/api/my/blacked，TabRow 切换两个名单），入口在「我的」页设置上方
    @Serializable data object Blocklist : Route
    // 开发者页（官网 /dashboard/dev：user_id、Webhook URL、API Token），入口在「我的」页屏蔽与拒绝名单下方
    @Serializable data object Developer : Route
    // 售卖商品管理页（创作者后台 all-plans 的 sale_list），入口在「我的」页快捷入口占位
    @Serializable data object Shop : Route
}
