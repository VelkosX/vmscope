package org.velkos.vmscope

import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * Immutable configuration snapshot controlling how uncaught exceptions thrown inside
 * [vmScope] coroutines are reported.
 *
 * Construct instances via [VmScopeConfig.Builder] or the [vmScopeConfig] DSL. Install via
 * [VmScopeConfig.Provider] on your [android.app.Application] (recommended) or by calling
 * [VmScope.install] manually (advanced).
 *
 * Two mutually non-exclusive hooks are available:
 *
 * - [onUnhandledException] — a release-mode callback. Receives exceptions wrapped in
 *   [UnhandledViewModelException]. Typical use: forward to a crash reporter.
 * - [customHandler] — takes full control. If set, vmScope steps aside entirely: the handler
 *   receives the original throwable (no wrapping), and the release/debug branching rules
 *   described in the library README do not apply.
 */
public class VmScopeConfig internal constructor(
    internal val onUnhandledException: ((UnhandledViewModelException) -> Unit)?,
    internal val customHandler: CoroutineExceptionHandler?,
) {
    /**
     * Contract: expose a [VmScopeConfig] as [vmScopeConfiguration]. Platform behavior diverges:
     *
     * **Android.** Implement on your `android.app.Application`. vmScope's App Startup
     * initializer discovers the implementation at process startup and installs
     * [vmScopeConfiguration] automatically. Mirrors `androidx.work.Configuration.Provider`:
     *
     * ```
     * class MyApp : Application(), VmScopeConfig.Provider {
     *     override val vmScopeConfiguration: VmScopeConfig = vmScopeConfig {
     *         onUnhandledException { e ->
     *             FirebaseCrashlytics.getInstance().recordException(e)
     *         }
     *     }
     * }
     * ```
     *
     * **iOS / JVM / other non-Android targets.** There is no auto-discovery — implementing this
     * interface has no effect on its own. Call [VmScope.install] manually from your app's
     * bootstrap:
     *
     * ```
     * // Kotlin code callable from Swift / your JVM main()
     * fun initVmScope() {
     *     VmScope.install {
     *         onUnhandledException { e -> reportToCrashlytics(e) }
     *     }
     * }
     * ```
     *
     * The interface is still declared in commonMain for API symmetry — Android-auto-init
     * consumers and manual-install consumers can share a `vmScopeConfig { … }` definition
     * even when the install path differs per target.
     */
    public interface Provider {
        public val vmScopeConfiguration: VmScopeConfig
    }

    /**
     * Builder for [VmScopeConfig]. Prefer the [vmScopeConfig] DSL.
     */
    public class Builder {
        private var releaseReporter: ((UnhandledViewModelException) -> Unit)? = null
        private var handler: CoroutineExceptionHandler? = null

        /**
         * Invoked in release builds when a coroutine launched on [vmScope] throws an uncaught
         * exception. The throwable is wrapped in [UnhandledViewModelException] before delivery.
         *
         * Not invoked in debug builds — debug always crashes so bugs are visible during
         * development.
         *
         * If [handler] is also set, this callback is ignored.
         */
        public fun onUnhandledException(block: (UnhandledViewModelException) -> Unit): Builder {
            this.releaseReporter = block
            return this
        }

        /**
         * Replaces vmScope's default handler entirely. If set, vmScope does not wrap throwables
         * and does not branch on debug/release — the handler receives the raw throwable in all
         * build types and is solely responsible for deciding what to do.
         */
        public fun handler(handler: CoroutineExceptionHandler): Builder {
            this.handler = handler
            return this
        }

        public fun build(): VmScopeConfig =
            VmScopeConfig(
                onUnhandledException = releaseReporter,
                customHandler = handler,
            )
    }
}

/**
 * DSL entry point for building a [VmScopeConfig].
 *
 * ```
 * val config = vmScopeConfig {
 *     onUnhandledException { e -> reportToCrashlytics(e) }
 * }
 * ```
 */
public fun vmScopeConfig(block: VmScopeConfig.Builder.() -> Unit): VmScopeConfig = VmScopeConfig.Builder().apply(block).build()
