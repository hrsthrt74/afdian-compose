plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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
    }

    flavorDimensions += "distribution"
    productFlavors {
        // 保留正式渠道的无后缀包名；加入 flavor 维度后，正式变体名称为 productionRelease。
        create("production") {
            dimension = "distribution"
            buildConfigField("boolean", "INTERNAL_BUILD", "false")
        }
        create("internal") {
            dimension = "distribution"
            // 内测版与正式版并存安装，登录态和缓存也自然隔离。
            applicationIdSuffix = ".internal"
            buildConfigField("boolean", "INTERNAL_BUILD", "true")
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

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.core.ktx)
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
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
