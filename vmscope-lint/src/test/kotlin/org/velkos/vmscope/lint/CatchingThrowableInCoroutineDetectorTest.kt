package org.velkos.vmscope.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class CatchingThrowableInCoroutineDetectorTest {

    private val issue = CatchingThrowableInCoroutineDetector.ISSUE

    // Test 10: fires on catch (t: Throwable) {} inside vmScope.launch { }.
    @Test
    fun `fires inside launch block`() {
        lint()
            .allowMissingSdk()
            .files(
                *VmScopeLintStubs.all,
                kotlin(
                    """
                    package app

                    import androidx.lifecycle.ViewModel
                    import org.velkos.vmscope.vmScope
                    import kotlinx.coroutines.launch

                    class MyVM : ViewModel() {
                        fun doWork() {
                            vmScope.launch {
                                try {
                                    something()
                                } catch (t: Throwable) {
                                    log(t)
                                }
                            }
                        }
                        private suspend fun something() {}
                        private fun log(x: Any) {}
                    }
                    """
                ).indented(),
            )
            .issues(issue)
            .run()
            .expectWarningCount(1)
    }

    // Test 11: fires on catch (e: Exception) inside a suspend function.
    @Test
    fun `fires inside suspend function`() {
        lint()
            .allowMissingSdk()
            .files(
                *VmScopeLintStubs.all,
                kotlin(
                    """
                    package app

                    fun log(x: Any) {}

                    suspend fun workflow() {
                        try {
                            println("work")
                        } catch (e: Exception) {
                            log(e)
                        }
                    }
                    """
                ).indented(),
            )
            .issues(issue)
            .run()
            .expectWarningCount(1)
    }

    // Test 12: fires inside coroutineScope, supervisorScope, flow.
    @Test
    fun `fires inside coroutineScope supervisorScope flow`() {
        lint()
            .allowMissingSdk()
            .files(
                *VmScopeLintStubs.all,
                kotlin(
                    """
                    package app

                    import kotlinx.coroutines.coroutineScope
                    import kotlinx.coroutines.supervisorScope
                    import kotlinx.coroutines.flow

                    fun log(x: Any) {}

                    suspend fun a() = coroutineScope {
                        try { } catch (t: Throwable) { log(t) }
                    }

                    suspend fun b() = supervisorScope {
                        try { } catch (t: Throwable) { log(t) }
                    }

                    fun c() = flow<Int> {
                        try { } catch (t: Throwable) { log(t) }
                    }
                    """
                ).indented(),
            )
            .issues(issue)
            .run()
            .expectWarningCount(3)
    }

    // Test 13: does NOT fire when the body begins with a cancellation guard.
    @Test
    fun `does not fire when cancellation guard is present`() {
        lint()
            .allowMissingSdk()
            .files(
                *VmScopeLintStubs.all,
                kotlin(
                    """
                    package app

                    import androidx.lifecycle.ViewModel
                    import org.velkos.vmscope.vmScope
                    import kotlin.coroutines.cancellation.CancellationException
                    import kotlinx.coroutines.launch

                    class MyVM : ViewModel() {
                        fun doWork() {
                            vmScope.launch {
                                try {
                                    something()
                                } catch (t: Throwable) {
                                    if (t is CancellationException) throw t
                                    log(t)
                                }
                            }
                        }
                        private suspend fun something() {}
                        private fun log(x: Any) {}
                    }
                    """
                ).indented(),
            )
            .issues(issue)
            .run()
            .expectClean()
    }

    // Test 14: does NOT fire outside coroutine context.
    @Test
    fun `does not fire outside coroutine context`() {
        lint()
            .allowMissingSdk()
            .files(
                *VmScopeLintStubs.all,
                kotlin(
                    """
                    package app

                    fun log(x: Any) {}

                    fun plainFunction() {
                        try {
                            println("not a coroutine")
                        } catch (t: Throwable) {
                            log(t)
                        }
                    }
                    """
                ).indented(),
            )
            .issues(issue)
            .run()
            .expectClean()
    }

    // Test 15: quick fix inserts the cancellation guard using the actual parameter name.
    @Test
    fun `quick fix inserts cancellation guard`() {
        lint()
            .allowMissingSdk()
            .files(
                *VmScopeLintStubs.all,
                kotlin(
                    """
                    package app

                    fun log(x: Any) {}

                    suspend fun work() {
                        try {
                            println("work")
                        } catch (err: Throwable) {
                            log(err)
                        }
                    }
                    """
                ).indented(),
            )
            .issues(issue)
            .run()
            .expectFixDiffs(
                """
                Fix for src/app/test.kt line 8: Add CancellationException guard:
                @@ -8 +8
                -     } catch (err: Throwable) {
                +     } catch (err: Throwable) { if (err is kotlin.coroutines.cancellation.CancellationException) throw err;
                """.trimIndent()
            )
    }
}
