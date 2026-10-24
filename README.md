<div align="center">

# Afdian · 爱发电第三方 Android 客户端

基于 **Kotlin + Jetpack Compose + [Miuix](https://github.com/YuKongGame/Miuix)** 构建的爱发电（afdian.com）第三方客户端。

> ⚠️ **功能尚未全部完成**，已实现范围与已知限制见 [「已知限制」](#九已知限制) 

</div>

---

## 一、免责声明

> [!IMPORTANT]
> > **本项目是爱发电的第三方非官方客户端，与爱发电（afdian.com）及其运营方不存在任何隶属、授权或背书关系。**
> 
> > **本项目不包含下单、支付、购物车、提现等涉及交易的功能。此限制为设计如此，请勿反馈。交易相关会引导到官方网页/客户端继续操作。**

> [!WARNING]
> **本应用为爱发电的第三方客户端，有问题请勿向爱发电官方反馈。**
>
> 爱发电官方客服与反馈渠道只受理官方客户端与网站的问题。本应用的 bug、功能建议、账号异常请提到本项目的 [GitHub Issues](https://github.com/hrsthrt74/afdian-compose/issues)，反馈时附上设置页底部的版本号与构建类型。

- 「爱发电」「afdian」「Afdian」等名称与标识归其各自权利人所有，本项目不主张任何商标权。
- 本应用调用的是爱发电**网页端使用的接口**，并非官方开放 API。接口随时可能变更或失效，届时本应用的相关功能会不可用。
- 本项目按「现状提供」原则维护，不承诺任何 SLA、不承诺与官方客户端的功能一致。
- **使用本应用即表示你已知悉上述风险，并自行承担使用后果**；因接口变更、账号策略调整等原因造成的账号异常，本项目不承担责任。

---

## 二、功能一览

| 模块    | 能力                                                          |
|-------|-------------------------------------------------------------|
| 登录    | 密码 / 验证码双通道、图形验证码、【独有】**多账号切换**                             |
| 首页    | 【独有】**可自定义模块**：**数据卡（收入 / 提现 / 发电人数等）**、**可拖拽排序布局**等        |
| 图表    | 【独有】**收入趋势环形图与折线图，支持窄体可变字体与数字滚动动画**                         |
| 发现    | 动态流、创作者搜索、搜索历史、动态详情                                         |
| 创作者主页 | 目标、方案、商品、赞助榜、合集、动态列表                                        |
| 创作者后台 | 【独有】**可自定义数据卡**、收入明细、每月汇总、收到发电、赞助者管理、感谢页、订单、售卖商品、优惠码、FAQ 设置 |
| 消息    | 【独有】**置顶**、【独有】**一键已读**、【独有】**文本消息卡片解析**、FAQ 快捷回复           |
| 我的    | 收藏、发电的、【独有】**浏览历史**、屏蔽与拒绝名单、开发者页                            |
| 系统    | 【独有】**主题色 / Monet 配色、模糊开关、底栏样式、手势返回、预测性返回**                 |
| 工具    | 【独有】**存储空间管理**、日志管理与导出、接口调试                                 |
| 其他    | i18n 待做（当前仅简体中文）                                            |

> 【独有】指的是：与官方客户端相比独有的功能。

---

## 三、变体

本项目有 2 个构建维度，共 4 个变体：

| 变体                   | applicationId                          | minSdk | 用途                           |
|----------------------|----------------------------------------|--------|------------------------------|
| `productionStandard` | `com.hrsthrt74.afdian`                 | 34     | **正式完整版**：模糊、预测性返回全开，日常使用选它  |
| `productionLegacy`   | `com.hrsthrt74.afdian.legacy`          | 29     | 正式低配兼容版：仅供 Android 10~13 旧设备 |
| `internalStandard`   | `com.hrsthrt74.afdian.internal`        | 34     | 内测完整版：**7 天后过期**             |
| `internalLegacy`     | `com.hrsthrt74.afdian.internal.legacy` | 29     | 内测低配版：同样 7 天过期               |

- 不同 distribution（`production` / `internal`）包名带后缀，可与正式版**并存安装**，登录态与缓存天然隔离。
- `internal` 包带「时间炸弹」：构建时间起 168 小时后启动即被拦在过期页（`TimeBomb`）。正式包不受影响。

---

## 四、从源码构建

### 环境要求

| 组件                     | 版本                                                          |
|------------------------|-------------------------------------------------------------|
| JDK                    | **21**（`sourceCompatibility` / `targetCompatibility` 均为 21） |
| Android Gradle Plugin  | 9.4.1                                                       |
| Gradle                 | 9.7.1（由 wrapper 自动下载）                                       |
| Kotlin                 | 2.4.20                                                      |
| compileSdk / targetSdk | 37 / 36                                                     |
| minSdk                 | 34（legacy flavor 29）                                        |

### 验证命令

变体名必须写全，缩写会报 `Ambiguous matches`：

```bash
./gradlew :app:testInternalStandardDebugUnitTest
./gradlew :app:testInternalLegacyDebugUnitTest
./gradlew :app:assembleInternalStandardDebug
./gradlew :app:assembleInternalLegacyDebug
```

两个 edition 都要打包：legacy 的 minSdk 与模糊开关不同，只编 standard 会漏掉低配分支。

---

## 五、签名配置

release 签名信息从仓库根目录的 `key.properties` 读取（**该文件已在 `.gitignore` 中，不会被提交**）：

```properties
storeFile=afdian-hrsthrt74.jks
storePassword=你的密钥库密码
keyAlias=你的密钥别名
keyPassword=你的密钥密码
```

- `storeFile` 是**相对仓库根目录**解析的路径。
- 四个键缺任意一个，构建照常进行，但 release 产物**未签名、不可直接安装分发**（构建脚本会打印一条 warn 提示）。
- 贡献者请自备 keystore：`keytool -genkey -v -keystore my.keystore -alias myalias -keyalg RSA -keysize 2048 -validity 10000`，
  并把 `my.keystore` 放到仓库根目录、在 `key.properties` 里指向它。
- `*.jks` / `*.keystore` / `key.properties` / `local.properties` 均已在 `.gitignore` 中，**请不要**用 `git add -f` 强行提交。

### 版本号与发布流程

- `versionCode` 规则：`major * 10000 + minor * 100 + patch`。
- `versionName` 使用语义化版本（当前 `1.0.0`）。
- 修改位置：`app/build.gradle.kts` 的 `defaultConfig`。
- 发布时同步打同名 tag（如 `v1.0.0`）；应用内「检查更新」按 tag 比对版本。

手动发布流程：

1. 改 `app/build.gradle.kts` 的 `versionCode` / `versionName`；
2. 打包：`./gradlew :app:assembleProductionStandardRelease :app:assembleProductionLegacyRelease`；
3. 在 GitHub 上创建 Release，tag 填 `v1.0.0`，把两个 APK 作为附件上传上去。

**附件命名不能随意改** —— 应用内「检查更新」靠文件名里的 `standard` / `legacy` / `production` / `internal` / `release` 关键字挑包。

构建产出的文件名格式是 `Afdian-<MMDD 日期>-<edition>-<distribution>-<buildType>.apk`：

```
Afdian-1022-standard-production-release.apk
Afdian-1022-legacy-production-release.apk
```

上传前请按下面的格式**改成带版本号的名字**（中间那段日期换成版本号）：

```
Afdian-<版本>-standard-production-release.apk
Afdian-<版本>-legacy-production-release.apk
```

命名对不上，用户点「去下载」时会挑不到包（此时会降级为跳转到 Releases 页面）。

---

## 六、legacy 低配兼容版

`legacy` 是给 Android 10-13（API 29-33）旧设备准备的兜底版本：

- `minSdk` 降到 29；
- 关闭全部模糊效果与系统预测性返回；

**装错包会被拦截**：legacy 包被装在 API ≥ 34 的设备上时，`EditionGuard` 会在启动阶段直接阻断并提示换装完整版。
这是预期行为，不是崩溃。正式包 `productionStandard` 在所有 API ≥ 34 的设备上都可用。

---

## 七、隐私与数据

- 所有数据（登录凭据、设置、浏览历史、搜索历史、缓存）**只存在本机**，应用内没有账号注册、没有额外的追踪。
- 登录凭据经 **AndroidKeyStore + AES/GCM** 加密后落盘，与设备绑定；DataStore 已排除在系统云备份与设备迁移之外。
- **没有任何埋点、崩溃统计或行为分析 SDK**；崩溃报告只写本地文件，可手动导出。
- 运行日志与崩溃报告在落盘与导出前都会做**脱敏**（`auth_token`、`Cookie`、`token` 字段被替换为 `***`）。
- 唯一会离开设备的数据是你主动发起的网络请求 —— 本应用只请求爱发电域名，以及用于检查更新的 GitHub API（`api.github.com`，只读取最新的一个 Release）。

---

## 八、接口风险提示

本应用使用的是爱发电**网页端内部接口**（`/api/...` 路径），没有官方文档、没有稳定性承诺：

- 接口路径、参数、返回结构随时可能变化；
- 平台若加强风控（验证码、频控、设备指纹），部分功能可能不可用；
- 使用方式请自行判断风险，账号安全由你自己负责；
- 登录凭据只用于向爱发电发起请求，本项目不向任何第三方转发。

若某个功能突然失效，大概率是上游接口变了，而不是 App 出了 bug —— 欢迎提 issue 反馈。

---

## 九、已知限制

部分功能**尚未完成**，请参阅应用内【设置 → 关于 → 功能完成度】页了解详情。

另外：

- 界面文案**仅简体中文**，i18n 未做。
- 不包含下单、支付、购物车、提现等涉及交易的功能。此限制为设计如此，**请勿反馈**。交易相关会引导到官方网页/客户端继续操作。

---

## 十、项目结构与开发文档

```
afdian/
├── app/                        # 单一 app 模块
│   └── src/main/java/com/hrsthrt74/afdian/
│       ├── data/               # 网络、鉴权、缓存、设置、日志、统计、更新
│       ├── ui/                 # Compose 界面（screens / components / navigation / theme）
│       ├── util/               # 通用工具
│       ├── AppLog.kt           # 全局日志门面（禁止直接用 android.util.Log）
│       ├── CrashHandler.kt     # 本地崩溃报告
│       ├── EditionGuard.kt     # legacy 装错包拦截
│       └── TimeBomb.kt         # 内测包 7 天过期
├── gradle/libs.versions.toml   # 依赖与插件版本
├── DESIGN.md                   # UI 规范（卡片、弹层、滚动条、状态管理）
├── AGENTS.md                   # AI 协作与代码风格约定
├── CONTEXT*.md                 # 开发过程文档：架构 / 功能清单 / 缺口分析 / 路线图
└── LICENSE                     # Apache-2.0
```

`CONTEXT*.md` 与 `AGENTS.md` 是**开发过程记录**，部分描述可能滞后于当前实现，以代码为准。

---

## 十一、致谢

- [Miuix](https://github.com/YuKongGame/Miuix) —— HyperOS 风格的设计语言与组件库
- [Coil](https://github.com/coil-kt/coil) —— 图片加载
- [Vico](https://github.com/patrykandpatrick/vico) —— Compose 图表
- [Telephoto](https://github.com/saket/telephoto) —— 全屏图片查看器的缩放与子采样
- [AboutLibraries](https://github.com/mikepenz/AboutLibraries) —— 依赖与许可证清单
- [Reorderable](https://github.com/sh.calvin.reorderable) —— 拖拽排序
- [OkHttp](https://github.com/square/okhttp) / [Jsoup](https://github.com/jsoup/jsoup) —— 网络与 HTML 解析
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) —— JSON 解析（检查更新）

---

## 十二、License

本项目源码采用 [Apache License 2.0](LICENSE)。
