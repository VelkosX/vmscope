package org.velkos.vmscope.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class CancellationExceptionNotRethrownDetectorTest {

    private val issue = CancellationExceptionNotRethrownDetector.ISSUE

    // Test 6: fires on catch (e: CancellationException) { } with empty body.
    @Test
    fun `fires on empty catch body`() {
        lint()
            .allowMissingSdk()
            .files(
                *VmScopeLintStubs.all,
                kotlin(
                    """
                    package app

                    import kotlin.coroutines.cancellation.CancellationException

                    fun foo() {
                        try {
                            println("hi")
                        } catch (e: CancellationException) {
                        }
                    }
                    """
                ).indented(),
            )
            .issues(issue)
            .run()
            .expectErrorCount(1)
    }

    // Test 7: fires on catch (e: CancellationException) { log(e) } without rethrow.
    @Test
    fun `fires when body logs without rethrow`() {
        lint()
            .allowMissingSdk()
            .files(
                *VmScopeLintStubs.all,
                kotlin(
                    """
                    package app

                    import kotlin.coroutines.cancellation.CancellationException

                    fun log(x: Any) {}

                    fun foo() {
                        try {
                            println("hi")
                        } catch (e: CancellationException) {
                            log(e)
                        }
                    }
                    """
                ).indented(),
            )
            .issues(issue)
            .run()
            .expectErrorCount(1)
    }

    // Test 8: does NOT fire when body rethrows.
    @Test
    fun `does not fire when body rethrows`() {
        lint()
            .allowMissingSdk()
            .files(
                *VmScopeLintStubs.all,
                kotlin(
                    """
                    package app

                    import kotlin.coroutines.cancellation.CancellationException

                    fun log(x: Any) {}

                    fun foo() {
                        try {
                            println("hi")
                        } catch (e: CancellationException) {
                            log(e)
                            throw e
                        }
                    }
                    """
                ).indented(),
            )
            .issues(issue)
            .run()
            .expectClean()
    }

    // Test 9: quick fix inserts throw e at the top of the catch body.
    @Test
    fun `quick fix inserts rethrow at top of body`() {
        lint()
            .allowMissingSdk()
            .files(
                *VmScopeLintStubs.all,
                kotlin(
                    """
                    package app

                    import kotlin.coroutines.cancellation.CancellationException

                    fun log(x: Any) {}

                    fun foo() {
                        try {
                            println("hi")
                        } catch (e: CancellationException) {
                            log(e)
                        }
                    }
                    """
                ).indented(),
            )
            .issues(issue)
            .run()
            .expectFixDiffs(
                """
                Fix for src/app/test.kt line 10: Rethrow e:
                @@ -10 +10
                -     } catch (e: CancellationException) {
                +     } catch (e: CancellationException) { throw e;
                """.trimIndent()
            )
    }
}
