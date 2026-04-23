# sample-kmp

Full KMP reference consumer for `vmscope-core`. One shared ViewModel in `commonMain`, two app-side consumers — an Android app (`:sample-kmp:android-app`) and a SwiftUI iOS app (`sample-kmp/ios-app/`) — demonstrating the same library API driving both platforms from a single Kotlin codebase.

For the pure Android-only integration story (no KMP plugin, no shared code), see [`sample-android/`](../sample-android) instead. This directory is for KMP consumers.

## What this demonstrates

1. A single `SharedSampleViewModel` in `commonMain` consumed by both Android and iOS.
2. Platform-appropriate bootstrap on each side:
   - **Android:** `VmScopeConfig.Provider` on `MyApp : Application` — auto-discovered by vmscope-core's App Startup initializer, zero manual wiring.
   - **iOS:** `initVmScope()` in `iosMain/SampleBootstrap.kt` called from Swift `@main App.init()`. No App Startup equivalent on iOS.
3. Same four buttons on both apps driven by the shared ViewModel:
   - **Successful launch** — `vmScope.launch { delay(500); ... }`.
   - **Throwing launch (vmScope)** — debug framework → terminates via `platformCrash` (Android: uncaught-exception handler; iOS: POSIX `abort()`); release framework → `onUnhandledException` callback.
   - **Deliberate viewModelScope (lint demo)** — `viewModelScope.launch { ... }` without throwing. On Android, the `UseVmScope` lint rule fires on this call site with a quick-fix. On iOS, lint isn't loaded (ships inside the Android AAR only) so it's a no-op log line.
   - **Throw on raw viewModelScope** — `viewModelScope.launch { throw ... }`. Side-by-side comparison with the vmScope throwing button:
     - Android → `Thread.getDefaultUncaughtExceptionHandler` → app terminates (crash).
     - iOS → kotlinx-coroutines prints "Uncaught Kotlin exception" and the app keeps running. Silent failure — what vmScope exists to replace.

## Directory layout

```
sample-kmp/
├── README.md                              # this file
├── shared/                                # KMP library: androidTarget + ios(Sim)
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/org/velkos/vmscope/sample/
│       │   ├── SampleLog.kt                   # observable log hook (platform sinks wire it)
│       │   └── SharedSampleViewModel.kt       # the four-button demo ViewModel
│       └── iosMain/kotlin/org/velkos/vmscope/sample/
│           └── SampleBootstrap.kt             # initVmScope() for iOS (@main App.init())
├── android-app/                           # :sample-kmp:android-app — Android consumer
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── kotlin/org/velkos/vmscope/sample/kmp/
│       │   ├── MyApp.kt                       # Application + VmScopeConfig.Provider
│       │   └── MainActivity.kt                # `by viewModels()` → SharedSampleViewModel
│       └── res/layout/activity_main.xml
└── ios-app/                               # Xcode SwiftUI app
    ├── project.yml                            # xcodegen spec
    ├── sample-ios.xcodeproj/                  # committed — `open` to run
    └── sample-ios/
        ├── sample_iosApp.swift                # @main App — calls SampleBootstrapKt.doInitVmScope()
        └── ContentView.swift                  # four buttons + log view
```

## Scope + limitations

- **iOS sim-only.** `shared/` declares `iosSimulatorArm64` + `iosX64`, not `iosArm64`. Device runs need code signing + a universal XCFramework; both out of scope.
- **Not in CI.** `:sample-kmp:*` is included only on macOS hosts (see `settings.gradle.kts`) because `shared` has iOS targets. CI is Linux-only today; running the full KMP sample pipeline requires a macOS runner.
- **No CocoaPods, no SPM.** XCFramework drag-and-drop is simpler for a one-off sample. Real consumers are free to adopt either.

## Build and run — Android

```bash
./gradlew :sample-kmp:android-app:installDebug
adb shell am start -n org.velkos.vmscope.sample.kmp/.MainActivity
```

Or open the project in Android Studio, select the `sample-kmp.android-app` run configuration, and ⌃R. Logcat filter `vmScopeSample` shows the per-button output.

## Build and run — iOS

### 1. Build the XCFramework

```bash
./gradlew :sample-kmp:shared:assembleSampleSharedDebugXCFramework
```

Output: `sample-kmp/shared/build/XCFrameworks/debug/SampleShared.xcframework`

The project.yml points at the **debug** xcframework because that's what makes the **Throwing launch (vmScope)** button terminate via `abort()` — the intended debug demo behavior. For the release-mode `onUnhandledException` callback path, build with `assembleSampleSharedReleaseXCFramework` and edit `project.yml` to point at `release/` instead, then re-run `xcodegen`.

### 2. Open in Xcode

```bash
open sample-kmp/ios-app/sample-ios.xcodeproj
```

### 3. Run

- Pick an iPhone Simulator destination.
- `⌘R`.

## Expected output

Both apps launch, emit `vmScope installed` (iOS) or `Debug build: true` (Android), then on button taps:

| Button | Android (debug APK) | iOS (debug xcframework) |
|---|---|---|
| Successful launch | completes | completes |
| Throwing launch (vmScope) | process crashes (uncaught-exception handler) | process aborts (SIGABRT) |
| Deliberate viewModelScope (lint demo) | logs line; `UseVmScope` lint rule fires on source in Android Studio | logs line; lint N/A on iOS |
| Throw on raw viewModelScope | process crashes (uncaught-exception handler) | logs "Uncaught Kotlin exception"; **app keeps running** |

The contrast between the last two rows on iOS is the whole point of the library: raw viewModelScope silently swallows exceptions under the debugger; vmScope either terminates loudly in debug or routes to your handler in release.

## Regenerating the Xcode project

`sample-kmp/ios-app/sample-ios.xcodeproj` is generated from `project.yml`. Edit the YAML, not the pbxproj directly (hand-edits will be blown away on the next regeneration). To regenerate:

```bash
brew install xcodegen     # one-time, if you don't already have it
(cd sample-kmp/ios-app && xcodegen)
```
