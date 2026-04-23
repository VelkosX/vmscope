package org.velkos.vmscope.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest.java
import com.android.tools.lint.checks.infrastructure.LintDetectorTest.kotlin
import com.android.tools.lint.checks.infrastructure.TestFile

/**
 * Source-level stubs for the external types referenced in lint tests. Lint tests run against a
 * synthetic project, so we provide the types we need as source instead of depending on the real
 * artifacts.
 */
object VmScopeLintStubs {

    val application: TestFile = java(
        """
        package android.app;
        public class Application {
            public Application getApplicationContext() { return this; }
        }
        """.trimIndent()
    ).indented()

    val viewModel: TestFile = kotlin(
        """
        package androidx.lifecycle

        open class ViewModel
        """.trimIndent()
    ).indented()

    val viewModelScope: TestFile = kotlin(
        """
        package androidx.lifecycle

        import kotlinx.coroutines.CoroutineScope
        import kotlinx.coroutines.MainScope

        val ViewModel.viewModelScope: CoroutineScope
            get() = MainScope()
        """.trimIndent()
    ).indented()

    val coroutines: TestFile = kotlin(
        """
        package kotlinx.coroutines

        interface CoroutineScope
        fun MainScope(): CoroutineScope = object : CoroutineScope {}
        fun CoroutineScope.launch(block: suspend () -> Unit): Job = Job()
        suspend fun <T> coroutineScope(block: suspend CoroutineScope.() -> T): T = error("stub")
        suspend fun <T> supervisorScope(block: suspend CoroutineScope.() -> T): T = error("stub")
        fun <T> flow(block: suspend FlowCollector<T>.() -> Unit): Flow<T> = error("stub")
        interface Flow<T>
        interface FlowCollector<T> { suspend fun emit(value: T) }
        class Job
        """.trimIndent()
    ).indented()

    val cancellationException: TestFile = kotlin(
        """
        package kotlin.coroutines.cancellation

        open class CancellationException(message: String? = null) : IllegalStateException(message)
        """.trimIndent()
    ).indented()

    val vmScopeConfig: TestFile = kotlin(
        """
        package org.velkos.vmscope

        class VmScopeConfig {
            interface Provider {
                val vmScopeConfiguration: VmScopeConfig
            }
        }

        fun vmScopeConfig(block: () -> Unit): VmScopeConfig = VmScopeConfig()
        """.trimIndent()
    ).indented()

    val vmScope: TestFile = kotlin(
        """
        package org.velkos.vmscope

        import androidx.lifecycle.ViewModel
        import kotlinx.coroutines.CoroutineScope
        import kotlinx.coroutines.MainScope

        val ViewModel.vmScope: CoroutineScope
            get() = MainScope()
        """.trimIndent()
    ).indented()

    val all: Array<TestFile> = arrayOf(
        application,
        viewModel,
        viewModelScope,
        coroutines,
        cancellationException,
        vmScopeConfig,
        vmScope,
    )
}
