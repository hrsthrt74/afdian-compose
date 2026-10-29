import com.android.build.api.variant.BuildConfigField

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    // 构建期收集依赖与许可证，生成 res/raw/aboutlibraries.json 供「开源许可」页读取
    alias(libs.plugins.aboutlibraries.android)
}

// 读取签名配置文件，如果文件不存在则跳过（方便 CI 等无密钥环境构建 debug 包）
val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties: Map<String, String> = if (keystorePropertiesFile.exists()) {
    // 逐行解析而不是直接 associate：手写的 key.properties 里常有空行和 # 注释，
    // 直接 associate 遇到空行会抛 IndexOutOfBoundsException，堆栈还指向构建脚本，
    // 使用者根本不知道是自己配置文件写歪了。这里统一跳过空行、注释行与非法行。
    keystorePropertiesFile.readLines()
        .map { it.trim().removePrefix("\uFEFF") } // 去 UTF-8 BOM（Windows 记事本保存的默认带）
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            // 没有 "=" 的行不是合法配置项，忽略而不是让整次构建失败
            val separator = line.indexOf('=')
            if (separator <= 0) {
                null
            } else {
                val key = line.substring(0, separator).trim()
                // 值两端的引号一并去掉，避免 keyPassword="123" 这种写法把引号存进去
                val value = line.substring(separator + 1).trim().trim('"').trim('\'')
                key to value
            }
        }
        .toMap()
} else {
    emptyMap()
}

// release 签名需要的四个键。齐全才绑定签名配置：
// 否则 assembleRelease 会在配置阶段因非空断言直接崩掉，
// 外部贡献者（手上没有项目密钥）连 release 都编不出来。
val releaseSigningKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val hasReleaseSigning = releaseSigningKeys.all { keystoreProperties[it].orEmpty().isNotEmpty() }
if (!hasReleaseSigning) {
    // 用 lifecycle 而不是 warn：Gradle 控制台默认只显示到 QUIET 级，warn 会整条被吞掉，
    // 贡献者根本不知道自己少了什么配置。这条提示必须真的出现在构建输出里。
    logger.lifecycle(
        "[警告] 未找到完整的签名配置：key.properties 需要包含 ${releaseSigningKeys.joinToString(" / ")}，" +
            "release 产物将为未签名（不可直接分发）。debug 构建与单元测试不受影响。"
    )
}

// standard（完整版）的最低系统要求。同一个数字承担两件事：
// 1. standard flavor 的 minSdk；2. legacy 包的运行时拦截阈值（见 EditionGuard）——
// legacy 在 API >= 该值的设备上属于「装错包」，启动即阻断。
// 只在这里维护一份，避免以后调 minSdk 时漏改拦截逻辑导致静默放行。
val standardMinSdk = 34

// ── 版本号与构建标识（CI 注入） ──
// CI 用 `-P` 传参覆盖版本号与构建身份；本地直接点 Run 时一个都不传，
// 行为与改造前完全一致（1.1.0 / 10100 / BUILD_NUMBER=0）。
// 用 providers.gradleProperty 而不是 project.property：它是配置缓存友好的读法，
// 读到的值会进入缓存键，换参数时不会命中旧缓存。
fun propText(name: String): String? =
    providers.gradleProperty(name).orNull?.trim()?.takeIf { it.isNotEmpty() }

val resolvedVersionName = propText("versionName") ?: "1.1.0"
val resolvedVersionCode = propText("versionCode")?.toIntOrNull() ?: 10100
// 0 表示「不是 Actions 构建」：本地包与正式版包都用 0，Actions 包用 GitHub 的 run_number
val injectedBuildNumber = propText("buildNumber")?.toIntOrNull() ?: 0
// local / actions / stable：仅用于展示与排查，不参与任何业务判断
val injectedBuildChannel = propText("buildChannel") ?: "local"
// 短 commit sha（7 位），Actions / Release 构建才有
val injectedGitSha = propText("gitSha").orEmpty()

// ── 更新日志（CHANGELOG.md）──
// 仓库根的 CHANGELOG.md 是唯一数据源：构建时复制进 assets，「设置 → 关于 → 更新日志」运行时解析它。
// 不手工往 app/src/main/assets 里放一份：两份必然漂移，而这种「文档不同步」的错误
// 要等用户截图指出「更新日志少了最新一版」才会被发现。
//
// 为什么要自定义任务而不是内置 Copy：
// AGP 9 起禁止 `sourceSets.assets.srcDir(provider)`（Studio 无法判断 Provider 指向的是
// 生成目录还是静态文件），必须走 Variant API 的 `addGeneratedSourceDirectory`。
// 那条路径要求任务的输出声明成 DirectoryProperty，而 Copy 只暴露 File 类型的 destinationDir，
// 所以这里写一个最小任务。
abstract class CopyChangelogAssets : DefaultTask() {

    @get:InputFile
    abstract val source: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        source.get().asFile.copyTo(
            target = outputDir.get().asFile.resolve("CHANGELOG.md"),
            overwrite = true,
        )
    }
}

val copyChangelogAssets = tasks.register<CopyChangelogAssets>("copyChangelogAssets") {
    source.set(rootProject.layout.projectDirectory.file("CHANGELOG.md"))
}

android {
    namespace = "com.hrsthrt74.afdian"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        applicationId = "com.hrsthrt74.afdian"
        minSdk = standardMinSdk
        targetSdk = 36
        // 版本号：语义化版本 + 可读 versionCode。
        // versionCode 规则：major * 10000 + minor * 100 + patch，
        // 便于人工比较大小，也给「检查更新」一个可比的数字依据。
        // 发布时同步打同名 tag（如 v1.0.0），检查更新按 tag 比对。
        // CI 通过 -PversionName / -PversionCode 注入（tag 推导），本地保持默认值。
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 所有变体都写入构建时间：崩溃报告（CrashHandler）的 Build Time 字段用它，
        // 同时保证不同变体的 BuildConfig 结构一致。
        buildConfigField("long", "BUILD_TIMESTAMP_MS", "${System.currentTimeMillis()}L")
        // 是否支持模糊效果：完整版恒为 true；legacy 低配版在自身 flavor 里覆盖为 false，
        // ThemeRepository 据此在数据源层关闭全部模糊。defaultConfig + standard + legacy
        // 三处各声明一次，保证每个变体的 BuildConfig 字段集合完全一致。
        buildConfigField("boolean", "SUPPORT_BLUR", "true")
        // 是否支持系统预测性返回：legacy 低配版强制关闭（PredictiveBackGuard 拦截系统返回
        // 事件、降级为普通返回）。同样三处各声明一次，保持 BuildConfig 结构一致。
        buildConfigField("boolean", "SUPPORT_PREDICTIVE_BACK", "true")
        // 完整版的 minSdk，同时是 legacy 包的运行时拦截阈值。由 Gradle 注入而不是在 Kotlin
        // 里写死，保证「调 minSdk」和「调拦截阈值」永远是同一个动作。
        buildConfigField("int", "STANDARD_MIN_SDK", "$standardMinSdk")
        // 当前安装包是否为 legacy 低配兼容版 —— 这是「包的身份」，不是能力开关。
        // 刻意不复用 SUPPORT_BLUR：那是 UI 能力，日后任何一侧调整都会让拦截静默失效或误杀。
        buildConfigField("boolean", "LEGACY_EDITION", "false")
        // 构建身份：默认值 = 本地构建。CI 通过 -P 覆盖，三个字段在所有变体里取值相同，
        // 因此只在 defaultConfig 声明一次（不需要像 SUPPORT_BLUR 那样每个 flavor 各写一遍）。
        // Actions 包的 BUILD_NUMBER = GitHub run_number，是「这条构建比那条新」的唯一依据。
        buildConfigField("int", "BUILD_NUMBER", "$injectedBuildNumber")
        buildConfigField("String", "BUILD_CHANNEL", "\"$injectedBuildChannel\"")
        buildConfigField("String", "GIT_SHA", "\"$injectedGitSha\"")
    }

    // res/font 下的可变字体（MiSans Condensed）保持不压缩：
    // noCompress 是按扩展名作用于整个 APK 的 —— 不压缩时 Typeface.Builder 能直接 mmap 字体文件；
    // 压缩了就只能整份读进内存。
    androidResources {
        noCompress += "ttf"
    }

    // 唯一的 flavor 维度：standard=完整版，legacy=低配兼容版（minSdk 29、无模糊）。
    // 单维度下变体名即 standardDebug / standardRelease / legacyDebug / legacyRelease。
    flavorDimensions += "edition"
    productFlavors {
        // 完整版：模糊、预测性返回全开，minSdk 跟随 defaultConfig（34）。
        // 不设 applicationIdSuffix：沿用 com.hrsthrt74.afdian。
        create("standard") {
            dimension = "edition"
            buildConfigField("boolean", "SUPPORT_BLUR", "true")
            buildConfigField("boolean", "SUPPORT_PREDICTIVE_BACK", "true")
            buildConfigField("boolean", "LEGACY_EDITION", "false")
            isDefault = true
        }
        // 低配兼容版：minSdk 降到 29、不启用任何模糊效果，与完整版并存安装。
        create("legacy") {
            dimension = "edition"
            applicationIdSuffix = ".legacy"
            minSdk = 29
            buildConfigField("boolean", "SUPPORT_BLUR", "false")
            buildConfigField("boolean", "SUPPORT_PREDICTIVE_BACK", "false")
            // 是 legacy 包：装在 API >= standardMinSdk 的设备上会被 EditionGuard 拦在启动页
            buildConfigField("boolean", "LEGACY_EDITION", "true")
        }
    }

    // 签名配置：从 key.properties 读取密钥库信息
    signingConfigs {
        create("release") {
            // 仅在 key.properties 存在且四个键都齐全时才配置签名。
            // 用 getValue 而不是 map["key"]!!：缺键时给出明确的「键缺失」信息，
            // 而不是一句 NPE，让人猜是不是 key.properties 路径写错了。
            if (hasReleaseSigning) {
                storeFile = rootProject.file(keystoreProperties.getValue("storeFile"))
                storePassword = keystoreProperties.getValue("storePassword")
                keyAlias = keystoreProperties.getValue("keyAlias")
                keyPassword = keystoreProperties.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // debug 包名加 .debug 后缀，让 debug 包与正式包能同时装在一台机器上：
            //   standardDebug → com.hrsthrt74.afdian.debug
            //   legacyDebug   → com.hrsthrt74.afdian.legacy.debug（叠加 flavor 的 .legacy）
            // 两者数据目录、FileProvider authority 也随之独立，装 debug 不会顶掉正式版的登录态。
            // ⚠️ 副作用：debug 包里的「应用内更新」装不上正式包（包名不同，UpdateInstaller 会拦下），
            //    这是预期行为，验证发版请用正式包。
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // 默认规则 + 本项目的补充规则（目前只有 Ktor 的 java.lang.management 缺失类忽略，
            // 见 app/proguard-rules.pro）。必须用 optimize 版本：AGP 9 已不再支持
            // proguard-android.txt（它内含 -dontoptimize，会挡住 R8 优化）。
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            optimization {
                enable = false
            }
            // release 构建类型使用 release 签名配置。
            // 没有密钥时保持未签名：产物无法分发，但构建本身必须能跑通 ——
            // 开源仓库的贡献者手上通常没有项目密钥，不能让 assembleRelease 直接失败。
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // JVM 单元测试里 android.util.Log 是「未实现的桩」：一调用就抛 RuntimeException。
    // 数据层按项目规范要走 AppLog（debug 构建 debugEnabled 默认开），测试 ChartModel 这类
    // 带日志的业务函数必然踩到。打开默认值返回后，Log.x 变成空转，测试才能只验业务逻辑。
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// AboutLibraries：离线模式，构建期不访问网络（远程 license/funding 本就默认关闭，显式锁死更稳）
aboutLibraries {
    offlineMode = true
}

// 每个变体写入自己的完整变体名（如 standardDebug / legacyRelease），
// 供设置页底部居中小字提示当前打包类型；同时定制 APK 输出文件名
androidComponents {
    onVariants { variant ->
        // 更新日志：把复制任务的产物挂进这个变体的 assets。
        // addGeneratedSourceDirectory 会自动把任务接进构建图（不用手动 dependsOn mergeAssets），
        // 并把它登记成 Studio 眼中的「生成目录」，不会误报只读文件。
        variant.sources.assets?.addGeneratedSourceDirectory(
            copyChangelogAssets,
            CopyChangelogAssets::outputDir,
        )

        // AGP 9 的 BuildConfigField(type, value, comment)：value 即字面值，
        // String 类型必须自带引号（KDoc 明确要求），comment 传 null 无注释
        variant.buildConfigFields?.put(
            "APP_VARIANT_NAME",
            BuildConfigField("String", "\"${variant.name}\"", null)
        )

        // 自定义 APK 文件名，默认的 app-standardRelease.apk 不含版本号。
        // 目标格式：Afdian-1.2.0-standard-release.apk
        // 这正好是 ReleaseVersion.pickApkAsset 认的命名（含 standard/legacy、含 release），
        // 所以发版流程不再需要手工改名——手工改名漏一次，用户就挑不到包。
        // 只剩 edition 一个 flavor 维度，直接拼接即可，无需重排
        val flavorPart = variant.productFlavors.joinToString("-") { it.second }
        // 构建类型（debug / release）单独取，避免依赖变体名的驼峰切分
        val buildTypePart = variant.buildType ?: "unknown"
        val fileName = "Afdian-$resolvedVersionName-$flavorPart-$buildTypePart.apk"
        // outputs 里通常只有一个 APK 产物；对 AAB 等非 APK 产物赋值不生效但也无害
        variant.outputs.forEach { output ->
            output.outputFileName.set(fileName)
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    // Telephoto：图片查看器的双指缩放 / 拖动 / 大图子采样
    implementation(libs.telephoto.coil3)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.nav)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.reorderable)
    implementation(libs.miuix.blur)
    implementation(libs.vico.compose)
    implementation(libs.aboutlibraries.core)
    implementation(libs.shapes)
    // AI 集成：Markdown 渲染（AI 回复弹层）、Ktor Server（MCP 本地服务器）
    implementation(libs.markdown.renderer)
    // M3 变体（不是 m2）：项目只有 Material3，m2 会在运行时找不到 MaterialTheme 而崩溃
    implementation(libs.markdown.renderer.m3)
    implementation(libs.markdown.renderer.coil3)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver3)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
