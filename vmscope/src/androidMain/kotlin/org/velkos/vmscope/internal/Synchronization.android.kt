package org.velkos.vmscope.internal

internal actual class SynchronizedLock

internal actual inline fun <T> synchronize(lock: SynchronizedLock, block: () -> T): T =
    kotlin.synchronized(lock, block)
