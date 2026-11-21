// 从固定官方源码生成材质模块，只替换统一 G2 轮廓所需的实现文件。
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeCompiler)
}

group = "top.yukonga.miuix.kmp"
version = libs.versions.miuix.get()

val upstreamVersion = libs.versions.miuix.get()
val sourceArchives = rootProject.layout.projectDirectory.dir("third-party/miuix-material")
val patchedFiles = listOf("GlassShape.kt", "GlassOverlays.kt", "MaterialAgsl.kt", "EdgeStrokeShader.kt")
val upstreamSources = tasks.register<Sync>("prepareUpstreamSources") {
    from(zipTree(sourceArchives.file("miuix-material-$upstreamVersion-sources.jar"))) {
        include("commonMain/**/*.kt", "skikoMain/**/*.kt")
        patchedFiles.forEach { exclude("**/$it") }
    }
    from(zipTree(sourceArchives.file("miuix-material-android-$upstreamVersion-sources.jar"))) {
        include("androidMain/**/*.kt")
    }
    into(layout.buildDirectory.dir("generated/upstream"))
}

kotlin {
    jvmToolchain(21)
    android {
        namespace = "com.ohmyfly.material"
        compileSdk { version = release(libs.versions.android.compileSdk.get().toInt()) { minorApiLevel = libs.versions.android.compileSdkMinor.get().toInt() } }
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
    }
    val appleHost = providers.systemProperty("os.name").get().startsWith("Mac")
    if (appleHost) {
        iosArm64()
        if (!providers.gradleProperty("iosDeviceOnly").map(String::toBooleanStrict).getOrElse(false)) {
            iosSimulatorArm64()
        }
    }
    sourceSets {
        commonMain {
            kotlin.srcDir(upstreamSources.map { it.destinationDir.resolve("commonMain") })
            dependencies {
                api(libs.compose.runtime)
                api(libs.compose.foundation)
                api(libs.compose.ui)
                implementation(libs.kotlinx.coroutines.core)
                implementation(project(":miuix-squircle"))
            }
        }
        androidMain {
            kotlin.srcDir(upstreamSources.map { it.destinationDir.resolve("androidMain") })
            dependencies { implementation(libs.androidx.lifecycle.runtime.compose) }
        }
        if (appleHost) {
            val skikoMain = create("skikoMain") {
                dependsOn(commonMain.get())
                kotlin.srcDir(upstreamSources.map { it.destinationDir.resolve("skikoMain") })
            }
            // 真机单目标没有自动 iosMain，直接连接已注册目标的主源集。
            targets.withType<KotlinNativeTarget>().configureEach {
                compilations.getByName("main").defaultSourceSet.dependsOn(skikoMain)
            }
        }
    }
}
