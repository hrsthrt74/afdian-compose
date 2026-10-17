import com.android.build.api.variant.BuildConfigField
// 打包日期用：生成 APK 文件名里的 MMDD 后缀
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
    keystorePropertiesFile.readLines().associate { line ->
        val (key, value) = line.split("=", limit = 2)
        key.trim() to value.trim()
    }
} else {
    emptyMap()
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
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 所有变体都写入构建时间，正式版虽然不启用时间炸弹，但保留字段可避免
        // 不同变体的 BuildConfig 结构不一致。
        buildConfigField("long", "BUILD_TIMESTAMP_MS", "${System.currentTimeMillis()}L")
        buildConfigField("boolean", "INTERNAL_BUILD", "false")
        // 是否支持模糊效果：完整版恒为 true；legacy 低配版在自身 flavor 里覆盖为 false，
        // ThemeRepository 据此在数据源层关闭全部模糊。三处显式声明保持 BuildConfig 结构一致。
        buildConfigField("boolean", "SUPPORT_BLUR", "true")
        // 是否支持系统预测性返回：legacy 低配版强制关闭（PredictiveBackGuard 拦截系统返回
        // 事件、降级为普通返回）。同样三处显式声明保持 BuildConfig 结构一致。
        buildConfigField("boolean", "SUPPORT_PREDICTIVE_BACK", "true")
    }

    // res/font 下的可变字体（MiSans Condensed）保持不压缩：
    // noCompress 是按扩展名作用于整个 APK 的 —— 不压缩时 Typeface.Builder 能直接 mmap 字体文件；
    // 压缩了就只能整份读进内存。
    androidResources {
        noCompress += "ttf"
    }

    // 维度声明顺序决定变体名拼接顺序：distribution 在前，edition 在后。
    flavorDimensions += "distribution"
    // edition 维度：standard=完整版，legacy=低配兼容版（minSdk 29、无模糊）
    flavorDimensions += "edition"
    productFlavors {
        // 保留正式渠道的无后缀包名；加入 flavor 维度后，正式变体名称为 productionStandardRelease。
        create("production") {
            dimension = "distribution"
            buildConfigField("boolean", "INTERNAL_BUILD", "false")
            isDefault = true
        }
        create("internal") {
            dimension = "distribution"
            // 内测版与正式版并存安装，登录态和缓存也自然隔离。
            applicationIdSuffix = ".internal"
            buildConfigField("boolean", "INTERNAL_BUILD", "true")
        }
        // 完整版：显式创建以固定变体命名（若缺失 standard，distribution 维度会直接与
        // legacy 拼接，变体名错位）。
        create("standard") {
            dimension = "edition"
            buildConfigField("boolean", "SUPPORT_BLUR", "true")
            buildConfigField("boolean", "SUPPORT_PREDICTIVE_BACK", "true")
            isDefault = true
        }
        // 低配兼容版：minSdk 降到 29、不启用任何模糊效果，与完整版并存安装。
        create("legacy") {
            dimension = "edition"
            applicationIdSuffix = ".legacy"
            minSdk = 29
            buildConfigField("boolean", "SUPPORT_BLUR", "false")
            buildConfigField("boolean", "SUPPORT_PREDICTIVE_BACK", "false")
        }
    }

    // 签名配置：从 key.properties 读取密钥库信息
    signingConfigs {
        create("release") {
            // 仅在 key.properties 存在且包含必要字段时才配置签名
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties["storeFile"]!!)
                storePassword = keystoreProperties["storePassword"]!!
                keyAlias = keystoreProperties["keyAlias"]!!
                keyPassword = keystoreProperties["keyPassword"]!!
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            optimization {
                enable = false
            }
            // release 构建类型使用 release 签名配置
            signingConfig = signingConfigs.getByName("release")
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
}

// AboutLibraries：离线模式，构建期不访问网络（远程 license/funding 本就默认关闭，显式锁死更稳）
aboutLibraries {
    offlineMode = true
}

// 打包日期，格式 MMDD（例：8 月 9 日 -> 0809），用于拼装 APK 输出文件名。
// 配置阶段每次构建都会重新执行（本项目未启用 configuration cache，见 gradle.properties），
// 所以取的是打包当天的日期，与 BUILD_TIMESTAMP_MS 的取值时机一致。
val buildDateSuffix: String = LocalDate.now()
    .format(DateTimeFormatter.ofPattern("MMdd"))

// APK 文件名里 flavor 维度的排列顺序：edition（standard/legacy）在前、distribution（production/internal）在后。
// 注意：只影响文件名，AGP 原生变体名（APP_VARIANT_NAME、输出目录）仍按 flavorDimensions 的声明顺序。
private val apkFileNameFlavorOrder = listOf("edition", "distribution")

// 每个变体写入自己的完整变体名（如 productionStandardDebug / internalLegacyDebug），
// 供设置页底部居中小字提示当前打包类型；同时定制 APK 输出文件名
androidComponents {
    onVariants { variant ->
        // AGP 9 的 BuildConfigField(type, value, comment)：value 即字面值，
        // String 类型必须自带引号（KDoc 明确要求），comment 传 null 无注释
        variant.buildConfigFields?.put(
            "APP_VARIANT_NAME",
            BuildConfigField("String", "\"${variant.name}\"", null)
        )

        // 自定义 APK 文件名，默认的 app-productionStandardRelease.apk 不便区分变体。
        // 目标格式：Afdian-0809-standard-internal-debug.apk（日期为打包当天 MMDD）
        // flavor 部分按 apkFileNameFlavorOrder 重排：edition 在前、distribution 在后
        val flavorPart = variant.productFlavors
            .sortedBy { apkFileNameFlavorOrder.indexOf(it.first) }
            .joinToString("-") { it.second }
        // 构建类型（debug / release）单独取，避免依赖变体名的驼峰切分
        val buildTypePart = variant.buildType ?: "unknown"
        val fileName = "Afdian-${buildDateSuffix}-$flavorPart-$buildTypePart.apk"
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
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
