package org.velkos.vmscope.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class MissingVmScopeConfigProviderDetectorTest {

    private val issue = MissingVmScopeConfigProviderDetector.ISSUE

    // Test 1: Fires when Application subclass exists but doesn't implement the provider.
    @Test
    fun `fires when Application exists without Provider`() {
        lint()
            .files(
                *VmScopeLintStubs.all,
                kotlin(
                    """
                    package app

                    import android.app.Application

                    class MyApp : Application()
                    """
                ).indented(),
            )
            .allowMissingSdk()
            .issues(issue)
            .run()
            .expectErrorCount(1)
    }

    // Test 2: Does NOT fire when Application subclass exists and implements the provider.
    @Test
    fun `does not fire when Application implements Provider`() {
        lint()
            .files(
                *VmScopeLintStubs.all,
                kotlin(
                    """
                    package app

                    import android.app.Application
                    import org.velkos.vmscope.VmScopeConfig
                    import org.velkos.vmscope.vmScopeConfig

                    class MyApp : Application(), VmScopeConfig.Provider {
                        override val vmScopeConfiguration: VmScopeConfig = vmScopeConfig {}
                    }
                    """
                ).indented(),
            )
            .allowMissingSdk()
            .issues(issue)
            .run()
            .expectClean()
    }

    // Test 3: Does NOT fire when no Application subclass exists (library module).
    @Test
    fun `does not fire when no Application subclass is present`() {
        lint()
            .files(
                *VmScopeLintStubs.all,
                kotlin(
                    """
                    package app

                    class SomeRegularClass

                    fun foo(): Int = 42
                    """
                ).indented(),
            )
            .allowMissingSdk()
            .issues(issue)
            .run()
            .expectClean()
    }

    // Test 4: Fires when Application extends a base class that doesn't implement the provider.
    @Test
    fun `fires when Application transitively extends Application without Provider`() {
        lint()
            .files(
                *VmScopeLintStubs.all,
                kotlin(
                    """
                    package app

                    import android.app.Application

                    abstract class BaseApp : Application()
                    class MyApp : BaseApp()
                    """
                ).indented(),
            )
            .allowMissingSdk()
            .issues(issue)
            .run()
            .expectErrorCount(1)
    }

    // Test 5: Does NOT fire when Application extends a base class that implements the provider.
    @Test
    fun `does not fire when base class implements Provider`() {
        lint()
            .files(
                *VmScopeLintStubs.all,
                kotlin(
                    """
                    package app

                    import android.app.Application
                    import org.velkos.vmscope.VmScopeConfig
                    import org.velkos.vmscope.vmScopeConfig

                    abstract class BaseApp : Application(), VmScopeConfig.Provider {
                        override val vmScopeConfiguration: VmScopeConfig = vmScopeConfig {}
                    }
                    class MyApp : BaseApp()
                    """
                ).indented(),
            )
            .allowMissingSdk()
            .issues(issue)
            .run()
            .expectClean()
    }
}
