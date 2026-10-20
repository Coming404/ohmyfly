import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "dev.ohmydrift.shared"
        compileSdk {
            version = release(libs.versions.android.compileSdk.get().toInt())
        }
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTest {}
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    // Apple toolchains require macOS; do not create unbuildable native tasks on Linux CI.
    if (providers.systemProperty("os.name").get().startsWith("Mac")) {
        listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
            target.binaries.framework {
                baseName = "Shared"
                isStatic = true
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.okio)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.miuix.ui)
            implementation(libs.miuix.core)
            implementation(libs.miuix.preference)
            implementation(libs.miuix.icons)
            implementation(libs.miuix.blur)
            implementation(libs.miuix.shader)
            implementation(libs.miuix.squircle)
            implementation(libs.miuix.nav)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        if (providers.systemProperty("os.name").get().startsWith("Mac")) {
            iosMain.dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
