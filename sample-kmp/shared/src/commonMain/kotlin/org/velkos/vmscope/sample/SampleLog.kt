package org.velkos.vmscope.sample

/**
 * Observable log hook. Consumers (the Android app's `MyApp.onCreate` and the iOS app's
 * `LogStore.init`) set [onLine] once at startup to receive every log line emitted by the
 * shared code ([SharedSampleViewModel] and, on iOS, the `initVmScope` bootstrap).
 *
 * Lives in `commonMain` so the same [emit] call-sites in [SharedSampleViewModel] drive both
 * platforms without an `expect/actual` indirection — each platform's app owns the sink and
 * translates log lines into its native surface (`Log.i` + `Toast` on Android; `@Published`
 * mutation on iOS).
 *
 * Exposed as a Kotlin `object` — accessed from Swift as `SampleLog.shared`, from Android as
 * just `SampleLog`.
 */
public object SampleLog {
    public var onLine: ((String) -> Unit)? = null

    public fun emit(line: String) {
        // Also print to stdout/stderr — routes to Logcat on Android and the Xcode console on
        // iOS. Guarantees visibility even if a consumer forgets to set `onLine`.
        println("[vmScopeSample] $line")
        onLine?.invoke(line)
    }
}
