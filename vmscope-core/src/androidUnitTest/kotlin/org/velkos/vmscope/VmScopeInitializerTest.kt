package org.velkos.vmscope

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** An Application subclass that implements [VmScopeConfig.Provider]. */
private abstract class ProviderApplication : Application(), VmScopeConfig.Provider

@OptIn(ExperimentalCoroutinesApi::class)
class VmScopeInitializerTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    @Before
    fun setUp() {
        VmScope.resetForTesting()
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> /* swallow for tests */ }
    }

    @After
    fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(null)
        VmScope.resetForTesting()
    }

    // Test 18: Initializer with Application implementing VmScopeConfig.Provider installs the config.
    @Test
    fun `initializer installs config from provider Application`() = runTest {
        val expectedConfig = vmScopeConfig { onUnhandledException { } }
        val providerApp = mock(ProviderApplication::class.java)
        `when`(providerApp.applicationContext).thenReturn(providerApp)
        `when`(providerApp.vmScopeConfiguration).thenReturn(expectedConfig)

        VmScopeInitializer().create(providerApp)

        assertSame(expectedConfig, VmScope.currentConfig())
    }

    // Test 19: Initializer with Application NOT implementing provider installs empty config.
    @Test
    fun `initializer installs empty config when Application is not a Provider`() = runTest {
        val nonProviderApp = mock(Application::class.java)
        `when`(nonProviderApp.applicationContext).thenReturn(nonProviderApp)

        VmScopeInitializer().create(nonProviderApp)

        val installed = VmScope.currentConfig()
        assertNotNull("A config should be installed even for non-Provider apps", installed)

        // Empty config → subsequent launch crashes via default uncaught (release, no callback).
        VmScope.setDebuggableForTesting(false)
        val captured = AtomicReference<Throwable?>(null)
        Thread.setDefaultUncaughtExceptionHandler { _, t -> captured.set(t) }

        val vm = TestViewModel()
        vm.vmScope.launch { throw IOException("from-test-19") }
        advanceUntilIdle()

        assertTrue(captured.get() is UnhandledViewModelException)
    }

    // Test 20: Initializer that throws internally does not propagate — falls back to empty config.
    @Test
    fun `initializer swallows internal exceptions`() {
        // Mock an Application whose `vmScopeConfiguration` getter throws.
        val exploding = mock(ProviderApplication::class.java)
        `when`(exploding.applicationContext).thenReturn(exploding)
        `when`(exploding.vmScopeConfiguration).thenThrow(RuntimeException("boom from provider"))

        // Must not throw out of create().
        VmScopeInitializer().create(exploding)

        // A fallback empty config should still have been installed.
        assertNotNull(VmScope.currentConfig())
    }

    // Test 21: install() after initializer overrides the initializer's config.
    @Test
    fun `manual install overrides initializer config`() = runTest {
        VmScope.setDebuggableForTesting(false)
        val initialCallbackFired = AtomicInteger(0)
        val initialConfig = vmScopeConfig {
            onUnhandledException { initialCallbackFired.incrementAndGet() }
        }
        val providerApp = mock(ProviderApplication::class.java)
        `when`(providerApp.applicationContext).thenReturn(providerApp)
        `when`(providerApp.vmScopeConfiguration).thenReturn(initialConfig)

        VmScopeInitializer().create(providerApp)

        // Now manually install a different config. The custom handler is the distinguishing signal.
        val custom = AtomicReference<Throwable?>(null)
        VmScope.install(vmScopeConfig {
            handler(CoroutineExceptionHandler { _, t -> custom.set(t) })
        })

        val vm = TestViewModel()
        val cause = IOException("from-test-21")
        vm.vmScope.launch { throw cause }
        advanceUntilIdle()

        assertSame("Manual install's handler must receive the throwable", cause, custom.get())
        assertTrue(
            "Initializer's callback must not fire after override",
            initialCallbackFired.get() == 0,
        )
    }

    @Test
    fun `initializer with null applicationContext still installs a config`() {
        val ctx = mock(Context::class.java)
        `when`(ctx.applicationContext).thenReturn(null)

        VmScopeInitializer().create(ctx)

        assertNotNull(VmScope.currentConfig())
        // No Application supplied → isDebuggable falls back to false for tests that haven't
        // overridden it.
        assertNull((VmScope.currentConfig()!!).customHandler)
    }
}
