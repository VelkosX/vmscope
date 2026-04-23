# vmscope Migration Guide (for AI coding agents)

**For humans:** This file instructs an AI coding agent (Claude Code, Codex, Cursor, Aider) to migrate your codebase to the `vmscope` library. Works for Android-only and Kotlin Multiplatform projects.

**How to use it:**

1. Add the vmscope dependency to your project (see the library README).
2. Sync Gradle. Android modules will show lint findings in the IDE.
3. On each Android app/library module, run `./gradlew updateLintBaseline` and commit the generated `lint-baseline.xml`. This prevents CI from going red mid-migration.
4. Save this file as `VMSCOPE_MIGRATION.md` in your repo root.
5. Invoke your agent. Examples:
   - Claude Code: `claude "follow the instructions in VMSCOPE_MIGRATION.md"`
   - Codex CLI: `codex "read VMSCOPE_MIGRATION.md and execute the migration"`
   - Cursor: open the file, prompt "execute this migration plan"
6. **Review the diff carefully before merging.** This migration touches exception-handling code. Expect to surface latent bugs that were previously swallowed — this is the library working as intended, not a regression.
7. For KMP projects: the agent will NOT modify Swift source. When the agent finishes, it will give you a snippet to paste into your iOS app's `@main` — you wire that up manually.
8. Delete `VMSCOPE_MIGRATION.md` and (if fully clean) `lint-baseline.xml` after the migration lands.

Everything below the horizontal rule is instructions for the agent.

---

## Agent: you are executing a vmscope migration

Your job is to migrate this codebase to use the `vmscope` library. The library is already added as a dependency. You will complete **seven phases** in strict order. Do not skip phases. Do not combine phases. After each phase, verify the build and report status before proceeding.

### Context you need

`vmscope` provides `vmScope`, a drop-in replacement for `androidx.lifecycle.viewModelScope`. The key difference is that `vmScope` routes uncaught exceptions through a configurable `CoroutineExceptionHandler`, whereas `viewModelScope` has no handler and uncaught exceptions crash the app (on Android) or fail silently (on other platforms).

Four Android lint rules ship with the library:

| Rule ID | Severity | What it flags |
|---|---|---|
| `MissingVmScopeConfigProvider` | Fatal | Android module has an `Application` subclass that doesn't implement `VmScopeConfig.Provider` |
| `CancellationExceptionNotRethrown` | Error | `catch (e: CancellationException)` without rethrow |
| `CatchingThrowableInCoroutine` | Warning | `catch (Throwable)` or `catch (Exception)` inside suspend/coroutine contexts without cancellation guard |
| `UseVmScope` | Warning | `viewModelScope` reference that should be `vmScope` |

**These lint rules run only on Android source sets.** In a KMP project, they do NOT fire on code in `commonMain`, `iosMain`, `jvmMain`, etc. That means for non-Android source sets, lint cannot point you at the findings — you will have to scan the code manually and apply the same discipline. This is documented per phase below.

### General rules

- **Never suppress a lint finding just to make it go away.** Suppression is acceptable only when there is a specific, documented reason (e.g., a framework-imposed Application class that can't implement the Provider interface). If you're tempted to suppress to skip work, you're doing it wrong.
- **Never delete `lint-baseline.xml` during the migration.** The baseline protects CI. Removing it is Phase 7's responsibility, and only if lint is clean.
- **Do not modify Swift source files.** You will generate Swift snippets for iOS wiring in Phase 5 and tell the human to paste them. You will not edit `.swift` files directly, even if you have access to them.
- **Do not run iOS or macOS builds.** Even if Xcode is available, iOS verification is the human's responsibility after they wire up the Swift bootstrap.
- **Preserve behavior unless the migration explicitly requires changing it.** The library changes *where* exceptions are reported, not *what* your code does. If you find yourself rewriting business logic, stop.
- **After each phase, run verification and report findings.** For Android work, that's `./gradlew :<module>:lintDebug` and/or `./gradlew :<module>:compileDebugKotlin`. For common/non-Android work, that's `./gradlew :<module>:compile<Target>Kotlin`.
- **If a step fails or produces unexpected results, stop and report.** Do not work around problems silently. Do not expand scope.

### Placeholders in this document

Before writing any code that references vmscope types, **verify the following against the actual vmscope dependency**:

1. The group ID in build files (e.g., `org.velkos`, `io.github.velkosx`). Grep the project's build files for `vmscope-core`.
2. The package root for import statements. Open one vmscope-core class in the consumer's dependency cache or module metadata and check the package declaration.
3. The names of the library's public symbols — they should be `vmScope`, `VmScopeConfig`, `VmScopeConfig.Provider`, `VmScopeConfig.Builder`, `vmScopeConfig`, `UnhandledViewModelException`, `VmScope.install`. If any symbol has been renamed in the shipped version, use the shipped name and note the divergence in your final report.

Throughout this document, `org.velkos` and `org.velkos` are placeholders. Resolve them from the actual dependency before writing imports. In most code samples here, imports are written as `org.velkos.vmscope.xxx` — substitute the real package.

---

## Phase 0 — Environment detection and verification

Before making any changes, detect what you're working with and verify the starting state.

### Step 0.1 — Detect project shape

Look for these signals:

**Android-only project:**
- `settings.gradle.kts` or `settings.gradle` lists modules that apply `com.android.application` or `com.android.library` plugins.
- No `commonMain` directories anywhere.
- No `org.jetbrains.kotlin.multiplatform` plugin applied in any module.

**KMP project:**
- One or more modules apply `org.jetbrains.kotlin.multiplatform` plugin.
- `commonMain` directories exist.
- A `kotlin { … }` block declares targets like `androidTarget()`, `iosArm64()`, `jvm()`, etc.

**Hybrid (most common KMP setup):**
- A shared module (e.g., `shared/`, `core/`) uses the multiplatform plugin with a `commonMain` source set.
- Separate `android-app`, `androidApp`, or similarly named module uses `com.android.application`.
- Separate iOS app directory (Xcode project) consumes the shared module's framework output.

Report the detected shape before proceeding. Example report:

> Detected project shape: **KMP hybrid**
> - Shared module: `:shared` (applies `org.jetbrains.kotlin.multiplatform`)
> - Android app module: `:androidApp` (applies `com.android.application`)
> - iOS app: Xcode project at `iosApp/`
> - Declared Kotlin targets: `androidTarget`, `iosArm64`, `iosX64`, `iosSimulatorArm64`
> - commonMain contains: 12 .kt files including ViewModels

### Step 0.2 — Verify prerequisites

For every Android app/library module detected:

1. Confirm vmscope is a dependency. Grep for `vmscope-core` in all build files. If absent from any Android consumer module, stop and report — the user hasn't completed the install step.
2. Confirm `lint-baseline.xml` exists in every Android module that ships application code. If absent, stop and report — the user hasn't run `./gradlew updateLintBaseline`. (Android library modules that only export a ViewModel API may not need a baseline; use judgment.)
3. Run `./gradlew build` on the whole project. It must succeed. If it fails, stop and report — do not attempt migration on a broken baseline.

For KMP shared modules:

1. Confirm vmscope is declared in `commonMain.dependencies`. If declared only in `androidMain.dependencies`, the shared code can't call `vmScope`; flag this as a dependency-placement error and stop.
2. Run `./gradlew :<shared-module>:build` to confirm common targets compile.

### Step 0.3 — Inventory findings

For each Android module with vmscope:

```bash
./gradlew :<module>:lintDebug
```

Note the count per rule ID: `MissingVmScopeConfigProvider`, `CancellationExceptionNotRethrown`, `CatchingThrowableInCoroutine`, `UseVmScope`.

For KMP shared modules, do a manual pre-scan of `commonMain` source:

```bash
grep -rn "viewModelScope" <shared>/src/commonMain/kotlin/
grep -rn "catch (.*: CancellationException)" <shared>/src/commonMain/kotlin/
grep -rn "catch (.*: Throwable)" <shared>/src/commonMain/kotlin/
grep -rn "catch (.*: Exception)" <shared>/src/commonMain/kotlin/
```

### Step 0.4 — Report and get confirmation

Report to the user:

- Detected project shape.
- Per Android module: findings count by rule ID.
- Per shared module: counts of grep matches above, flagged as "manual review required."
- vmscope group ID and version in use.
- Path to each `Application` subclass (for Phase 1) — derived from `AndroidManifest.xml` `android:name` attributes.
- If KMP: the planned iOS wiring target file (the Swift `@main` or `AppDelegate`) identified for Phase 5.

Proceed to Phase 1 only after the user confirms — or if running non-interactively, after verifying Step 0.2 prerequisites pass.

---

## Phase 1 — Fix `MissingVmScopeConfigProvider` (Fatal, Android only)

This rule exists only on Android. KMP shared modules have no Application class and no equivalent. Fix this once per Android app module.

### Finding the Application class

From each `AndroidManifest.xml`'s `<application android:name="...">` attribute. If the attribute is absent, the module uses the default `android.app.Application` — you must create a subclass, register it, and implement the provider there. Name it `App` or match the project's existing naming conventions.

### Detecting the crash reporter

Scan the Android app module's dependencies for known crash reporters, in priority order:

1. **Firebase Crashlytics** — `com.google.firebase:firebase-crashlytics` (with or without `-ktx` suffix).
   - Import: `com.google.firebase.crashlytics.FirebaseCrashlytics`
   - Call: `FirebaseCrashlytics.getInstance().recordException(exception)`
2. **Sentry** — `io.sentry:sentry-android` or `io.sentry:sentry`.
   - Import: `io.sentry.Sentry`
   - Call: `Sentry.captureException(exception)`
3. **Bugsnag** — `com.bugsnag:bugsnag-android`.
   - Import: `com.bugsnag.android.Bugsnag`
   - Call: `Bugsnag.notify(exception)`
4. **Other** (Datadog, Embrace, Instabug, Rollbar) — if clearly identifiable, use the library's standard exception-reporting API. Research the specific call if needed.

If **no crash reporter** is detected: insert a `TODO()` that logs via `android.util.Log.e` and report to the user that they need to wire one up. Do not block the migration — an unreported exception is still better than a swallowed one.

If **multiple crash reporters** are detected: ask the user which to use. Do not guess.

### Implementation pattern

```kotlin
// In <package>/App.kt (or the existing Application class)

import android.app.Application
import org.velkos.vmscope.VmScopeConfig
import org.velkos.vmscope.vmScopeConfig
// Plus the crash reporter import (example: Crashlytics):
import com.google.firebase.crashlytics.FirebaseCrashlytics

class App : Application(), VmScopeConfig.Provider {
    override val vmScopeConfiguration: VmScopeConfig = vmScopeConfig {
        onUnhandledException { exception ->
            FirebaseCrashlytics.getInstance().recordException(exception)
        }
    }
}
```

**Verify the exact property name `vmScopeConfiguration` against the shipped library.** If the library exposes the config via a different name (e.g., `provideVmScopeConfiguration()` function, or `val vmscopeConfig` without capital S), use the shipped name. Report any divergence.

### Handling common variants

**Hilt-annotated Application:**
```kotlin
@HiltAndroidApp
class App : Application(), VmScopeConfig.Provider {
    override val vmScopeConfiguration = vmScopeConfig {
        onUnhandledException { exception -> /* ... */ }
    }
}
```

**Existing multi-interface Application:**
```kotlin
class App : Application(), Configuration.Provider, VmScopeConfig.Provider {
    override val workManagerConfiguration = /* existing */
    override val vmScopeConfiguration = vmScopeConfig {
        onUnhandledException { /* ... */ }
    }
}
```

**No Application subclass exists:**
1. Create `App.kt` in the app module's root package (matching `applicationId`).
2. Implement the provider as above.
3. Register in `AndroidManifest.xml`:
   ```xml
   <application
       android:name=".App"
       ... />
   ```

### Verification

After implementing, run `./gradlew :<android-module>:lintDebug`. The `MissingVmScopeConfigProvider` finding should disappear. If it still reports, verify:
- The interface is `VmScopeConfig.Provider`, not `VmScopeConfigProvider` (a top-level type — not what the library ships).
- The property is `val vmScopeConfiguration`, not a function.
- The import resolves.

### Report

- Which crash reporter was detected and used (or `TODO` if none).
- Whether an Application class was created or modified.
- The Fatal finding is resolved.

Proceed to Phase 2 only if the Fatal finding is resolved.

---

## Phase 2 — Fix `CancellationExceptionNotRethrown`

Every instance of this pattern is a bug. The fix is mechanical.

### Where to look

**Android modules:** run `./gradlew :<module>:lintDebug`, grep the report for `CancellationExceptionNotRethrown`, process each finding by file and line.

**KMP commonMain (and any non-Android source set):** lint does not run here. Manually scan:

```bash
grep -rn "catch (.*CancellationException" <shared>/src/commonMain/kotlin/ \
                                          <shared>/src/iosMain/kotlin/ \
                                          <shared>/src/jvmMain/kotlin/
```

For each hit, read the catch block. If it does not rethrow the caught exception (either `throw <param>` directly, or via `cleanupStateSpecificToCancellation(); throw <param>`), it is a finding.

### The fix

```kotlin
// Before (bug)
try { … } catch (e: CancellationException) {
    logger.log("cancelled", e)
}

// After
try { … } catch (e: CancellationException) {
    throw e
}
```

**If the original catch body was doing useful work** (logging, state cleanup), you have two choices depending on intent:

**If the logging was unconditional (runs whether or not cancelled), move to `finally`:**

```kotlin
try { … }
finally {
    logger.log("work ended")
}
// Remove the catch entirely — cancellation will now propagate, logging still happens.
```

**If the logic was specifically "on cancellation, do X," catch and rethrow:**

```kotlin
try { … } catch (e: CancellationException) {
    cleanupStateSpecificToCancellation()
    throw e
}
```

Use judgment per site. If the catch body's purpose is unclear, prefer catch-and-rethrow — it's the minimum-change-that-fixes-the-bug pattern.

### Parameter name

The parameter name varies (`e`, `ex`, `t`, `cancellationException`). Match what's there:

```kotlin
} catch (ex: CancellationException) {
    throw ex   // uses `ex`, not `e`
}
```

### Verification

After each fix:

- Android: `./gradlew :<module>:lintDebug` — finding count decreases by one.
- commonMain: re-run the grep — finding no longer appears (or appears with `throw` in the next line).

Proceed to Phase 3 only when the finding count reaches zero across all source sets.

### Report

- Number of Android findings fixed.
- Number of commonMain / non-Android findings fixed (by manual scan).
- Any catches where logic moved to `finally` — user should review these.

---

## Phase 3 — Fix `CatchingThrowableInCoroutine`

Not mechanical. Examine each case before fixing.

### Where to look

**Android modules:** run lint, process each `CatchingThrowableInCoroutine` finding.

**KMP non-Android source sets:** manual scan. Same grep pattern as Phase 2, but for `Throwable` and `Exception`:

```bash
grep -rn "catch (.*: Throwable)" <shared>/src/commonMain/kotlin/ \
                                 <shared>/src/iosMain/kotlin/ \
                                 <shared>/src/jvmMain/kotlin/
grep -rn "catch (.*: Exception)" <shared>/src/commonMain/kotlin/ \
                                 <shared>/src/iosMain/kotlin/ \
                                 <shared>/src/jvmMain/kotlin/
```

Filter to only hits where the catch is inside a suspend function or a coroutine builder (`launch`, `async`, `flow`, `coroutineScope`, `supervisorScope`, `withContext`, `channelFlow`, `callbackFlow`, `produce`, `runBlocking`).

### Decision tree for each finding

Read the catch body. Classify:

**Category A — catch logs and does nothing else.**

```kotlin
} catch (t: Throwable) {
    log.error("failed", t)
}
```

Fix: insert cancellation guard at top.

```kotlin
} catch (t: Throwable) {
    if (t is CancellationException) throw t
    log.error("failed", t)
}
```

**Category B — catch updates UI state or domain state.**

```kotlin
} catch (t: Throwable) {
    _state.value = State.Error(t.message)
}
```

Fix: insert guard, preserve behavior.

**Category C — catch is empty (silent swallow).**

```kotlin
} catch (t: Throwable) {
    // nothing
}
```

**DO NOT just insert a guard.** A silent swallow of all throwables is almost always wrong. Flag to the user:

> Silent swallow found at `<path:line>`. This catches every throwable and does nothing. Options:
> 1. Remove the try/catch entirely — let the exception propagate to the vmScope handler.
> 2. Add logging and the cancellation guard.
> 3. Keep it, but add `@Suppress("CatchingThrowableInCoroutine")` with a comment explaining why silence is correct.

If running non-interactively, apply the minimum fix (insert guard) and add `// TODO: verify this swallow is intentional` above the catch.

**Category D — catch is narrow in intent but broad in type.**

```kotlin
} catch (t: Throwable) {
    // intended to catch network errors only
    _state.value = State.NetworkError
}
```

Fix: narrow the catch.

```kotlin
} catch (e: IOException) {
    _state.value = State.NetworkError
}
```

If type narrowing isn't obvious, fall back to Category B (insert guard, preserve behavior) and flag for user review.

### What NOT to do

- Don't replace `catch (Throwable)` with `catch (Exception)` to avoid the rule. The rule matches both.
- Don't wrap everything in `runCatching`. Same problem.
- Don't introduce a `launchCatching` helper — the library explicitly does not ship one.

### Halt threshold

If more than **10 Category C findings** (silent swallows) exist across the codebase, halt the migration entirely. That many silent swallows indicates systemic error-handling issues that need human review, not mechanical migration. Report the count and stop.

### Verification

- Android: `./gradlew :<module>:lintDebug` until findings reach zero.
- Non-Android: re-run the grep pattern, verify each remaining hit has a cancellation guard as its first statement.

### Report

- Count per category (A, B, C, D) across all source sets.
- Category C findings — user review required.
- Category D findings where type was narrowed — user should verify the narrowed type is correct.

---

## Phase 4 — Fix `UseVmScope`

Mechanical. Text replacement + import update.

### Where to look

**Android modules:** lint flags every `viewModelScope` reference. Quick-fix applies cleanly.

**KMP commonMain (and any source set where ViewModels live):** lint does not fire here, but the code is wrong anyway. Grep:

```bash
grep -rn "viewModelScope" <shared>/src/commonMain/kotlin/ \
                          <shared>/src/iosMain/kotlin/ \
                          <shared>/src/androidMain/kotlin/ \
                          <shared>/src/jvmMain/kotlin/
```

### The fix

```kotlin
// Before
import androidx.lifecycle.viewModelScope

class ProfileViewModel : ViewModel() {
    fun load() = viewModelScope.launch { … }
}

// After
import org.velkos.vmscope.vmScope

class ProfileViewModel : ViewModel() {
    fun load() = vmScope.launch { … }
}
```

### Rules

- Replace every `viewModelScope` with `vmScope`.
- Remove `import androidx.lifecycle.viewModelScope` if no remaining usages in the file.
- Add the vmscope import if not already present.
- References other than `.launch` are also replaced: `viewModelScope.coroutineContext`, `viewModelScope.async`, etc. `vmScope` has the same shape.
- **Do not touch** `lifecycleScope`, `coroutineScope`, `MainScope`, `GlobalScope`, or any other scope. Only `viewModelScope`.

### Verification

- Android: `./gradlew :<module>:compileDebugKotlin` and `:lintDebug` — both must pass. Unused `viewModelScope` imports will fail the compile if not removed.
- commonMain: `./gradlew :<shared>:compileCommonMainKotlinMetadata` (or equivalent for the shared module's compile task).

### Report

- Number of files modified in each source set.
- Total references replaced.

---

## Phase 5 — iOS wiring (KMP only; skip if Android-only)

**Skip this phase entirely if Phase 0 detected an Android-only project.**

For KMP projects with iOS targets, iOS consumers use the manual `VmScope.install { }` path. They have no auto-init equivalent on iOS. You'll prepare the Kotlin side and give the user a Swift snippet for the iOS side.

### Step 5.1 — Create the Kotlin bootstrap function

Create a new file in the KMP shared module at `<shared>/src/commonMain/kotlin/<package>/platform/VmScopeBootstrap.kt`:

```kotlin
package <consumer-package>.platform

import org.velkos.vmscope.VmScope

fun initVmScope() {
    VmScope.install {
        onUnhandledException { exception ->
            // TODO: Route to your iOS crash reporter.
            // Common options:
            //
            //   Firebase Crashlytics for iOS (via cocoapods):
            //     FIRCrashlytics.crashlytics().record(error: exception.asNSError())
            //   Sentry:
            //     SentrySDK.capture(error: exception.asNSError())
            //
            // Or log to NSLog for server-side ingestion:
            platform.Foundation.NSLog("vmScope uncaught: %@", exception.toString())
        }
    }
}
```

If the user's iOS crash reporter is identifiable (look at iOS app Podfile or Package.swift if accessible), customize the callback. If not, leave the TODO and flag it.

### Step 5.2 — Verify the function compiles

```bash
./gradlew :<shared>:compileKotlinIosArm64
./gradlew :<shared>:compileKotlinIosSimulatorArm64
```

(Adjust for the consumer's declared iOS targets.)

### Step 5.3 — Generate Swift snippet for the user

Do NOT modify Swift source files. Instead, produce the exact snippet the user must paste. Write it to `VMSCOPE_IOS_WIRING.md` in the repo root (will be cleaned up after migration):

````markdown
# iOS Wiring — Add to your `@main` App or AppDelegate

Kotlin/Native exposes `initVmScope` to Swift as `doInitVmScope` (the `init` prefix
collides with Swift's `init` keyword, so Kotlin prefixes it with `do`).

Paste this into your SwiftUI `@main App`:

```swift
import <KMP-framework-name>   // typically `Shared` or your framework's name

@main
struct MyApp: App {
    init() {
        VmScopeBootstrapKt.doInitVmScope()
    }

    var body: some Scene {
        WindowGroup { ContentView() }
    }
}
```

Or for UIKit-based iOS apps, in `AppDelegate.swift`:

```swift
func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: …
) -> Bool {
    VmScopeBootstrapKt.doInitVmScope()
    return true
}
```

**Replace `<KMP-framework-name>` with the actual framework name** — this is the `baseName` you set in the shared module's `kotlin { iosArm64 { binaries.framework { baseName = "…" } } }` block.

After pasting, run the iOS app from Xcode and verify it launches without errors.
Check Xcode's console for any `"vmScope uncaught"` messages from your test.
````

### Step 5.4 — Detect framework name if possible

Open the shared module's `build.gradle.kts`. Look for:

```kotlin
listOf(iosArm64(), iosX64(), iosSimulatorArm64()).forEach {
    it.binaries.framework {
        baseName = "Shared"  // <-- this name
    }
}
```

If detectable, substitute the real name into the Swift snippet. If not, leave the placeholder and tell the user to fill it in.

### Report

- `VmScopeBootstrap.kt` created at `<path>`.
- `VMSCOPE_IOS_WIRING.md` generated with Swift snippet.
- Framework name used: `<detected>` or `<placeholder>`.
- **User action required:** paste the snippet into their iOS app's bootstrap.

Do not proceed to Phase 6 if the user hasn't confirmed they've pasted the snippet — but you can proceed if running non-interactively; flag the incomplete state in the final summary.

---

## Phase 6 — Final verification

### Android verification

```bash
./gradlew clean build
./gradlew :<android-module>:lintDebug
./gradlew test
```

All three must pass.

### KMP verification (if applicable)

```bash
./gradlew :<shared>:compileCommonMainKotlinMetadata
./gradlew :<shared>:compileKotlinJvm        # if JVM target declared
./gradlew :<shared>:compileKotlinIosArm64   # if iOS target declared
./gradlew :<shared>:allTests
```

Do NOT run iOS host-app builds. That's the human's responsibility after pasting the Swift snippet.

### Possible outcomes

**Outcome A — all green, no findings.** Proceed to Phase 7.

**Outcome B — lint reports findings that are in the baseline.** Pre-existing, unrelated to vmscope. The migration is complete for vmscope purposes. Do NOT proceed to Phase 7; leave `lint-baseline.xml` in place.

**Outcome C — lint reports new vmscope findings.** Something went wrong. Report each remaining finding, ask the user for guidance. Do not auto-fix at this stage.

**Outcome D — tests fail.** Likely cause: a catch block that was silently swallowing a real exception now propagates it, and a test was depending on the swallow. Report failing tests. **Do not modify tests to pass.** The failure is likely surfacing a latent bug.

**Outcome E — compile fails on a non-Android target.** Rare. Usually means an import or API reference in commonMain needs adjustment for a non-Android target's ViewModel setup. Report the compile error verbatim and stop.

### Report

- Android build / lint / test status.
- KMP compile + test status per target.
- Outcome classification (A–E).
- If outcome is A, recommend Phase 7. Otherwise, the recommended next step.

---

## Phase 7 — Clean up the baseline (only if Phase 6 reached Outcome A)

### Steps

1. Final check: `./gradlew lint` across the whole project. Zero findings for any vmscope rule ID, including baselined ones.
2. Inspect `lint-baseline.xml` in each Android module. Check `id=` attributes:
   - **All entries are vmscope-rule entries:** delete the file.
   - **Some entries are for non-vmscope rules:** remove only the vmscope-rule entries, keep the file.
3. Run `./gradlew :<module>:lintDebug` one final time. Must still be clean without the baselined vmscope entries.

### iOS wiring cleanup (if applicable)

- If the user confirmed they've pasted the Swift snippet and iOS builds, delete `VMSCOPE_IOS_WIRING.md`.
- If not confirmed, leave it in place and note it in the final report.

### Report

- Migration complete.
- Baseline file status per module: deleted / partially cleaned / untouched.
- Recommend tightening `CatchingThrowableInCoroutine` to Error in `lint.xml` now that the codebase is clean:

  ```xml
  <issue id="CatchingThrowableInCoroutine" severity="error" />
  ```

- iOS wiring state: complete / awaiting user paste.

---

## Global rules and escape hatches

### When to stop and ask

Stop and ask the user — do not guess — if you encounter:

- A framework-provided Application class that cannot accept additional interfaces.
- Multiple Application subclasses (multi-process setups).
- A crash reporter you don't recognize.
- A catch block whose behavior can't be classified into Phase 3's four categories.
- Any build failure not immediately explained by your changes.
- Any test failure without an obvious root cause.
- More than 10 Category C (silent swallow) findings — halt threshold per Phase 3.
- The user's `VmScopeConfig.Provider` property is a function, not a val, in the shipped library — signals API divergence from this guide; use the shipped API and report.

### When to use `@Suppress`

Only in two cases:

1. A framework constraint makes a rule impossible to satisfy. Suppress on the specific class or function with a comment.
2. The user explicitly directs you to suppress a specific finding after review.

Do not suppress to finish faster.

### What you will not do

- Modify Swift source files.
- Run iOS builds (Xcode, `xcodebuild`, simulator launches).
- Add new tests or modify existing test code unless a test itself is the bug.
- Reformat unrelated code.
- Change dependency versions.
- Fix pre-existing lint findings unrelated to vmscope.
- Install the library itself (it's a prerequisite; the human does this).

### Success criteria

- `./gradlew build` passes.
- `./gradlew test` passes (or `./gradlew allTests` for KMP).
- `./gradlew lint` passes with zero vmscope findings on Android modules.
- Non-Android source sets (commonMain, iosMain, jvmMain) have no `viewModelScope` references, no catch blocks swallowing `CancellationException`, no broad catches without cancellation guards.
- If KMP: `VmScopeBootstrap.kt` exists and compiles; `VMSCOPE_IOS_WIRING.md` has the Swift snippet for the user.
- `lint-baseline.xml` either deleted (cleanest) or pruned of vmscope entries.
- Diff is reviewable — no unrelated changes.

---

## When running without a human in the loop

- Downgrade Phase 3 Category C findings to minimum-guard + TODO comment. Do not remove try/catches.
- Do not narrow types beyond obvious cases in Category D.
- Do not halt on Phase 5 (iOS wiring) just because the human can't paste the snippet live. Generate the snippet, mark the task as "awaiting user," proceed to Phase 6.
- Do not proceed past Phase 6 Outcome D (test failures). Halt and produce a full report.
- Do not delete or modify `lint-baseline.xml` except as described in Phase 7.
- Produce a `MIGRATION_REPORT.md` at the end summarizing everything done, skipped, and requiring user follow-up.

---

## Worked examples

### Category A (Phase 3): simple guard insertion

```kotlin
// Before
vmScope.launch {
    try {
        val data = repo.fetch()
        process(data)
    } catch (t: Throwable) {
        logger.error("fetch failed", t)
    }
}

// After
vmScope.launch {
    try {
        val data = repo.fetch()
        process(data)
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        logger.error("fetch failed", t)
    }
}
```

### Category C (Phase 3): flagged, not auto-fixed

```kotlin
// Before
vmScope.launch {
    try {
        optionalPrecompute()
    } catch (t: Throwable) {
        // empty — intentional? bug?
    }
}

// Agent output: flags to user, does NOT modify without confirmation.
```

### Phase 4: viewModelScope replacement in commonMain

```kotlin
// Before — commonMain/ProfileViewModel.kt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class ProfileViewModel(private val repo: UserRepo) : ViewModel() {
    fun load() = viewModelScope.launch {
        try {
            _state.value = State.Loaded(repo.fetch())
        } catch (e: IOException) {
            _state.value = State.Error
        }
    }
}

// After
import androidx.lifecycle.ViewModel
import org.velkos.vmscope.vmScope
import kotlinx.coroutines.launch

class ProfileViewModel(private val repo: UserRepo) : ViewModel() {
    fun load() = vmScope.launch {
        try {
            _state.value = State.Loaded(repo.fetch())
        } catch (e: IOException) {
            _state.value = State.Error
        }
    }
}
```

The `vmScope` extension is in commonMain — the import resolves on every target.

### Phase 5: iOS wiring file output

Example `VMSCOPE_IOS_WIRING.md` produced when the shared module's framework baseName is `Shared`:

```swift
import Shared

@main
struct UserProfileApp: App {
    init() {
        VmScopeBootstrapKt.doInitVmScope()
    }
    var body: some Scene { WindowGroup { ContentView() } }
}
```

User pastes this into their existing `@main App` struct, replacing or extending the existing `init()`.

---

End of migration guide.
