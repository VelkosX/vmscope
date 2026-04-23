# Security Policy

## Supported versions

Only the latest released version of vmscope receives security updates. Pre-1.0 releases ship without a long-term-support commitment — expect patches to land on the newest minor line.

| Version | Supported |
|---|---|
| 0.1.x | ✅ |
| < 0.1.0 | ❌ |

## Reporting a vulnerability

**Please do not open a public GitHub issue for security reports.**

Use GitHub's [private vulnerability reporting](https://github.com/VelkosX/vmscope/security/advisories/new) on this repository. That flow creates a confidential advisory only maintainers can see, and gives us a channel for coordinated disclosure.

Include:

- The version of vmscope you observed the issue on.
- A minimal reproduction (repo link, code snippet, or plain description — whichever is fastest for you).
- What impact you believe the issue has on consumers of the library.

We will acknowledge within 7 days and aim to ship a patch within 30 days for confirmed issues. Once the patch is released, the advisory is published so consumers can pick up the fix.

## Out of scope

- Issues in upstream dependencies (AndroidX, kotlinx-coroutines, etc.) — please report to those projects directly. If the report involves how vmscope uses a dependency, that's in scope here.
- Build-time issues in consumer projects (R8, Gradle version conflicts, etc.) — open a public issue instead.
