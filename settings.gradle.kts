pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ohmyfly"
include(":shared", ":androidApp", ":miuix-squircle", ":miuix-material")
project(":miuix-squircle").projectDir = file("ui-shapes")
project(":miuix-material").projectDir = file("ui-material")
