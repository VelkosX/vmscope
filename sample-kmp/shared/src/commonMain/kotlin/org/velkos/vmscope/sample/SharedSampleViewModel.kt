package org.velkos.vmscope.sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.velkos.vmscope.vmScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Shared ViewModel consumed by both the Android app (`sample-kmp:android-app`) and the iOS
 * app (`sample-kmp/ios-app/`). Lives in `commonMain` — the same byte-for-byte code runs on both
 * platforms via the KMP ViewModel (`org.jetbrains.androidx.lifecycle:lifecycle-viewmodel`).
 *
 * Four entry points mirroring the UI buttons on both targets:
 *
 * - **Successful launch** — happy-path `vmScope.launch`. Verifies the extension getter, scope
 *   creation, `Dispatchers.Main.immediate` availability, and coroutine completion.
 *
 * - **Throwing launch (vmScope)** — throws from inside `vmScope.launch`. Debug → `VmScope.deliver`
 *   falls into the debug-crash branch and terminates the process via `platformCrash` (Android:
 *   `Thread.getDefaultUncaughtExceptionHandler`; iOS: POSIX `abort()`). Release → routed to the
 *   configured `onUnhandledException` callback.
 *
 * - **Deliberate viewModelScope (Android lint demo; informational on iOS)** — launches on plain
 *   `viewModelScope` without throwing. On Android the `UseVmScope` lint rule fires with a
 *   quick-fix to replace `viewModelScope` with `vmScope`. On iOS the rule isn't loaded (lint
 *   ships inside the Android AAR only) so the button is informational.
 *
 * - **Throw on raw viewModelScope** — throws from plain `viewModelScope.launch`. Shows default
 *   platform coroutine-exception behavior for side-by-side comparison with the vmScope throwing
 *   button:
 *     - Android → `Thread.getDefaultUncaughtExceptionHandler` → process terminates.
 *     - iOS → kotlinx-coroutines prints "Uncaught Kotlin exception" and returns; app keeps
 *       running (the silent-failure mode vmScope exists to replace).
 *
 * Observers consume state transitions via [SampleLog.onLine]. Android wires it to `Log.i` and
 * the error path surfaces a `Toast`; iOS wires it to a `@Published` string array in a SwiftUI
 * `ObservableObject`.
 */
public class SharedSampleViewModel : ViewModel() {
    /** Happy path — `vmScope.launch` with a normally-completing suspend body. */
    public fun launchSuccessful() {
        vmScope.launch {
            SampleLog.emit("successful launch starting")
            delay(500)
            SampleLog.emit("successful launch completed")
        }
    }

    /**
     * Throws from inside `vmScope.launch`. Debug → process terminates via `platformCrash`.
     * Release → routed through the configured `onUnhandledException` callback.
     */
    public fun launchThrowing() {
        vmScope.launch {
            SampleLog.emit("throwing launch starting")
            throw IllegalStateException("demo-throw")
        }
    }

    /**
     * Uses `viewModelScope` without throwing — on Android the `UseVmScope` lint rule fires here
     * with a quick-fix to replace `viewModelScope` with `vmScope`. On iOS the rule isn't loaded
     * so this just emits a log line.
     */
    @Suppress("UseVmScope")
    public fun launchOnViewModelScopeEquivalent() {
        viewModelScope.launch {
            SampleLog.emit("launched on viewModelScope (would trigger UseVmScope lint on Android)")
        }
    }

    /**
     * Throws from inside a raw `viewModelScope.launch` — no vmScope handler in the context.
     * Android → terminates the process. iOS → logs and keeps running. Side-by-side comparison
     * with [launchThrowing].
     */
    @Suppress("UseVmScope")
    public fun launchThrowingOnViewModelScope() {
        viewModelScope.launch {
            SampleLog.emit("viewModelScope throwing launch starting (no vmScope handler)")
            throw IllegalStateException("demo-throw-viewModelScope")
        }
    }
}
