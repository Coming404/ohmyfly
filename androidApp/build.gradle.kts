plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "dev.ohmydrift.app"
    compileSdk {
        version = release(libs.versions.android.compileSdk.get().toInt())
    }
    defaultConfig {
        // Development identifier; confirm ownership before release.
        applicationId = "dev.ohmydrift.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }
    signingConfigs {
        create("release") {
            storeFile = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull?.let { file(it) }
            storePassword = providers.environmentVariable("ANDROID_RELEASE_STORE_PASSWORD").orNull
            keyAlias = providers.environmentVariable("ANDROID_RELEASE_KEY_ALIAS").orNull
            keyPassword = providers.environmentVariable("ANDROID_RELEASE_KEY_PASSWORD").orNull
        }
    }
    buildTypes {
        getByName("release") {
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
}
