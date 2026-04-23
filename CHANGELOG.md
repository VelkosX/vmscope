# Changelog

All notable changes to vmscope are documented in this file. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] - 2026-04-23

First public release.

vmscope is a Kotlin Multiplatform drop-in replacement for `androidx.lifecycle.viewModelScope` that adds a configurable `CoroutineExceptionHandler`. Uncaught exceptions thrown inside a `vmScope.launch { }` flow through your installed handler — crash the process in debug, report to Crashlytics / Sentry / Bugsnag in release, or hand off entirely to a custom handler. Android is the primary target; iOS (arm64 + both simulator slices) and JVM desktop/server are supported.

### Library

- `vmScope` extension on `androidx.lifecycle.ViewModel` with the same context shape as `viewModelScope` (`SupervisorJob + Dispatchers.Main.immediate`), plus the installed handler. Source-compatible drop-in replacement.
- `VmScopeConfig` + `VmScopeConfig.Builder` + `vmScopeConfig { }` DSL.
- `UnhandledViewModelException` — wrapper type used by the default handler, gives crash reporters a consistent searchable signal for ViewModel-originated crashes.
- `VmScope.install(config)` — manual install entry point for iOS / JVM consumers and Android opt-out scenarios.

### Android integration

- `VmScopeConfig.Provider` interface — implement on your `Application` and vmScope auto-discovers and installs it at process startup via Jetpack App Startup.
- Debuggable-aware handler split: debug builds crash unconditionally so bugs stay visible; release builds route through your configured callback.
- Published `consumer-rules.pro` keeps the initializer, the provider interface, and `UnhandledViewModelException` across R8.

### Lint rules (Android)

Four rules ship inside the Android AAR via `lintPublish` — no consumer wiring required:

- `MissingVmScopeConfigProvider` (Fatal) — fires if the module has an `Application` subclass that doesn't implement the provider. Release builds fail without explicit action.
- `CancellationExceptionNotRethrown` (Error) — catching `CancellationException` without rethrowing it suppresses coroutine cancellation. Quick fix inserts the rethrow.
- `CatchingThrowableInCoroutine` (Warning) — catching `Throwable`/`Exception` inside a coroutine also catches `CancellationException`. Quick fix inserts the guard.
- `UseVmScope` (Warning) — suggests replacing `viewModelScope` with `vmScope`. Quick fix replaces the reference, adds the import, and removes the now-unused `androidx.lifecycle.viewModelScope` import when it's the last reference in the file.

### Multiplatform targets

Published per-target artifacts:

- `org.velkos:vmscope` — root metadata (Gradle picks the right target automatically)
- `org.velkos:vmscope-android` — AAR with embedded lint rules
- `org.velkos:vmscope-jvm` — JAR for desktop / server JVM
- `org.velkos:vmscope-iosarm64`, `-iosx64`, `-iossimulatorarm64` — klibs
- `org.velkos:vmscope-lint` — standalone lint JAR (normally consumed transitively via the Android AAR)

### Samples

- `sample-android/` — minimal pure-Android consumer. Plain `com.android.application` + `kotlin-android`, conventional `src/main/` layout, no KMP plugin. Four-button demo: successful launch, throwing launch (vmScope), deliberate `viewModelScope` to trigger the `UseVmScope` lint rule, and throw on raw `viewModelScope` for side-by-side comparison with the vmScope path. The shape a typical Android-only consumer would write. Builds under R8.
- `sample-kmp/` — full KMP consumer demonstrating one shared codebase driving two platforms. `sample-kmp:shared` hosts `SharedSampleViewModel` + `SampleLog` in `commonMain`; Android app (`sample-kmp:android-app`) consumes the shared module and wires `VmScopeConfig.Provider` on its `Application` for App Startup auto-init; iOS app (`sample-kmp/ios-app/`) links the XCFramework and calls `initVmScope()` from Swift `@main App.init()`. Four-button demo including a side-by-side comparison of `vmScope.launch { throw }` vs. `viewModelScope.launch { throw }` to show the platform-default failure modes vmScope is replacing. Xcode project generated from an [XcodeGen](https://github.com/yonaskolb/XcodeGen) `project.yml` and committed for open-and-run. iOS simulator-only; `:sample-kmp:*` is macOS-gated in settings.gradle.kts.

### Known platform caveats

- `Dispatchers.Main.immediate` is not available on bare JVM without a UI framework. vmScope falls back to `EmptyCoroutineContext` — matches the AndroidX `viewModelScope` behavior. Compose Desktop consumers should add `kotlinx-coroutines-swing` or `-javafx`.
- Lint rules are Android-only (lint inspects JVM bytecode). iOS / pure-JVM consumers get no static analysis from this library; detekt is a reasonable parallel.
- iOS debug-mode crash uses POSIX `abort()` (SIGABRT), surfaced via Xcode's debugger and iOS crash reports. An earlier design re-threw the exception from the coroutine exception handler expecting the Kotlin/Native runtime to terminate; in practice kotlinx-coroutines caught the re-throw and forwarded it to `Dispatchers.Main` instead of terminating, leaving the app in an exception loop. The `abort()` path terminates synchronously so the debug-build "loud failure" behavior matches Android.
