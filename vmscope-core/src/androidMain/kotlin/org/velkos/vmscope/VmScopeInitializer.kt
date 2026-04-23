package org.velkos.vmscope

import android.content.Context
import android.util.Log
import androidx.startup.Initializer
import org.velkos.vmscope.internal.AndroidAppContextHolder

/**
 * Jetpack App Startup entry point. Captures the application [Context] for the platform hooks to
 * use (debuggable detection) and installs the configuration declared on the host
 * [android.app.Application] if it implements [VmScopeConfig.Provider].
 *
 * This class is registered in the library's manifest via manifest merging; consumers normally do
 * not reference it directly. To opt out, remove the initializer meta-data in your manifest and
 * call [VmScope.install] manually from `Application.onCreate` — see the README.
 */
public class VmScopeInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        try {
            val app = context.applicationContext
            AndroidAppContextHolder.set(app)
            val config = (app as? VmScopeConfig.Provider)?.vmScopeConfiguration
                ?: VmScopeConfig(onUnhandledException = null, customHandler = null)
            VmScope.install(config)
        } catch (t: Throwable) {
            // The startup initializer must never throw — a crash here is unrelated to the user's
            // actual bug and would be deeply confusing. Fall back to an empty config.
            Log.e(TAG, "vmScope initialization failed; installing empty config", t)
            VmScope.install(VmScopeConfig(onUnhandledException = null, customHandler = null))
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()

    private companion object {
        private const val TAG = "vmScope"
    }
}
