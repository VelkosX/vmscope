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
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "vmscope"

include(":vmscope")
include(":vmscope-lint")
include(":sample-android")

// The KMP sample (Android + iOS) consumer is macOS-only because its `shared` module declares
// iOS targets, which require Xcode tooling. Gate the includes so Linux/Windows contributors
// (and ubuntu-latest CI) can still configure the Android-only parts of the build without the
// iOS targets failing to resolve. `:sample-android` remains available everywhere as the
// pure-Android reference consumer.
if (System.getProperty("os.name").lowercase().contains("mac")) {
    include(":sample-kmp:shared")
    include(":sample-kmp:android-app")
}
