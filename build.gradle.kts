plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
}

// 包括预编译 Miuix 组件的传递依赖，整个图只保留一份圆角实现。
subprojects {
    configurations.configureEach {
        resolutionStrategy.dependencySubstitution {
            substitute(module("top.yukonga.miuix.kmp:miuix-squircle"))
                .using(project(":miuix-squircle"))
                .because("统一控件与玻璃表面的连续曲率")
        }
    }
}
