import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
}

/**
 * KMP sample — the library's "full KMP" reference consumer. Targets Android + two iOS simulator
 * architectures, and hosts the [org.velkos.vmscope.sample.SharedSampleViewModel] in
 * `commonMain` so both app-side consumers (`sample-kmp:android-app` and `sample-kmp/ios-app`)
 * run byte-for-byte identical Kotlin against a single library dependency wiring.
 *
 * Device targets (`iosArm64`) are intentionally excluded — the sample is meant to run in the
 * iOS simulator, not signed and deployed to a device. See `sample-kmp/README.md` for the
 * rationale.
 */
kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    val xcf = XCFramework("SampleShared")

    listOf(
        iosSimulatorArm64(),
        iosX64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "SampleShared"
            isStatic = true
            xcf.add(this)
            // Re-export vmscope-core so Swift can reference VmScope, VmScopeConfig, and
            // UnhandledViewModelException directly by name. Without `export`, these would be
            // visible to the Kotlin sample glue but not to the Swift consumer.
            export(project(":vmscope-core"))
            // Re-export the KMP ViewModel so SharedSampleViewModel's superclass is visible
            // to Swift.
            export(libs.jetbrains.lifecycle.viewmodel)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":vmscope-core"))
            api(libs.jetbrains.lifecycle.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

android {
    namespace = "org.velkos.vmscope.sample.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
