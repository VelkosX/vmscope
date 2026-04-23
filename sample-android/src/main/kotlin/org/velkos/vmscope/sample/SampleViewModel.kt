package org.velkos.vmscope.sample

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.velkos.vmscope.vmScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

class SampleViewModel : ViewModel() {
    /** Happy path — completes normally. */
    fun launchSuccessful() {
        vmScope.launch {
            delay(500)
            Log.i(TAG, "successful launch completed")
        }
    }

    /** Throws. In debug → process crash. In release → routed to handler in [MyApp]. */
    fun launchThrowing() {
        vmScope.launch {
            throw IOException("demo-throw")
        }
    }

    /**
     * Deliberately uses `viewModelScope` to demonstrate the `UseVmScope` lint rule firing in the
     * IDE. Click this button to exercise the rule manually. Suppressed here so builds don't fail.
     */
    @Suppress("UseVmScope")
    fun launchOnViewModelScope() {
        viewModelScope.launch {
            Log.i(TAG, "launched on viewModelScope for demo purposes")
        }
    }

    /**
     * Throws from inside a raw `viewModelScope.launch` — no vmScope handler in the context.
     * On Android this goes to `Thread.getDefaultUncaughtExceptionHandler` and terminates the
     * process (identical observable behavior to [launchThrowing] in debug builds, but without
     * `UnhandledViewModelException` wrapping and without going through a configured callback
     * in release). Demonstrates the baseline behavior vmScope is augmenting — on iOS the
     * contrast is starker (raw `viewModelScope` silently swallows the exception there; see
     * `sample-kmp/ios-app` for the side-by-side).
     */
    @Suppress("UseVmScope")
    fun launchThrowingOnViewModelScope() {
        viewModelScope.launch {
            throw IOException("demo-throw-viewModelScope")
        }
    }

    /**
     * Deliberately catches `Throwable` without a cancellation guard to demonstrate the
     * `CatchingThrowableInCoroutine` lint warning. Suppressed for the same reason.
     */
    @Suppress("CatchingThrowableInCoroutine")
    fun catchThrowableDemo() {
        vmScope.launch {
            try {
                delay(100)
            } catch (t: Throwable) {
                Log.w(TAG, "swallowed throwable in demo", t)
            }
        }
    }

    private companion object {
        const val TAG = "SampleViewModel"
    }
}
