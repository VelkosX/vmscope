@file:OptIn(ExperimentalForeignApi::class)

package org.velkos.vmscope.internal

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.abort
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

/**
 * iOS (arm64, x64 sim, simulator-arm64) actuals.
 *
 * Debuggable detection uses [Platform.isDebugBinary], which reflects the build configuration the
 * Kotlin/Native framework was compiled with. [platformCrash] prints the throwable and calls the
 * POSIX `abort()` to produce SIGABRT — caught by Xcode's debugger, captured in iOS crash reports,
 * and surfaced to any attached crash reporter (Crashlytics / Sentry / Bugsnag) that hooks Mach
 * exceptions.
 *
 * Why `abort()` rather than a re-throw? [platformCrash] is invoked from inside a
 * [kotlinx.coroutines.CoroutineExceptionHandler]. A throw from there gets caught by coroutines'
 * `handlerException` which wraps it as "Exception while trying to handle coroutine exception"
 * and forwards to `propagateExceptionFinalResort`, which schedules the exception on
 * `Dispatchers.Main` rather than terminating. Net effect: the app keeps running, SwiftUI's
 * runloop re-enters the exception path on every subsequent gesture, and the debugger stops at
 * the same breakpoint repeatedly. `abort()` terminates synchronously, as intended.
 */

@OptIn(ExperimentalNativeApi::class)
internal actual fun platformIsDebuggable(): Boolean = Platform.isDebugBinary

internal actual fun platformCrash(throwable: Throwable) {
    // Emit the trace before aborting so the Xcode console shows what triggered the crash.
    // kotlinx-coroutines' own uncaught-exception path may also print, but this guarantees we
    // surface the cause at a known point regardless of downstream handler behavior.
    println("[vmScope] uncaught exception — aborting:")
    throwable.printStackTrace()
    abort()
}

internal actual fun platformLogError(tag: String, message: String, throwable: Throwable) {
    // Use println (routes to the OS's stderr → captured in Xcode console + os_log) rather than
    // NSLog. The NSLog varargs bridge via cinterop is flaky on Kotlin/Native 2.3.x — we observed
    // reproducible segfaults on iosSimulatorArm64 when passing Kotlin Strings as NSLog args,
    // including with single "%@" placeholders. println is simpler and Just Works.
    println("[$tag] $message: $throwable")
}
