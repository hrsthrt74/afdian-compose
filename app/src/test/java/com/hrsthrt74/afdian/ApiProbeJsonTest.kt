package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.probe.ApiProbeCatalog
import com.hrsthrt74.afdian.data.probe.ApiProbeSpec
import com.hrsthrt74.afdian.data.probe.buildHighlights
import com.hrsthrt74.afdian.data.probe.describeJsonValue
import com.hrsthrt74.afdian.data.probe.flattenJsonLeaves
import com.hrsthrt74.afdian.data.probe.maskPii
import com.hrsthrt74.afdian.data.probe.maskSecrets
import com.hrsthrt74.afdian.data.probe.prettyPrintAndCap
import com.hrsthrt74.afdian.data.probe.resolveJsonPath
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 接口调试页纯函数回归测试。
 *
 * 这些函数决定了「调试页上看到的字段值是否可信」，一旦漂移会让人把正确的接口数据误判为异常，
 * 因此单独立测。执行器本身需要真实 token，不在单测范围内。
 */
class ApiProbeJsonTest {

    private val json = Json

    /** 爱发电接口常见的信封结构：{ec, em, data:{...}} */
    private val dashboardEnvelope = json.parseToJsonElement(
        """
        {
          "ec": 200,
          "em": "",
          "data": {
            "summary": {
              "month_amount": "1234.56",
              "all_sum_amount": "88888.00",
              "all_sponsor_count": 42,
              "uv": 100,
              "pv": 200
            },
            "balance": "100.00",
            "balance_after_tax": "94.00"
          }
        }
        """.trimIndent()
    )

    // ---------- flattenJsonLeaves ----------

    @Test
    fun flattenExpandsNestedObjectsWithDottedPath() {
        val result = flattenJsonLeaves(
            json.parseToJsonElement("""{"a":{"b":{"c":1}},"d":"x"}"""),
        )

        assertEquals("1", result.toMap()["a.b.c"])
        assertEquals("x", result.toMap()["d"])
    }

    @Test
    fun flattenTruncatesLongArraysButKeepsTotalCount() {
        val result = flattenJsonLeaves(
            json.parseToJsonElement("""{"list":[{"id":1},{"id":2},{"id":3},{"id":4},{"id":5}]}"""),
            maxDepth = 5,
        )
        val map = result.toMap()

        // 只展开前 3 项
        assertEquals("1", map["list[0].id"])
        assertEquals("3", map["list[2].id"])
        assertNull(map["list[3].id"])
        // 但必须补一条总数，否则会让人误以为数组只有 3 项
        assertEquals("共 5 项", map["list[…]"])
    }

    @Test
    fun flattenCollapsesBeyondMaxDepth() {
        val result = flattenJsonLeaves(
            json.parseToJsonElement("""{"a":{"b":{"c":{"d":1}}}}"""),
            maxDepth = 2,
        )

        // 到深度上限就不再深入，只报告字段个数
        assertEquals("{1 个字段}", result.toMap()["a.b"])
        assertNull(result.toMap()["a.b.c.d"])
    }

    @Test
    fun flattenHandlesRootArrayAndRootPrimitive() {
        // 根就是数组：第一层路径直接是下标
        val arrayRoot = flattenJsonLeaves(json.parseToJsonElement("""[{"id":7}]"""), maxDepth = 3)
        assertEquals("7", arrayRoot.toMap()["[0].id"])

        // 根就是基本类型：路径为空串
        val primitiveRoot = flattenJsonLeaves(JsonPrimitive("hello"))
        assertEquals(listOf("" to "hello"), primitiveRoot)
    }

    @Test
    fun flattenMarksJsonNullExplicitly() {
        // JSON null 是 JsonPrimitive 的子类，必须先被识别成 "null"（而不是空串）
        val result = flattenJsonLeaves(json.parseToJsonElement("""{"a":null}"""))

        assertEquals("null", result.toMap()["a"])
    }

    // ---------- resolveJsonPath ----------

    @Test
    fun resolvePathWalksObjectsAndArrayIndexes() {
        val root = json.parseToJsonElement("""{"a":{"b":[{"c":"hit"}]}}""")

        assertEquals("\"hit\"", resolveJsonPath(root, "a.b[0].c").toString())
    }

    @Test
    fun resolvePathReturnsNullOnAnyMissingSegment() {
        val root = json.parseToJsonElement("""{"a":{"b":{"c":1}}}""")

        assertNull(resolveJsonPath(root, "a.x.c"))
        // 中间是对象却按下标取：类型不匹配也要返回 null，而不是抛异常
        assertNull(resolveJsonPath(root, "a[0]"))
        // 数组越界
        assertNull(resolveJsonPath(json.parseToJsonElement("""{"a":[]}"""), "a[0]"))
    }

    // ---------- describeJsonValue ----------

    @Test
    fun describeValueSummarizesContainers() {
        val root = json.parseToJsonElement("""{"obj":{"a":1,"b":2},"arr":[1,2,3],"n":null,"s":"v"}""") as JsonObject

        assertEquals("{2 个字段}", describeJsonValue(root["obj"]))
        assertEquals("[3 项]", describeJsonValue(root["arr"]))
        assertEquals("null", describeJsonValue(root["n"]))
        assertEquals("v", describeJsonValue(root["s"]))
        // 路径没解析到 -> null（UI 渲染成「（缺失）」，与字面 null 区分）
        assertNull(describeJsonValue(null))
        assertEquals("null", describeJsonValue(JsonNull))
    }

    // ---------- buildHighlights ----------

    @Test
    fun highlightsResolveThroughDataEnvelope() {
        // 这是本次需求的核心：spec 里写 data 内部的相对路径，必须能穿透 {ec, data} 信封命中
        val spec = ApiProbeSpec(
            id = "my_dashboard",
            title = "创作者看板",
            path = "/api/my/dashboard",
            summary = "",
            highlights = listOf("summary.month_amount", "balance"),
        )

        val result = buildHighlights(dashboardEnvelope, spec)
        val map = result.associate { it.path to it.value }

        assertEquals("1234.56", map["summary.month_amount"])
        assertEquals("100.00", map["balance"])
    }

    @Test
    fun highlightsFallBackToLeafExtractionWhenUnspecified() {
        val spec = ApiProbeSpec(id = "x", title = "x", path = "/api/x", summary = "")

        val result = buildHighlights(dashboardEnvelope, spec, fallbackCount = 3)

        assertEquals(3, result.size)
        // 兜底也走 data 基点，因此不会把 ec / em 这类信封字段混进摘要
        assertTrue(result.none { it.path == "ec" || it.path == "em" })
    }

    @Test
    fun highlightsMarkMissingFieldsAsNull() {
        val spec = ApiProbeSpec(
            id = "x", title = "x", path = "/api/x", summary = "",
            highlights = listOf("summary.not_exist"),
        )

        val result = buildHighlights(dashboardEnvelope, spec)

        assertEquals(1, result.size)
        // value == null 表示字段不存在，UI 显示「（缺失）」
        assertNull(result.single().value)
    }

    // ---------- maskSecrets ----------

    @Test
    fun maskSecretsHidesTokenValues() {
        assertEquals(
            "https://afdian.com/api/my/dashboard?auth_token=***",
            "https://afdian.com/api/my/dashboard?auth_token=abc123".maskSecrets(),
        )
        // 后面还有其他参数时不能把后续参数一起吃掉
        assertEquals(
            "https://afdian.com/api/my/stat?auth_token=***&page=1&type=day",
            "https://afdian.com/api/my/stat?auth_token=abc123&page=1&type=day".maskSecrets(),
        )
        // 大小写与别的 token 参数名也要覆盖
        assertEquals(
            "?Access_Token=***",
            "?Access_Token=xyz".maskSecrets(),
        )
    }

    @Test
    fun maskSecretsLeavesCleanStringsUntouched() {
        val clean = "https://afdian.com/api/my/stat?page=1&type=day"

        assertEquals(clean, clean.maskSecrets())
    }

    // ---------- maskPii ----------

    @Test
    fun maskPiiHidesFullAndPartiallyMaskedPhones() {
        // 完整 11 位手机号：保留首字符 1，其余 10 位全部星号（与号码等长）
        assertEquals("1**********", "13812345678".maskPii())
        // 服务端已半掩的形态（如 account 接口返回 187***2066）也要进一步掩成统一格式
        assertEquals("1**********", "187***2066".maskPii())
        // 星号数量不固定的半掩形态同样覆盖
        assertEquals("1**********", "187****2066".maskPii())
        // 夹在文本里也要能掩
        assertEquals(
            "绑定手机 1********** 已更新",
            "绑定手机 18912345678 已更新".maskPii(),
        )
    }

    @Test
    fun maskPiiMasksEmailLocalPartButKeepsDomain() {
        // 服务端半掩形态（account 接口）：本地部分进一步掩成 ***
        assertEquals("***@qq.com", "193***@qq.com".maskPii())
        // 未脱敏的完整邮箱同样只留域名
        assertEquals("***@qq.com", "user123@qq.com".maskPii())
    }

    @Test
    fun maskPiiKeepsLongDigitRunsIntact() {
        // 13 位时间戳：内部的 11 位片段前后都是数字，不能被误伤
        assertEquals("1759200000000", "1759200000000".maskPii())
        // 12 位数字串同样不是手机号
        assertEquals("138123456789", "138123456789".maskPii())
        // 金额与短数字不受影响
        assertEquals("1234.56", "1234.56".maskPii())
        assertEquals("42", "42".maskPii())
    }

    @Test
    fun describeValueMasksPhoneAndEmailLeaf() {
        val root = json.parseToJsonElement(
            """{"mobile":"13812345678","phone":"187***2066","email":"193***@qq.com","timestamp":1759200000000,"name":"张三"}"""
        ) as JsonObject

        // 完整手机号、半掩手机号：统一掩成 1**********
        assertEquals("1**********", describeJsonValue(root["mobile"]))
        assertEquals("1**********", describeJsonValue(root["phone"]))
        // 邮箱：本地部分掩成 ***，域名保留
        assertEquals("***@qq.com", describeJsonValue(root["email"]))
        // 时间戳、普通文本原样保留
        assertEquals("1759200000000", describeJsonValue(root["timestamp"]))
        assertEquals("张三", describeJsonValue(root["name"]))
    }

    @Test
    fun prettyPrintMasksPhoneNumbersInRawJson() {
        val text = """{"ec":200,"data":{"mobile":"13812345678"}}"""
        val (output, truncated) = prettyPrintAndCap(text, json.parseToJsonElement(text))

        assertFalse(truncated)
        assertTrue(output.contains("1**********"))
        assertFalse("原始 JSON 里不允许残留完整手机号", output.contains("13812345678"))
    }

    // ---------- prettyPrintAndCap ----------

    @Test
    fun prettyPrintKeepsSmallBodiesIntact() {
        val text = """{"ec":200,"data":{"a":1}}"""
        val parsed = json.parseToJsonElement(text)

        val (output, truncated) = prettyPrintAndCap(text, parsed)

        assertFalse(truncated)
        assertTrue(output.contains("\n"))   // 已美化：出现换行缩进
        assertTrue(output.contains("\"ec\""))
    }

    @Test
    fun prettyPrintCapsOversizedBodies() {
        // 构造超过 400 行上限的 JSON 数组
        val bigArray = (1..600).joinToString(prefix = "[", postfix = "]") { """{"i":$it}""" }

        val (output, truncated) = prettyPrintAndCap(bigArray, json.parseToJsonElement(bigArray))

        assertTrue(truncated)
        assertTrue(output.contains("已截断"))
    }

    @Test
    fun prettyPrintFallsBackToRawTextWhenNotJson() {
        val text = "<html>502 Bad Gateway</html>"

        val (output, truncated) = prettyPrintAndCap(text, parsed = null)

        assertFalse(truncated)
        assertEquals(text, output)
    }

    // ---------- ApiProbeCatalog ----------

    @Test
    fun catalogIdsAreUniqueAndPathsAreApiPaths() {
        val ids = ApiProbeCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)

        ApiProbeCatalog.all.forEach { spec ->
            assertTrue("path 必须以 /api/ 开头：${spec.path}", spec.path.startsWith("/api/"))
            assertFalse("path 不能含空白：${spec.path}", spec.path.any { it.isWhitespace() })
        }
    }

    @Test
    fun catalogPinsPaginationToFirstPage() {
        // 探针只取单页快照；分页接口必须把 page 写死，否则会退化成翻页聚合
        ApiProbeCatalog.all
            .filter { it.path.contains("page=") }
            .forEach { spec ->
                assertTrue(
                    "分页接口必须写死 page=1：${spec.path}",
                    spec.path.contains("page=1"),
                )
            }
    }
}
