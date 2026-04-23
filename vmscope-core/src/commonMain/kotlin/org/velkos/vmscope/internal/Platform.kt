package org.velkos.vmscope.internal

/**
 * Platform primitives for the vmScope handler decision tree. Kept deliberately tiny: the decision
 * logic (what to do with an uncaught throwable given the current config and debug mode) lives in
 * [org.velkos.vmscope.VmScope] in commonMain; platforms only supply the three
 * primitives below.
 */

/** `true` when running under a debuggable build of the host app; `false` otherwise. */
internal expect fun platformIsDebuggable(): Boolean

/**
 * Crash the current thread / process by forwarding [throwable] to the platform's default
 * uncaught-exception handler. Must not return normally — if the platform can't terminate,
 * re-throw.
 */
internal expect fun platformCrash(throwable: Throwable)

/** Best-effort error log. Never throws. */
internal expect fun platformLogError(tag: String, message: String, throwable: Throwable)
