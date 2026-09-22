// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    // AboutLibraries 插件：仅在 app 模块启用，根工程只声明版本
    alias(libs.plugins.aboutlibraries.android) apply false
}
