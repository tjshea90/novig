# novig — standing rules and build traps

**App name: Vigilant.** Android app, Tj's own project, targeting **Android
16 (API 36)** and optimized for a **Moto G 2026**. Purpose: **find and
surface positive-EV opportunities on the Novig sportsbook** — a peer-to-peer
betting exchange, not a traditional sportsbook, which changes what "positive
EV" even means here (RESEARCH.md §2: there's no house vig on Novig's own
board to strip out — the edge is the crowd not having converged to true
probability yet, not a stale line). Ships as a signed release APK, not to
the Play Store — the same distribution model as this account's other
Android projects (fantasy-football, Portfolio).

**Status as of 2026-09-20:** first real app code exists — a Gradle
multi-module project (`engine`, `data`, `app`) with a working devig/EV
engine (54 passing unit tests, verified for real in this dev container),
data-layer scaffolding for both the Novig and reference-odds legs, and a
basic Compose UI running on sample data. See `TASKS.md` for what's done vs.
open, and RESEARCH.md for the research this was built from. Sections below
that used to say TBD are now filled in with the decisions made building it
— sections still genuinely open stay marked TBD rather than invented.

## What is actually decided

- **Platform:** native Android, targeting API 36 (Android 16), **Kotlin +
  Jetpack Compose** (Portfolio's approach, not fantasy-football's WebView
  shell) — decided 2026-09-20: a live-updating, WebSocket-driven scanner
  UI (RESEARCH.md §7) fits native Compose's state model and Android
  background-service story much better than a WebView bridge would.
- **Target hardware:** a Moto G 2026. Nothing about that device's specific
  chipset, RAM, or display has been researched yet as of this writing — "one
  cheap-tier phone" should probably be a real constraint on wake locks,
  polling frequency, and background work (the same battery discipline
  Portfolio's `CLAUDE.md` "full tests" protocol checks for), but the
  specifics need research before they're rules.
- **Purpose:** find positive-EV opportunities on Novig — devig a reference
  line (a sharp book like Pinnacle/Circa if fetched, else average whatever
  major books were fetched — Tj's own instruction, 2026-09-20) and compare
  it to Novig's live price, as close to real time as the data sources
  allow. Not yet decided: automated bet placement (the engine computes EV,
  nothing places a trade yet), position tracking, or anything beyond
  finding and surfacing the edge — those remain open, ask before assuming.
- **Distribution:** sideloaded signed release APK, built and signed by
  GitHub Actions (not this container), matching Portfolio's model — see
  `CLAUDE.md`'s "Releasing" section. Claude triggers the build via the
  GitHub API and confirms it went green; Tj gets a link, not a raw file.
  **Not wired up yet** — `.github/workflows/ci.yml` currently only builds a
  debug APK and runs tests (no signing); see "Toolchain" below for why, and
  "Releasing" in `CLAUDE.md` for the ordered steps still ahead (keystore,
  a real release workflow, `ship.sh`'s real gate).

## The rule that will apply the moment a keystore exists

Not yet true — there is no keystore yet, because there is no app to sign.
But the day one is generated (`keytool -genkeypair ...` or GitHub Actions
generating it into a Secret), this rule is retroactively already in force,
ported from this account's other two Android projects where getting it
wrong has cost real user data:

Android only performs a **data-preserving in-place update** when the
package name AND the signing certificate both match. A new keystore forces
an uninstall first, which **erases whatever the app has stored locally**
(auth state, positions, cached lines — whatever it ends up persisting). So,
from the moment a keystore is first generated:

- Never regenerate it. Sign every release with the same one.
- Never change the applicationId once it's picked.
- Always bump `versionCode` before shipping — Android refuses to install a
  build whose versionCode is not strictly higher than what's already
  installed.
- Record its certificate fingerprint in this file the day it's generated,
  the way Portfolio's `BRIEF.md` records its own — a keystore silently
  swapped for a same-DN regeneration is not something a build failure
  catches; only a fingerprint comparison does.
- If it's kept in git secrets for GitHub Actions to sign with (Portfolio's
  model): the moment the repo is ever considered for going public, check
  whether the keystore or any secret ever touched a commit, the same way
  Portfolio's `CLAUDE.md` flags for its own history. Cheap to check
  (`git log --all --diff-filter=A --name-only -- '*.jks'`), expensive to
  discover after the fact.

## Toolchain

**TBD.** No `build.gradle.kts`, no `package.json`, nothing pinned yet.
Pin real versions here — AGP/Kotlin/compileSdk, or whatever the chosen stack
turns out to be — the day a build actually depends on them, the same way
Portfolio's `BRIEF.md` pins JDK 21 / Gradle 8.14.3 / AGP 8.13.2 / Kotlin
2.3.10. An unpinned toolchain silently drifts to whatever a fresh container
downloads, which is how "upgrading" a dependency turns into fighting a build
failure that isn't a code problem.

## Build traps

**None recorded yet — there is nothing to build.** This section exists so
the pattern is followed rather than invented later: the moment a build
fails for a reason that isn't obviously a code bug (a proxy 429, a
JAVA_TOOL_OPTIONS variable polluting version output, a concurrent-build
lock collision — the classes of trap both sibling projects' `BRIEF.md`s
record), write it here before moving on, so the next session that hits the
same wall doesn't re-diagnose it from scratch.

## Locked architecture decisions

**None yet.** When real ones exist (a data source's priority order, a
caching policy, an accounting/scoring method — whatever this app's
equivalent turns out to be), record them here the way Portfolio's `BRIEF.md`
does for its market-data source order, and cite *why*, not just *what* — the
reasoning is what stops a future session from "simplifying" a decision that
was actually load-bearing.
