package org.velkos.vmscope

/**
 * Wraps any throwable that escapes a coroutine launched on [vmScope] without being caught
 * by application code.
 *
 * The default handler installed by vmScope wraps uncaught throwables in this type so that
 * crash dashboards and unhandled-exception telemetry surface a consistent, searchable signal
 * for ViewModel-originated crashes. [cause] is always non-null and holds the original throwable.
 *
 * If a consumer installs a [CoroutineExceptionHandler][kotlinx.coroutines.CoroutineExceptionHandler]
 * via [VmScopeConfig.Builder.handler], this wrapper is NOT applied — the custom handler receives
 * the original throwable verbatim.
 */
public class UnhandledViewModelException(cause: Throwable) : RuntimeException(cause)
