package org.velkos.vmscope

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Platform-independent scope semantics. Covers the portions of the Android semantics test that
 * don't require invoking `ViewModel.clear()` (which is internal in lifecycle-viewmodel and
 * therefore platform-reflection-specific). Clear-related tests stay in androidUnitTest because
 * Kotlin/Native has no reflection to reach `clear$lifecycle_viewmodel_release`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VmScopeCommonSemanticsTest {

    private val main = MainDispatcherSetup()

    @BeforeTest
    fun setUp() {
        main.install()
        VmScope.resetForTesting()
    }

    @AfterTest
    fun tearDown() {
        VmScope.resetForTesting()
        main.tearDown()
    }

    @Test
    fun vmScope_returns_same_instance_on_repeated_access() {
        val vm = TestViewModel()
        assertSame(vm.vmScope, vm.vmScope)
    }

    @Test
    fun different_ViewModels_get_different_scopes() {
        val vm1 = TestViewModel()
        val vm2 = TestViewModel()
        assertNotSame(vm1.vmScope, vm2.vmScope)
    }

    @Test
    fun vmScope_context_contains_a_SupervisorJob() {
        val vm = TestViewModel()
        val job = vm.vmScope.coroutineContext[Job]
        assertNotNull(job, "Job must be present")
        // SupervisorJob is identifiable by its implementation class name.
        val className = job::class.simpleName ?: ""
        assertTrue(
            className.contains("Supervisor", ignoreCase = true),
            "Expected SupervisorJob, got $className",
        )
    }

    @Test
    fun vmScope_uses_Main_immediate_dispatcher() {
        val vm = TestViewModel()
        val interceptor = vm.vmScope.coroutineContext[ContinuationInterceptor]
        assertSame(Dispatchers.Main.immediate, interceptor)
    }

    @Test
    fun child_failure_does_not_cancel_siblings() = runTest {
        VmScope.setDebuggableForTesting(false)
        VmScope.install(vmScopeConfig { onUnhandledException { /* swallow for this test */ } })

        val vm = TestViewModel()
        val siblingReachedEnd = CompletableDeferred<Unit>()

        vm.vmScope.launch { throw RuntimeException("boom in first child") }
        vm.vmScope.launch { siblingReachedEnd.complete(Unit) }
        advanceUntilIdle()

        assertTrue(siblingReachedEnd.isCompleted, "sibling must complete despite failure")
        assertTrue(vm.vmScope.isActive, "scope must remain active under supervisor semantics")
    }
}
