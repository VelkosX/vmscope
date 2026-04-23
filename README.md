# vmScope

[![Maven Central](https://img.shields.io/maven-central/v/org.velkos/vmscope-core.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/org.velkos/vmscope-core)
[![Build](https://github.com/VelkosX/vmscope/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/VelkosX/vmscope/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A Kotlin Multiplatform drop-in replacement for `viewModelScope` with a configurable `CoroutineExceptionHandler`. Targets Android, iOS (arm64, x64 simulator, simulator-arm64), and JVM. Ships with four Android lint rules that enforce correct configuration and correct exception-handling discipline at build time.

## Supported targets

| Target | Status | Install path |
|---|---|---|
| Android | first-class | auto-init via App Startup + `VmScopeConfig.Provider` on `Application` |
| iOS arm64 / x64 sim / simulator-arm64 | supported | manual `VmScope.install(config)` |
| JVM (desktop / server) | supported | manual `VmScope.install(config)` |

Lint rules are Android-only — they run against JVM bytecode and ship inside the Android AAR. iOS and non-Android JVM consumers get no lint coverage; detekt is a reasonable parallel for those.

## What and why

`androidx.lifecycle.viewModelScope` has no installed exception handler. Any exception thrown in a coroutine launched on it that isn't caught by the application code propagates to the JVM's default uncaught-exception handler — in practice, it crashes the process. On release builds this lands in your crash reporter as a `RuntimeException` with no particular signal, mixed in with everything else; in tests it's easy to not notice at all.

```kotlin
// viewModelScope: uncaught exceptions propagate unhelpfully.
viewModelScope.launch {
    val user = api.fetchUser()  // throws IOException on failure — crashes the process.
    _state.value = user
}

// vmScope: routed through a handler you configured in your Application.
vmScope.launch {
    val user = api.fetchUser()
    _state.value = user
}
```

`vmScope` gives you a single place to decide what happens to uncaught ViewModel exceptions: crash in debug, report to Crashlytics in release, or hand off to a custom `CoroutineExceptionHandler` — your call.

## Install

```kotlin
dependencies {
    implementation("org.velkos:vmscope-core:0.1.0")
}
```

Lint rules ship in the AAR via `lintPublish` — no additional setup.

## Configure (Android)

Required on Android. Implement `VmScopeConfig.Provider` on your `Application`:

```kotlin
class MyApp : Application(), VmScopeConfig.Provider {
    override val vmScopeConfiguration = vmScopeConfig {
        onUnhandledException { e ->
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }
}
```

vmScope auto-discovers your provider at process start via Jetpack App Startup. You do not need to register it, call `VmScope.install(…)`, or wire anything into `onCreate`.

The `MissingVmScopeConfigProvider` lint rule fires at **Fatal** severity if your module declares an `Application` subclass that does not implement the provider — release builds will not ship without explicit action.

## Configure (iOS)

No Application class, no auto-init. Call `VmScope.install(config)` once during app bootstrap. A common pattern is a shared-Kotlin `initVmScope()` function that Swift calls at app launch:

```kotlin
// in your shared KMP module (commonMain or iosMain)
fun initVmScope() {
    VmScope.install {
        onUnhandledException { e ->
            // Route to your iOS crash reporter. Crashlytics / Sentry / Bugsnag all expose
            // per-target native SDKs you can call into from Kotlin/Native, or you can log
            // to NSLog and pick up in your server-side ingestion.
            NSLog("%@", "vmScope uncaught: $e")
        }
    }
}
```

```swift
// in AppDelegate or @main App
import YourSharedKMP

@main
struct MyApp: App {
    init() {
        // Kotlin/Native rewrites top-level functions whose name starts with `init` to
        // `do<Original>` in the Swift interface (avoids colliding with Swift's `init`
        // constructor keyword). If your Kotlin file is `VmScopeBootstrap.kt` the synthetic
        // Swift class is `VmScopeBootstrapKt` and the Swift-visible name is `doInitVmScope`.
        VmScopeBootstrapKt.doInitVmScope()
    }
    var body: some Scene { … }
}
```

`VmScopeConfig.Provider` still exists on iOS as a declared interface, but implementing it has no discovery effect — only the manual `install(...)` call takes effect.

A full KMP reference consumer lives in [`sample-kmp/`](sample-kmp/) — one `SharedSampleViewModel` in `commonMain` consumed by both an Android app (`:sample-kmp:android-app`) and a SwiftUI iOS app (`sample-kmp/ios-app/`). Demonstrates the auto-init path on Android (`VmScopeConfig.Provider` on `Application`) alongside the manual `initVmScope()` path on iOS, and contrasts `vmScope.launch { throw }` with raw `viewModelScope.launch { throw }` so you can see the platform-default failure modes vmScope is replacing. Build + run instructions in [`sample-kmp/README.md`](sample-kmp/README.md). iOS side is simulator-only; the whole directory is macOS-gated in `settings.gradle.kts`.

For the pure Android-only integration (no KMP plugin, no shared code — the shape a typical Android-only consumer would write), see [`sample-android/`](sample-android/) instead.

## Configure (JVM desktop / server)

Same pattern as iOS — call `VmScope.install(config)` once from `main()`:

```kotlin
fun main() {
    VmScope.install {
        onUnhandledException { e ->
            logger.error("vmScope uncaught", e)
            sentryClient.captureException(e)
        }
    }
    // rest of startup
}
```

**Caveat — `Dispatchers.Main`.** The library uses `Dispatchers.Main.immediate` to match `viewModelScope` semantics. On Android and iOS this is automatic. On bare JVM with no UI framework, `Dispatchers.Main` is not available and the library falls back to `EmptyCoroutineContext` (same behavior as `androidx.lifecycle.viewModelScope` on those targets). For Compose Desktop consumers, add `org.jetbrains.kotlinx:kotlinx-coroutines-swing` or `-javafx` to supply a Main dispatcher.

## Using `vmScope`

Swap `viewModelScope` for `vmScope` in your ViewModels:

```kotlin
class UserViewModel : ViewModel() {
    fun load() {
        vmScope.launch {
            val user = api.fetchUser()
            _state.value = user
        }
    }
}
```

The `UseVmScope` lint rule offers an Android Studio quick fix to replace `viewModelScope` references and add the import.

## Exception handling discipline

Write `try/catch` with specific exception types:

```kotlin
vmScope.launch {
    try {
        doNetworkCall()
    } catch (e: IOException) {
        _state.value = UiState.Error(e)
    }
    // Unexpected exceptions fall through to your configured handler.
}
```

Two lint rules back this up:

- **`CancellationExceptionNotRethrown`** (Error) — catching `CancellationException` without `throw e` breaks coroutine cancellation. Always rethrow.
- **`CatchingThrowableInCoroutine`** (Warning) — catching `Throwable` or `Exception` inside a coroutine context swallows `CancellationException` too. If you must catch broadly, open the block with `if (t is CancellationException) throw t`.

## Testing

Consumer ViewModel tests work unchanged. No test rule, no test dependency, no setup. Unhandled exceptions in `vmScope` during tests crash the test thread, which JUnit reports as a test failure — the same loud behavior you'd get from vanilla `viewModelScope`.

## Debug vs. release

- **Debug** (`ApplicationInfo.FLAG_DEBUGGABLE` is set): uncaught exceptions always crash the process via `Thread.getDefaultUncaughtExceptionHandler()`. Your `onUnhandledException` callback is ignored so bugs stay visible during development.
- **Release**: your `onUnhandledException` callback is invoked with `UnhandledViewModelException` wrapping the original throwable. If you set a custom `handler(CoroutineExceptionHandler)` instead, it takes over entirely and receives the original throwable unwrapped.

## Migrating an existing codebase

The four rules are designed to be turned on without a flag day. In a large codebase with many `viewModelScope` call-sites:

```bash
./gradlew updateLintBaseline
```

Commit `lint-baseline.xml`, migrate incrementally (the `UseVmScope` quick fix handles most cases), and tighten severities in `lint.xml` once clean. **Do not baseline `MissingVmScopeConfigProvider`** — it's the one rule that needs to be addressed immediately before shipping.

## Advanced — opt out of auto-init

If your app uses a non-standard `Application` class (from a cross-platform framework, a game engine, or similar) and implementing `VmScopeConfig.Provider` is awkward, you can disable auto-init and install manually. Remove the initializer in your `AndroidManifest.xml`:

```xml
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    tools:node="merge">
    <meta-data
        android:name="org.velkos.vmscope.VmScopeInitializer"
        tools:node="remove" />
</provider>
```

Then install from `Application.onCreate`:

```kotlin
override fun onCreate() {
    super.onCreate()
    VmScope.install {
        onUnhandledException { e -> reportToCrashlytics(e) }
    }
}
```

Suppress the lint check on your Application class with `@Suppress("MissingVmScopeConfigProvider")`.

This mirrors the opt-out pattern used by `WorkManager.initialize` — see [WorkManager's custom-configuration documentation](https://developer.android.com/topic/libraries/architecture/workmanager/advanced/custom-configuration) for conceptual precedent.

## FAQ

**Why not just `viewModelScope.launch(CoroutineExceptionHandler { … })`?** You'd have to boilerplate every single `launch` and `async`, and forgetting it crashes the process silently. vmScope installs the handler once, in the scope's `CoroutineContext`, and the lint rules make the remaining correctness concerns visible at build time.

**Does this change testing?** No. ViewModel tests work exactly as they did with `viewModelScope`. Unhandled exceptions crash the test — which is what you want.

**What about `lifecycleScope`?** Out of scope for v1. File an issue if this matters to your team.

**Why crash by default in release if no callback is configured?** The `MissingVmScopeConfigProvider` lint rule exists precisely so this path is unreachable in well-configured projects. If you get there anyway, loud failure is the right behavior — silently swallowing exceptions in production is worse than crashing.

## License

```
Copyright 2026 Luka Velimirovic

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
