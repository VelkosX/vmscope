package org.velkos.vmscope

import androidx.lifecycle.ViewModel

/**
 * Invokes the internal `clear()` function on a [ViewModel]. That function is Kotlin-`internal` on
 * the library side, so it's compiled with a module-name suffix and inaccessible by regular name.
 * Tests reflectively invoke it to simulate the lifecycle clearing the VM.
 *
 * The suffix changed between AndroidX 2.8.x and JetBrains KMP 2.9+:
 * - AndroidX 2.8.x: `clear$lifecycle_viewmodel_release`
 * - JetBrains KMP 2.9+: `clear$lifecycle_viewmodel`
 *
 * We probe both so the helper survives a routine lifecycle-viewmodel bump.
 *
 * JVM-only. On Kotlin/Native we can't reach the mangled symbol; tests that depend on `clear()`
 * are consequently pinned to androidUnitTest.
 */
fun ViewModel.forceClear() {
    val method = ViewModel::class.java.declaredMethods
        .firstOrNull { it.name.startsWith("clear\$lifecycle_viewmodel") && it.parameterCount == 0 }
        ?: error(
            "Could not find ViewModel.clear() mangled method — lifecycle-viewmodel internal-name " +
                "convention may have changed again."
        )
    method.isAccessible = true
    method.invoke(this)
}
