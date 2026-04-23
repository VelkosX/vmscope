/*
 * vmScope is a drop-in replacement for `androidx.lifecycle.viewModelScope` that adds a
 * configurable CoroutineExceptionHandler. The implementation below intentionally mirrors the
 * AndroidX KMP viewModelScope implementation (AOSP, Apache 2.0) verbatim aside from three
 * differences:
 *
 *   1. The storage key (`VM_SCOPE_KEY`) and the scope's `CoroutineContext` include
 *      [VmScope.currentHandler] so uncaught exceptions flow through [VmScopeConfig].
 *   2. `SynchronizedLock` is our tiny in-house expect/actual (see `internal/Synchronization.kt`)
 *      rather than AndroidX's internal `SynchronizedObject`, since that type is not public API.
 *   3. `CloseableCoroutineScope` is the minimal one-constructor form; we don't expose the
 *      `CoroutineScope`-accepting overload that AndroidX keeps for its ViewModel constructor
 *      integration (vmScope doesn't offer a corresponding constructor).
 *
 * Reference: AndroidX lifecycle-viewmodel 2.9.x —
 * https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:lifecycle/lifecycle-viewmodel/src/commonMain/kotlin/androidx/lifecycle/ViewModel.kt
 */
package org.velkos.vmscope

import androidx.lifecycle.ViewModel
import org.velkos.vmscope.internal.SynchronizedLock
import org.velkos.vmscope.internal.synchronize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private const val VM_SCOPE_KEY = "org.velkos.vmscope.VM_SCOPE_KEY"

private val VM_SCOPE_LOCK = SynchronizedLock()

/**
 * A [CoroutineScope] tied to this [ViewModel]'s lifecycle, with an installed
 * [kotlinx.coroutines.CoroutineExceptionHandler] that honors the library's [VmScopeConfig].
 *
 * Semantically equivalent to `androidx.lifecycle.viewModelScope` — same context
 * ([SupervisorJob] + [kotlinx.coroutines.MainCoroutineDispatcher.immediate]), cancelled when the
 * ViewModel is cleared, idempotent on repeated access — EXCEPT that uncaught exceptions are
 * routed through your configured handler instead of silently propagating. See [VmScopeConfig]
 * for the handling rules.
 *
 * Configure on Android by implementing [VmScopeConfig.Provider] on your
 * [android.app.Application]:
 *
 * ```
 * class MyApp : Application(), VmScopeConfig.Provider {
 *     override val vmScopeConfiguration = vmScopeConfig {
 *         onUnhandledException { e -> FirebaseCrashlytics.getInstance().recordException(e) }
 *     }
 * }
 * ```
 *
 * On iOS / JVM desktop / server, there's no `Application` to auto-discover. Call
 * [VmScope.install] once from your app's entry point.
 *
 * If [Dispatchers.Main] is not available on the current platform (e.g. bare JVM server or
 * Kotlin/Native Linux), the scope falls back to [EmptyCoroutineContext] — matching
 * viewModelScope's behavior.
 */
public val ViewModel.vmScope: CoroutineScope
    get() =
        synchronize(VM_SCOPE_LOCK) {
            getCloseable(VM_SCOPE_KEY)
                ?: createVmScope().also { scope ->
                    addCloseable(VM_SCOPE_KEY, scope)
                }
        }

/**
 * Builds the [CloseableCoroutineScope] used by [vmScope]. Mirrors AndroidX's
 * `createViewModelScope` — same context shape, same `Dispatchers.Main.immediate` availability
 * fallback — plus this library's [VmScope.currentHandler] so uncaught exceptions flow through
 * the configured handler.
 */
private fun createVmScope(): CloseableCoroutineScope {
    val dispatcher =
        try {
            Dispatchers.Main.immediate
        } catch (_: NotImplementedError) {
            // Kotlin/Native platforms without a Main dispatcher (e.g. Linux).
            EmptyCoroutineContext
        } catch (_: IllegalStateException) {
            // JVM desktop / server with no UI framework supplying Main.
            EmptyCoroutineContext
        }
    return CloseableCoroutineScope(
        coroutineContext = dispatcher + SupervisorJob() + VmScope.currentHandler(),
    )
}

/**
 * [CoroutineScope] that implements [AutoCloseable] so the [ViewModel] machinery can cancel it
 * when the owner is cleared. Semantics match AndroidX's internal `CloseableCoroutineScope`.
 */
internal class CloseableCoroutineScope(
    override val coroutineContext: CoroutineContext,
) : AutoCloseable, CoroutineScope {
    override fun close() {
        coroutineContext.cancel()
    }
}
