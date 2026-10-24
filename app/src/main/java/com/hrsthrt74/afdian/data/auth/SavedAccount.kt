package com.hrsthrt74.afdian.data.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 本地保存的一个账号。
 *
 * - [userId] 是唯一键，也是所有「按账号隔离」数据的分区键
 * - [encryptedToken] 是加密后的 token，绝不落明文
 * - [tokenFingerprint] 用于判定「这个记录是不是当前活动账号」：
 *   拿活动 token 现算指纹对比即可，不需要额外存 isCurrent 标记（避免标记与真实 token 不一致）
 */
@Serializable
data class SavedAccount(
    val userId: String = "",
    // 昵称与头像只用于面板展示，属于非敏感信息，明文存储
    val name: String = "",
    val avatar: String = "",
    val encryptedToken: String = "",
    val tokenFingerprint: String = "",
    // 最近使用时间：列表排序 + 删除当前账号时挑兜底账号
    val lastUsedAt: Long = 0L,
)

/** 暴露给 UI 的只读模型：不含任何 token 字段，避免密文进入界面层。 */
data class SavedAccountSummary(
    val userId: String,
    val name: String,
    val avatar: String?,
    val lastUsedAt: Long,
)

fun SavedAccount.toSummary(): SavedAccountSummary = SavedAccountSummary(
    userId = userId,
    name = name,
    avatar = avatar.takeIf { it.isNotBlank() },
    lastUsedAt = lastUsedAt,
)

/**
 * `accounts` DataStore 的文件结构。
 *
 * 用 version 包一层是为了以后加字段/改结构时能识别旧格式；
 * 读取端配置了 ignoreUnknownKeys，降级时也不会因为多出的字段直接解析失败。
 */
@Serializable
data class SavedAccountsFile(
    val version: Int = 1,
    val accounts: List<SavedAccount> = emptyList(),
)

internal val savedAccountsJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
}

/** 最多保存 5 个账号：再多面板就变成列表页了，超出时提示先删一个。 */
const val MAX_SAVED_ACCOUNTS = 5

/**
 * 新增或更新账号，并按最近使用排序。
 *
 * @return 新列表；[account] 的 userId 非法、或新增账号会导致超限时返回 null
 */
internal fun upsertAccount(
    accounts: List<SavedAccount>,
    account: SavedAccount,
): List<SavedAccount>? {
    if (account.userId.isBlank()) return null
    val existing = accounts.any { it.userId == account.userId }
    if (!existing && accounts.size >= MAX_SAVED_ACCOUNTS) return null
    // 覆盖同 userId 的旧记录（token 可能已刷新），再按最近使用倒序：
    // 调用方保证 account.lastUsedAt 是当前时间，因此它会排在首位。
    return (accounts.filterNot { it.userId == account.userId } + account)
        .sortedByDescending { it.lastUsedAt }
}

/** 用活动 token 的指纹找当前账号；找不到返回 null（老用户还没补建记录时会走这条路）。 */
internal fun resolveCurrentUserId(accounts: List<SavedAccount>, fingerprint: String?): String? {
    if (fingerprint.isNullOrBlank()) return null
    return accounts.firstOrNull { it.tokenFingerprint == fingerprint }?.userId
}

/**
 * 退出登录时该删除哪条记录。
 *
 * 优先按活动 token 的指纹匹配：这是唯一能可靠指出「这条记录就是刚才那个会话」的依据，
 * 且不依赖任何内存态。指纹匹配不到（老记录没存指纹、或记录已被删）时退回 userId。
 *
 * 之所以不能只用 userId：`MainViewModel.onLoggedOut()` 会在 `AuthSession.logout()` 之前
 * 先清空 `CurrentAccount`，那样一条记录都删不掉，手动退出的账号会留在列表里，
 * 登录页的「已保存账号」卡片仍能一键切回去。
 *
 * @param fingerprint 活动 token 的指纹；未登录时传 null
 * @param fallbackUserId 内存里的当前账号 id，可能已被上层提前置空
 * @return 需要删除的记录；null 表示列表里本来就没有这条
 */
internal fun pickLoggedOutAccount(
    accounts: List<SavedAccount>,
    fingerprint: String?,
    fallbackUserId: String?,
): SavedAccount? {
    val byFingerprint = if (fingerprint.isNullOrBlank()) {
        null
    } else {
        accounts.firstOrNull { it.tokenFingerprint == fingerprint }
    }
    if (byFingerprint != null) return byFingerprint
    if (fallbackUserId.isNullOrBlank()) return null
    return accounts.firstOrNull { it.userId == fallbackUserId }
}

/**
 * 删除当前账号时的预检顺序：最近使用的其他账号排前面，逐个尝试直到可用。
 *
 * 注意这里是「有序候选列表」而不是「单个兜底账号」：排在前面的账号凭证可能已失效，
 * 需要按顺序逐个预检，全部失败才登出。
 */
internal fun orderSwitchCandidates(accounts: List<SavedAccount>, excludedUserId: String): List<SavedAccount> =
    accounts.filter { it.userId != excludedUserId }.sortedByDescending { it.lastUsedAt }

/** 切换失败的两类原因，决定提示文案与是否标记「登录已失效」。 */
internal enum class SwitchFailure {
    /** 网络层失败：不改任何状态，也不标记账号失效，稍后可重试 */
    Network,

    /** 凭证失效 / 账号信息不匹配：留在原账号，并给该账号打失效标记 */
    Expired,
}

/**
 * 把切换时的异常归类。
 *
 * 只有 `IOException`（含 SocketTimeout、UnknownHost）算网络问题；
 * 其余异常来自接口业务错误或 HTTP 非 2xx（`AccountService` 里用 `error(...)` 抛出），
 * 一律按「登录状态已失效」处理。
 */
internal fun classifySwitchFailure(error: Throwable): SwitchFailure =
    if (error is java.io.IOException) SwitchFailure.Network else SwitchFailure.Expired
