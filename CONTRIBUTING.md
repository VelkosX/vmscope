# Contributing to vmscope

Thanks for taking an interest. This doc covers the things that aren't obvious from reading the source — local dev mechanics, API change conventions, and what kinds of changes are in scope.

## Quick start

Clone, then:

```bash
./gradlew :vmscope:testDebugUnitTest      # Android unit tests
./gradlew :vmscope:jvmTest                # JVM tests
./gradlew :vmscope-lint:test                   # lint rule tests
./gradlew apiCheck                             # public API surface check
```

`./gradlew build` runs the lot, plus assemble.

iOS targets require macOS + Xcode:

```bash
./gradlew :vmscope:iosSimulatorArm64Test  # iOS sim test runner
./gradlew :vmscope:compileKotlinIosArm64  # iOS device klib (no test runner — sim only)
```

## Public API changes

The library uses Kotlin's `explicitApi()` (every top-level + member declaration must be marked `public` or `internal`) plus [binary-compatibility-validator](https://github.com/Kotlin/binary-compatibility-validator) with klib enabled. The validator generates one `.api` file per target under `vmscope/api/` — checked into the repo.

If your change adds, removes, or modifies anything in the public API surface, **CI will fail at `apiCheck`** until you regenerate and commit the dump:

```bash
./gradlew apiDump
git add vmscope/api/
```

Reviewers will look at the `.api` diff to evaluate the API surface change explicitly. This is the intended workflow — it forces public-API decisions to be visible in the PR rather than slipping in.

## Lint rule changes

Each lint rule lives in `vmscope-lint/src/main/kotlin/org/velkos/vmscope/lint/` as one detector class. To add a new rule:

1. Create the detector — `class FooDetector : Detector(), SourceCodeScanner { … }` with a `companion object { val ISSUE = Issue.create(…) }`.
2. Register the issue in `VmScopeIssueRegistry.issues`.
3. Add a test in `vmscope-lint/src/test/kotlin/.../FooDetectorTest.kt` using `LintDetectorTest`. Mirror the structure of an existing detector test (e.g. `UseVmScopeDetectorTest`).
4. If you want the lint rule to fire end-to-end on a real consumer, add a triggering site in `sample-android/` or `sample-kmp/android-app/` — both have `lint.checkDependencies = true`.

Run lint on the samples after changes:

```bash
./gradlew :sample-android:lintDebug :sample-kmp:android-app:lintDebug
```

## iOS sample (sample-kmp/ios-app)

The Xcode project at `sample-kmp/ios-app/sample-ios.xcodeproj` is generated from `project.yml` by [XcodeGen](https://github.com/yonaskolb/XcodeGen). The generated `.xcodeproj` is committed so `open` works without any tooling. **If you change `project.yml`, regenerate:**

```bash
brew install xcodegen   # one-time
(cd sample-kmp/ios-app && xcodegen)
git add sample-kmp/ios-app/sample-ios.xcodeproj
```

Hand-edits to the pbxproj will be blown away on next regeneration. See [`sample-kmp/README.md`](sample-kmp/README.md) for the full build-and-run instructions.

## Pull requests

- **Small + focused.** One concern per PR. Easier to review, easier to revert.
- **Explain the *why*, not just the *what*.** PR descriptions should state the motivation and any non-obvious tradeoffs. The diff already shows what changed.
- **Tests + apiCheck must pass.** CI runs both on every PR.
- **Don't add backward-compatibility shims** for v0.x changes. We're pre-1.0; small breaking changes are expected.
- **No new dependencies without discussion.** Open an issue first if you think a new transitive dep is warranted.

## Out of scope (for now)

Things to skip writing PRs for unless you've discussed in an issue first:

- New KMP targets (wasm, watchOS, Linux Native, etc.). Adding targets is straightforward but expands the support matrix; coordinate first.
- Changing the handler decision tree in `VmScope.deliver`. It's load-bearing for several tests; semantic changes need a CHANGELOG entry and a careful API-surface review.
- Adding library dependencies to make the sample apps look more "real" (DI frameworks, network clients, etc.). The samples are deliberately minimal.

## Reporting bugs / asking questions

- Bugs: file an issue using the bug-report template (`.github/ISSUE_TEMPLATE/bug_report.yml`).
- Security: see [SECURITY.md](SECURITY.md) — do **not** file a public issue.
- Questions about usage: open a Discussion (Discussions tab on GitHub), not an issue.
