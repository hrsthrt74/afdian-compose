package com.hrsthrt74.afdian.data.auth

/**
 * 爱发电网页端当前使用的 RSA 公钥（2048 位）。
 *
 * ## 它会被服务端轮换，轮换后必须同步更新
 *
 * 登录请求里 `ar_ept` 是「用公钥加密的 AES 密钥」，服务端用**对应的私钥**解密，
 * 才能解出 `account` / `password`。公钥一旦与服务端不匹配，服务端就解不出 AES 密钥，
 * 拿不到账号密码 —— 这时它不会报「密码错误」，而是把请求当成可疑流量，
 * 返回 `ec=400 操作频繁，请稍后再试`。
 *
 * 这个错报极具迷惑性：文案指向「你操作太频繁」，真实原因却是「你的钥匙过期了」。
 * 实测确认过：用旧公钥发请求，即使 body 是非法 JSON（连参数都解析不了），
 * 也一样返回「操作频繁」；而换成正确公钥，同样的请求体立刻变成正常的
 * 业务响应（如 `ec=404 账号不正确`）。
 *
 * ## 怎么确认它过期了
 *
 * 抓网页端的 `app.js`（`https://static.afdiancdn.com/js/app.<hash>.js`），
 * 搜 `BEGIN PUBLIC KEY`，取那段 PEM 的 base64 部分（去掉换行与空格），
 * 替换这里的常量即可。`app.js` 与 `app-legacy.js` 用的是同一把，可以互相印证。
 *
 * 公钥指纹由 `AuthServicePublicKeyTest` 锁定：服务端轮换后该测试会失败，
 * 提示需要更新这里 —— 避免再次出现「登录突然全面失效、但看不出原因」的情况。
 */
internal const val AFDIAN_RSA_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAv9Z7+OayuwjtWfLscvchF+UTndAbY2ze0ZFcUtEBQL+3E8uAPynSLyzZYG76gQzEzwQjDDorqnPB48losz9iskFfhjK+utpCgZ9wAGPxfmPT1Lmje4O4BxSyJ+8c5rnoWsda/X05Z1J+BcvyMJutJ9m679/mzYn3g6wc46E/qfqTwYc5cgABEWuOdehg/mk0/ztFB1kP4dNZOIT64e8tHa41bnNugFYNlM1DQE3qOTR/ttkcBsxF4VeXrCC+pXQtbx24Xx/HoFdi/l3tKx77kw09jqKbRLOmIxOjX9onmdM3/3PTjKqrRuxVoln8TOExNPnzEj8eH4fv9GMarg3EqQIDAQAB"

/**
 * 网页端加密用的 AES-CBC 初始向量。
 *
 * 与网页端 `app.js` 里的 `WordArray.parse("7brVHncu7wIDAQAB")` 严格一致。
 * 改这里之前请先确认网页端是否也换了 —— 不一致时服务端解不出明文。
 */
internal val AFDIAN_AES_IV = "7brVHncu7wIDAQAB".toByteArray()
