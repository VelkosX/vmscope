package org.velkos.vmscope

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/**
 * JVM smoke test: proves the handler flow reaches the configured callback via the real coroutine
 * machinery with JVM actuals wired in. The full `vmScope` + ViewModel integration is verified by
 * [VmScopeCommonSemanticsTest], which runs on this target automatically; this test specifically
 * exercises the handler-delivery path against the JVM `platformCrash` / `platformLogError`
 * actuals rather than going through the extension.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JvmSmokeTest {

    private lateinit var previousUncaught: Thread.UncaughtExceptionHandler

    @BeforeTest
    fun setUp() {
        VmScope.resetForTesting()
        previousUncaught = Thread.getDefaultUncaughtExceptionHandler() ?: Thread.UncaughtExceptionHandler { _, _ -> }
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> /* swallow for tests */ }
    }

    @AfterTest
    fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(previousUncaught)
        VmScope.resetForTesting()
    }

    @Test
    fun `handler delivers wrapped exception to configured callback on JVM`() = runTest {
        VmScope.setDebuggableForTesting(false)
        val received = AtomicReference<UnhandledViewModelException?>(null)
        VmScope.install(vmScopeConfig { onUnhandledException { e -> received.set(e) } })

        val scope = CoroutineScope(
            UnconfinedTestDispatcher(testScheduler) + SupervisorJob() + VmScope.currentHandler()
        )
        val cause = IOException("jvm-smoke")
        scope.launch { throw cause }
        advanceUntilIdle()
        scope.cancel()

        val delivered = received.get()
        assertNotNull(delivered, "callback must be invoked")
        assertSame(cause, delivered.cause, "wrapped exception must carry the original as cause")
    }
}
