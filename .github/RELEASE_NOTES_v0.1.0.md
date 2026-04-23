# v0.1.0 — Initial release

First public release of vmscope. A Kotlin Multiplatform drop-in replacement for `viewModelScope` with a configurable `CoroutineExceptionHandler`.

## Install

```kotlin
dependencies {
    implementation("org.velkos:vmscope:0.1.0")
}
```

## Supported targets

- Android (auto-init via App Startup)
- iOS (arm64, x64 simulator, simulator-arm64) — manual install
- JVM desktop/server — manual install

## Features

- `vmScope` extension on `ViewModel` with configurable exception handling
- `UnhandledViewModelException` wrapping for clear crash-dashboard signal
- `VmScopeConfig.Provider` interface for auto-discovered Android configuration
- Four Android lint rules (one Fatal, one Error, two Warnings) with quick fixes
- Jetpack App Startup auto-initializer with opt-out support
- Sample apps for Android-only and KMP consumer patterns

## Documentation

See the [README](https://github.com/VelkosX/vmscope/blob/main/README.md).

## Known limitations

- Lint rules are Android-only (non-Android consumers have no equivalent build-time checks)
- JVM desktop requires consumers to install a `Dispatchers.Main` if they need main-dispatched coroutines
- iOS `isDebuggable()` reflects the Kotlin framework's build configuration, not the host app's
