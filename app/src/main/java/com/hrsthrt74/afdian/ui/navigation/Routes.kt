package com.hrsthrt74.afdian.ui.navigation

import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.data.content.RecommendedPost
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

@Serializable
sealed interface Route : NavKey {
    @Serializable data object MainTabs : Route

    // 未登录流程的根路由：登录页本身也是一条路由。
    // 这样「登录页 → 免责声明」能走与其他页面完全一致的推入/返回（转场、圆角裁剪、预测性返回、
    // 返回栈保存的表单状态），而不是在根组合里手写一套动画。
    @Serializable data object Login : Route

    @Serializable data object Messages : Route
    @Serializable data object Profile : Route
    @Serializable data object History : Route

    @Serializable data class CreatorDetail(val user: AfadianUser) : Route
    // 动态详情：路由里携带列表页已有的 post 数据，详情页可先秒显再拉全文。
    // scrollToComments=true 表示「从列表页点评论按钮进来的」，进页面后要把评论区滚进视口。
    // 该标记进路由而非全局变量，进程重建/返回栈重放后依然有效。
    @Serializable data class PostDetail(val post: RecommendedPost, val scrollToComments: Boolean = false) : Route
    // 按动态 id 打开详情（如文档页、开发者页的文档入口，只持有 id 时进入）；语义同上
    @Serializable data class PostDetailById(val postId: String, val scrollToComments: Boolean = false) : Route
    @Serializable data class MessageConversation(val userId: String, val userName: String, val avatarUrl: String? = null) : Route

    @Serializable data object Favorites : Route
    @Serializable data object Sponsoring : Route
    @Serializable data object Orders : Route
    @Serializable data object Management : Route
    // 收入明细页（创作者后台 /api/my/stat，按日/周/月统计表格）
    @Serializable data object IncomeDetail : Route
    // 每月汇总页（创作者后台 /api/my/income，按年分组的月度账单，可切换抽成前/后金额）
    @Serializable data object MonthlySummary : Route
    // 独立收到发电订单列表，入口在「我的」页快捷入口的创作者分组
    @Serializable data object ReceivedSponsors : Route
    // 赞助者管理页（创作者后台 who-sponsored-me），入口在「我的」页创作者分组
    @Serializable data object SponsorManage : Route
    // 赞助者感谢页（创作者后台 get-thank-sponsors）；年月由「每月汇总」页按行带入
    @Serializable data class ThankSponsors(val year: Int, val month: Int) : Route
    // 优惠码页（创作者后台 creator/list-affiliate），入口在「我的」页快捷入口的创作者分组
    @Serializable data object Affiliates : Route
    // FAQ 设置页（创作者后台 /api/faq/list 及其增删改），入口在消息页三点菜单
    @Serializable data object FaqSetting : Route
    // 设置总览页：账号列表 + 三个分类入口。两个入口共用这一条路由 ——
    // 已登录「我的」页（MainScreen 栈），未登录登录页顶栏（MainActivity 的未登录栈）
    @Serializable data object Settings : Route
    // 设置 → 通用：启动页、历史记录、网络线路、文档入口与本地管理/调试入口
    @Serializable data object SettingsGeneral : Route
    // 设置 → 外观：配色、模糊、过渡动画与数据卡片
    @Serializable data object SettingsAppearance : Route
    // 设置 → 关于：站外链接、开源许可、免责声明、功能完成度、更新与版本页脚
    @Serializable data object SettingsAbout : Route
    // 免责声明页：首次启动强制确认（不在返回栈里），另有两个只读入口——
    // 登录页底部链接（未登录流程的返回栈）与设置 → 关于（主界面返回栈）
    @Serializable data object Disclaimer : Route
    // 功能完成度页：列出当前尚未完成的功能，入口在设置 → 关于
    @Serializable data object FeatureStatus : Route
    // 开源许可页：AboutLibraries 收集的第三方依赖与许可证，入口在设置 → 关于
    @Serializable data object OssLicenses : Route
    // 更新日志页：解析打包进 assets 的 CHANGELOG.md，按版本折叠展示，入口在设置 → 关于
    @Serializable data object Changelog : Route
    // 存储空间管理页：本地缓存/数据占用总览与分类清理，入口在设置 → 通用
    @Serializable data object Storage : Route
    // 日志页：运行日志与崩溃报告的记录开关、占用查看、导出与清空，入口在设置 → 通用
    @Serializable data object Log : Route
    // 接口调试页：主动探针逐个运行核心无参接口并查看原始返回，入口在设置 → 通用
    @Serializable data object ApiProbe : Route
    // 屏蔽 / 拒绝名单页（/api/my/blacked，TabRow 切换两个名单），入口在「我的」页设置上方
    @Serializable data object Blocklist : Route
    // 开发者页（官网 /dashboard/dev：user_id、Webhook URL、API Token），入口在「我的」页屏蔽与拒绝名单下方
    @Serializable data object Developer : Route
    // 文档聚合页：收录官网文档站、创作者指南与开发者文档入口，入口在「我的」页底部卡
    @Serializable data object Docs : Route
    // 售卖商品管理页（创作者后台 all-plans 的 sale_list），入口在「我的」页快捷入口的创作者分组
    @Serializable data object Shop : Route
    // 外部商品链接打开商品详情 Sheet，底层复用商品管理页的数据与弹层实现
    @Serializable data class ProductDetail(val productId: String) : Route
}
