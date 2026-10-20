package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.auth.MAX_SAVED_ACCOUNTS
import com.hrsthrt74.afdian.data.auth.SavedAccount
import com.hrsthrt74.afdian.data.auth.SavedAccountsFile
import com.hrsthrt74.afdian.data.auth.authTokenFingerprint
import com.hrsthrt74.afdian.data.auth.orderSwitchCandidates
import com.hrsthrt74.afdian.data.auth.resolveCurrentUserId
import com.hrsthrt74.afdian.data.auth.savedAccountsJson
import com.hrsthrt74.afdian.data.auth.upsertAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 账号列表纯逻辑测试：不依赖 Android Context / DataStore。
 *
 * 覆盖增删排序、当前账号判定、删除兜底与序列化契约——这些是切换流程最容易出错、
 * 又最容易测的部分。
 */
class SavedAccountTest {

    private fun account(id: String, at: Long = 0L, token: String = "token-$id") = SavedAccount(
        userId = id,
        name = "name-$id",
        avatar = "",
        encryptedToken = "enc-$id",
        tokenFingerprint = authTokenFingerprint(token),
        lastUsedAt = at,
    )

    @Test
    fun upsertAddsNewAccountAndKeepsRecentFirst() {
        val existing = listOf(account("a", at = 100))
        val next = upsertAccount(existing, account("b", at = 200))

        assertEquals(listOf("b", "a"), next?.map { it.userId })
    }

    @Test
    fun upsertReplacesSameUserWithoutDuplicating() {
        val existing = listOf(account("a", at = 100), account("b", at = 200))
        val next = upsertAccount(existing, account("a", at = 300, token = "new-token"))

        assertEquals(2, next?.size)
        assertEquals("a", next?.first()?.userId)
        assertEquals("enc-a", next?.first()?.encryptedToken)
        assertEquals(authTokenFingerprint("new-token"), next?.first()?.tokenFingerprint)
    }

    @Test
    fun upsertRejectsNewAccountBeyondLimit() {
        val full = (1..MAX_SAVED_ACCOUNTS).map { account("u$it", at = it.toLong()) }
        assertNull(upsertAccount(full, account("overflow", at = 999)))
    }

    @Test
    fun upsertAllowsReplacingExistingAccountWhenFull() {
        val full = (1..MAX_SAVED_ACCOUNTS).map { account("u$it", at = it.toLong()) }
        val next = upsertAccount(full, account("u1", at = 999))
        assertEquals(MAX_SAVED_ACCOUNTS, next?.size)
        assertEquals("u1", next?.first()?.userId)
    }

    @Test
    fun upsertRejectsBlankUserId() {
        assertNull(upsertAccount(emptyList(), account("", at = 1)))
    }

    @Test
    fun resolveCurrentUserMatchesFingerprintOnly() {
        val accounts = listOf(account("a", token = "token-a"), account("b", token = "token-b"))

        assertEquals("b", resolveCurrentUserId(accounts, authTokenFingerprint("token-b")))
        assertNull(resolveCurrentUserId(accounts, authTokenFingerprint("unknown")))
        assertNull(resolveCurrentUserId(accounts, null))
    }

    @Test
    fun orderSwitchCandidatesExcludesSelfAndSortsByRecent() {
        val accounts = listOf(account("a", 300), account("c", 100), account("b", 200))
        val ordered = orderSwitchCandidates(accounts, excludedUserId = "a")

        assertEquals(listOf("b", "c"), ordered.map { it.userId })
    }

    @Test
    fun jsonRoundTripIsLossless() {
        val file = SavedAccountsFile(accounts = listOf(account("a", 123), account("b", 456)))
        val encoded = savedAccountsJson.encodeToString(SavedAccountsFile.serializer(), file)
        val decoded = savedAccountsJson.decodeFromString(SavedAccountsFile.serializer(), encoded)

        assertEquals(file, decoded)
    }

    @Test
    fun jsonIgnoresUnknownFieldsAndKeepsDefaults() {
        val raw = """{"version":1,"accounts":[{"userId":"a","tokenFingerprint":"x","futureField":true}]}"""
        val decoded = savedAccountsJson.decodeFromString(SavedAccountsFile.serializer(), raw)

        assertEquals(1, decoded.accounts.size)
        assertEquals("a", decoded.accounts.first().userId)
        assertTrue(decoded.accounts.first().name.isBlank())
    }

    @Test
    fun jsonHandlesEmptyPayload() {
        val decoded = savedAccountsJson.decodeFromString(SavedAccountsFile.serializer(), "{}")
        assertTrue(decoded.accounts.isEmpty())
        assertEquals(1, decoded.version)
    }
}
