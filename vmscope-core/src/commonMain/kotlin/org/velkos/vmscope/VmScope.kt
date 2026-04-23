@file:OptIn(ExperimentalAtomicApi::class)

package org.velkos.vmscope

import org.velkos.vmscope.internal.platformCrash
import org.velkos.vmscope.internal.platformIsDebuggable
import org.velkos.vmscope.internal.platformLogError
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Entry point for advanced configuration of [vmScope].
 *
 * Most users should NOT call into this object directly. On Android, prefer implementing
 * [VmScopeConfig.Provider] on your `Application` — vmScope auto-discovers the implementation at
 * startup via Jetpack App Startup.
 *
 * On non-Android targets (iOS, JVM desktop/server), there is no auto-init mechanism. Call
 * [install] once during app bootstrap. The [VmScopeConfig.Provider] interface still exists on
 * those targets for API symmetry, but implementing it has no discovery effect — the Android
 * initializer is the only thing that honors it.
 */
public object VmScope {

    private val installedConfig = AtomicReference<VmScopeConfig?>(null)
    private val debuggableOverride = AtomicReference<Boolean?>(null)
    private val crashOverride = AtomicReference<((Throwable) -> Unit)?>(null)

    /**
     * Install a configuration manually. Re-callable — the most recent call wins.
     *
     * Only new coroutine launches pick up the new configuration; already-running coroutines keep
     * the handler they captured at launch time.
     */
    public fun install(config: VmScopeConfig) {
        installedConfig.store(config)
    }

    /** DSL overload of [install]. */
    public fun install(block: VmScopeConfig.Builder.() -> Unit) {
        install(vmScopeConfig(block))
    }

    internal fun currentConfig(): VmScopeConfig? = installedConfig.load()

    internal fun currentHandler(): CoroutineExceptionHandler = VmScopeExceptionHandler

    internal fun isDebuggable(): Boolean =
        debuggableOverride.load() ?: platformIsDebuggable()

    internal fun deliver(throwable: Throwable) {
        val config = installedConfig.load()

        // 1. Custom handler takes full control — no wrapping, no branching.
        val custom = config?.customHandler
        if (custom != null) {
            try {
                custom.handleException(EmptyCoroutineContext, throwable)
            } catch (t: Throwable) {
                platformLogError(TAG, "vmScope custom handler threw", t)
            }
            return
        }

        val wrapped = UnhandledViewModelException(throwable)

        // 2. Debug → always crash. Callback is ignored in debug.
        if (isDebuggable()) {
            crash(wrapped)
            return
        }

        // 3. Release + callback → invoke callback; swallow any throwable it raises.
        val callback = config?.onUnhandledException
        if (callback != null) {
            try {
                callback(wrapped)
            } catch (t: Throwable) {
                platformLogError(TAG, "vmScope onUnhandledException callback threw", t)
            }
            return
        }

        // 4. Release + no callback → crash. The MissingVmScopeConfigProvider lint rule exists
        // precisely so reaching this path means something went wrong; loud failure is correct.
        crash(wrapped)
    }

    /**
     * Final-step crash, with a test-only override. The override exists for the common-test
     * suite which needs to assert behavior on a target whose [platformCrash] does something
     * irreversible (e.g. terminating the process on iOS). Production code never sets it.
     */
    private fun crash(throwable: Throwable) {
        val override = crashOverride.load()
        if (override != null) {
            override(throwable)
        } else {
            platformCrash(throwable)
        }
    }

    internal fun resetForTesting() {
        installedConfig.store(null)
        debuggableOverride.store(null)
        crashOverride.store(null)
    }

    internal fun setDebuggableForTesting(value: Boolean?) {
        debuggableOverride.store(value)
    }

    internal fun setCrashHandlerForTesting(handler: ((Throwable) -> Unit)?) {
        crashOverride.store(handler)
    }

    private const val TAG = "vmScope"
}

private object VmScopeExceptionHandler :
    AbstractCoroutineContextElement(CoroutineExceptionHandler),
    CoroutineExceptionHandler {
    override fun handleException(context: CoroutineContext, exception: Throwable) {
        VmScope.deliver(exception)
    }
}
