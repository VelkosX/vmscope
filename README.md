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

Lint rules are Android-only — they run against JVM bytecode and ship inside the Android AAR. iOS and non-Android JVM consumers get no lint coverage.

## What and why

`androidx.lifecycle.viewModelScope` has no installed exception handler. Any exception thrown in a coroutine launched on it that isn't caught by the application code propagates to the JVM's default uncaught-exception handler — in practice, it crashes the process. In tests it's easy to not notice at all; in production you get a crash report with no hook for your own handling logic (no "wrap it, route it, decide per build type").

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

## Using `vmScope`

After installing, replace `viewModelScope` with `vmScope` in your ViewModels. The next section explains how to wire up the exception handler per platform.

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

## Migrating an existing codebase

Primarily relevant if you're adding vmscope to an existing Android codebase that uses `viewModelScope` — greenfield projects can skip ahead. iOS-only / JVM-only consumers won't have lint to deal with.

### With an AI coding agent (recommended for any non-trivial codebase)

An agent-assisted migration guide ships at [`migration/AGENT_MIGRATION.md`](migration/AGENT_MIGRATION.md). Point your coding agent of choice (Claude Code, Codex, Cursor, Aider) at the file — the agent walks through a seven-phase migration in the correct order with safety checks for judgment-heavy steps (broad `catch (Throwable)` blocks, `CancellationException` handling, missing `Application` class, Hilt interaction). Supports both Android-only and Kotlin Multiplatform projects. Review the diff carefully before merging — migrations touch exception-handling code paths and can surface latent bugs.

### Manual migration (without an agent)

The four lint rules are designed to be turned on without a flag day. In a large codebase with many `viewModelScope` call-sites:

```bash
./gradlew updateLintBaseline
```

Commit `lint-baseline.xml`, migrate incrementally (the `UseVmScope` quick fix handles most cases), and tighten severities in `lint.xml` once clean. **Do not baseline `MissingVmScopeConfigProvider`** — it's the one rule that needs to be addressed immediately before shipping.

## Configure (iOS)

No Application class, no auto-init. Call `VmScope.install(config)` once during app bootstrap. A common pattern is a shared-Kotlin `initVmScope()` function that Swift calls at app launch:

```kotlin
// in your shared KMP module (commonMain or iosMain)
fun initVmScope() {
    VmScope.install {
        onUnhandledException { e ->
            // Route to your iOS crash reporter — Crashlytics / Sentry / Bugsnag all expose
            // per-target native SDKs callable from Kotlin/Native.
            println("vmScope uncaught: $e")
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
        VmScopeBootstrapKt.doInitVmScope()
    }
    var body: some Scene { … }
}
```

Swift-visible name note: Kotlin/Native rewrites any top-level function whose name starts with `init` to `do<Original>` in the generated Swift interface, to avoid colliding with Swift's `init` constructor keyword. Your Kotlin `initVmScope()` is therefore called as `doInitVmScope()` from Swift. The rewrite cannot be suppressed on top-level functions.

NSLog-from-Kotlin/Native caveat: the NSLog varargs bridge segfaults reproducibly on Kotlin/Native 2.3.x when passed Kotlin strings — vmscope itself uses `println` internally for this reason. If you need output visible in Xcode's console, `println` routes through stderr to the same place. If you need an iOS crash report signal, call into your crash reporter's native SDK directly; don't rely on NSLog.

`VmScopeConfig.Provider` still exists on iOS as a declared interface, but implementing it has no discovery effect — only the manual `install(...)` call takes effect.

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

## Samples

Two reference consumers live in the repo, each demonstrating a distinct integration path:

- [`sample-android/`](sample-android/) — pure Android (no KMP plugin, no shared code). The shape a typical Android-only consumer would write: `VmScopeConfig.Provider` on `Application`, App Startup auto-init, `vmScope.launch` in ViewModels. Builds under R8.
- [`sample-kmp/`](sample-kmp/) — full KMP. One `SharedSampleViewModel` in `commonMain` consumed by both an Android app (`:sample-kmp:android-app`) and a SwiftUI iOS app (`sample-kmp/ios-app/`). Demonstrates the auto-init path on Android alongside the manual `initVmScope()` path on iOS, and contrasts `vmScope.launch { throw }` with raw `viewModelScope.launch { throw }` to surface the platform-default failure modes vmScope is replacing. Simulator-only; the whole directory is macOS-gated in `settings.gradle.kts`. Build + run details in [`sample-kmp/README.md`](sample-kmp/README.md).

## FAQ

**Why not just `viewModelScope.launch(CoroutineExceptionHandler { … })`?** You'd have to boilerplate every single `launch` and `async`, and forgetting it crashes the process silently. vmScope installs the handler once, in the scope's `CoroutineContext`, and the lint rules make the remaining correctness concerns visible at build time.

**Does this change testing?** No. ViewModel tests work exactly as they did with `viewModelScope`. Unhandled exceptions crash the test — which is what you want.

**What about `lifecycleScope`?** Out of scope for v1. File an issue if this matters to your team.

**Why crash by default in release if no callback is configured?** The `MissingVmScopeConfigProvider` lint rule exists precisely so this path is unreachable in well-configured projects. If you get there anyway, loud failure is the right behavior — silently swallowing exceptions in production is worse than crashing.

**Does this work with Hilt?** Yes. Implement `VmScopeConfig.Provider` on your `@HiltAndroidApp`-annotated `Application` alongside any other interfaces you're already using. The annotations and interface composition work together:

```kotlin
@HiltAndroidApp
class MyApp : Application(), VmScopeConfig.Provider {
    override val vmScopeConfiguration = vmScopeConfig {
        onUnhandledException { e -> FirebaseCrashlytics.getInstance().recordException(e) }
    }
}
```

The library's App Startup initializer runs before `Application.onCreate` (before Hilt's injection completes), and reads the provider via `context.applicationContext as? VmScopeConfig.Provider` — this cast works regardless of Hilt's injection state because the interface is defined on the class, not injected into it.

**What's the runtime overhead?** Small. The runtime AAR is 18 KB. vmScope's allocation shape matches `viewModelScope`'s — one `SupervisorJob` + one scope per ViewModel at first access, nothing new at `launch` time. App Startup does a context capture and one interface cast at process start. No reflection anywhere.

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
