import SwiftUI
import SampleShared

/// vmScope iOS sample — SwiftUI reference consumer mirroring `sample/` (Android).
///
/// The `init()` below is the iOS equivalent of implementing `VmScopeConfig.Provider` on an
/// Android `Application`. `SampleBootstrapKt.doInitVmScope()` is the shared-Kotlin bootstrap
/// function that calls `VmScope.install { ... }` with the sample's `onUnhandledException`
/// callback — see `SampleBootstrap.kt` in the shared module.
///
/// Naming notes:
/// - Kotlin top-level functions in `SampleBootstrap.kt` are exposed to Swift on a synthetic
///   class named `SampleBootstrapKt` (per Kotlin/Native's Swift interop conventions).
/// - The Kotlin function is named `initVmScope()`, but Kotlin/Native automatically rewrites
///   any top-level function whose name starts with `init` to `do<Original>` in the Swift
///   interface — to avoid colliding with Swift's `init` constructor keyword. So the
///   Swift-facing call is `doInitVmScope()`. This rewrite cannot be suppressed on top-level
///   functions (`@ObjCName(exact = true)` works only for classes/objects/interfaces).
@main
struct SampleIosApp: App {
    init() {
        SampleBootstrapKt.doInitVmScope()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
