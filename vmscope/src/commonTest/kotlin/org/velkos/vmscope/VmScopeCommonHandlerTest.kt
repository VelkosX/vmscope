@file:OptIn(ExperimentalAtomicApi::class)

package org.velkos.vmscope

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Handler decision-tree tests that run on every target. The tests rely on
 * [VmScope.setCrashHandlerForTesting] to intercept what would otherwise be a platform crash
 * (Thread.getDefaultUncaughtExceptionHandler on JVM, process-terminating throw on Native),
 * so the same assertions hold identically across Android / JVM / iOS.
 *
 * Each test spins up a throwaway [CoroutineScope] carrying [VmScope.currentHandler] rather than
 * using [vmScope] — so failures in the handler pipeline show up here directly, not tangled with
 * ViewModel / closeable lifecycle. Scope semantics on top of [vmScope] are covered separately by
 * [VmScopeCommonSemanticsTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VmScopeCommonHandlerTest {

    private lateinit var captured: AtomicReference<Throwable?>
    private val main = MainDispatcherSetup()

    @BeforeTest
    fun setUp() {
        main.install()
        VmScope.resetForTesting()
        captured = AtomicReference(null)
        VmScope.setCrashHandlerForTesting { t -> captured.store(t) }
    }

    @AfterTest
    fun tearDown() {
        VmScope.resetForTesting()
        main.tearDown()
    }

    private fun TestScope(): CoroutineScope {
        @OptIn(ExperimentalCoroutinesApi::class)
        return CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob() + VmScope.currentHandler())
    }

    // Test 10: No config installed → crash path with wrapped exception.
    @Test
    fun no_config_installed_reaches_crash_path() = runTest {
        VmScope.setDebuggableForTesting(false)
        val cause = RuntimeException("no-config")
        TestScope().apply {
            launch { throw cause }
            advanceUntilIdle()
            cancel()
        }
        val delivered = captured.load()
        assertTrue(delivered is UnhandledViewModelException, "wrapped")
        assertSame(cause, delivered.cause)
    }

    // Test 11: Config installed but no callback and no custom handler → still crashes.
    @Test
    fun empty_config_in_release_crashes() = runTest {
        VmScope.setDebuggableForTesting(false)
        VmScope.install(vmScopeConfig {})
        val cause = RuntimeException("empty-config")
        TestScope().apply {
            launch { throw cause }
            advanceUntilIdle()
            cancel()
        }
        val delivered = captured.load()
        assertTrue(delivered is UnhandledViewModelException, "wrapped")
        assertSame(cause, delivered.cause)
    }

    // Test 12: Debug + callback → callback ignored, crashes.
    @Test
    fun debug_mode_ignores_callback_and_crashes() = runTest {
        VmScope.setDebuggableForTesting(true)
        val callbackFired = AtomicInt(0)
        VmScope.install(vmScopeConfig { onUnhandledException { callbackFired.fetchAndAdd(1) } })
        val cause = RuntimeException("debug-crash")
        TestScope().apply {
            launch { throw cause }
            advanceUntilIdle()
            cancel()
        }
        assertEquals(0, callbackFired.load(), "callback must not be invoked in debug")
        val delivered = captured.load()
        assertTrue(delivered is UnhandledViewModelException, "wrapped")
        assertSame(cause, delivered.cause)
    }

    // Test 13: Release + callback → callback receives wrapped.
    @Test
    fun release_with_callback_invokes_callback_with_wrapped_exception() = runTest {
        VmScope.setDebuggableForTesting(false)
        val received = AtomicReference<UnhandledViewModelException?>(null)
        VmScope.install(vmScopeConfig { onUnhandledException { e -> received.store(e) } })
        val cause = RuntimeException("release-callback")
        TestScope().apply {
            launch { throw cause }
            advanceUntilIdle()
            cancel()
        }
        val delivered = received.load()
        assertTrue(delivered != null, "callback must fire")
        assertSame(cause, delivered.cause)
        assertNull(captured.load(), "crash path must NOT fire")
    }

    // Test 14: Custom handler receives raw throwable, no wrapping, no callback.
    @Test
    fun custom_handler_receives_raw_throwable_without_wrapping() = runTest {
        VmScope.setDebuggableForTesting(false)
        val received = AtomicReference<Throwable?>(null)
        val callbackFired = AtomicInt(0)
        val custom = CoroutineExceptionHandler { _, t -> received.store(t) }
        VmScope.install(
            vmScopeConfig {
                handler(custom)
                onUnhandledException { callbackFired.fetchAndAdd(1) }
            }
        )
        val cause = RuntimeException("custom-handler")
        TestScope().apply {
            launch { throw cause }
            advanceUntilIdle()
            cancel()
        }
        assertSame(cause, received.load(), "raw, not wrapped")
        assertEquals(0, callbackFired.load(), "callback must not fire")
        assertNull(captured.load(), "crash path must NOT fire")
    }

    // Test 15: Callback that throws → swallowed, does not crash the crash path.
    @Test
    fun callback_that_throws_does_not_propagate() = runTest {
        VmScope.setDebuggableForTesting(false)
        VmScope.install(
            vmScopeConfig {
                onUnhandledException { throw RuntimeException("reporter is broken") }
            }
        )
        TestScope().apply {
            launch { throw RuntimeException("origin") }
            advanceUntilIdle()
            cancel()
        }
        assertNull(captured.load(), "reporter exception must be swallowed")
    }

    // Test 16: CancellationException is NOT delivered to the handler.
    @Test
    fun cancellation_exception_not_delivered() = runTest {
        VmScope.setDebuggableForTesting(false)
        val fired = AtomicInt(0)
        VmScope.install(vmScopeConfig { onUnhandledException { fired.fetchAndAdd(1) } })
        TestScope().apply {
            launch { throw CancellationException("cancel from inside") }
            advanceUntilIdle()
            cancel()
        }
        assertEquals(0, fired.load(), "CancellationException must not be delivered")
        assertNull(captured.load(), "crash path must NOT fire")
    }

    // Reinstall bonus: new config takes effect for new launches.
    @Test
    fun reinstall_changes_handler_for_subsequent_launches() = runTest {
        VmScope.setDebuggableForTesting(false)
        val first = AtomicInt(0)
        VmScope.install(vmScopeConfig { onUnhandledException { first.fetchAndAdd(1) } })
        TestScope().apply {
            launch { throw RuntimeException("first") }
            advanceUntilIdle()
            cancel()
        }
        assertEquals(1, first.load())

        val second = AtomicInt(0)
        VmScope.install(vmScopeConfig { onUnhandledException { second.fetchAndAdd(1) } })
        TestScope().apply {
            launch { throw RuntimeException("second") }
            advanceUntilIdle()
            cancel()
        }
        assertEquals(1, first.load(), "original callback must not fire again")
        assertEquals(1, second.load(), "new callback must fire")
    }
}
