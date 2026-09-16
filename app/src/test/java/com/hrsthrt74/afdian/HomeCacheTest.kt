package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.content.HomeCacheCard
import com.hrsthrt74.afdian.data.content.HomeCacheSnapshot
import com.hrsthrt74.afdian.data.content.homeCacheJson
import com.hrsthrt74.afdian.data.stats.ChartCatalog
import com.hrsthrt74.afdian.data.stats.ChartDataItem
import com.hrsthrt74.afdian.data.stats.ChartSeries
import com.hrsthrt74.afdian.data.stats.ChartType
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首页快照缓存的序列化契约测试（纯 JVM，不碰 DataStore）。
 *
 * 守两条线：
 * 1. 图表数据往 JSON 往返一趟必须无损——缓存里的图要和内存里的图一模一样。
 * 2. 早期版本写入、没有 `creatorCharts` 字段的旧快照必须仍能解析，且图表回落为空。
 */
class HomeCacheTest {

    /** 造一份带图表的最小快照。 */
    private fun snapshotWithCharts(): HomeCacheSnapshot = HomeCacheSnapshot(
        ownerUserId = "user-1",
        creatorCards = listOf(HomeCacheCard("income", "收入", "1280.00", null)),
        creatorCharts = mapOf(
            ChartType.IncomeDaily30.storageValue to ChartDataItem(
                key = ChartType.IncomeDaily30.storageValue,
                title = ChartType.IncomeDaily30.title,
                categories = listOf("9/1", "9/2", "9/3"),
                series = listOf(ChartSeries("收入", listOf(0.0, 12.5, 88.0))),
            ),
            ChartType.UvDaily30.storageValue to ChartDataItem(
                key = ChartType.UvDaily30.storageValue,
                title = ChartType.UvDaily30.title,
                categories = listOf("9/1", "9/2"),
                series = listOf(ChartSeries("访问人数", listOf(3.0, 7.0))),
            ),
        ),
    )

    @Test
    fun chartDataSurvivesJsonRoundTrip() {
        val original = snapshotWithCharts()

        val restored = homeCacheJson.decodeFromString(
            HomeCacheSnapshot.serializer(),
            homeCacheJson.encodeToString(original),
        )

        assertEquals(original, restored)
        // 图表键与横轴标签也要原样保留，否则 Vico 画出来会错位
        assertEquals(
            listOf("9/1", "9/2", "9/3"),
            restored.creatorCharts.getValue(ChartType.IncomeDaily30.storageValue).categories,
        )
        assertEquals(
            listOf(0.0, 12.5, 88.0),
            restored.creatorCharts.getValue(ChartType.IncomeDaily30.storageValue).series.single().values,
        )
    }

    @Test
    fun legacySnapshotWithoutChartsStillLoads() {
        // 旧版本（没有 creatorCharts 字段）写下的快照原文
        val legacyJson = """
            {
              "ownerUserId": "user-1",
              "sponsoring": [],
              "markedCreators": [],
              "creatorCards": [{"id": "income", "title": "收入", "value": "1280.00"}]
            }
        """.trimIndent()

        val restored = homeCacheJson.decodeFromString(HomeCacheSnapshot.serializer(), legacyJson)

        assertEquals("user-1", restored.ownerUserId)
        assertEquals(1, restored.creatorCards.size)
        assertTrue(restored.creatorCharts.isEmpty())
    }

    @Test
    fun snapshotWithNullChartsFallsBackToEmptyMap() {
        // 旧快照若写出显式 null，coerceInputValues 应把它收敛成默认空 Map 而不是解析失败
        val json = """{"ownerUserId": "user-1", "creatorCharts": null}"""

        val restored = homeCacheJson.decodeFromString(HomeCacheSnapshot.serializer(), json)

        assertTrue(restored.creatorCharts.isEmpty())
    }

    @Test
    fun cachedChartKeysAreReachableFromCatalog() {
        // 缓存里的图要能被 ChartCatalog 认出来，否则卡片会一直显示加载占位
        val restored = homeCacheJson.decodeFromString(
            HomeCacheSnapshot.serializer(),
            homeCacheJson.encodeToString(snapshotWithCharts()),
        )

        assertTrue(restored.creatorCharts.keys.all { ChartCatalog.find(it) != null })
    }
}
