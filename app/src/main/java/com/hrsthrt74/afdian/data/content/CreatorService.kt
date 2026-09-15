package com.hrsthrt74.afdian.data.content

import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.network.AfdianHttpClient
import com.hrsthrt74.afdian.data.network.requireAfdianSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 创作者详情领域服务。
 *
 * 负责创作者资料及其目标、方案、相册、动态和商品的聚合加载。
 * 各子模块失败时沿用旧行为回退为空列表，但不会吞掉协程取消。
 */
internal class CreatorService(
    private val tokenStore: AuthTokenStore,
    client: OkHttpClient,
    private val feedService: FeedService,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }
    private val http = AfdianHttpClient(client)

    private companion object {
        const val AFDIAN_HOST = "https://afdian.com"
        const val IFDIAN_HOST = "https://ifdian.net"
        const val TAG = "CreatorService"
    }

    suspend fun fetchCreatorDetail(user: AfadianUser): CreatorDetail {
        val userID = user.user_id
        return coroutineScope {
            val profileDeferred = async {
                if (user.url_slug.isNullOrBlank()) user
                else runCatchingNonCancellation { fetchProfileBySlug(user.url_slug) }.getOrDefault(user)
            }
            val goalsDeferred = async { runCatchingNonCancellation { fetchGoals(userID) }.getOrDefault(emptyList()) }
            val sponsorsDeferred = async { runCatchingNonCancellation { fetchTopSponsors(userID) }.getOrDefault(emptyList()) }
            val plansDeferred = async { runCatchingNonCancellation { fetchPlans(userID) }.getOrDefault(emptyList()) }
            // 相册只请求一次，帖子加载复用相同结果。
            val albumsDeferred = async { runCatchingNonCancellation { fetchAlbums(userID) }.getOrDefault(emptyList()) }
            val postsDeferred = async {
                val albums = albumsDeferred.await()
                runCatchingNonCancellation { fetchUserPosts(userID, albums) }.getOrDefault(emptyList())
            }
            val productsDeferred = async { runCatchingNonCancellation { fetchCreatorProducts(userID) }.getOrDefault(emptyList()) }
            val markedDeferred = async {
                runCatchingNonCancellation { feedService.fetchMarkedCreators() }.getOrDefault(emptyList())
            }
            CreatorDetail(
                profile = profileDeferred.await(),
                goals = goalsDeferred.await(),
                topSponsors = sponsorsDeferred.await(),
                plans = plansDeferred.await(),
                albums = albumsDeferred.await(),
                posts = postsDeferred.await(),
                products = productsDeferred.await(),
                isMarked = markedDeferred.await().any { it.id == userID },
            )
        }
    }

    /** 根据公开主页 slug 获取完整用户资料。 */
    fun fetchProfileBySlug(slug: String): AfadianUser =
        get("/api/user/get-profile-by-slug?url_slug=$slug", UserProfileResponse.serializer())
            .data?.user
            ?: error("用户信息加载失败")

    private fun fetchGoals(userID: String): List<CreatorGoal> {
        val payload = get("/api/creator/get-goals?user_id=$userID", CreatorGoalsResponse.serializer()).data
            ?: return emptyList()
        return payload.list ?: payload.goals ?: emptyList()
    }

    private fun fetchTopSponsors(userID: String): List<TopSponsor> =
        get("/api/creator/get-top-sponsors?user_id=$userID", TopSponsorsResponse.serializer()).data?.list.orEmpty()

    private fun fetchPlans(userID: String): List<CreatorPlan> {
        val payload = get(
            "/api/creator/get-plans?user_id=$userID&album_id=&unlock_plan_ids=&diy=&affiliate_code=",
            CreatorPlansResponse.serializer(),
            host = IFDIAN_HOST,
        ).data ?: return emptyList()
        return payload.list ?: payload.plans ?: emptyList()
    }

    private fun fetchAlbums(userID: String): List<CreatorAlbum> {
        val payload = get(
            "/api/user/get-album-list?user_id=$userID&page=1&per_page=10",
            CreatorAlbumsResponse.serializer(),
        ).data ?: return emptyList()
        return payload.list ?: payload.albums ?: emptyList()
    }

    private suspend fun fetchUserPosts(userID: String, albums: List<CreatorAlbum>): List<RecommendedPost> {
        if (albums.isEmpty()) return emptyList()
        return coroutineScope {
            albums.mapNotNull { it.album_id }.map { albumID ->
                async {
                    runCatchingNonCancellation {
                        get(
                            "/api/user/get-album-post?album_id=$albumID",
                            AlbumPostsResponse.serializer(),
                        ).data?.list.orEmpty()
                    }.getOrDefault(emptyList())
                }
            }.flatMap { it.await() }
                .distinctBy { it.post_id }
                .sortedByDescending { it.publish_time ?: 0L }
        }
    }

    private fun fetchCreatorProducts(userID: String): List<CreatorProduct> =
        get(
            "/api/creator/get-products?user_id=$userID&page=1&tag_id=",
            CreatorProductsResponse.serializer(),
            host = IFDIAN_HOST,
        ).data?.list.orEmpty()

    private fun <T> get(
        path: String,
        serializer: kotlinx.serialization.KSerializer<T>,
        host: String = AFDIAN_HOST,
    ): T {
        val url = (host + path).toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", tokenStore.token)
            .build()
        val request = Request.Builder().url(url).get()
            .header("Cookie", "auth_token=${tokenStore.token}")
            .build()
        val start = System.currentTimeMillis()
        return http.execute(request).use { response ->
            if (!response.isSuccessful) {
                AppLog.e(TAG, "$path -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("请求失败 (${response.code})")
            }
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            root.requireAfdianSuccess(TAG, path, "请求失败", requireEc = true)
            json.decodeFromJsonElement(serializer, root).also {
                AppLog.d(TAG, "$path -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
            }
        }
    }

    private suspend inline fun <T> runCatchingNonCancellation(crossinline block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
}

@Serializable
private data class UserProfileResponse(val ec: Int? = null, val data: UserProfilePayload? = null)

@Serializable
private data class UserProfilePayload(val user: AfadianUser? = null)

@Serializable
private data class CreatorGoalsResponse(val ec: Int? = null, val data: CreatorGoalsPayload? = null)

@Serializable
private data class CreatorGoalsPayload(
    val list: List<CreatorGoal>? = null,
    val goals: List<CreatorGoal>? = null,
)

@Serializable
private data class TopSponsorsResponse(val ec: Int? = null, val data: TopSponsorsPayload? = null)

@Serializable
private data class TopSponsorsPayload(val list: List<TopSponsor>? = null)

@Serializable
private data class CreatorPlansResponse(val ec: Int? = null, val data: CreatorPlansPayload? = null)

@Serializable
private data class CreatorPlansPayload(
    val list: List<CreatorPlan>? = null,
    val plans: List<CreatorPlan>? = null,
)

@Serializable
private data class CreatorAlbumsResponse(val ec: Int? = null, val data: CreatorAlbumsPayload? = null)

@Serializable
private data class CreatorAlbumsPayload(
    val list: List<CreatorAlbum>? = null,
    val albums: List<CreatorAlbum>? = null,
)

@Serializable
private data class AlbumPostsResponse(val ec: Int? = null, val data: AlbumPostsPayload? = null)

@Serializable
private data class AlbumPostsPayload(val list: List<RecommendedPost> = emptyList())

@Serializable
private data class CreatorProductsResponse(val ec: Int? = null, val data: CreatorProductsPayload? = null)

@Serializable
private data class CreatorProductsPayload(val list: List<CreatorProduct>? = null)
