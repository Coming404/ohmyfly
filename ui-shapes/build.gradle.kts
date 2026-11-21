// 统一圆角适配保留 Miuix 模块身份，让预编译组件共用同一曲率实现。
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeCompiler)
}

group = "top.yukonga.miuix.kmp"
version = libs.versions.miuix.get()

kotlin {
    jvmToolchain(21)
    android {
        namespace = "com.ohmyfly.shapes"
        compileSdk { version = release(libs.versions.android.compileSdk.get().toInt()) { minorApiLevel = libs.versions.android.compileSdkMinor.get().toInt() } }
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
    }
    if (providers.systemProperty("os.name").get().startsWith("Mac")) {
        iosArm64()
        if (!providers.gradleProperty("iosDeviceOnly").map(String::toBooleanStrict).getOrElse(false)) {
            iosSimulatorArm64()
        }
    }
    sourceSets.commonMain.dependencies {
        api(libs.compose.runtime)
        api(libs.compose.foundation)
        api(libs.compose.ui)
        api(libs.kyant.shapes)
    }
}
