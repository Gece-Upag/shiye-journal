plugins {
    alias(libs.plugins.android.application)
    // 注意：AGP 9 起内置 Kotlin 支持，不能再显式声明 org.jetbrains.kotlin.android，
    // 否则会报 "plugin is already on the classpath with an unknown version"。
}

// 下面三个插件不用 plugins{} 块声明，原因：
// plugins{} 块只从插件仓库解析、要求带版本号，而本项目需要它们与 AGP 实际使用的 Kotlin
// 完全同版本（2.3.10），版本由根工程 build.gradle.kts 的 buildscript classpath 提供。
// plugins{} DSL 读不到父工程的 buildscript classpath，所以这里用 apply() 从继承的 classpath 取。
// 详见 gradle/libs.versions.toml 顶部说明。
//
// 注意：不要在这里声明 org.jetbrains.kotlin.android —— AGP 9 内置 Kotlin，
// 重复声明会报 "plugin is already on the classpath with an unknown version"。
apply(plugin = "org.jetbrains.kotlin.plugin.compose")
apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
apply(plugin = "com.google.devtools.ksp")

android {
    namespace = "com.shiye.journal"
    // 依赖（Compose 1.12.1、Lifecycle 2.11.0 等）要求 compileSdk ≥ 37，
    // 因此用 API 37 的最新稳定平台（本机已安装 android-37.2）。
    // 若将来本机只剩 android-36.1，需要同步把 Compose BOM / Lifecycle 降到支持 36 的版本。
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "com.shiye.journal"
        // 规格要求最低 Android 8.0（API 26），AS 模板默认 24，已按要求上调
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 依赖清单（Play 用来统计 SDK）默认会被塞进 APK/AAB，本项目不上架、也不需要它
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        release {
            // R8：删掉用不到的代码与资源。
            // 这是启动速度上收益最大的一项 —— 冷启动有相当一部分时间花在
            // 类加载与校验上，代码越少越快；APK 变小也少读一点闪存。
            // 配套的保留规则写在 proguard-rules.pro，改之前先读那个文件的说明。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

// Kotlin 编译目标与 compileOptions 保持一致。
// kotlin 扩展由 AGP 9 的内置 Kotlin 提供，所以这里能直接用类型安全的访问器写法。
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

// Room 数据库结构导出目录，用于迁移校验。该目录纳入版本控制，便于回溯 schema 变化。
// KSP 是用 apply() 应用的，Kotlin DSL 不会为它生成类型安全访问器，所以这里显式取扩展。
extensions.configure<com.google.devtools.ksp.gradle.KspExtension>("ksp") {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)

    // Compose
    //
    // 注意：这里**没有** ui-tooling / ui-tooling-preview。
    // 那两个是 Android Studio 预览面板用的，本项目一个 @Preview 都没写，
    // 带上它们只会把预览工具链打进安装包（release 也一样），白占体积、白拖慢类加载。
    // 哪天真要写预览，再把 `debugImplementation(libs.androidx.compose.ui.tooling)`
    // 加回来即可 —— 一定要用 debugImplementation，不要用 implementation。
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)

    // Activity / Lifecycle
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // lifecycle-runtime-compose 同时提供 collectAsStateWithLifecycle：
    // 让 Flow 只在界面可见时收集，退到后台自动停止，省电
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Room：注解处理器走 KSP（版本由根工程 buildscript classpath 提供）
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    // SAF 文件夹遍历/创建（Markdown 导出用）。体积可忽略，纯本地 API，无网络行为。
    implementation("androidx.documentfile:documentfile:1.0.1")
    // 同样因为没有类型安全访问器，这里用 add("ksp", ...) 而不是 ksp(...)
    add("ksp", libs.androidx.room.compiler)

    // JSON：备份文件序列化
    implementation(libs.kotlinx.serialization.json)

    // V2 可选 AI（规格 §8）：只有 HTTP 客户端，不引任何第三方 AI SDK。
    // 这两项只在使用 AI 时才被加载；AI 默认关闭，关闭时不发起任何网络请求。
    implementation(libs.com.squareup.okhttp)
    implementation(libs.androidx.security.crypto)

    testImplementation(libs.junit)
    // runTest / 虚拟时间：自动保存队列的防抖与串行行为必须能脱离真机验证
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
