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
    // reflectively by lint itself). `shared` is the iOS sample's KMP sub-module; it's only
    // included on macOS hosts (see settings.gradle.kts) but we unconditionally list it — the
    // validator tolerates names that don't resolve to a project on the current host.
    ignoredProjects += listOf("sample-android", "vmscope-lint", "shared", "android-app")
    // Also validate the per-target KMP publications, not just the JVM one.
    @OptIn(kotlinx.validation.ExperimentalBCVApi::class)
    klib {
        enabled = true
    }
}
