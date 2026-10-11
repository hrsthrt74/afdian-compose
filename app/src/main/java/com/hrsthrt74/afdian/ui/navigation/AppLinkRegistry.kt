package com.hrsthrt74.afdian.ui.navigation

import java.net.URI

/**
 * App 内可处理的公开链接目标。
 *
 * 新增 App 页面时，先在这里增加目标类型与匹配规则，
 * 再由 [AppLinkNavigator] 负责把目标执行成具体的 Route。
 */
sealed interface AppLinkDestination {
    data class Post(val postId: String) : AppLinkDestination
    data class Creator(val slug: String) : AppLinkDestination
    data object Explore : AppLinkDestination
    data object Favorites : AppLinkDestination
    data object Shop : AppLinkDestination
    data class Product(val productId: String) : AppLinkDestination
    data object Management : AppLinkDestination
    data object ReceivedSponsors : AppLinkDestination
    data object MonthlySummary : AppLinkDestination
    data object Sponsoring : AppLinkDestination
    data object Orders : AppLinkDestination
    data object Affiliates : AppLinkDestination
    data object Developer : AppLinkDestination
    data object CustomerService : AppLinkDestination
}

private val AFDIAN_HOSTS = setOf(
    "afdian.com",
    "ifdian.net",
    "www.afdian.com",
    "www.ifdian.net",
)

/**
 * App 内链接注册表。
 *
 * 返回 null 表示当前页面尚未在 App 内实现，调用方应继续使用外部打开策略。
 * 规则按「精确路径结构」匹配，避免将后台、支付等未知页面误认为已支持页面。
 */
internal fun parseAppLink(url: String): AppLinkDestination? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    if (uri.scheme !in setOf("http", "https") || uri.host?.lowercase() !in AFDIAN_HOSTS) {
        return null
    }

    val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)
    if (segments.size == 2 && segments[0] == "p" && segments[1].isNotBlank()) {
        return AppLinkDestination.Post(segments[1])
    }
    // TODO: 动态合集页面实现后，album 链接应进入合集页；目前直接打开其中指定的动态。
    if (segments.size == 3 && segments[0] == "album" && segments[2].isNotBlank()) {
        return AppLinkDestination.Post(segments[2])
    }
    if (segments.size == 2 && segments[0] == "a" && segments[1].isNotBlank()) {
        return AppLinkDestination.Creator(segments[1])
    }
    if (segments.size == 1 && segments[0].startsWith("@") && segments[0].length > 1) {
        return AppLinkDestination.Creator(segments[0].removePrefix("@"))
    }
    when (segments) {
        listOf("explore") -> return AppLinkDestination.Explore
        listOf("dashboard", "mark") -> return AppLinkDestination.Favorites
        listOf("dashboard", "stats") -> return AppLinkDestination.Management
        listOf("dashboard", "sponsored-bill") -> return AppLinkDestination.ReceivedSponsors
        listOf("dashboard", "income") -> return AppLinkDestination.MonthlySummary
        listOf("dashboard", "sponsoring") -> return AppLinkDestination.Sponsoring
        listOf("dashboard", "order") -> return AppLinkDestination.Orders
        listOf("dashboard", "dev") -> return AppLinkDestination.Developer
        listOf("setting", "aff") -> return AppLinkDestination.Affiliates
        listOf("setting", "shop") ->
            return if (uri.host?.lowercase() in setOf("ifdian.net", "www.ifdian.net")) {
                AppLinkDestination.Shop
            } else {
                null
            }
    }
    if (segments.size == 2 && segments[0] == "message" &&
        segments[1] == "27f7cea2370d11e8ae8852540025c377"
    ) {
        return AppLinkDestination.CustomerService
    }
    if (segments.size == 2 && segments[0] in setOf("item", "bundle") && segments[1].isNotBlank()) {
        return AppLinkDestination.Product(segments[1])
    }
    return null
}
