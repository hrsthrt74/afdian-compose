package com.hrsthrt74.afdian.ui.navigation

import androidx.compose.ui.graphics.Color

/**
 * 官方文档常量集中处。
 *
 * App 内的「看官方文档」入口有三处：原「我的」页快捷入口的创作者指南（现已收进文档页）、
 * 开发者页的「文档」卡（两篇开发者文档）、文档页（[Route.Docs]）的聚合收录。
 * 这些 post id 与文档站 URL 原本分散在各入口文件里且互为 private，统一收来这里，
 * 后续新增收录只引用一份定义，不会出现多处漂移。
 */

/** 「创作者指南」动态的 post id：官网的创作者指南长文，按 id 打开动态详情页。 */
internal const val CREATOR_GUIDE_POST_ID = "2da755247a8911e8ad6d52540025c377"

/**
 * 官方开发者文档动态的 post id（两篇）。
 *
 * 都是爱发电官方（Lain音酱）发布的动态，点击走动态详情页展示；
 * 开发者页「文档」卡与文档页（[Route.Docs]）收录的是同两篇。
 * 2026-10 浏览器实测确认标题与发布时间。
 */

/** 「爱发电 Webhook 与 API 文档」动态的 post id（官网开发者文档之一）。 */
internal const val DEV_DOC_WEBHOOK_API_POST_ID = "9c65d9cc617011ed81c352540025c377"

/** 「开发者功能汇总：API 与 Webhook、OAuth2、网页嵌入」动态的 post id（官网开发者文档之一）。 */
internal const val DEV_DOC_OVERVIEW_POST_ID = "010ff078177211eca44f52540025c377"

/**
 * 爱发电官方文档站域名。
 *
 * **刻意不跟随「优先线路」**：另外两个线路域名（ifdian.net / www.afdian.com）下
 * 并没有 guide 子域，把 host 跟着线路改写会直接 404。
 */
internal const val AFDIAN_GUIDE_HOST = "guide.afdian.com"

/** 爱发电官方文档站地址（写死域名，不走 `afdianWebUrl()`）。 */
internal const val AFDIAN_GUIDE_URL = "https://$AFDIAN_GUIDE_HOST"

// 文档卡图标块的固定配色：MIUI 风格彩色方块，文档页与开发者页的文档卡共用；
// Monet 动态色开启时会被主题主色统一覆盖（判断在 CategoryIconTile 里）。
internal val DocTileBlue = Color(0xFF3D8BFD)     // 官网文档站
internal val DocTileOrange = Color(0xFFFF9500)   // 创作者指南
internal val DocTileGreen = Color(0xFF34C759)    // Webhook 与 API
internal val DocTilePurple = Color(0xFF8B7BF7)   // 开发者功能汇总
