@file:OptIn(ExperimentalAtomicApi::class)

package org.velkos.vmscope

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/**
 * iOS smoke test: proves the handler flow reaches the configured callback via the real coroutine
 * machinery with Native actuals wired in. Covers the path an iOS consumer would hit after
 * calling `VmScope.install(config)` from their app bootstrap.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IosSmokeTest {

    @BeforeTest
    fun setUp() {
        VmScope.resetForTesting()
    }

    @AfterTest
    fun tearDown() {
        VmScope.resetForTesting()
    }

    @Test
    fun handler_delivers_wrapped_exception_to_configured_callback_on_ios() = runTest {
        VmScope.setDebuggableForTesting(false)
        val received = AtomicReference<UnhandledViewModelException?>(null)
        VmScope.install(vmScopeConfig { onUnhandledException { e -> received.store(e) } })

        val scope = CoroutineScope(
            UnconfinedTestDispatcher(testScheduler) + SupervisorJob() + VmScope.currentHandler()
        )
        val cause = RuntimeException("ios-smoke")
        scope.launch { throw cause }
        advanceUntilIdle()
        scope.cancel()

        val delivered = received.load()
        assertNotNull(delivered, "callback must be invoked")
        assertSame(cause, delivered.cause, "wrapped exception must carry the original as cause")
    }
}
