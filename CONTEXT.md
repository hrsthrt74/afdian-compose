# Afdian Android 第三方客户端

## 文档目的

本文件是项目文档索引：记录基线版本，并告诉 AI 每个任务该读哪个子文档。不要一次性全读，按需加载。

- 爱发电（afdian.com）Android 第三方客户端，Miuix（HyperOS 设计语言）构建。
- Android 工程：`C:\Users\hrsth\AndroidStudioProjects\afdian`

## 当前 Android 基线

- Kotlin：`2.4.10`
- AGP：`9.3.2`
- compileSdk：`37`
- targetSdk：`36`
- minSdk：`34`
- Java source/target：`21`
- Miuix：`0.9.4-rc01`
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
| Miuix 组件 API 存疑 | miuix-skill |

## 每次提交前的验证

flavor 有 distribution（production / internal）与 edition（standard / legacy）两个维度，
任务名必须写全变体名——缩写成 `:app:testInternalDebugUnitTest` 之类会报 `Ambiguous matches`。

```text
./gradlew.bat :app:testInternalStandardDebugUnitTest
./gradlew.bat :app:assembleInternalStandardDebug
./gradlew.bat :app:assembleInternalLegacyDebug
```

两个 edition 都要打包：legacy 的 minSdk 与模糊开关不同，只编 standard 会漏掉低配分支。
