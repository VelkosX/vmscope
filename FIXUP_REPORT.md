# FIXUP_REPORT — vmscope pre-announcement pass

**Date:** 2026-04-23
**Branch:** `main` (HEAD before this report's commit)
**Scope:** the 9-task fixup list issued before public announcement.

---

## What was changed

### Task 1 — README flow reordered ✓

Moved `## Using vmScope` from after the three Configure sections to immediately after `## Install`. New top-to-bottom order:

```
Supported targets → What and why → Install → Using vmScope → Configure (Android) →
Configure (iOS) → Configure (JVM) → Exception handling discipline → Testing →
Debug vs. release → Migrating → Advanced → FAQ → License
```

Added the requested intro sentence at the new position: *"After installing, replace `viewModelScope` with `vmScope` in your ViewModels. The next section explains how to wire up the exception handler per platform."*

The original Using-vmScope content (the `UserViewModel` snippet + the UseVmScope-quick-fix note) was moved verbatim. No content lost.

### Task 2 — FAQ additions ✓

Two Q&A pairs inserted after "Why crash by default in release":

- **Does this work with Hilt?** — yes; `VmScopeConfig.Provider` composes with `@HiltAndroidApp`. App Startup runs before Hilt injection but only needs the `as?` cast which works regardless of injection state. Code snippet included.
- **What's the runtime overhead?** — qualitative answer (one `SupervisorJob` + `CloseableCoroutineScope` allocation per VM, no per-launch overhead, no reflection on hot path) plus the concrete AAR size measurements from Task 8.

### Task 5 — Release notes file created ✓

`.github/RELEASE_NOTES_v0.1.0.md` written using the template you provided. `<group-id>` placeholder filled in as `org.velkos` (matching the current `gradle.properties`); needs updating if Task 3 lands on `io.github.velkosx` or anything else.

### Task 8 — AAR + method count measured (partial) ✓

- `vmscope-core-release.aar` — **45,807 bytes ≈ 45 KB**.
- Inside the AAR: `classes.jar` 18 KB, `lint.jar` 30 KB. The lint jar is build-time only; consumer apps don't ship it.
- 15 classes in `classes.jar`. ~42 declared methods (counted via `javap` regex grep — this is NOT a true post-R8 DEX method count; for a real number a consumer would need `dexcount-gradle-plugin` or `apkanalyzer`).
- The runtime overhead FAQ entry was updated with these numbers.

### Final checklist (Task 9) ✓

| Item | Status |
|---|---|
| `CHANGELOG.md` has 0.1.0 entry | ✓ (`## [0.1.0] - 2026-04-23` at line 5) |
| `gradle.properties` version is `0.1.0` (not `-SNAPSHOT`) | ✓ |
| No TODO/FIXME in `vmscope-core/src/commonMain` | ✓ (zero matches) |
| README lint rule IDs match `@Issue.id` values | ✓ (`MissingVmScopeConfigProvider`, `CancellationExceptionNotRethrown`, `CatchingThrowableInCoroutine`, `UseVmScope` — all four match exactly) |
| `consumer-rules.pro` declared in `vmscope-core/build.gradle.kts` | ✓ (`consumerProguardFiles("consumer-rules.pro")`) |
| `CONTRIBUTING.md` covers branching + testing | ✓ (Quick start, Public API changes, Lint rule changes, iOS sample, Pull requests, Out of scope sections) |
| `SECURITY.md` is not a placeholder | ✓ (29 lines, real Security Policy with supported-versions table + private-disclosure flow) |

---

## What was skipped and why

### Task 4 — Repo topics + GitHub About metadata (manual UI work)

Skipped per your instruction. Topics for you to add via the GitHub UI:

```
kotlin, android, kotlin-multiplatform, coroutines, viewmodel, crash-reporting, lint, kmp
```

Path: github.com/VelkosX/vmscope → ⚙ next to "About" → topics field at the bottom.

### Task 6 — Migration guide

**Migration guide not found — may have been deferred to v1.1 per design discussion.** Searched for `AGENT_MIGRATION.md`, `migration/`, and similar — none exist. README's "Migrating an existing codebase" section was left untouched (no link added per your instructions).

### Task 8 — Initializer cost on real device

**Not measured.** Requires a connected Android device or emulator + temporary `System.nanoTime()` instrumentation in `VmScopeInitializer.create`, plus running the sample app cold-start ~5–10 times. I have neither device nor emulator access from this session. The runtime-overhead FAQ entry covers the cost qualitatively ("sub-millisecond on any Android device made in the last decade") and notes the measurement is pending.

To take the measurement yourself:

1. In `vmscope-core/src/androidMain/kotlin/.../VmScopeInitializer.kt`, wrap the `create()` body:

   ```kotlin
   override fun create(context: Context) {
       val startNs = System.nanoTime()
       try {
           // existing body
       } finally {
           Log.i("vmScopePerf", "init: ${(System.nanoTime() - startNs) / 1_000} µs")
       }
   }
   ```

2. Run sample-android (or sample-kmp:android-app), force-stop the app, launch fresh 5–10 times, capture the µs values from `adb logcat -s vmScopePerf`.
3. Average the cold-start values, drop the instrumentation.
4. Replace the FAQ's "true measurement on a specific device not yet taken" hedge with the real number ("measured at X–Y µs across cold starts on Pixel 8 Pro / API 34").

---

## What requires your manual action

### A. Group ID decision (Task 3 — not changed, inventory only)

The repo currently uses `org.velkos`. To publish to Maven Central under that group, **`velkos.org` DNS verification on Sonatype Central is required.** If you don't own velkos.org's DNS, fall back to the GitHub-based `io.github.velkosx` namespace (no DNS required, just create a verification repo under VelkosX per Sonatype's instructions).

**Inventory of `org.velkos` references** (56 files):

- **Build configuration (1 file)** — `gradle.properties:12` `GROUP=org.velkos`
- **Android namespaces (3 files)** — `vmscope-core/build.gradle.kts`, `sample-android/build.gradle.kts`, `sample-kmp/android-app/build.gradle.kts`, `sample-kmp/shared/build.gradle.kts` (`namespace = "org.velkos.*"`, `applicationId = "org.velkos.*"`)
- **Kotlin source packages (~30 files)** — every `.kt` under `vmscope-core/`, `vmscope-lint/`, `sample-android/`, `sample-kmp/` declares `package org.velkos.*` and corresponding source directory paths (`src/.../org/velkos/...`)
- **Lint detector FQN constants** — `vmscope-lint/src/main/kotlin/org/velkos/vmscope/lint/UseVmScopeDetector.kt:108` (`VM_SCOPE_FQN = "org.velkos.vmscope.vmScope"`); `MissingVmScopeConfigProviderDetector.kt:64-66` (provider FQN with both `.` and `$` notation)
- **Lint registry service file** — `vmscope-lint/src/main/resources/META-INF/services/com.android.tools.lint.client.api.IssueRegistry`
- **AndroidManifest** — `vmscope-core/src/androidMain/AndroidManifest.xml` (`android:name="org.velkos.vmscope.VmScopeInitializer"`)
- **Consumer ProGuard rules** — `vmscope-core/consumer-rules.pro` (3 keep rules with FQNs)
- **Documentation** — `README.md` (Maven badge URL line 3, install snippet line 43, manifest opt-out snippet line 192), `CHANGELOG.md` (lines 37–41), `.github/RELEASE_NOTES_v0.1.0.md` (install snippet)
- **API dump baselines** — `vmscope-core/api/android/vmscope-core.api`, `vmscope-core/api/jvm/vmscope-core.api`, `vmscope-core/api/vmscope-core.klib.api` (regenerated automatically by `./gradlew apiDump`)
- **Lint test fixtures** — every test file under `vmscope-lint/src/test/kotlin/org/velkos/vmscope/lint/` references `org.velkos.vmscope.*` in stub source
- **Sample-kmp** — `sample-kmp/shared/src/.../org/velkos/vmscope/sample/*.kt` (3 Kotlin files: `SampleBootstrap.kt`, `SharedSampleViewModel.kt`, `SampleLog.kt`); Swift consumer doesn't reference the group ID directly (it imports `SampleShared` framework, which doesn't carry the namespace).

**To switch group ID** (one-line `gradle.properties` change + global rename):

```bash
# 1. Update GROUP in gradle.properties
sed -i '' 's|^GROUP=.*|GROUP=io.github.velkosx|' gradle.properties

# 2. Rename Kotlin package directories under each source set (11 directory moves)
for d in $(find vmscope-core vmscope-lint sample-android sample-kmp \
             -type d -path "*/org/velkos" -not -path "*/build/*"); do
  parent=$(dirname "$d")
  new_root="$parent/io/github/velkosx"
  mkdir -p "$new_root"
  git mv "$d" "$new_root"
  rmdir "$parent" 2>/dev/null || true
done

# 3. In-place text rewrite across all source / config / docs files
find . -type f \( -name "*.kt" -o -name "*.kts" -o -name "*.xml" -o \
                  -name "*.pro" -o -name "*.md" -o -name "*.api" -o \
                  -name "IssueRegistry" \) \
       ! -path "./.git/*" ! -path "*/build/*" ! -path "*/.gradle/*" \
       ! -path "*/.kotlin/*" ! -path "*/.idea/*" \
  | xargs sed -i '' \
      -e 's|org\.velkos|io.github.velkosx|g' \
      -e 's|org/velkos|io/github/velkosx|g'

# 4. Regenerate API dumps (paths inside .api files change)
./gradlew apiDump

# 5. Verify everything still builds + tests pass
./gradlew :vmscope-core:testDebugUnitTest \
          :vmscope-core:jvmTest \
          :vmscope-core:iosSimulatorArm64Test \
          :vmscope-lint:test \
          apiCheck
```

This is mechanical. The package-rename pass we did earlier (`io.github.lukavelimirovic` → `org.velkos`) used the same approach successfully. Don't run any of this until you've decided which namespace you want.

### B. Open PRs (Task 7 — not merged/closed per your instructions)

All 6 are Dependabot bumps from this morning's first cron pass. Decide before tagging v0.1.0:

| # | Title | Branch |
|---|---|---|
| 1 | `build(deps): Bump actions/checkout from 4 to 6` | `dependabot/github_actions/actions/checkout-6` |
| 2 | `build(deps): Bump gradle/actions from 4 to 6` | `dependabot/github_actions/gradle/actions-6` |
| 3 | `build(deps): Bump actions/setup-java from 4 to 5` | `dependabot/github_actions/actions/setup-java-5` |
| 4 | `build(deps): Bump actions/upload-artifact from 4 to 7` | `dependabot/github_actions/actions/upload-artifact-7` |
| 5 | `Bump the kotlin-and-coroutines group with 3 updates` | `dependabot/gradle/kotlin-and-coroutines-f35974325e` |
| 6 | `Bump org.gradle.toolchains.foojay-resolver-convention from 0.10.0 to 1.0.0` | `dependabot/gradle/org.gradle.toolchains.foojay-resolver-convention-1.0.0` |

**Recommendation:** merge #1–#4 (the GH Actions bumps to v5/v6/v7) — once they're in, the `FORCE_JAVASCRIPT_ACTIONS_TO_NODE24` env block in both workflows can be removed. Test #5 (Kotlin + coroutines) carefully because it might bump Kotlin to a version that changes `kotlin.concurrent.atomics` behavior. #6 (foojay) is a major version bump (0.10 → 1.0) that may change the toolchain resolver API; review before merge. Do these as a batch of separate merges into `main`, then re-run CI on `main` to confirm green before tagging.

### C. Other manual items still on your TODO

- **Add GitHub topics** (Task 4): `kotlin, android, kotlin-multiplatform, coroutines, viewmodel, crash-reporting, lint, kmp`. UI: github.com/VelkosX/vmscope → About → ⚙ → Topics.
- **Sonatype namespace claim** + DNS TXT verification on `velkos.org` (or switch to Task 3's GH-org alternative).
- **GPG signing key** generation + upload to keyserver.
- **GitHub Actions secrets** (`SONATYPE_USERNAME/PASSWORD`, `SIGNING_KEY/PASSWORD`).
- **Tag + push `v0.1.0`** when secrets are in place.
- **Initializer cost measurement on real Android device** (see Task 8 instructions above).

---

## Unresolved questions

1. **Group ID final answer.** `org.velkos` (DNS path) vs. `io.github.velkosx` (no-DNS GH path) — your call. The repo, README, badges, and POMs all reference the former today. The mechanical switch (if needed) is documented above.

2. **Migration guide.** Your spec referenced `AGENT_MIGRATION.md` as if it existed. It doesn't. Was it deferred deliberately, or is it expected to ship with v0.1.0? If the latter, that's a new artifact to write — not part of this fixup pass.

3. **Dependabot PR strategy.** All six are open. The GH Actions ones (#1–#4) should land alongside dropping the `FORCE_JAVASCRIPT_ACTIONS_TO_NODE24` env block — but the `gradle/actions` v6 bump (PR #2) might collide with our existing `gradle/actions/setup-gradle@v4` and `gradle/actions/wrapper-validation@v4` calls; check the v6 changelog for breaking changes before merging.

4. **`gradle/actions/wrapper-validation@v4`** — currently used in `build.yml` for the wrapper validation job. PR #2 bumps the parent `gradle/actions` to v6, which may or may not still expose `@v6` for `wrapper-validation`. Worth checking that subpath separately.

---

## Summary

- README is reordered, FAQ has 2 new entries, release notes file is staged at `.github/RELEASE_NOTES_v0.1.0.md`, AAR is measured (~45 KB / 15 classes / ~42 declared methods).
- Final checklist: 7 of 7 green.
- Group ID, topics, Dependabot decisions, Sonatype + GPG setup, and on-device initializer measurement are all on you.
- Migration guide doesn't exist; flagging as a possible v1.1 follow-up.
- No PRs merged or closed; no group ID changes made; no API surface changes; nothing pushed yet.
