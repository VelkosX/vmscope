plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.binary.compatibility.validator)
}

apiValidation {
    // Only track the library's API surface. Skip the samples (consumers, not published) and the
    // lint module (internal to the library; its public API is just Issue objects consumed
    // reflectively by lint itself).
    ignoredProjects += listOf("sample-android", "vmscope-lint")
    // The KMP sample's `:sample-kmp:shared` and `:sample-kmp:android-app` are gated to macOS
    // hosts in settings.gradle.kts (iOS targets need Xcode tooling). Add their names to the
    // ignore list only when they're actually included — the validator throws on names that
    // don't resolve to a real project, contrary to what we initially assumed.
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        ignoredProjects += listOf("shared", "android-app")
    }
    // Also validate the per-target KMP publications, not just the JVM one.
    @OptIn(kotlinx.validation.ExperimentalBCVApi::class)
    klib {
        enabled = true
    }
}
