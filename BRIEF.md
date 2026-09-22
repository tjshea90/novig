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
  GitHub Actions (not this container). Claude triggers the build via the
  GitHub API and confirms it went green; Tj gets a link, not a raw file.
  **Wired up 2026-09-20** — `.github/workflows/release.yml` builds, signs,
  verifies the signature, tags, and publishes a GitHub Release, no secrets
  needed (see the keystore section below for the model — this deliberately
  does *not* match Portfolio's secret-based one; it matches
  fantasy-football's committed-keystore one, Tj's explicit call).

## The rule that will apply the moment a keystore exists

**Now true — the keystore was generated 2026-09-20.** This rule is in
force starting now, ported from this account's other two Android projects
where getting it wrong has cost real user data. Read this alongside the
note right after it — the *security model* here is deliberately not
Portfolio's, but the *permanence* rules below apply exactly the same way
regardless of which model a keystore uses.

- **Committed directly into the repo:** `app/keystore/vigilant-debug.jks`.
  **Alias:** `vigilant`. **Password:** Android's own standard well-known
  debug password (`android`) — intentionally public, see below. **Valid
  until:** 2056-09-12 (30 years).
- **SHA-256 fingerprint:**
  `AB:22:07:A8:6C:4E:59:18:BB:C1:A2:20:04:FB:14:42:B2:86:6E:6E:5A:B0:C3:D3:27:A7:EE:0E:AA:98:5C:C1`
  — `release.yml` verifies every build's signature against this exact
  value before publishing and refuses to publish on a mismatch.

**Why this one is committed instead of a GitHub Secret (a real, deliberate
tradeoff, not an oversight):** Tj's explicit instruction, 2026-09-20 —
"the other repos GitHub can make the apk without secret, it doesn't need
to be secure" — pointing at fantasy-football's `android/debug.keystore`,
which is committed with the same standard public debug password for the
same reason. Checked both other repos directly rather than assumed:
Portfolio's real `android.yml` *does* use a Secret
(`SIGNING_KEYSTORE_BASE64`); that's the model an earlier pass of this file
followed and sent Tj a keystore for — **that Secret-based keystore is
abandoned, unused, and superseded by this one.** What this tradeoff
actually costs: because the password is public knowledge, anyone can
generate a byte-identical keystore and sign an APK Android will accept as
a legitimate in-place update over a real install — there is no real secret
material here to protect. That's a fine trade for an app that (as of this
writing) ships wired to sample data only and holds no real credentials.
**Revisit this the day the app holds real Novig API credentials or
anything else worth protecting** — switch back to a Secret-held keystore
(Portfolio's model, already built once this session, easy to redo) rather
than leaving a forgeable signing key on an app handling real trading
credentials.

Separately from *which* model is used, Android only performs a
**data-preserving in-place update** when the package name AND the signing
certificate both match. A new keystore forces an uninstall first, which
**erases whatever the app has stored locally** (auth state, positions,
cached lines — whatever it ends up persisting). So, from the moment a
keystore is first generated — committed or secret-held, doesn't matter:

- Never regenerate it. Sign every release with the same one.
- Never change the applicationId once it's picked.
- Always bump `versionCode` before shipping — Android refuses to install a
  build whose versionCode is not strictly higher than what's already
  installed.
- Record its certificate fingerprint in this file the day it's generated
  or changed — a keystore silently swapped for a same-DN regeneration is
  not something a build failure catches; only a fingerprint comparison
  does. (This is exactly why `release.yml` checks it on every build.)
- If a *future* keystore goes back to being Secret-held: the moment the
  repo is ever considered for going public, check whether it or any
  secret ever touched a commit, the same way Portfolio's `CLAUDE.md` flags
  for its own history (`git log --all --diff-filter=A --name-only --
  '*.jks'`) — not a concern for the current committed one, which is
  public on purpose.

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

Three hit and fixed 2026-09-20, standing into the future — don't
re-diagnose these from scratch:

- **`android-actions/setup-android@v3` unconditionally fails.** It runs
  `sdkmanager tools` as part of its own setup, and that package was
  removed from the Android SDK repository years ago — the whole action
  errors out on every run, before your workflow's own steps even start.
  Fix (already in `.github/workflows/ci.yml`): don't use that action at
  all. GitHub-hosted `ubuntu-latest` runners already ship an Android SDK
  with `ANDROID_HOME` set — just write the license-acceptance hash files
  directly (`$ANDROID_HOME/licenses/android-sdk-license` = the well-known
  hash `24333f8a63b6825ea9c5514f83c2829b004d1fee`, plus
  `android-sdk-preview-license`), and let AGP auto-download whatever
  specific platform/build-tools `compileSdk` needs during the Gradle build
  itself.
- **`android { kotlinOptions { jvmTarget = "21" } }` is a hard compile
  error on Kotlin 2.3.10** — that whole DSL path is gone. Use
  `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }` (import
  `org.jetbrains.kotlin.gradle.dsl.JvmTarget`) as its own top-level block
  in the module's `build.gradle.kts`, not inside `android { }`.
- **A Kotlin class with an all-default-parameter constructor still only
  has ONE constructor at the JVM level** unless it's annotated
  `@JvmOverloads` — Kotlin's default-argument sugar is call-site only.
  This bit `ScannerViewModel`: `by viewModels()`'s reflection-based
  factory looks for a true zero-argument constructor, doesn't find one
  without `@JvmOverloads`, and it's a **runtime** crash the first time the
  screen opens — compiles clean, so neither `./gradlew build` nor a code
  review that only checks for compile errors will catch it. Anywhere a
  ViewModel (or anything else instantiated via reflection by an Android
  framework class) has a default-only constructor, it needs
  `@JvmOverloads`.
- **A fourth one, found and fixed 2026-09-20, bigger than the others: this
  repo's actual GitHub default branch was NOT `main`** — it was
  `claude/novig-checkpoint-tests-qqgnnb`, an old session branch from when
  this repo was first created (whichever branch existed at repo-creation
  time became the default, and nothing ever changed it since — `main`
  being "the only source of truth" per `CLAUDE.md` described this repo's
  own *intended* workflow, but it was never actually a GitHub repo-settings
  fact until now). This matters more than it looks: GitHub only registers a
  `workflow_dispatch`-triggered workflow (shows it in the API/UI "Run
  workflow" picker at all) once that workflow's YAML file exists on the
  **actual GitHub default branch** — not just on `main`, and not on
  whatever branch a session happens to be working from. `release.yml`
  triggering with a 404 despite existing on `main` and the working branch
  was this, not a workflow bug. **Fixed:** Tj switched the repo's default
  branch to `main` in GitHub's own repo settings (2026-09-20T16:07:32Z) —
  a Claude session hit the auto-mode permission classifier blocking a
  repo-settings PATCH call outright, so this specifically needed a human
  (or explicit authorization) to do, not something to retry from a
  session. Confirmed fixed: `release.yml` now shows up in
  `list_workflows` and triggers successfully. If this ever regresses (a
  repo transfer, a new repo created fresh) the same fix applies — check
  `default_branch` on the repo via the API before assuming a
  `workflow_dispatch` 404 is a workflow-syntax problem, which is what it
  looked like at first here.
- **A sixth, found and fixed 2026-09-20: an explicit import of `weight`
  from `androidx.compose.foundation.layout` breaks `Modifier.weight()`
  instead of merely being redundant.** `Modifier.weight(1f)` inside a
  `Column { }`/`Row { }` body resolves via `ColumnScope.weight`/
  `RowScope.weight` — a *member* extension function on those scope
  interfaces, found automatically through the lambda's implicit receiver,
  never through an import (member extensions aren't top-level symbols, so
  there is no valid import path to them at all). That same package also
  has an unrelated **internal** top-level symbol literally named `weight`
  (part of `RowColumnParentData`'s implementation) — an explicit
  `import androidx.compose.foundation.layout.weight` binds to *that* one
  instead, and every `Modifier.weight(...)` call site in the file then
  fails with `Cannot access 'val RowColumnParentData?.weight: Float': it
  is internal in file`. Caught for real by CI (run 35536648655) — the fix
  is to delete the import, not add one; `Modifier.weight()` needs none.
- **`apksigner verify --print-certs`'s SHA-256 digest output has no colons
  and is lowercase** (`ab2207a8...`) — unlike `keytool -list -v`'s
  colon-separated uppercase (`AB:22:07:A8:...`, what BRIEF.md's own
  fingerprint above is written as, matching `keytool`'s convention). A
  literal string comparison between the two formats fails even when the
  certificate is exactly right — hit for real on the first release run
  (2026-09-20), and cost nothing except confusion since the build itself
  had already succeeded. `release.yml`'s verify step now strips colons and
  lowercases both sides before comparing — don't go back to a literal
  string match.
- **A seventh, found and fixed 2026-09-22: cancelling an in-flight
  `release.yml` run doesn't stop it instantly, and a step already running
  when the cancel signal arrives can still finish** — hit for real
  shipping v0.3.2's release: a run got cancelled while its "create the
  release tag" step (`git push origin "$tag"`) had *already* raced ahead
  and completed, while the next step ("create GitHub Release") got cut
  off mid-flight. Net effect: a real tag existed on GitHub with no Release
  attached, and separately a draft Release existed with no real tag
  attached (`softprops/action-gh-release` got interrupted before finishing).
  The retry's own "refuse to overwrite" check didn't catch either leftover
  and collided with them, because it ran `git rev-parse` against the
  **local, shallow checkout** (doesn't reliably see a tag pushed to the
  remote moments earlier by a different run) and `gh release view` matches
  by the release's `tag_name` *field* (which a draft can carry even when
  not attached to the real tag). Fixed in `release.yml`: the check now
  queries the remote directly and self-heals both leftover cases (deletes
  a bare tag with no Release; deletes a draft Release with no real tag)
  automatically — anything genuinely published still refuses exactly as
  before.
  **Important correction to this trap's own original write-up, same
  session:** the cancellation that started this whole chain was itself a
  mistake, not a response to a real stuck build. Each of those release
  runs actually completed in well under 3 minutes by GitHub's own
  timestamps — confirmed only *after* Tj pointed out the "stuck 20+
  minutes" claim was wrong. The false read came from treating the sum of
  several `ScheduleWakeup` `delaySeconds` values as confirmed elapsed real
  time, when a session's own scheduled-wakeup requests are not a reliable
  clock — check a resource's own real timestamps (here, the workflow run's
  `created_at`/`updated_at` from the GitHub API) before concluding
  something has been running too long, never the sum of your own prior
  wait requests. Compounding this: a `Monitor` task left running
  concurrently with `ScheduleWakeup` was independently emitting a new
  notification every 20 seconds regardless of whether anything had
  changed (an unconditional `echo` inside the poll loop, not gated on a
  state change) — worth remembering generally: a polling loop's `echo`
  belongs on state changes, not unconditionally on every iteration, since
  each line is a separate event.

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
  models this explicitly — `FeeResult.Unknown` still exists as a type and
  the UI still has a code path for it, but as of 2026-09-22 every
  `TradeContext` has a confirmed formula (pregame straight: $0; live
  straight taker: `price × (1-price) × 0.03`; parlay taker:
  `price × (1-price) × 0.10`, confirmed by the novig_ev_scanner briefing —
  RESEARCH.md §3/§10 item 3; maker side is always $0). A sample fixture
  (`EvScannerTest`'s live-market case) exists specifically to prove a real,
  positive raw edge can still net negative once Novig's live taker fee is
  applied — don't "simplify" this back to ignoring fees, that's the exact
  failure mode this was built to avoid.
- **The UI must never present sample/demo data as if it were live.**
  `ScannerViewModel`/`OpportunitiesScreen` carry explicit `novigIsLive`/
  `referenceIsLive` flags (one per leg, not one combined flag — see below)
  and render a visible banner naming exactly which leg(s) are sample when
  either is false. Keep both flags wired correctly as real providers get
  plugged in or swapped.
- **`NovigGraphQlClient` supplies the Novig leg (wired 2026-09-22,
  superseding the SharpAPI wiring below), The Odds API supplies the
  reference leg — each provider used only for the one leg it can actually
  serve.** **History:** SharpAPI was originally wired for the Novig leg
  (2026-09-20) on the premise its free tier uniquely included Novig among
  ~40 books — wrong, proven by a real HTTP 403 against Tj's own account and
  confirmed by SharpAPI's own product page ("Available on Hobby plan and
  above," $79/mo; free tier is DraftKings/FanDuel only). `SharpApiClient`
  was deleted 2026-09-22 (RESEARCH.md §4.2/§4.2.2) — dead code with no
  future value once the free-tier path was proven categorically closed,
  unlike `NovigApiClient` (kept dormant; still has future value if Novig's
  official API reply ever lands). **The actual replacement, 2026-09-22
  (RESEARCH.md §4.4):** Tj supplied a working, MIT-licensed, third-party
  Python package (`novig-liquidity`) reverse-engineering unauthenticated
  read access to Novig's own internal GraphQL backend
  (`gql.novig.us/v1/graphql`) — verified directly against the package's
  real source, not just a summary. **Real, disclosed tradeoff, not a free
  lunch:** no account/login is involved (so it can't get Tj's Novig
  *account* banned), but it requires a paid rotating-proxy subscription to
  avoid IP-based anti-bot blocking, and sits in a genuine ToS gray area now
  that Novig is CFTC-regulated (RESEARCH.md §9). Ships opt-in only — zero
  proxies configured *and* direct mode off (the default) falls back to
  sample data for this leg, same as every other provider, with the risk
  spelled out directly in the Settings screen, not just in these docs. The
  Odds API supplies Pinnacle/consensus for the reference side (RESEARCH.md
  §4.3) and is unaffected — that leg is live whenever Tj has a key
  configured.

  **A free "direct, no proxy" mode was added 2026-09-22 (RESEARCH.md
  §4.4.1)**, after Tj asked whether a free alternative to paid proxies
  existed (a VPN, or airplane-mode IP cycling). Real finding: the
  reference package's hard proxy requirement was written for its own
  continuous, high-frequency polling — this app's manual-refresh usage is
  much lighter and may not need a pool at all, so it's worth trying free
  first. `NovigGraphQlClient` now accepts zero proxies and connects
  directly over whatever network the device is currently routed through
  (a system-wide VPN, if active, works automatically — no per-app
  configuration needed). A separate Settings toggle is the explicit
  opt-in for this — proxies and direct mode are two independent ways to
  go live, neither is a silent default. Direct mode has no pool to retry
  with, so a rate-limit or rejection there fails immediately and visibly
  (`NovigDirectAccessException`) rather than being silently swallowed.

  `NovigGraphQlClient implements NovigRepository`, `TheOddsApiClient implements ReferenceOddsRepository`
  — both live in `data`, both real (the GraphQL client's queries/parsing
  verified against the package's actual source and unit-tested against
  fixture JSON matching that verified shape; `TheOddsApiClient`
  `MockWebServer`-tested against its documented response shape), both fall
  back independently to `SampleNovigRepository`/`SampleReferenceOddsRepository`
  when that leg has no stored proxies/key yet — which is exactly the
  mechanism keeping the Novig leg honest about running on sample data
  rather than silently degraded. `NovigGraphQlClient`'s proxy pool reuses
  `KeyRotator`/`ApiKeyStore`/the encrypted Settings-screen storage verbatim
  (`ApiProvider.NOVIG_PROXY`) rather than building parallel plumbing for
  what is functionally the same problem (a list of credentials to try in
  order, rotating past ones that fail).
- **Automatic multi-key rotation: `KeyRotator` (`data/keys/KeyRotator.kt`),
  provider-agnostic — Tj's own explicit request, 2026-09-20 ("make a
  system for the app to switch keys automatically when my usage runs
  out").** Holds a list of keys per provider, tries them in the order Tj
  added them, and on each attempt: 429 → mark that key cooling down
  (honoring `Retry-After`/rate-limit-reset headers when present, else a
  60s default) and try the next key, auto-recovering once the cooldown
  passes; 401/403 → mark that key exhausted permanently (never
  auto-recovers — a human has to add a fresh key); success → use it and
  remember it worked. Throws `AllKeysExhaustedException` only once every
  key for that provider is rate-limited or invalid. `NovigGraphQlClient`
  and `TheOddsApiClient` are wired through the same `KeyRotator` — the
  rotation logic itself has no provider-specific knowledge, only each
  client's own mapping to `KeyAttemptResult` does (429/401 for
  `TheOddsApiClient`; connection failures/HTTP errors/malformed proxy
  strings for `NovigGraphQlClient`, which rotates *proxies* through the
  exact same mechanism as an API key — see the bullet above).
- **API keys are stored encrypted on-device, never in plaintext, never
  committed.** Researched `androidx.security:security-crypto`
  (`EncryptedSharedPreferences`) first and found it **deprecated** (every
  API deprecated since 1.1.0, no further releases planned) — did not build
  against it. Current approach instead: **Jetpack DataStore Preferences**
  (`androidx.datastore:datastore-preferences:1.2.1`) for storage, encrypted
  with **Android Keystore-backed AES/256-GCM** (`KeyCipher.kt`, plain
  `javax.crypto`/`android.security.keystore` platform APIs, no Tink
  dependency — deliberately avoids pulling in another library's API
  surface that can't be verified locally, no Android SDK in this
  container). `EncryptedApiKeyStore implements ApiKeyStore`
  (`data/keys/ApiKeyStore.kt`'s interface) is the real Android-side
  implementation; order is preserved (a JSON array, not a Set) since
  `KeyRotator` depends on trying keys in the order Tj entered them. Added
  and removed via the in-app `SettingsScreen`/`SettingsViewModel`, reached
  from a ⚙ icon in `OpportunitiesScreen`'s top bar (simple state-based
  navigation in `MainActivity`, no Navigation-Compose library needed for
  two screens).
- **Resolved 2026-09-20 (was a known v1 limitation): sport is now a
  multi-select picker, not hardcoded to NFL.** `data.scanner.SportsCatalog`
  holds a curated subset of The Odds API's documented sport keys (not
  exhaustive — RESEARCH.md §4.3); `EvScanner` takes a `List<String>` of
  sport keys and fetches the reference leg once per selected sport,
  merging the results before matching against Novig's board.
- **Nothing loads until the user acts — Tj's own explicit instruction,
  2026-09-20 ("do not load any odds at all for any sport until I select
  the sport or sports and press refresh or pull down to refresh
  gesture").** `ScannerViewModel` no longer auto-scans on init (starts in
  a new `ScanUiState.Idle`); selecting a sport (`toggleSport`) only ever
  updates local state, never triggers a fetch. `rescan()` is the single
  path that touches a repository, and it's a no-op with zero sports
  selected — both the FAB refresh button and Compose Material3's
  `PullToRefreshBox` (the pull-down gesture) call the same `rescan()`, so
  there's exactly one way odds ever load. This also means returning from
  Settings after adding a key does **not** auto-rescan anymore (it did
  briefly, for one request) — that would have silently violated this same
  rule the moment a sport was already selected, so it was removed in the
  same change that added the picker. `EvScanner.scan()` itself also
  short-circuits to an empty result without calling either repository
  when its sport-key list is empty, so the "don't load anything" guarantee
  holds even if a future caller forgets to gate on the UI side.
