package com.hrsthrt74.afdian.data.content

import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.network.sharedAfdianHttpClient
import okhttp3.OkHttpClient

/**
 * 内容数据外观（Facade）。
 *
 * 具体接口实现已按领域拆分到各个 Service，本类只做转发并保留历史方法签名，
 * 让现有 ViewModel 无需跟随重构迁移。
 */
class AfadianContentService(tokenStore: AuthTokenStore, client: OkHttpClient = sharedAfdianHttpClient) {

    // 消息接口单独维护请求与解析逻辑；外观层继续暴露旧 API，避免调用方一次性迁移。
    private val messageService = MessageService(tokenStore, client)

    // 优惠码接口独立维护，外观层保留旧方法签名，调用方无需同步迁移。
    private val affiliateService = AffiliateService(tokenStore, client)

    // 动态、收藏和互动接口独立维护，外观层继续兼容旧调用方。
    private val feedService = FeedService(tokenStore, client)

    // 创作者详情聚合复用动态领域的收藏状态查询。
    private val creatorService = CreatorService(tokenStore, client, feedService)

    // 收到发电账单独立维护筛选、分页和订单字段解析。
    private val receivedSponsorService = ReceivedSponsorService(tokenStore, client)

    // 感谢页接口独立维护感谢者分页和感谢语读写。
    private val thankSponsorsService = ThankSponsorsService(tokenStore, client)

    // 账号资料与未读检查独立维护，外观层只保留委托。
    private val accountService = AccountService(tokenStore, client)

    /** 账号域外观方法，保留原有签名，调用方无需迁移。 */
    suspend fun fetchAccount(): AfadianAccount = accountService.fetchAccount()

    suspend fun fetchProfile(): AfadianProfile = accountService.fetchProfile()

    suspend fun fetchSponsoring(): List<SponsoringItem> = accountService.fetchSponsoring()

    /** 轻量未读检查，供底部导航栏刷新全局未读数。 */
    suspend fun fetchMyCheck(): MyCheckData = accountService.fetchMyCheck()

    /** 收到发电账单外观方法，保留原有筛选与分页参数。 */
    suspend fun fetchReceivedSponsors(
        page: Int = 1,
        lastOrderId: String? = null,
        redeemOnly: Boolean = false,
        remarkOnly: Boolean = false,
        shipmentStatus: ReceivedShipmentFilter = ReceivedShipmentFilter.All,
        sort: ReceivedSponsorsSort = ReceivedSponsorsSort.Newest,
        beginTime: Long? = null,
        endTime: Long? = null,
    ): ReceivedSponsorsPage = receivedSponsorService.fetchReceivedSponsors(
        page = page,
        lastOrderId = lastOrderId,
        redeemOnly = redeemOnly,
        remarkOnly = remarkOnly,
        shipmentStatus = shipmentStatus,
        sort = sort,
        beginTime = beginTime,
        endTime = endTime,
    )

    /**
     * 赞助者感谢页（创作者后台 get-thank-sponsors）：按年月分页拉取「该感谢谁」的用户资料。
     * 响应里只有用户资料（无金额/计划）；分页只看 has_more。
     */
    suspend fun fetchThankSponsors(userId: String, year: Int, month: Int, page: Int = 1): ThankSponsorsPage =
        thankSponsorsService.fetchThankSponsors(userId, year, month, page)

    /** 优惠码外观方法，保留原有 API 供 AffiliateViewModel 使用。 */
    suspend fun fetchAffiliates(): List<Affiliate> = affiliateService.fetchAffiliates()

    suspend fun setAffiliateSwitch(affiliateCode: String, enable: Boolean) =
        affiliateService.setAffiliateSwitch(affiliateCode, enable)

    suspend fun generateAffiliateCode(): String = affiliateService.generateAffiliateCode()

    suspend fun fetchAffiliateSelectablePlans(): List<AffiliateSelectablePlan> =
        affiliateService.fetchAffiliateSelectablePlans()

    suspend fun editAffiliate(form: AffiliateForm) = affiliateService.editAffiliate(form)

    suspend fun fetchAffiliateDetail(affiliateCode: String): Affiliate =
        affiliateService.fetchAffiliateDetail(affiliateCode)

    suspend fun deleteAffiliate(id: Long) = affiliateService.deleteAffiliate(id)

    /**
     * 读取感谢语（感谢页顶部创作者自定义文案，与年月无关，接口带年月参数但内容全局一份）
     */
    suspend fun fetchThankWord(userId: String, year: Int, month: Int): String =
        thankSponsorsService.fetchThankWord(userId, year, month)

    /**
     * 保存感谢语：POST JSON {content, pic, year, month}（服务端会校验年月，缺失时报 year\month error），
     * 成功时 ec=200、em=「感谢语保存成功」
     */
    suspend fun editThankWord(content: String, year: Int, month: Int) =
        thankSponsorsService.editThankWord(content, year, month)

    /** 动态领域外观方法，保留原有签名，调用方无需迁移。 */
    suspend fun fetchMarkedCreators(): List<FavoriteItem> = feedService.fetchMarkedCreators()
    suspend fun fetchMarkedPosts(page: Int = 1): MarkedPostsPage = feedService.fetchMarkedPosts(page)
    suspend fun fetchMarkedCrowdfundings(): List<FavoriteItem> = feedService.fetchMarkedCrowdfundings()
    suspend fun fetchRecommendedPosts(publishSN: String = ""): RecommendedPostsPage =
        feedService.fetchRecommendedPosts(publishSN)
    suspend fun fetchPostDetail(postId: String, albumId: String = ""): RecommendedPost =
        feedService.fetchPostDetail(postId, albumId)

    suspend fun fetchCreatorDetail(user: AfadianUser): CreatorDetail = creatorService.fetchCreatorDetail(user)

    /** 读取单个发电方案的商品 SKU。 */
    suspend fun fetchPlanSkus(planId: String, isExt: String = ""): CreatorPlanSkus =
        creatorService.fetchPlanSkus(planId, isExt)

    suspend fun markCreator(userID: String) = feedService.markCreator(userID)
    suspend fun unmarkCreator(userID: String) = feedService.unmarkCreator(userID)
    suspend fun likePost(postID: String) = feedService.likePost(postID)
    suspend fun unlikePost(postID: String) = feedService.unlikePost(postID)
    suspend fun markPost(postID: String) = feedService.markPost(postID)
    suspend fun unmarkPost(postID: String) = feedService.unmarkPost(postID)

    /** 消息域外观方法，保留旧签名，让现有 ViewModel 无需同步迁移。 */
    suspend fun fetchMessageDialogs(page: Int = 1, unread: Int = 0): MessageDialogPage =
        messageService.fetchMessageDialogs(page, unread)

    suspend fun fetchMessages(userID: String, type: String = "old", messageID: String = ""): MessageConversation =
        messageService.fetchMessages(userID, type, messageID)

    suspend fun fetchSponsorInfo(userID: String): SponsorInfo = messageService.fetchSponsorInfo(userID)

    suspend fun fetchFAQs(userID: String): List<MessageFAQ> = messageService.fetchFAQs(userID)

    suspend fun sendMessage(userID: String, content: String): MessageItem =
        messageService.sendMessage(userID, content)

    suspend fun askFaq(userID: String, faqId: Int): MessageItem = messageService.askFaq(userID, faqId)

    /** 根据公开主页 slug 获取完整用户资料，供正文创作者链接跳转使用。 */
    fun fetchProfileBySlug(slug: String): AfadianUser = creatorService.fetchProfileBySlug(slug)
}
