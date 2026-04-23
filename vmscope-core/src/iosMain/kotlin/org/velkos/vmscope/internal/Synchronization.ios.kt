@file:OptIn(ExperimentalAtomicApi::class, ExperimentalForeignApi::class)

package org.velkos.vmscope.internal

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.sched_yield
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Native lock — CAS flag with a bounded spin then yield. Contention in vmScope is expected to
 * be essentially zero (one scope creation per ViewModel during an access race), so the hot path
 * is the first CAS and we never enter the spin loop. If we do, we spin a small number of times
 * to absorb the common case of a preempted holder running on a hotter core, then fall back to
 * `sched_yield` to park the thread until the scheduler reschedules. Prevents the unbounded-CPU
 * failure mode on pathological preemption without pulling in pthread_mutex via cinterop.
 */
internal actual class SynchronizedLock {
    val flag: AtomicInt = AtomicInt(0)
}

private const val SPIN_COUNT: Int = 64

internal actual inline fun <T> synchronize(lock: SynchronizedLock, block: () -> T): T {
    var spins = 0
    while (!lock.flag.compareAndSet(0, 1)) {
        spins++
        if (spins >= SPIN_COUNT) {
            sched_yield()
            spins = 0
        }
    }
    try {
        return block()
    } finally {
        lock.flag.store(0)
    }
}
