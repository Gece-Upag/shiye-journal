// 顶层构建文件。
//
// 这里的 buildscript 只做一件事：统一提供 Kotlin Gradle Plugin 与 KSP 的版本。
// 原因与注意事项都写在 gradle/libs.versions.toml 顶部，改版本前请先读那段注释。
//
// 为什么用 AGP 9 的内置 Kotlin，却还要在这里声明 KGP 版本：
// AGP 9 内置 Kotlin，不能再显式应用 org.jetbrains.kotlin.android（会报
// "plugin is already on the classpath with an unknown version"）。
// 但它内置的 KGP 2.2.10 对应的 KSP 2.2.10-2.0.2 仍用旧的 kotlin.sourceSets DSL
// 注册生成源码，与内置 Kotlin 冲突，构建直接失败。
// 官方给的升级方式就是在这里把 KGP / KSP 提到更新版本（2.3.10 / 2.3.6），
// 模块里的 Compose 编译器、序列化、KSP 三个插件再用 apply() 从这份 classpath 取。
//
// 注意：buildscript 块读不到 version catalog，所以版本号在这里硬编码，
// 与 libs.versions.toml 里的记录值必须保持一致。
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // 实现构件
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.10")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.6")

        // 插件标识（marker）构件：模块里用 apply(plugin = "xxx") 时，Gradle 依赖
        // classpath 中的 META-INF/gradle-plugins/<id>.properties 定位插件实现，
        // 只放实现构件是不够的（会报 Plugin with id 'xxx' not found）。
        classpath("org.jetbrains.kotlin.plugin.compose:org.jetbrains.kotlin.plugin.compose.gradle.plugin:2.3.10")
        classpath("org.jetbrains.kotlin.plugin.serialization:org.jetbrains.kotlin.plugin.serialization.gradle.plugin:2.3.10")
        classpath("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.3.6")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
}
