package org.velkos.vmscope.sample

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import android.widget.Toast
import org.velkos.vmscope.UnhandledViewModelException
import org.velkos.vmscope.VmScopeConfig
import org.velkos.vmscope.vmScopeConfig

class MyApp :
    Application(),
    VmScopeConfig.Provider {
    override val vmScopeConfiguration: VmScopeConfig by lazy {
        vmScopeConfig {
            // Debug builds never reach this callback — they always crash so bugs are visible
            // during development. In release builds we log and show a toast; a real app would
            // route to Crashlytics / Sentry / Bugsnag here.
            onUnhandledException { e: UnhandledViewModelException ->
                Log.e("vmScopeSample", "Uncaught ViewModel exception", e)
                val msg = "Crash routed to handler: ${e.cause?.message}"
                Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i("vmScopeSample", "Debug build: ${(applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0}")
    }
}
