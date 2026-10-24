# Afdian Android 第三方客户端

## 文档目的

本文件是项目文档索引：记录基线版本，并告诉 AI 每个任务该读哪个子文档。不要一次性全读，按需加载。

- 爱发电（afdian.com）Android 第三方客户端，Miuix（HyperOS 设计语言）构建。

## 当前 Android 基线

- Kotlin：`2.4.20`
- AGP：`9.4.1`
- Gradle：`9.7.1`（wrapper）
- compileSdk：`37`
- targetSdk：`36`
- minSdk：`34`
- Java source/target：`21`
- Miuix：`0.9.4`
- Miuix Android 依赖：`miuix-ui-android`、`miuix-preference-android`、`miuix-icons-android`、`miuix-nav-android`、`miuix-blur-android`
- 其他主要依赖：Kotlin Serialization、OkHttp 5.x、Coil 3.x、Jsoup（HTML 解析）、Vico（图表）、reorderable（拖拽排序）、DataStore Preferences、Lifecycle ViewModel Compose。

## 文档地图

| 任务 | 先读 |
|---|---|
| 改导航、开新页面、调页面布局 | `CONTEXT-architecture.md` |
| 改某个已实现的功能 | `CONTEXT-features.md` 对应小节 |
| 写 UI、不确定组件/样式写法 | `DESIGN.md` |
| 查开发规则（Miuix/线程/状态） | `CONTEXT-rules.md` |
| 嵌套滚动/收拢动画/高度动画/滚动驱动 UI | `CONTEXT-best-practices.md` |
| 规划下一步做什么 | `CONTEXT-roadmap.md` |
| 改 CI / 发版 / 签名 Secrets | `README.md` 的「CI 与 Secrets」+ `.github/workflows/*.yml` |
| 改检查更新 / 更新通道 | `CONTEXT-features.md` 的「检查更新」小节 |
| 对照网页端补功能、确认某功能是否已实现 | `CONTEXT-gap-analysis.md` |
| Miuix 组件 API 存疑 | miuix-skill |

## 每次提交前的验证

flavor 只有 edition（standard / legacy）一个维度，变体名即 `standardDebug` / `legacyRelease`。
聚合任务（`assemble` / `assembleDebug` / `assembleRelease` / `test`）会把两个 edition 一起跑；
但**变体专属任务不能省掉 edition 那一段**——`:app:testDebugUnitTest` 会直接报
`Ambiguous matches`（候选：`testStandardDebugUnitTest` / `testLegacyDebugUnitTest`）。

```text
./gradlew.bat :app:testStandardDebugUnitTest
./gradlew.bat :app:testLegacyDebugUnitTest
./gradlew.bat :app:assembleStandardDebug
./gradlew.bat :app:assembleLegacyDebug
```

两个 edition 都要打包：legacy 的 minSdk 与模糊开关不同，只编 standard 会漏掉低配分支。
legacy 包在 API 34+ 的设备上会被版本拦截页挡住（见 `EditionGuard`），这是预期行为，不是装崩了。
