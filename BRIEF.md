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

Pinned 2026-09-20, mirroring Portfolio's proven versions (see
`gradle/libs.versions.toml` for the single source of truth — these numbers
should never drift out of sync with each other):

- **JDK 21**, **Gradle 8.14.3** (both confirmed already installed and
  working in this dev container), **AGP 8.13.2**, **Kotlin 2.3.10**.
- **compileSdk = targetSdk = 36** (Android 16, per the platform decision
  above). **minSdk = 30** — the target device will ship far newer, but
  there's no real cost to a little headroom for testing on whatever other
  device is on hand.
- **applicationId = `com.tjshea.vigilant`.** Permanent per the keystore
  rule above — never change this once a keystore is generated against it.
- **Module layout:** `engine` (plain Kotlin/JVM — the devig/EV math, zero
  Android dependency on purpose) → `data` (plain Kotlin/JVM — repositories,
  the Novig/The-Odds-API clients, also zero Android dependency) → `app`
  (the actual Android module: Compose UI, manifest, eventually the
  foreground WebSocket service from RESEARCH.md §7). Deliberate: keeping
  `engine`/`data` Android-free is what lets their real logic be unit tested
  in a container with no Android SDK — see the next point.

**Known, permanent constraint on this dev container: no Android SDK.**
`ANDROID_HOME`/`ANDROID_SDK_ROOT` are unset here and there's no `sdkmanager`
— confirmed 2026-09-20, not expected to change. This is *why* the module
split above exists: `engine` and `data` compile and run their real test
suites here (`./gradlew --configure-on-demand :engine:test :data:test` —
54 tests, all green as of this writing), but the `app` module can only be
verified by CI (`.github/workflows/ci.yml`). This matches this project's own
release model (`CLAUDE.md`'s "Releasing" section) — GitHub Actions builds
the real thing, not this container — so treat it as the expected shape,
not a gap to keep re-flagging. **Confirmed green end-to-end 2026-09-20**
(run 35493330913: all tests across all three modules pass, `assembleDebug`
succeeds, a real debug APK was produced). Any session working on
`app`-module code should confirm it via a pushed CI run, not assume compile-correctness from
review alone.

## Build traps

**None recorded yet — there is nothing to build.** This section exists so
the pattern is followed rather than invented later: the moment a build
fails for a reason that isn't obviously a code bug (a proxy 429, a
JAVA_TOOL_OPTIONS variable polluting version output, a concurrent-build
lock collision — the classes of trap both sibling projects' `BRIEF.md`s
record), write it here before moving on, so the next session that hits the
same wall doesn't re-diagnose it from scratch.

## Locked architecture decisions

- **Reference-line source order: prefer a sharp book (Pinnacle/Circa) alone
  when fetched, else average every major book fetched.** Tj's own explicit
  instruction (2026-09-20). Implemented in `engine`'s `Consensus` object —
  do not quietly change this to "always average" or "always prefer a sharp
  book" without checking with Tj first, since it was a specific ask, not a
  default we picked.
- **Devig method is a parameter, never hard-coded.** `DevigMethod` (engine)
  supports multiplicative/additive/power/Shin, matching OddsJam's own
  "pick your source of truth and method" model (RESEARCH.md §5) — and
  directly motivated by RESEARCH.md §8.1's finding that Odds Assist Pro's
  *undisclosed* method is exactly why its longshot edges can't be trusted
  blindly. Don't collapse this back down to one hard-coded method.
- **Novig's own fee must be netted into EV, and a fee we don't have a
  formula for must never silently become $0.** `engine.Fees`/`FeeResult`
  models this explicitly (`Unknown` for parlays, since that fee structure
  isn't confirmed yet — RESEARCH.md §3/§10). A sample fixture
  (`EvScannerTest`'s live-market case) exists specifically to prove a real,
  positive raw edge can still net negative once Novig's live taker fee is
  applied — don't "simplify" this back to ignoring fees, that's the exact
  failure mode this was built to avoid.
- **The UI must never present sample/demo data as if it were live.**
  `ScannerViewModel`/`OpportunitiesScreen` carry an explicit `isLiveData`
  flag and render a visible banner when it's false. The app ships wired to
  sample repositories by default (no live credentials exist yet) — keep
  that flag wired correctly as real providers get plugged in.
