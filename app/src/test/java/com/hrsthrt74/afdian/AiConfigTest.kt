package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.ai.AiProviderPreset
import com.hrsthrt74.afdian.data.ai.isAiConfigReady
import com.hrsthrt74.afdian.data.ai.normalizeAiBaseUrl
import com.hrsthrt74.afdian.data.ai.normalizeAiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** AI 配置归一化的回归测试：设置页「保存」入口的校验口径都锁在这里。 */
class AiConfigTest {

    // ── Base URL 归一化 ──

    @Test
    fun `baseUrl 保留合法值并去掉末尾斜杠`() {
        assertEquals("https://api.deepseek.com/v1", normalizeAiBaseUrl("https://api.deepseek.com/v1/"))
        assertEquals("https://api.deepseek.com/v1", normalizeAiBaseUrl("  https://api.deepseek.com/v1  "))
        // 路径中间的斜杠不能动（智谱端点带多级路径）
        assertEquals(
            "https://open.bigmodel.cn/api/paas/v4",
            normalizeAiBaseUrl("https://open.bigmodel.cn/api/paas/v4/"),
        )
    }

    @Test
    fun `baseUrl 剥掉用户多粘的完整端点路径`() {
        assertEquals(
            "https://api.deepseek.com/v1",
            normalizeAiBaseUrl("https://api.deepseek.com/v1/chat/completions"),
        )
        // 大小写不敏感地剥
        assertEquals(
            "https://api.x.com/v1",
            normalizeAiBaseUrl("https://api.x.com/v1/Chat/Completions"),
        )
        // 粘了两层也能剥干净
        assertEquals(
            "https://api.x.com/v1",
            normalizeAiBaseUrl("https://api.x.com/v1/chat/completions/chat/completions"),
        )
    }

    @Test
    fun `baseUrl 非法输入返回 null`() {
        assertNull(normalizeAiBaseUrl(""))
        assertNull(normalizeAiBaseUrl("   "))
        // 缺协议头：不猜 http/https，明确拒绝
        assertNull(normalizeAiBaseUrl("api.deepseek.com/v1"))
        assertNull(normalizeAiBaseUrl("https://"))
        // 非 http(s) 协议拒绝（避免把 Key 发进明文/未知通道）
        assertNull(normalizeAiBaseUrl("ftp://api.x.com/v1"))
        assertNull(normalizeAiBaseUrl("file:///etc/passwd"))
    }

    // ── 其他字段归一化 ──

    @Test
    fun `模型名 trim 空白`() {
        assertEquals("deepseek-chat", normalizeAiModel("  deepseek-chat\n"))
        assertEquals("", normalizeAiModel("   "))
    }

    // ── 配置完整性判断 ──

    @Test
    fun `缺 BaseUrl 或模型名视为未就绪`() {
        assertFalse(isAiConfigReady(AiProviderPreset.DeepSeek, "", "deepseek-chat", hasKey = true))
        assertFalse(isAiConfigReady(AiProviderPreset.DeepSeek, "https://api.deepseek.com/v1", " ", hasKey = true))
    }

    @Test
    fun `云服务缺 Key 未就绪而自定义端点允许无 Key`() {
        assertFalse(
            isAiConfigReady(AiProviderPreset.DeepSeek, "https://api.deepseek.com/v1", "deepseek-chat", hasKey = false),
        )
        assertTrue(
            isAiConfigReady(AiProviderPreset.Custom, "http://192.168.1.5:8080/v1", "qwen", hasKey = false),
        )
    }

    @Test
    fun `完整配置就绪`() {
        assertTrue(
            isAiConfigReady(AiProviderPreset.DeepSeek, "https://api.deepseek.com/v1", "deepseek-chat", hasKey = true),
        )
    }
}
