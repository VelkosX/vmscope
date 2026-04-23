package org.velkos.vmscope.internal

import java.lang.management.ManagementFactory

/**
 * Pure-JVM actuals. No Android dependencies. Suitable for desktop Kotlin (Compose Desktop) and
 * server-side Kotlin.
 *
 * Debuggable detection: opt-in via the `vmscope.debuggable` system property, or auto-detects a
 * debugger attached via JDWP. Defaults to `false` when neither signal is present, which is the
 * safest choice for server-side consumers where "crash-by-default" would be inappropriate.
 */

internal actual fun platformIsDebuggable(): Boolean {
    System.getProperty("vmscope.debuggable")?.let { return it.toBoolean() }
    return runCatching {
        ManagementFactory.getRuntimeMXBean().inputArguments.any { it.contains("-agentlib:jdwp") }
    }.getOrDefault(false)
}

internal actual fun platformCrash(throwable: Throwable) {
    val thread = Thread.currentThread()
    val handler = Thread.getDefaultUncaughtExceptionHandler()
    if (handler != null) {
        handler.uncaughtException(thread, throwable)
    } else {
        throw throwable
    }
}

internal actual fun platformLogError(tag: String, message: String, throwable: Throwable) {
    System.err.println("[$tag] $message")
    throwable.printStackTrace(System.err)
}
