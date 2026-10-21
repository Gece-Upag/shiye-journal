pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()

        // ------------------------------------------------------------------
        // 镜像兜底：仅当上面官方源不可达时才会被访问
        // 本机曾出现 dl.google.com / repo1.maven.org 直连超时的情况，
        // 此时 Gradle 会依次重试后面的仓库，由阿里云镜像顶上。
        // 官方源可用时以下两行不会被访问，对构建结果没有任何影响。
        // 若希望只用官方源，删掉下面两个 maven {} 即可。
        // ------------------------------------------------------------------
        // 代理 Maven Central：Kotlin 插件、KSP、kotlinx.serialization
        maven("https://maven.aliyun.com/repository/public")
        // 代理 Google 仓库：AGP、androidx、Compose、Room
        maven("https://maven.aliyun.com/repository/google")
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // 镜像兜底，作用同上：官方源超时时才生效
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
    }
}

rootProject.name = "拾页"
include(":app")
