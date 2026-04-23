package org.velkos.vmscope.sample

import org.velkos.vmscope.UnhandledViewModelException
import org.velkos.vmscope.VmScope
import org.velkos.vmscope.vmScopeConfig

/**
 * iOS bootstrap — the equivalent of implementing `VmScopeConfig.Provider` on an Android
 * Application. Swift calls this from `@main App.init()` at app launch (see `sample_iosApp.swift`).
 *
 * Lives in `iosMain` (not `commonMain`) because the Android path uses App Startup's automatic
 * `VmScopeConfig.Provider` discovery via `VmScopeInitializer` + a `Provider` implementation on
 * the `Application`; there's no manual `install` call on Android. Keeping this function
 * platform-scoped makes that asymmetry explicit and prevents Android consumers from
 * accidentally wiring both paths.
 *
 * The `onUnhandledException` callback fires in release builds only (as determined by
 * `Platform.isDebugBinary`). Debug builds terminate via `platformCrash` → `abort()` so bugs
 * stay visible during development, matching the Android debug-APK behavior.
 *
 * **Swift call-site name.** Kotlin/Native rewrites any top-level function whose name starts
 * with `init` to `do<Original>` in the generated Objective-C / Swift interface, to avoid
 * colliding with Swift's `init` constructor keyword. This rewrite cannot be suppressed on
 * top-level functions (`@ObjCName(..., exact = true)` is only applicable to classes, objects,
 * and interfaces). Swift consumers therefore call this as `SampleBootstrapKt.doInitVmScope()`.
 * Any real iOS consumer writing their own `initVmScope()` hook will hit the same rewrite.
 */
public fun initVmScope() {
    VmScope.install(
        vmScopeConfig {
            onUnhandledException { e: UnhandledViewModelException ->
                // A real app would route to Crashlytics / Sentry / Bugsnag here. The sample
                // surfaces the crash into the UI via the log hook so a human can verify the
                // callback fired and the app kept running.
                SampleLog.emit("Crash routed to handler: ${e.cause?.message}")
            }
        },
    )
    SampleLog.emit("vmScope installed")
}
