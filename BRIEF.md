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

**Status:** shipping. `BUILDLOG.md` has every release and the current
version, `TASKS.md` what's done vs. open, RESEARCH.md the research behind
each decision. No section below is TBD any more; what's still undecided
says so where it applies.

## What is actually decided

- **Platform:** native Android, targeting API 36 (Android 16), **Kotlin +
  Jetpack Compose** (Portfolio's approach, not fantasy-football's WebView
  shell) — decided 2026-09-20: a live-updating, WebSocket-driven scanner
  UI (RESEARCH.md §7) fits native Compose's state model and Android
  background-service story much better than a WebView bridge would.
- **Target hardware:** a Moto G 2026 (researched 2026-09-29, GSMArena's review and Motorola's listing):
  MediaTek Dimensity 6300 (6 nm; 2x Cortex-A76 at 2.4 GHz + 6x A55 at 2.0 GHz), Mali-G57 MC2 GPU,
  **4 GB RAM**, 128 GB + microSD, 6.7" 720x1604 IPS LCD at **120 Hz**, 5,200 mAh battery, 30 W charging,
  Android 16. What that means for the app: little RAM, so Android kills background work early (long
  jobs run in a foreground service, as `ScanService`/`AutoScanService` do); a 120 Hz screen, so a
  state read every frame costs twice what it would at 60 Hz (the `compose-performance` skill); and
  the battery discipline the full test checks for (wake locks, polling, sleep when backgrounded).
- **Purpose:** find positive-EV opportunities on Novig — devig a reference
  line (a sharp book like Pinnacle/Circa if fetched, else average whatever
  major books were fetched — Tj's own instruction, 2026-09-20) and compare
  it to Novig's live price, as close to real time as the data sources
  allow. Tj places bets himself in Novig's app (Vigilant opens the bet
  slip; nothing places a bet on its own), and every bet he marks placed is
  tracked and settled (Tracker tab). Automated bet placement is not
  decided: ask before assuming.
- **Distribution:** sideloaded signed release APK, built and signed by
  GitHub Actions (not this container). Claude triggers the build via the
  GitHub API and confirms it went green; Tj gets a link, not a raw file.
  **Wired up 2026-09-20** — `.github/workflows/release.yml` builds, signs,
  verifies the signature, tags, and publishes a GitHub Release, no secrets
  needed (see the keystore section below for the model — this deliberately
  does *not* match Portfolio's secret-based one; it matches
  fantasy-football's committed-keystore one, Tj's explicit call).

## The keystore rule

**The keystore was generated 2026-09-20** and this rule is in force,
ported from this account's other two Android projects
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
credentials. **Decided 2026-09-29: keep the public committed key.** The
app has held a Novig `trading::read` key since 2026-09-25 (it reads; it can't
move money), and Tj's answer: "As far as keys, I'm not worried about app
security. Public is fine." Don't propose a Secret-held keystore again; a new
key would also force an uninstall that erases everything the app stores
(next paragraph).

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
  (the actual Android module: Compose UI, manifest, the foreground scan
  services). Deliberate: keeping
  `engine`/`data` Android-free is what lets their real logic be unit tested
  in a container with no Android SDK — see the next point.

**The dev container has no Android SDK until `tools/setup-android.sh` runs**
(build trap 6; the cloud environment's setup script runs it, so sessions
start with `/opt/android-sdk`). With it, all three modules build and test
here (`./gradlew :engine:test :data:test :app:testDebugUnitTest`); without
it, `engine` and `data` still do (plain Kotlin/JVM: the reason for the
module split). GitHub Actions (`ci.yml`) stays the authority on green and
builds every release.

## Build traps

Hit and fixed since 2026-09-20, standing into the future — don't
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
  This bit the app's first ViewModel (`ScannerViewModel`, since replaced): `by viewModels()`'s reflection-based
  factory looks for a true zero-argument constructor, doesn't find one
  without `@JvmOverloads`, and it's a **runtime** crash the first time the
  screen opens — compiles clean, so neither `./gradlew build` nor a code
  review that only checks for compile errors will catch it. Anywhere a
  ViewModel (or anything else instantiated via reflection by an Android
  framework class) has a default-only constructor, it needs
  `@JvmOverloads`.
- **Found and fixed 2026-09-20, bigger than the others: this
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
- **Found and fixed 2026-09-20: an explicit import of `weight`
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
- **Found and fixed 2026-09-22: cancelling an in-flight
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

### Build trap 6 (2026-09-25; one command since 2026-09-27; checked and sped up 2026-09-29): `bash tools/setup-android.sh`

The container has no Android SDK by default, and Maven Central answers 429 to Gradle here often.
`tools/setup-android.sh` (idempotent, self-contained, and it never fails: a step it can't finish prints
WARN and it still exits 0, because a cloud setup script that exits non-zero stops the session from starting)
fixes both:
1. SDK: command-line tools from dl.google.com into `/opt/android-sdk`, then `platforms;android-36`,
   `build-tools;35.0.0` (AGP 8.13's default, the one the build uses: before 2026-09-29 AGP fetched it itself
   mid-build), `build-tools;36.0.0`, `platform-tools`. Build with `ANDROID_HOME=/opt/android-sdk`.
2. Gradle mirror: `~/.gradle/init.d/mirror.gradle.kts` puts Google's Maven Central mirror
   (`https://maven-central.storage-download.googleapis.com/maven2/`) FIRST, inside
   `settingsEvaluated { }`, in `pluginManagement.repositories` and
   `dependencyResolutionManagement.repositories` only. Adding it to project repositories fails the
   build (`FAIL_ON_PROJECT_REPOS`). First, not last: Gradle stops at a repository that errors, so a
   429 never falls through to a mirror listed after it.
3. Robolectric mirror: Robolectric downloads its `android-all-instrumented` jar at test time by
   itself, outside Gradle's repositories (a mirror in step 2 doesn't cover it: exactly this failed
   on 2026-09-27 with "Failed to fetch maven artifact org.robolectric:android-all-instrumented").
   The script writes `vigilant.mavenMirror=<mirror>` to `~/.gradle/gradle.properties`, and
   `app/build.gradle.kts` passes it to test JVMs as `robolectric.dependency.repo.url`. CI never sets
   it and keeps Maven Central (verified 2026-09-27: with the property pointing nowhere and the jar
   cache cleared, the tests fail to fetch; with the mirror they pass).
4. `--prewarm` (what the cloud environment runs): builds a throwaway clone and runs one Robolectric test, so
   every Gradle dependency (1.3 GB) and Robolectric's android-all jar (191 MB) are on disk before Claude starts.
   It gets what's left of a 250 s budget, so the whole script stays under the ~5 minutes a setup script has
   to finish in to be snapshotted.
With it run, `bash tools/test.sh` (= `./gradlew test`: engine, data, and app incl. the Robolectric screen
tests, with a short summary) runs every test. `-Pscreenshots` writes PNGs of every screen to `app/screenshots/`
(gitignored), and `:app:assembleRelease` builds the R8-minified APK. CI stays the authority on green.

**Every account's cloud environment (checked 2026-09-29; environments belong to one account):**
- **Setup script**, one line, the same on all of Tj's accounts (always the current script from `main`, whatever
  repo the session is for): `curl -fsSL https://raw.githubusercontent.com/tjshea90/novig/main/tools/setup-android.sh | bash -s -- --prewarm`
- **Network access** must allow `dl.google.com` (Custom with it added, or Full). It is NOT on the default
  "Trusted" list; the mirror (`*.googleapis.com`), `maven.google.com`, `services.gradle.org` and
  `raw.githubusercontent.com` are.
- How it behaves (code.claude.com/docs/en/cloud-environments, "Setup scripts"): runs as root before Claude Code
  launches (after the clone); exit non-zero = no session; finishing within ~5 minutes gets the filesystem
  snapshotted and reused by new sessions for ~7 days (rebuilt when the script or the allowed hosts change);
  running processes are not kept.
- Measured here 2026-09-29 (4 cores): the full test floor from empty caches 210 s (1.5 GB through the mirror,
  zero 429s) vs 106-110 s with the caches the pre-warm leaves (and 2 MB downloaded); the whole script cold with
  `--prewarm` 210-235 s (SDK 13-65 s, pre-download 170 s).

**Why Central 429s here (researched 2026-09-27, Sonatype's own docs):** Maven Central rate-limits by
EGRESS IP on the aggregate traffic from that IP ("the source of the traffic may not be the build that
failed"), since its 2025 move to new CDN infrastructure with limits for high-volume consumers. Every
Claude Code on the web session exits through a small shared pool of cloud egress addresses and starts
with an empty Gradle cache (a Vigilant build pulls ~1 GB, thousands of requests), so the pool keeps
crossing the threshold and everyone on it gets 429s. Blocks start short and escalate for repeat
offenders (up to 30 days per Sonatype); repeated requests during a block extend it, so retrying
makes it worse. Nothing one account does changes the pool's total; only the platform (Anthropic)
can take it up with Sonatype ("infrastructure provider" path). The fix on our side is to not ask
Central at all: Google's mirror above. **Done on Tj's side (seen 2026-09-28):** this account's cloud
environment's **Setup script** installs the SDK and both mirrors before Claude starts (`/opt/android-sdk` and
`~/.gradle/init.d/mirror.gradle.kts` are there at session start); the one line above replaces whatever it
holds, on every account. Optional: report it to Anthropic (github.com/anthropics/claude-code issues) so they
raise it with Sonatype.
Sources: central.sonatype.org/faq/429-error/, central.sonatype.org/faq/429-contact-support/,
robolectric.org/configuring/.

## Locked architecture decisions

- **Accuracy and speed over mobile data and phone storage, always (Tj, 2026-09-30):** "My mobile data is fast and unlimited and my
  phone storage is large. Choose accuracy and speed over mobile data or phone storage always." Never gate a read to Wi-Fi, shrink a
  download or cache, or skip a source to save data or storage. (API credits and rate limits are a different budget: those still matter.)

- **Auto-bet (Tj, 2026-10-01; v0.39.0, RESEARCH.md §51): the only thing in the app that spends money with nobody confirming, so its rules are fixed.**
  Off by default, turned on only by Tj in Settings › Betting (after a plain confirm). It places CrazyNinjaOdds' bets only, through the betting API
  from the Vigilant wallet only (never the cash wallet), inside the background CNO auto-scan cycle, pregame only (never a game that has started, or
  starts within a minute). Every order goes through `ApiBetPlacer`/`ApiBetPlanner` (fresh fair odds, the edge still there at the real book price,
  the per-bet and per-day limits, an IOC at a ceiling that is never chased) under ONE lock shared with the Bet sheet. On top: Novig's own price read
  in the last minute; the order book's price for the outcome must match the price judged (±3 points) and the outcome Novig's price came from must be
  the outcome found; one bet per Novig market; an edge over 15% is never bet unattended; the wallet is read before each pass and caps each stake
  (under a cent = stop, and since 2026-10-02 an empty wallet also pauses every scan, as the Pause button does, until Tj resumes (`AutoBettor.walletRanOut`, once per emptying; Tj: "stop scanning and put the app to sleep once the wallet runs out of money"); the least a stake can be is one cent, Tj 2026-10-01, v0.39.3: a Kelly stake under a dollar is placed as it is; Novig refusing a small order, `ORDER_TOO_SMALL`, skips that bet and the size is remembered, it never stops auto-bet); an order whose answer is lost HALTS it until Tj resumes it and is never re-sent, and an order is marked in flight (saved) BEFORE it is sent so a
  process that dies mid-order leaves it halted; Novig refusing or the daily limit backs it off. Tj's longest-odds limit (`autoBetMaxOdds`, American; v0.39.2,
  default none) is checked twice, on the price the bet was judged at and again on the order book read just before the order (`BetLimits.maxOdds`, the
  planner), so a price that drifts out past it is never bet; favorites always pass; a Bet-sheet bet is never held to it. Every bet placed gets its own
  HIGH-importance notification with the stake and the EV (`AutoBetNotes`, channel `auto_bet_placed`). The check interval goes down to 5 s (a cycle's wait after a long
  cycle is 1 s there, 5 s above 15 s). Every Bet sheet has "Add money" (chips $1-$20 and a typed amount; it sends the transfer itself with a saved management key).
  An optional "every book must agree" switch (`autoBetAllAgree`) makes every book that prices both sides say +EV on its own ("5 of 5"), on top of the other criteria.
  Check odds now holds a focus (`FocusGate`, in memory, 15 minutes at most): the background cycle (so auto-bet), CNO's refresh, scans, the widget's rescans and
  the movers wait until it ends, and it looks for every closing line (`CloseBackfill.run(force = true)`).
  Every bet is tracked exactly like a Bet-sheet bet (`TrackedBet.auto` marks it). Loosening any of this needs Tj's word.
  **Only a phone restart switches auto-bet off (Tj, 2026-10-02 16:05Z: "I want the app never to turn off auto bet unless I turn it off. The default is auto bet off but
  only when opening the app after a restart or after I already turned off auto bet manually"; v0.44.3, RESEARCH.md §64.1):** `LaunchGate` keeps Android's boot count; the first
  look after a new boot (the boot receiver, else the first screen) saves `autoBet = false` and `autoScan = OFF` (`LaunchReset`) and the next screen says so. Nothing else turns
  them off: not a swipe out of Recents, a force stop, an update, a crash, Android ending the process, or a return from another app. Never add another reset without his word.
  (The v0.42.0 rule, "off at every reopen", and v0.44.2's "off after a close", are both replaced.)
  **Make orders / the Bids tab (Tj, 2026-10-03; v0.51.0, RESEARCH.md §70, NOVIG_API.md §17): the second thing that spends money unattended, so its rules are fixed too.**
  Off by default (`ScanSettings.maker`, the Bids tab's switch, with a confirm). Bids are priced from Vigilant's own scan (every line it prices), posted from the Vigilant wallet only, pregame only,
  each one `PO` (post-only: refused rather than taking) with a `ttl` (default 30 min, never past the start) at `PriceGrid.floor(fair / (1 + makerMargin))` (default 4%), only
  under Novig's offer (at or over it is a bet to take, not a bid), within the price window (0.10-0.65), on the chosen kinds (props, 1st-half/inning lines and team totals by
  default; game lines off: §70.2), with at least `makerMinBooks` books and a fresh fair. Every pass (`MakerDesk.cycle`, after each Vigilant scan and each background cycle,
  under the ONE order lock the Bet sheet, auto-bet and locks share) first reads what Novig did (fills logged to the Tracker as bets with the fair and EV when posted,
  `TrackedBet.maker`), then cancels a bid whose line is no longer wanted, moves it down at once when the fair falls and up only after 2 grid steps, re-posts one about to expire,
  and posts new ones cheapest first within `makerMaxBids`, `makerMaxDollars`, the wallet and the day's API limit (fills count; resting bids may not push it over). The same side is
  never bought twice (an open Tracker bet on it, or a filled bid); a partly filled bid isn't re-posted; a lost answer is never re-sent (found by its `clientId`); a post-only
  order Vigilant has no record of is cancelled. Switching bids off or pausing (an empty wallet pauses) cancels every bid at once (`AppContainer`). Each fill gets a notification.
  **v0.52.0 (Tj, 2026-10-03: "it only will make bets which are positive EV … It should not keep make orders long enough that they lose their positive
  EV … auto make bets just the same way that auto bet already takes bets … recommend bets to make and I manually approve or deny them"):** a bid's expiry is
  the earliest of the ttl, the start minus the stop window, and the fair's own freshness (`Freshness.maxAgeMs` from its oldest book price; a fair of
  unknown age is never bid on); every resting bid is at least the margin under the CURRENT fair or it comes down (a 4,000-case property test); at least
  `makerMinBooks` books must each put the bid at +EV on their own (worst-case devig), a sharp book saying no vetoes it (`makerSharpVeto`), game lines need a
  sharp book in the fair; sized like auto-bet (`makerStakeMode`, ¼ Kelly on the bankroll by default, never over `makerMaxStake` / `apiMaxStake`). A cancel
  is only queued, so a bid goes CANCELING and is watched until Novig confirms (a fill in that gap is recorded; the side gets no new bid until then);
  fills are read even when the order's record can't be; a lost answer is searched for by `clientId` in every list; a refused post-only side cools off 5
  min. Auto-make (`maker`) asks first to switch on and a phone restart switches it off (`LaunchReset`), as auto-bet. With it off, passes only watch the
  bids Tj approved (down when the fair falls under them, never moved or re-posted) and recommend new ones (`makerRecommend`: the tab's Approve / Deny,
  and up to 3 notifications a pass, each side once, Approve re-checking on the latest prices: `MakerActionReceiver`); Deny or Cancel by hand blocks that
  side until its game (`MakerDenials`, Undo in the tab). Turning auto-make off takes down only the bids it posted.
  **v0.53.0 (Tj, 2026-10-03: "I had auto make bids turned on, but it didn't actually make any bids by itself" … "make the bidding system always shown"):**
  passes judge a scan STILL RUNNING (its finished leagues' lines, every 20 s: a bid whose line it hasn't judged yet stays up, its `ttl` already bounds it;
  `MakerPlan.plan(partial)`), lines carry the last scan's Novig book up to 20 min old (`MakerLines.MAX_BOOK_AGE_MS`: the bid's price comes from the fair
  alone, `PO` refuses a cross), the Bids tab is in every scanner mode with one choice Off · Recommend · Automatic (`BidMode`), and picking a mode that bids
  turns on what it needs (`MakerSetup.set`: Vigilant's scanner, the background scan with Vigilant at least once a minute, scanning resumed; asks first when that
  would also start auto-bet). The +EV-only invariant, the expiry bound and every cap are unchanged.
  **v0.54.0 (Tj, 2026-10-03: "None of my auto bids were accepted"; RESEARCH.md §70.9):** an expiring bid is re-posted only when the new one would rest
  at least `minLifeMs` longer (a fresher fair), else it keeps its place to its expiry; bids that would lead their side go up first (`MakerPlan.PRIORITY`:
  leads, then the cheapest, then the most EV), a line's best bid leaving out Vigilant's own bids that were in the book read (`MakerLines.withoutOwn`); every
  bid not yet ended counts against the wallet (Novig holds nothing for a resting bid: verified, NOVIG_API.md §17); one fills read a pass for every bid
  (`fillsStartingAfter`), cancels confirmed by one re-read of the open orders, an order's own record read only if it never showed open (`MakerBid.seenOpen`),
  and a fills read that fails finishes nothing; the background cycle's pass is skipped within 15 s of another (`MakerRunner.BACKGROUND_GAP_MS`).
  Loosening any of this needs Tj's word.
  **Sharp-book confirmation (Tj, 2026-10-02; §60):** `ScanSettings.sharpConfirmAutoBet` / `sharpConfirmAlerts` (off by default): on top of every other criterion, a sharp book's own devigged
  two-sided price for the exact line and side, no older than `sharpConfirmMaxAgeSeconds` (never over 5 minutes), must show +EV at Novig's price now, and no fresh sharp quote may say otherwise
  (`SharpConfirm`, `SharpBooks`, `SharpGate`). Asked last, for a bet about to be placed or alerted, CNO's page vetoes for free; a check that can't answer is a skip, never a bet.
  **Diagnostics is a file for Claude (Tj, 2026-10-02; v0.43.0, RESEARCH.md §61):** Settings › Tools › "Share diagnostics with Claude" (and the report dialog's button) writes one text file
  (`DiagnosticsFile`: a read-me for Claude, the ranked findings from `Advisor`, what changed since the previous report, the recorder's sections, a JSON block) to the app's cache and opens Android's
  share sheet (`DiagnosticsShare`, FileProvider). The flight recorder under it (`EventLog`, `NetStats` + `NetInterceptor` on the shared client, `PerfStats`, `LogcatTail`, `DiagHistory`) records
  without a switch. **Keep it improving:** a new feature or a new failure mode gets its own `eventLog` event/counter (and an `Advisor` rule when it has a threshold worth acting on) in the same
  change; a rule never advises saving data or storage (Tj's rule above); free text from outside is masked (`ProblemLog.mask`) and a test asserts a key-shaped string never reaches the file.
  **Keeping it alive with the screen off (Tj, 2026-10-02; v0.41.0, RESEARCH.md §59):** `ScanSettings.autoScanKeepAwake` (on by default) makes `AutoScanService` hold a
  partial wake lock (CPU, never the screen) and run the cycles from its own loop while auto-scan runs faster than every 9 minutes (`KeepAwake.active`); the alarm is then only a safety net
  (`KeepAwake.watchdogAtMs`, never announced as the next scan). Alarms alone are throttled in Doze, so a fast schedule can't be kept that way. Never use a screen or full wake lock for it.
  Every cycle is written to the `CycleLog` (Diagnostics' Cycle record) with the screen and Doze state, so "does it keep its schedule overnight" is answered from the report, not guessed.

- **Vigilant MGM: the same app for BetMGM (Tj, 2026-09-27; v0.17.0, RESEARCH.md §25).** A second app,
  `com.tjshea.vigilant.betmgm` ("Vigilant MGM"), built by module `mgm` from `app`'s OWN sources and resources
  (never a copy) with `BuildConfig.BOOK = "betmgm"`; `app` sets `"novig"` and stays `com.tjshea.vigilant`.
  `AppBook` is the one switch for everything book-specific. Both applicationIds are permanent (keystore rules
  below), both sign with the same committed keystore, and both share one versionCode/versionName (read from
  `app/build.gradle.kts`). BetMGM's prices ride in the PropLine / The Odds API calls that fetch the fair line
  (no request just for BetMGM) and never price their own fair line. Each app scans only its own book.
  **DORMANT since 2026-09-27 (Tj):** every task, version and Release is Vigilant (Novig) only unless Tj
  names Vigilant MGM; `mgm/` is frozen at v0.17.0 and only in the build with `-Pmgm` (CLAUDE.md "Vigilant
  MGM is dormant").

- **No futures (Tj, 2026-09-29): "I'm not interested in futures bets. Leave those out of the app
  and don't investigate them further."** Futures markets never reach the +EV feed, the CNO list, the
  Games board or alerts; don't research them or build anything for them.

- **Novig data comes from Novig's official v3 API. Read
  [`NOVIG_API.md`](NOVIG_API.md) before touching any Novig client code.**
  (2026-09-25: Tj has beta access.) Public no-key routes cover the catalog and
  books. Signed routes (Ed25519/P-256, `NOVIG-V3`) cover the real-time
  websocket. The app should only ever hold a `trading::read` key, never the
  money-moving `management` key, and never commit any key. **This repo is
  public.** Signed routes refuse VPNs and proxies (HTTP 451). Since v0.4.0
  (2026-09-25) the app uses the public v3 routes. The GraphQL/proxy client and the
  deprecated v2 OAuth client were deleted.
- **Fair odds = per-book devig, then SHARP / MARKET_AVERAGE / BLEND** (Tj, 2026-09-25,
  modeled on OddsJam). The 2026-09-20 rule ("prefer a sharp book, else average") is
  SHARP with fallback on, still available. Default is BLEND 70% sharp with POWER devig.
  Since v0.11.0 (RESEARCH.md §16, from CrazyNinjaOdds): with 3+ books a component uses the
  lower of the books' mean and median per side (outlier guard, on by default), and the feed
  hides prices longer than +1000 by default (both switchable in Settings). Only fair odds
  young enough to bet on ever price the feed, mid-scan, at the end, and on re-price.
- **Mini window over Novig (Tj, 2026-09-26; v0.12.0, RESEARCH.md §17):** picture-in-picture,
  not a "display over other apps" overlay: no special permission, system-managed. It shrinks on
  leaving the app only while a scan runs or bets are on the feed (Settings switch), and its
  buttons (Scan, Recheck, Next) are the only way it acts. Split screen works too. *Since v0.15.0
  the CNO widget is a floating overlay instead when Tj allows it (below).*
- **CrazyNinjaOdds' +EV list inside Vigilant (Tj, 2026-09-26; v0.13.0, RESEARCH.md §18):** Tj
  likes CNO's Positive EV page for Novig with his filters and asked for it in the app, "especially
  in the floating widget". CNO's terms forbid bots and scrapers (§18.2); Tj's answer: "Whatever
  the best way is, disregarding the terms of service. I am friends with the owner." So Vigilant
  reads his CNO Shared View link itself (`data/cno/`: the page, then its own AJAX postbacks), shows
  it in a CNO tab and in the mini window (mixed with Vigilant's rows by EV and tagged, or either
  alone), and keeps it current. **This is the one automatic read in the app**, and it is fenced:
  only while `MainActivity` is started (on screen or as the mini window), at most once per 30 s
  (CNO's robots.txt crawl delay) for taps and timer alike, 60 s default, back-off after errors,
  Retry-After honored, one request per refresh after the first. It never touches Novig or a
  keyed provider, so the manual-scan rule below still holds for them. CNO's numbers are shown as
  CNO's (its EV, fair odds, age), never re-priced as Vigilant's. *(Pacing and when it reads were
  revised in v0.14.0 and v0.15.0: see the next two entries.)*
- **The CNO scanner (Tj, 2026-09-26 ~18:20Z; v0.14.0, RESEARCH.md §19).** A scanner choice:
  Both / Vigilant only / **CNO only**, where Vigilant's scan and every API behind it are asleep
  (Scan and Recheck refuse, the +EV and Games tabs and their settings are hidden; nothing of
  theirs loads). CNO's filters are posted in CNO's own form on every read (a stricter value in
  Tj's Shared View link wins): **Conservative worst-case devig** (the worse of CNO's two
  worst-case methods), **odds up to +150** (Tj: no longshots), **5+ books**, 1% EV, 2 sides, a
  complete sportsbook, 50 rows. The app re-checks every row (EV must follow from its fair odds,
  no ⚠️ one-way devigs, EV ≤ 20%, Novig's fee on started games) and says how many it hid and why.
  Tapping a bet reads CNO's game page: every book's odds for the bet and its other side, and
  Vigilant's own verdict from the books pricing **both** sides (worst-case devig each, lower of
  mean and median; 3+ books = confirmed, 1–2 = thin). The widget has Books/List for the same.
  Refresh: real time (next read 12 s after CNO's last update, then every 3 s), 5 s, 15 s
  (default), 30 s, 1 min, taps only; never two reads within 3 s; a stuck CNO (>10 min) is read
  every 30 s. "Open in Novig" follows CNO's deeplink to `novigapp://events/<id>/cno`, where
  `<id>` is a Novig **outcome** id: Novig's app opens with that exact bet in its bet slip
  (RESEARCH.md §20).
- **The CNO widget, and when CNO is read (Tj, 2026-09-26 ~20:15Z; v0.15.0, RESEARCH.md §20).**
  With CNO on, the widget is a **floating window drawn over other apps** (Android's "Display over
  other apps", asked once; `FloatingWidget` + `ui/FloatingFeed`) because picture-in-picture takes
  no touches: Up/Down always at the bottom, tap a bet = Novig's bet slip on it, ✓ = placed (hidden
  from the widget and the CNO tab through refreshes and restarts, `placed.json`, backed up, gone
  12 h after the game starts; Undo), hold = every book. Picture-in-picture stays the fallback (no
  permission, or the switch off) and is unchanged for Vigilant only; with CNO alone its buttons
  are Books/Refresh, Up, Down. **CNO is read only while someone is looking** (`CnoWatch`): the CNO
  tab with Vigilant started, the picture-in-picture window, or the floating widget while it's up,
  not shrunk to a bubble, and the screen on and unlocked. Other tabs, a closed widget, a locked
  phone, or Vigilant closed (backed out or swiped away) read nothing. Two slow lanes run only
  alongside the list: the **green ✓** (the 12 best bets' CNO game pages, one every ≥2 s, each
  again after 5 min; ✓ = 3+ two-sided books whose consensus is +EV and 3+ of which say so alone)
  and **player teams** from ESPN's free rosters (the one non-CNO read in CNO-only mode; two small
  reads per new game, cached a day in `teams.json`). Both have a switch in Settings.
- **Rechecks** (v0.11.0): Tj taps Recheck to re-read the feed's (≤40) or one bet's Novig books,
  with no fair-odds calls.
- **EV is always computed against Novig's executable taker price** (1 − best opposing bid,
  with depth), never last trade or mid. Stakes are fractional Kelly capped at +EV
  liquidity. Fees are read per market from Novig's `fee` object.
- **Manual scans only (Tj, 2026-09-25, after Novig 429s on v0.5.0).** Nothing requests
  odds from Novig or any provider unless Tj taps **Scan** or pulls to refresh: not on
  launch, not on a timer, not on a tab or settings change (those re-price from the last
  scan). Novig reads are paced (`RateGate`: 4/s rising to at most 6/s
  after clean runs, burst 10, 3 at a time, pause on Retry-After, halve and restart the ramp after
  a 429); with a key, books use the signed per-key route instead (14/s, under the documented
  16/s), and since v0.19.0 a **scan** also opens Novig's websocket with the key (Tj, 2026-09-28:
  "taking full advantage of the novig API key"; RESEARCH.md §27): one subscribe loads the whole
  plan ~8 s in, pushes keep it current, and it closes 2 minutes after the last scan or recheck
  used it, or at once off screen (a background scan's end, or leaving Vigilant with no scan running).
  No socket without a scan. Don't reintroduce auto-refresh without asking. The asked-for exceptions, each
  either on screen only or off until Tj turns it on: CrazyNinjaOdds' list (above; it reads CNO only),
  background auto-scan (below; off by default), and the widget's rescan (`widgetRescanMinutes`, off by
  default; Tj, 2026-09-27: a Vigilant scan every N minutes while CNO's list is on screen,
  `WidgetRescan`). Settling bets reads final scores, not odds (`SettleWorker`, every 3 h). First asked
  2026-09-20: "do not load any odds at all for any sport until I select the sport or sports and press
  refresh or pull down to refresh gesture".
- **Background auto-scan and +EV alerts, opt-in (Tj, 2026-09-28; v0.18.0, RESEARCH.md §26).**
  Settings › Background auto-scan: Off (default) / CNO / CNO + Vigilant, every 5 / 10 / 20 / 30 /
  40 min, with Vigilant closed. `AutoScanService` (foreground, `specialUse`: no daily cap like
  `dataSync`'s 6 h) runs only while it's on, with one quiet notification (Scan now, Stop); each scan
  is woken by an exact alarm (`USE_EXACT_ALARM`) and holds a wake lock only while it runs (≤20 min);
  restarted after a reboot or an update. A cycle: CNO's list, Novig's price now and CNO's game page
  for its best bets at or over the alert minimum (≤8), then with CNO + Vigilant a normal Vigilant
  scan. Alerts (Off / 2 / 3 / 4%, 3% default): one push per new bet at or over the minimum that 3+
  two-sided books agree on (CNO: `CnoBooks` CONFIRMED; Vigilant: `Agreement`, worst-case devig per
  book), placed/removed bets and games outside "Starts within" never; tap = the bet slip in Novig
  (`novigapp://events/<outcome>`); `alerts.json` keeps each bet to one alert, across scanners.
- **A scan Tj starts runs to the end in the background, and streams (Tj, 2026-09-25 ~18:05Z;
  v0.10.0, RESEARCH.md §15).** The scan lives in the app-lifetime `ScanRunner`, never in a
  screen; `ScanService` (foreground service, `dataSync`) holds the process for exactly one scan,
  with a progress notification and a 20-minute-capped partial wake lock (10 until v0.18.0's
  1,200-price scans), then stops itself; a
  "scan done" notification only when Vigilant isn't on screen. Novig books are read while the
  fair odds load (re-planned as each provider answers), most-promising first (open bets, last
  scan's +EV and near misses, then props/period lines, then main lines), and every 8 books the
  feed updates. Mid-scan the feed offers only prices read this scan. Without background
  auto-scan: no timers, no boot receiver, nothing when idle. Since v0.18.0 a scan's fair lines are
  devigged once per plan (`FairMemo`), not once per partial result, and the feed's edges read over a
  minute before the scan ends are read again (≤40) so a long scan never offers minutes-old prices.
- **API keys: plain JSON in app storage, several per provider, rotated by a usage ledger (Tj,
  2026-09-25 ~14:00Z; "don't worry about security, they are free keys").** `api_keys.json`
  (survives updates, in Android backup, export/import), moved once from the old Keystore store.
  `UsageMeter` (`usage.json`) meters every call per key: server headers where sent (The Odds
  API), local counts otherwise (pinnapi). `KeyPool` always starts at key 1, skips a key before
  it can't afford a call, and rests a spent key until its provider's reset (1st of the month /
  midnight UTC), so rotation falls back to key 1 after each reset. Limits and ToS: RESEARCH.md §12
  (pinnapi's terms forbid circumventing its rate limits: warned in Settings).
- **Leagues and markets (Tj, 2026-09-25 ~15:20Z).** Chip order NFL, NCAAF, MLB, WNBA, NHL, ATP, WTA
  (tennis since v0.19.0), then NBA, NCAAB, UFC, Boxing. Soccer, CFL, KBO and NPB are removed entirely (no 3-way markets remain).
  Alternative markets priced where a fair source exists (RESEARCH.md §13, §15): 1st-half/F5 spreads
  and totals, MLB 1st-inning totals (NRFI/YRFI, Kalshi `KXMLBRFI`), team totals, and NFL/MLB/WNBA
  player props that Kalshi quotes on the same line (incl. pitcher outs, earned runs, walks, pass
  completions since v0.10.0). Defaults since v0.10.0: 8 props per game, 300 Novig reads per scan. 1st-half
  moneylines are deliberately not priced (3-way vs 2-way, FMV voids). Per-game caps
  (`linesPerGame`, `propsPerGame`) and a per-scan budget (`maxBooksPerScan`, main lines and open
  bets first) keep scans fast and under Novig's limit.
- **Sportsbook props (Tj, 2026-09-25 ~16:15Z; v0.9.0, RESEARCH.md §14).** Player props from
  the books via The Odds API's per-game endpoint (`OddsApiPropsSource`), only for games Novig
  lists props for, soonest first, inside `bookPropCreditsPerScan` (default 24), re-used per game
  for `bookPropReuseMinutes`; only the stats Novig lists for the game are bought. Priced by the
  same engine: each book devigged, averaged, blended with Kalshi. Players match across books by
  `PlayerNames` (never surname-only). Needs an Odds API key; off switch in Settings.
- **Fair odds come from several free sources, merged per game (v0.6.0, RESEARCH.md §11):**
  Pinnacle via pinnapi (free key, 100 req/day), Polymarket and Kalshi (free, no key; count
  as sharp by default when ≤3¢ wide with real depth), and The Odds API (optional, re-used
  for `oddsApiReuseMinutes`). Spreads/totals are capped at `linesPerGame` per game because
  each priced line is one Novig request. Multiple free Odds API accounts: advised against
  (abuse clause), not needed.
- **v0.16.0 (RESEARCH.md §22): Pinnacle through PinnWire first** (free key, 100 req/day, includes
  Pinnacle's player props via `include_specials`; pinnapi keys are the fallback) **and PropLine**
  (free key, 1,000 req/day: every reference book's lines per league, props per game; exchanges,
  DFS and Novig itself never priced from it). Both optional, each behind its key and switch.
- **Bets settle from final scores (v0.16.0):** ESPN's free scoreboard/box scores and MLB's Stats
  API (`data/tracker/Scores.kt`, `BetGrader`); Novig's public catalog drops finished games, so it
  can't settle (NOVIG_API.md). A bet it can't read for certain stays open for a tap.
- **Reference-line source order (Tj, 2026-09-20: a sharp book alone when fetched, else the average
  of every major book):** superseded by his 2026-09-25 choice above ("Fair odds = per-book devig,
  then SHARP / MARKET_AVERAGE / BLEND"); the 2026-09-20 rule is SHARP with fallback on. Changing the
  default (BLEND, 70% sharp, POWER devig: `ScanSettings`) still needs Tj.
- **Devig method is a parameter, never hard-coded.** `DevigMethod` (engine)
  supports multiplicative/additive/power/Shin, matching OddsJam's own
  "pick your source of truth and method" model (RESEARCH.md §5) — and
  directly motivated by RESEARCH.md §8.1's finding that Odds Assist Pro's
  *undisclosed* method is exactly why its longshot edges can't be trusted
  blindly. Don't collapse this back down to one hard-coded method.
- **Novig's own fee must be netted into EV, and a fee we don't have a
  formula for must never silently become $0.** Each Novig market carries
  its own `fee` object (NOVIG_API.md §8), read into `MarketFee` (coefficient,
  maker credit, charged `WHEN_LIVE` or `ALWAYS`) and applied by
  `Fees.takerFee`: game markets 3% once live, NFL/MLB/NCAAF futures 6% pregame
  too, makers never. Vigilant's pricing skips any market whose fee can't be
  read (`Pricing`), and `NovigLive` prices CNO bets with their market's own
  fee. CNO's list re-check, its game-page verdict and tracked CNO bets
  use the game schedule (`MarketFee.GAME`); futures are left out of the app
  (below), so that's the schedule every bet there has. `EvMathTest` "a pregame edge can vanish once the
  live taker fee applies" proves a real, positive raw edge can net negative
  after the fee — don't "simplify" fees away, that's the exact failure mode
  this was built to avoid.
- **The UI must never present sample/demo data as if it were live.**
  Vigilant has no sample-data path any more; any future one must say so on
  screen.
- **API keys: plain JSON since 2026-09-25** (the "API keys: plain JSON in
  app storage" bullet above, Tj: "don't worry about security, they are free
  keys"). The first version encrypted them with an Android Keystore key
  (`EncryptedApiKeyStore`, `KeyCipher`); `VigilantApp.migrateKeys` reads that
  old store once to move any keys into `api_keys.json`. Never commit a key:
  this repo is public.
- **Leagues are multi-select chips** (since 2026-09-20; "Leagues and markets" above), and
  **nothing loads until Tj acts** ("Manual scans only" above, with its asked-for exceptions).
