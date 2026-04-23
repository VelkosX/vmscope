package org.velkos.vmscope

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.coroutines.ContinuationInterceptor

@OptIn(ExperimentalCoroutinesApi::class)
class VmScopeSemanticsTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    @Before
    fun setUp() {
        VmScope.resetForTesting()
    }

    @After
    fun tearDown() {
        VmScope.resetForTesting()
    }

    // Test 1: same instance on repeated access.
    // Tests 1–5 (scope identity, SupervisorJob, Main.immediate, child failure) are in
    // VmScopeCommonSemanticsTest in commonTest; they run on every target automatically,
    // including this one. The tests below are Android-unit-test-only because they rely on
    // JVM reflection to call the module-mangled internal `ViewModel.clear()`, which is not
    // reachable from Kotlin/Native.

    // Test 6: ViewModel cleared → job cancelled.
    @Test
    fun `clearing ViewModel cancels the scope job`() = runTest {
        val vm = TestViewModel()
        val scope = vm.vmScope
        assertTrue(scope.isActive)
        vm.forceClear()
        assertFalse(scope.isActive)
    }

    // Test 7: after clear, vmScope.isActive is false.
    @Test
    fun `vmScope isActive is false after clear`() {
        val vm = TestViewModel()
        val scope = vm.vmScope
        vm.forceClear()
        assertFalse(scope.isActive)
    }

    // Test 8: repeated clear is idempotent.
    @Test
    fun `repeated clear is idempotent`() {
        val vm = TestViewModel()
        vm.vmScope  // force creation
        vm.forceClear()
        // Second clear should not crash.
        vm.forceClear()
    }

    // Test 9: accessing vmScope after clear returns a cancelled scope.
    @Test
    fun `accessing vmScope after clear returns a cancelled scope`() {
        val vm = TestViewModel()
        vm.vmScope  // initial access
        vm.forceClear()
        val postClearScope = vm.vmScope
        assertFalse(
            "Scope accessed after clear should be cancelled",
            postClearScope.isActive,
        )
    }

    // Also ensure a long-running child is cancelled on clear (belt + suspenders for test 6).
    @Test
    fun `in-flight children are cancelled on clear`() = runTest {
        val vm = TestViewModel()
        val child: Job = vm.vmScope.launch { awaitCancellation() }
        vm.forceClear()
        assertTrue(child.isCancelled)
    }
}
