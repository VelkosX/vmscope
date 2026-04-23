package org.velkos.vmscope

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
class VmScopeConcurrencyTest {

    @Before
    fun setUp() {
        // Use a real dispatcher substitute for Main so scope construction doesn't hit the
        // uninitialized Android Main looper from background threads.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        VmScope.resetForTesting()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        VmScope.resetForTesting()
    }

    // Test 17: 100 threads concurrently access vmScope — exactly one scope is observable.
    @Test
    fun `concurrent access returns the same scope and leaks no SupervisorJobs`() {
        val threadCount = 100
        val vm = TestViewModel()
        val barrier = CyclicBarrier(threadCount)
        val pool = Executors.newFixedThreadPool(threadCount)
        val observed = AtomicReference(HashSet<CoroutineScope>())

        try {
            val futures = (0 until threadCount).map {
                pool.submit {
                    barrier.await()
                    val scope = vm.vmScope
                    synchronized(observed) {
                        observed.get().add(scope)
                    }
                }
            }
            futures.forEach { it.get(10, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
            pool.awaitTermination(5, TimeUnit.SECONDS)
        }

        val scopes = observed.get()
        assertEquals(
            "Exactly one scope instance must be visible across threads",
            1,
            scopes.size,
        )

        // The scope must be active (no lost scope cancelled the winning scope).
        val scope = scopes.first()
        val job = scope.coroutineContext[Job]
        assertTrue("Winning scope's job must be active", job != null && job.isActive)

        // Clearing the VM cancels the winning scope — and that should not leave any other job
        // still active (i.e. no leaked SupervisorJob from a losing construction).
        vm.forceClear()
        assertFalse("Winning scope must be cancelled after clear", job!!.isActive)
    }
}
