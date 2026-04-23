@file:OptIn(ExperimentalAtomicApi::class)

package org.velkos.vmscope.internal

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Holds a reference to the [Context.getApplicationContext] captured at App Startup. Written once
 * from the Android initializer; read by [platformIsDebuggable]. Before init completes (pure JVM
 * unit tests, background processes that bypass Application init) the holder is `null` and
 * debuggable defaults to `false`.
 */
internal object AndroidAppContextHolder {
    private val ref = AtomicReference<Context?>(null)

    fun set(context: Context) {
        ref.store(context.applicationContext)
    }

    fun get(): Context? = ref.load()
}

internal actual fun platformIsDebuggable(): Boolean {
    val ctx = AndroidAppContextHolder.get() ?: return false
    return (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
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
    Log.e(tag, message, throwable)
}
