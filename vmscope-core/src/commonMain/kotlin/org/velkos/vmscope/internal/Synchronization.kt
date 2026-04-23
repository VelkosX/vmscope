package org.velkos.vmscope.internal

/**
 * Multiplatform equivalent of Kotlin's JVM-only `synchronized(lock) { … }`. Kept in an
 * internal utility file because `kotlin.concurrent.atomics` (stdlib, 2.1+) provides atomic
 * references across targets but does not yet expose a multiplatform blocking lock. When that
 * ships in stdlib, this whole file can be deleted and call sites can import the stdlib version.
 */
internal expect class SynchronizedLock()

internal expect inline fun <T> synchronize(lock: SynchronizedLock, block: () -> T): T
