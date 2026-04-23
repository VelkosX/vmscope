package org.velkos.vmscope.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class UseVmScopeDetectorTest {

    private val issue = UseVmScopeDetector.ISSUE

    // Test 16: fires on viewModelScope.launch { } in a ViewModel subclass.
    @Test
    fun `fires on viewModelScope in a ViewModel`() {
        lint()
            .allowMissingSdk()
            .files(
                *VmScopeLintStubs.all,
                kotlin(
                    """
                    package app

                    import androidx.lifecycle.ViewModel
                    import androidx.lifecycle.viewModelScope
                    import kotlinx.coroutines.launch

                    class MyVM : ViewModel() {
                        fun doWork() {
                            viewModelScope.launch { }
                        }
                    }
                    """
                ).indented(),
            )
            .issues(issue)
            .run()
            .expectWarningCount(1)
    }

    // Test 17: fires on viewModelScope passed as an argument.
    @Test
    fun `fires when viewModelScope is passed as argument`() {
        lint()
            .allowMissingSdk()
            .files(
                *VmScopeLintStubs.all,
                kotlin(
                    """
                    package app

                    import androidx.lifecycle.ViewModel
                    import androidx.lifecycle.viewModelScope
                    import kotlinx.coroutines.CoroutineScope

                    fun takeScope(scope: CoroutineScope) {}

                    class MyVM : ViewModel() {
                        fun doWork() {
                            takeScope(viewModelScope)
                        }
                    }
                    """
                ).indented(),
            )
            .issues(issue)
            .run()
            .expectWarningCount(1)
    }

    // Test 18: does NOT fire on user-defined `viewModelScope` in a non-ViewModel class.
    @Test
    fun `does not fire on user-defined property of the same name`() {
        lint()
            .allowMissingSdk()
            .files(
                *VmScopeLintStubs.all,
                kotlin(
                    """
                    package app

                    import kotlinx.coroutines.CoroutineScope
                    import kotlinx.coroutines.MainScope

                    class NotAViewModel {
                        val viewModelScope: CoroutineScope = MainScope()

                        fun doWork() {
                            val s = viewModelScope
                        }
                    }
                    """
                ).indented(),
            )
            .issues(issue)
            .run()
            .expectClean()
    }

    // Test 19: does NOT fire on vmScope.
    @Test
    fun `does not fire on vmScope`() {
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
                            vmScope.launch { }
                        }
                    }
                    """
                ).indented(),
            )
            .issues(issue)
            .run()
            .expectClean()
    }

    // Test 20: quick fix replaces the reference, adds the new import, and removes the old import.
    @Test
    fun `quick fix replaces reference, adds new import, removes old import`() {
        lint()
            .allowMissingSdk()
            .files(
                *VmScopeLintStubs.all,
                kotlin(
                    """
                    package app

                    import androidx.lifecycle.ViewModel
                    import androidx.lifecycle.viewModelScope
                    import kotlinx.coroutines.launch

                    class MyVM : ViewModel() {
                        fun doWork() {
                            viewModelScope.launch { }
                        }
                    }
                    """
                ).indented(),
            )
            .issues(issue)
            .run()
            .expectFixDiffs(
                """
                Fix for src/app/MyVM.kt line 9: Replace with vmScope:
                @@ -4 +4
                - import androidx.lifecycle.viewModelScope
                + import org.velkos.vmscope.vmScope
                @@ -9 +9
                -         viewModelScope.launch { }
                +         vmScope.launch { }
                """.trimIndent()
            )
    }

    // Test 21: when the file has multiple viewModelScope references, applying the fix to any one
    // of them MUST leave the import alone so the remaining references still resolve. Once the
    // user fixes the last reference, the import goes away on that final fix.
    @Test
    fun `quick fix keeps import when other references remain`() {
        lint()
            .allowMissingSdk()
            .files(
                *VmScopeLintStubs.all,
                kotlin(
                    """
                    package app

                    import androidx.lifecycle.ViewModel
                    import androidx.lifecycle.viewModelScope
                    import kotlinx.coroutines.launch

                    class MyVM : ViewModel() {
                        fun a() { viewModelScope.launch { } }
                        fun b() { viewModelScope.launch { } }
                    }
                    """
                ).indented(),
            )
            .issues(issue)
            .run()
            .expectFixDiffs(
                """
                Fix for src/app/MyVM.kt line 8: Replace with vmScope:
                @@ -4 +4
                + import org.velkos.vmscope.vmScope
                @@ -8 +9
                -     fun a() { viewModelScope.launch { } }
                +     fun a() { vmScope.launch { } }
                Fix for src/app/MyVM.kt line 9: Replace with vmScope:
                @@ -4 +4
                + import org.velkos.vmscope.vmScope
                @@ -9 +10
                -     fun b() { viewModelScope.launch { } }
                +     fun b() { vmScope.launch { } }
                """.trimIndent()
            )
    }
}
