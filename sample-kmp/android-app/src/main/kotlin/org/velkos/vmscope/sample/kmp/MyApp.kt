package org.velkos.vmscope.sample.kmp

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import android.widget.Toast
import org.velkos.vmscope.UnhandledViewModelException
import org.velkos.vmscope.VmScopeConfig
import org.velkos.vmscope.sample.SampleLog
import org.velkos.vmscope.vmScopeConfig

/**
 * Android-side bootstrap for the KMP sample. Implements `VmScopeConfig.Provider` so vmscope's
 * App Startup initializer auto-discovers the configuration at process launch — no manual
 * `VmScope.install(...)` call is needed. This is the exact integration pattern an Android
 * consumer of a KMP app would use in production.
 *
 * Contrast with the iOS side: `sample-kmp/shared/src/iosMain/.../SampleBootstrap.kt` defines
 * `initVmScope()` which Swift calls manually from `@main App.init()`. Auto-init is not available
 * on iOS — no `Application` concept, no App Startup equivalent.
 *
 * `SampleLog.onLine` is wired in [onCreate] so log lines emitted by the shared
 * [org.velkos.vmscope.sample.SharedSampleViewModel] land in Logcat. The `onUnhandledException`
 * callback additionally shows a `Toast` so the handler firing is visible in the UI on release
 * builds (debug builds never reach the callback — they terminate via `platformCrash`).
 */
class MyApp :
    Application(),
    VmScopeConfig.Provider {
    override val vmScopeConfiguration: VmScopeConfig by lazy {
        vmScopeConfig {
            onUnhandledException { e: UnhandledViewModelException ->
                Log.e("vmScopeSample", "Uncaught ViewModel exception", e)
                val msg = "Crash routed to handler: ${e.cause?.message}"
                Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()
                SampleLog.emit(msg)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Route shared SampleLog emissions to Logcat so the shared ViewModel's log lines are
        // visible during development. The iOS side wires `SampleLog.onLine` to a @Published
        // SwiftUI state instead — same hook, platform-appropriate sink.
        SampleLog.onLine = { line ->
            Log.i("vmScopeSample", line)
        }
        Log.i("vmScopeSample", "Debug build: ${(applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0}")
    }
}
