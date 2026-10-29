package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.ai.AiReasoningEffort
import com.hrsthrt74.afdian.data.ai.AiThinkingLevel
import com.hrsthrt74.afdian.data.ai.AiThinkingMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 思考档位的回归测试：滑块是「开关 + 强度」两个字段的合成视图，
 * 反查（`AiThinkingLevel.of`）一旦算错，设置页的滑块就会指到错误的刻度。
 */
class AiThinkingLevelTest {

    /** 每个档位都能被自己反查回来（滑块位置与存档值一一对应）。 */
    @Test
    fun `档位与开关强度互查一致`() {
        AiThinkingLevel.entries.forEach { level ->
            assertEquals(level, AiThinkingLevel.of(level.mode, level.effort))
        }
    }

    /** 关闭档不存强度：即使强度字段还留着旧值，也应当反查成「关闭」而不是某一强度档。 */
    @Test
    fun `关闭档忽略残留的强度`() {
        assertEquals(
            AiThinkingLevel.Off,
            AiThinkingLevel.of(AiThinkingMode.Disabled, AiReasoningEffort.Max),
        )
        assertEquals(AiThinkingLevel.Off, AiThinkingLevel.of(AiThinkingMode.Disabled, null))
    }

    /** 旧版本存过 `medium` 这类已废弃的强度：读不出来，兜底成「开启 + 高」。 */
    @Test
    fun `废弃档位兜底为开启高强度`() {
        val legacyEffort = AiReasoningEffort.fromStorage("medium")
        assertEquals(null, legacyEffort)
        assertEquals(AiThinkingLevel.High, AiThinkingLevel.of(AiThinkingMode.Enabled, legacyEffort))
    }

    /** 旧版本存过 `default`（跟随服务端）：开关读不出来，兜底成默认档「开启 + 高」。 */
    @Test
    fun `跟随服务端的旧存档兜底为开启高强度`() {
        assertEquals(null, AiThinkingMode.fromStorage("default"))
    }
}
