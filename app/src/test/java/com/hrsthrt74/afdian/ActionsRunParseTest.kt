package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.update.GithubActionsRun
import com.hrsthrt74.afdian.data.update.GithubActionsRunList
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GitHub Actions Runs API 的解析测试。
 *
 * 字段名全部来自真实响应（`run_number` / `head_sha` / `conclusion` …），
 * 一旦哪天写错 `@SerialName`，这里会直接红掉，而不是等用户看到「暂无构建」。
 */
class ActionsRunParseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val sample = """
        {
          "total_count": 12,
          "workflow_runs": [
            {
              "id": 1234567890,
              "name": "Build",
              "head_branch": "master",
              "head_sha": "6aeb7562cfa280abcdef",
              "run_number": 128,
              "run_attempt": 1,
              "display_title": "refactor(probe): 登录态状态直连",
              "status": "completed",
              "conclusion": "success",
              "event": "push",
              "created_at": "2026-10-24T07:12:33Z",
              "html_url": "https://github.com/hrsthrt74/afdian-compose/actions/runs/1234567890",
              "unknown_future_field": "ignored"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun runListIsDecoded() {
        val list = json.decodeFromString<GithubActionsRunList>(sample)
        assertEquals(1, list.workflowRuns.size)

        val run = list.workflowRuns.first()
        assertEquals(128, run.runNumber)
        assertEquals("6aeb7562cfa280abcdef", run.headSha)
        assertEquals("master", run.headBranch)
        assertEquals("refactor(probe): 登录态状态直连", run.displayTitle)
        assertEquals("completed", run.status)
        assertEquals("success", run.conclusion)
        assertEquals("2026-10-24T07:12:33Z", run.createdAt)
        assertTrue(run.htmlUrl.endsWith("/actions/runs/1234567890"))
        assertTrue(run.isSuccess)
    }

    @Test
    fun onlyCompletedSuccessCountsAsSuccess() {
        assertFalse(GithubActionsRun(status = "completed", conclusion = "failure").isSuccess)
        assertFalse(GithubActionsRun(status = "in_progress", conclusion = "").isSuccess)
        assertFalse(GithubActionsRun(status = "completed", conclusion = "cancelled").isSuccess)
        assertTrue(GithubActionsRun(status = "completed", conclusion = "success").isSuccess)
    }

    @Test
    fun missingFieldsFallBackToEmpty() {
        // 服务端字段缺失时不应该抛异常，最坏情况是「没有可用构建」
        val run = json.decodeFromString<GithubActionsRunList>("{}")
        assertEquals(0, run.totalCount)
        assertTrue(run.workflowRuns.isEmpty())
    }
}
