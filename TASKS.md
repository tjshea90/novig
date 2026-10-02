# TASKS — the current job

## Tj's request, 2026-09-20 (his own words — full text in INBOX.md)

> All work will be done in the novig repo. Do not touch or make any changes
> to the other repos. They are to be read only. I'm starting a new project
> called novig. It will be an android 16 app optimized for a moto g 2026.
> The purpose of the app is to profit using the novig sports book. I will
> have Claude make the code, and trigger GitHub actions to sign and make the
> apk, then Claude send me a link to the finished APK.
>
> To begin, all work on this project needs to have a strong checkpoint
> system in place to save all progress and work without losing any data
> even if Claude usage is interrupted in the middle of a task.
>
> For this, review the linked repos called fantasy-football and portfolio.
> From those two repos, copy and adapt the checkpoint system for this
> project. Look for all the parts of the repos that have checkpoint and data
> save systems and figure out how they work then implement it in the novig
> repo. Make sure everything is adapted for novig, because a lot of things
> you read and copy only apply to the other projects. The checkpoint system
> you make should be based on the other repos but not exact copies because
> you may need to take out or alter wording or logic specific to those
> repos that don't apply here.
>
> Also copy and adapt the "run light test" and "run full test" systems in
> the portfolio repo

### Progress on this request

- [x] Read fantasy-football's and Portfolio's checkpoint mechanics in full:
      `resume.sh`, `ckpt.sh`, `autosave.sh`, `capture_inbox.sh` (ff only),
      `push.sh`, `unpushed.sh`, `toobig.sh`, `secretscan.sh`,
      `install-hooks.sh` + `session-root-hooks.json` + `tools/hooks/*`
      (Portfolio only), `test_resume.sh` (Portfolio only), `bootstrap.sh`,
      `ship.sh`, `record-release.sh`, and both `CLAUDE.md`/`BRIEF.md` files.
- [x] Design the adapted system for novig: Portfolio's session-root hook
      install (confirmed the only mechanism that actually fires hooks in
      this multi-repo container) as the base, PLUS fantasy-football's
      `INBOX.md`/`capture_inbox.sh` raw-message backstop layered in as a
      fifth hook event (`UserPromptSubmit`) that Portfolio's own template
      doesn't define. Deliberately dropped Portfolio's multi-repo
      AGGREGATION piece (each hook globbing every repo under the session
      root and running ITS `autosave.sh`, i.e. committing+pushing in it) —
      that would mean this repo's infrastructure executing `git commit`/
      `git push` inside fantasy-football's and Portfolio's real checkouts,
      which are read-only for this project's work. novig's installed hooks
      are absolute paths into this repo's own `tools/` only; verified this
      empirically too (see CHECKPOINT.md and `tools/test_resume.sh` section 4).
- [x] Implement `tools/`: `secretscan.sh`, `push.sh`, `unpushed.sh`,
      `autosave.sh`, `capture_inbox.sh`, `ckpt.sh`, `resume.sh`, `toobig.sh`,
      `install-hooks.sh`, `session-root-hooks.json`, `record-release.sh`
      (signature adapted — takes versionCode explicitly since the toolchain
      isn't decided yet, see its own header).
- [x] Write `.claude/settings.json` (standalone fallback), `.gitignore`,
      `bootstrap.sh`, `BRIEF.md` (honest about what's decided vs. TBD —
      Android 16/API 36, Moto G 2026, Novig-sportsbook-profit purpose are
      real; toolchain, keystore, and the actual profit strategy are not
      invented), `CLAUDE.md` (the full working agreement), `ship.sh`
      (refuses honestly — there's no build system yet to gate).
- [x] Adapt Portfolio's `test_resume.sh` hermetic suite for novig: same
      JSON-emission / hook-merge / checkpoint-numbering / secretscan /
      unpushed-refuses-to-guess checks, stripped of everything Android/
      Gradle/keystore-specific (none of that exists here yet), plus new
      checks for the INBOX capture path Portfolio doesn't have.
- [x] Copy and adapt Portfolio's "light tests" / "full tests" protocols into
      `CLAUDE.md`, written generically (no tabs/subsystems exist yet to name)
      but structured so a future session extends them the same way
      Portfolio names its own tabs and subsystems, instead of rediscovering
      the pattern from scratch.
- [x] Run the adapted `tools/test_resume.sh` for real and fix whatever it
      finds, before claiming any of this works.
- [x] Checkpoint and push everything for real (`tools/ckpt.sh`), confirm the
      hooks are actually installed and firing in this session, and report
      back honestly about what was verified vs. what is still TBD (the
      actual app, its architecture, and the release pipeline all remain
      unbuilt on purpose — this request was scoped to the checkpoint/test
      system only).

## Not yet started (deliberately out of scope for the checkpoint-system request above)

Tj's first message also describes the eventual app itself — an Android 16
app, optimized for a Moto G 2026, that profits using the Novig sportsbook,
with GitHub Actions building and signing the APK. The checkpoint-system
request above did not include building it.

## Tj's request, 2026-09-20T04:36:03Z (his own words — full text in INBOX.md)

> Research methods this app can use to find positive ev for novig sports
> book odds constantly updating in real time to capture odds movements and
> new positive EV bets. Currently I'm using oddsjam, but I cannot afford the
> subscription. Look for a way to do something just like oddsjam, but
> either free or less than 30 dollars per month. My goal is to build an app
> that is equal to or better than oddsjam at finding positive EV bets on
> novig. Begin deep research across the internet on what is needed for this
> app and how to do it so we can begin building it. Maybe make a research
> findings file permanently on GitHub in this repo so all research is
> saved.

### Progress on this request

- [x] Research what "positive EV" detection actually requires: a de-vigged
      fair-odds/true-probability model, a source of sharp/consensus lines to
      devig from, and a feed of Novig's own live odds to compare against.
      Found: Novig itself has no built-in vig to strip (peer-to-peer, no
      house margin) — the edge is crowd-convergence lag, not stale vig. See
      `RESEARCH.md` §2.
- [x] Research how OddsJam and similar positive-EV tools (OddsJam, Odds
      Assist Pro, Sharp Lines, AVO, RebelBetting, etc.) actually source
      their data and compute EV, as far as publicly documented, and what
      they charge. See `RESEARCH.md` §8 — OddsJam Gold is $199.99/mo; Odds
      Assist Pro is a free tool that already covers Novig and is worth a
      hands-on trial before building further.
- [x] Find real, current options for a live odds-data feed usable at
      $0–$30/mo. Biggest finding: **Novig has its own official public API**
      (REST/WebSocket/GraphQL, docs.novig.com) built for algo traders — see
      `RESEARCH.md` §4.1. This is the key cost lever vs. paying a reseller
      $79–$399/mo. Whether API credentials are actually free to obtain is
      the #1 open item (§10.1) — not yet confirmed with Novig directly.
- [x] Research the actual math: devigging methods (multiplicative,
      additive, power, Shin — formulas recorded), EV calc adapted for
      Novig's probability-price quoting, and real-time architecture for
      Android (foreground service + single WebSocket + Doze-aware, battery-
      conscious per BRIEF.md's Moto G 2026 constraint). See `RESEARCH.md`
      §5–7.
- [x] Write findings into a permanent, cited research file in this repo:
      `RESEARCH.md` (data sources, EV math, fee structure, Android
      architecture, competitive landscape, ranked list of open items).
- [x] Checkpoint the research file. Did not start writing app code from
      this request — architecture/build decisions still need Tj's sign-off
      per BRIEF.md's TBD sections, and RESEARCH.md §10 lists what's still
      unverified before that sign-off can happen for real (chiefly:
      confirming with Novig whether API access is actually free).

## Tj's request, 2026-09-20T05:01:28Z (his own words — full text in INBOX.md)

> Research odds assist pro. Does it actually find positive EV on novig? Is
> it as good as oddsjam?

### Progress on this request

- [x] Deep-dive Odds Assist Pro specifically: company is Upper 9 Media LLC,
      a small indie operation with thin public review history (1 Trustpilot
      review). Verified **hands-on** (not just marketing copy) by loading
      the live tool in the pre-installed Chromium and filtering it down to
      Novig-only: it genuinely surfaces real, current, dated +EV
      opportunities on Novig, for free, right now. See `RESEARCH.md` §8.1.
- [x] Compare it feature-by-feature against OddsJam: fewer books (~12
      state-gated vs. OddsJam's 50+), no disclosed devig method or
      source-of-truth picker (OddsJam has both), no visible bet
      tracking/CLV, free-tier row cap. Also found and flagged a real
      substantive concern: its largest claimed edges cluster on extreme
      longshot lines, exactly where simple (multiplicative) devigging is
      known to overstate underdog value — the edge numbers shouldn't be
      trusted blindly. See `RESEARCH.md` §8.1 for the full verdict and what
      this means for what "equal to or better than OddsJam" should actually
      prioritize building (disclosed/tunable devig + real bet tracking, not
      just matching the free edge-list UI).
- [x] Updated `RESEARCH.md` §8 (table + new §8.1) and §10 (open item 4
      resolved) with the deeper, hands-on-verified findings, cited.
- [x] Checkpoint.

## Tj's request, 2026-09-20T05:37:41Z (his own words — full text in INBOX.md)

> Begin basic coding of this app. Give it a catchy name, not something
> boring like "novig ev". Make a basic beta of the app, which should be
> able to act like oddsjam by devigging odds and using sharp books like
> Pinnacle or circa if possible or an average of major sports books. It
> should be able to pull this data real time or as frequent as possible to
> catch actual positive EV and not stale odds. Let me know if I need to do
> anything

### Progress on this request

- [x] Named the app **Vigilant** — a real word (watchful/alert, matching the
      real-time-scanning purpose) with "vig" hidden inside as a pun. No
      collision found with an existing betting-edge app of that name.
      Recorded in BRIEF.md.
- [x] Made the toolchain decision: **Kotlin + Jetpack Compose**, pinned
      versions (JDK 21, Gradle 8.14.3, AGP 8.13.2, Kotlin 2.3.10,
      compileSdk/targetSdk 36, minSdk 30, applicationId
      `com.tjshea.vigilant`). Recorded in BRIEF.md's Toolchain section with
      reasoning.
- [x] Scaffolded a 3-module Gradle project: `engine` (plain Kotlin/JVM),
      `data` (plain Kotlin/JVM), `app` (the actual Android/Compose module).
      Gradle wrapper generated and committed.
- [x] Built the devig/EV engine (`engine` module): `Odds`
      (American/decimal/Novig-price conversions), `Devig` (multiplicative/
      additive/power/Shin — RESEARCH.md §5), `Consensus` (prefers a sharp
      book when fetched, else averages every book fetched — Tj's own
      instruction, verbatim), `Fees` (Novig's fee schedule, RESEARCH.md §3
      — parlay fee explicitly `Unknown`, never silently $0), `EvCalculator`
      (RESEARCH.md §6's formulas). **29 unit tests, run for real in this
      container, all green** — including a caught-and-fixed real bug (the
      power/Shin devig solvers assumed positive margin; a bad 3-way test
      fixture exposed it, fixed by adding an explicit guard rather than
      just patching the test).
- [x] Data layer (`data` module, also plain Kotlin/JVM so it's testable
      here too): `NovigRepository`/`NovigApiClient` (real OAuth2 + REST
      client for RESEARCH.md §4.1's documented API — field-shapes are
      best-effort/inferred, flagged in code comments, RESEARCH.md §10 item
      5), `NovigWebSocketLiveFeed` (real OkHttp WebSocket client for the
      `tape` feed), `ReferenceOddsRepository`/`TheOddsApiClient` (real
      client for The Odds API, RESEARCH.md §4.3 — this one's request/
      response shape is the well-established public v4 format, higher
      confidence than Novig's), `SampleNovigRepository`/
      `SampleReferenceOddsRepository` (fixture data so the app runs today
      with zero live credentials), `EvScanner` (ties a Novig board to a
      reference line and produces ranked `EvOpportunity`s — this is the
      actual "act like OddsJam" orchestration). **25 more unit tests, all
      green**, including one built specifically to prove a real, positive
      raw edge on a live market goes net-negative once Novig's live taker
      fee is applied — the exact failure mode RESEARCH.md §3 warned about.
      54 tests total across `engine`+`data`, all passing for real
      (`./gradlew --configure-on-demand :engine:test :data:test`).
- [x] Basic Compose UI (`app` module): one screen (`OpportunitiesScreen`)
      listing ranked opportunities with Novig price/fair%/net-EV%/devig
      method/reference books shown per row, a rescan button, and — since
      the app defaults to sample data — a visible "SAMPLE DATA, not live"
      banner so it can never be mistaken for a real scan.
- [x] Got real build/test verification where this container actually can:
      `engine`+`data` compile and their 54 tests pass for real, here.
      `app` needed CI (no Android SDK locally — confirmed, recorded in
      BRIEF.md). **CI is now confirmed green for real** — run
      https://github.com/tjshea90/novig/actions/runs/35493330913, every
      step succeeded, including all unit tests across all three modules
      and `assembleDebug`, and produced a real 9.3MB debug APK artifact
      (`vigilant-debug`). Getting there required three real, CI-caught
      fixes, each recorded so a future session doesn't waste a round-trip
      rediscovering them:
      1. `android-actions/setup-android@v3` unconditionally tries to
         install the long-removed legacy `tools` SDK package and crashes —
         dropped it, use the Android SDK GitHub-hosted runners already
         ship with `ANDROID_HOME` set, just write the license-acceptance
         hash files directly.
      2. `android { kotlinOptions { jvmTarget = "21" } }` is a hard error
         on Kotlin 2.3.10 — migrated to the `kotlin { compilerOptions {
         jvmTarget.set(JvmTarget.JVM_21) } }` DSL.
      3. (Caught by review, not CI, before pushing — worth keeping in the
         same list since it's the same class of "would only fail at
         runtime, never at compile time" risk:) `ScannerViewModel`'s
         all-default-parameter constructor needed `@JvmOverloads`, or
         `by viewModels()`'s reflection-based factory would have found no
         true zero-arg JVM constructor and crashed the first time the
         screen opened, despite compiling fine.
- [x] Tell Tj plainly what he needs to do to go from this sample-data beta
      to live data: (a) sign up for a free The Odds API key himself
      (self-serve, no card needed for the free tier) and (b) contact Novig
      directly from his existing account to ask about official API access
      (RESEARCH.md §4.1/§10 — still unconfirmed whether that's free or
      what the process is). Neither blocks trying the beta today — done in
      chat, not just here.
- [x] Checkpointed multiple times through this (engine, then data, then
      app+CI, then each CI fix) rather than only at the end.

## Tj's request, 2026-09-20T06:34:37Z (his own words — full text in INBOX.md)

> Where is the apk

### Progress on this request

The literal current answer (a debug build sitting as an unsigned CI
artifact, requiring GitHub login) isn't the real deliverable CLAUDE.md's
"Releasing" section describes — a signed Release with a plain tappable
link. Treating this as the trigger for BRIEF.md's "the day real app code
exists" ordered plan (keystore → real release workflow → `ship.sh` gate),
since that day is today:

- [x] Generate the release signing keystore (`keytool`). Delivered
      directly to Tj (SendUserFile), fingerprint recorded in BRIEF.md.
      **Superseded below** — this Secret-based keystore is no longer used;
      see the 2026-09-20T06:45:23Z follow-up.
- [x] Wrote a real release workflow signing with a Secret-held keystore.
      **Superseded below.**
- [x] Filled in `ship.sh`'s real gate (versionCode vs. BUILDLOG.md, push,
      hand off to the trigger+confirm+record sequence). Still valid —
      the gate logic doesn't depend on which keystore approach is used.

## Tj's follow-up, 2026-09-20T06:45:23Z (his own words — full text in INBOX.md)

> On the other repos GitHub can make the apk without secret. It doesn't
> need to be secure

Checked both other repos directly rather than assume (`add_repo`, read
access): **Portfolio's real `android.yml` does use a GitHub Secret**
(`SIGNING_KEYSTORE_BASE64`) for its release keystore — so that's not the
"no secret" model. **fantasy-football's `build.sh` is** — it signs with
`android/debug.keystore`, committed straight into that public repo, using
Android's standard well-known debug password (`android`/`android`,
literally public by convention — every Android SDK install can generate a
byte-identical one). No GitHub Secret needed because there's no actual
secret material to protect. That's the pattern Tj means, and it's a
reasonable, already-precedented call for an app that (right now) ships
with sample data only, holds no real credentials, and isn't going through
Play Store.

- [x] Generated a fresh debug-style keystore for Vigilant (alias
      `vigilant`, well-known password, **committed directly to the repo**
      — not a secret). The earlier Secret-based keystore sent to Tj is
      abandoned/unused; told him plainly so he doesn't think he still
      needs to add those 4 secrets.
- [x] Rewired `app/build.gradle.kts` to sign with the committed file
      directly — no env vars, no secrets, works locally or in any CI run
      with zero setup.
- [x] Simplified `.github/workflows/release.yml` to match — no secret
      decode step, runs on `workflow_dispatch` with nothing but a green
      light needed.
- [x] Corrected BRIEF.md's keystore section, which had recorded the now-
      abandoned Secret-based approach as settled — replaced with the real,
      current approach and why, so it doesn't mislead a future session.
      Noted explicitly: this is a real, deliberate security tradeoff
      (anyone can forge an "update" signed with this same public key) that
      was fine to make with nothing sensitive in the app yet, and is worth
      revisiting the day the app holds real Novig API credentials.
- [x] Tried to trigger the release workflow — hit a real, previously-
      unknown blocker: `release.yml` 404'd because this repo's actual
      GitHub default branch was never `main`, it was still
      `claude/novig-checkpoint-tests-qqgnnb` from repo creation (GitHub
      only registers `workflow_dispatch` workflows from the true default
      branch). Tried to fix it myself via the API — the auto-mode
      permission classifier blocked it outright as a repo-admin action.
      Recorded as a 4th build trap in BRIEF.md, asked Tj to flip the
      setting himself.
- [x] Tj switched the default branch to `main` (2026-09-20T16:07:32Z).
      Confirmed via the API, confirmed `release.yml` is now registered,
      triggered the real release build.
- [x] First release run failed at the fingerprint-verification step —
      real bug, not a false alarm: `apksigner`'s digest output has no
      colons and is lowercase, unlike the colon-separated uppercase
      BRIEF.md records (`keytool`'s convention). The build+signing itself
      had already succeeded. Fixed by normalizing both sides before
      comparing, recorded as a 5th build trap, re-triggered.
- [x] Second run: every step green — build, signature verified against
      BRIEF.md's recorded fingerprint, tag created server-side, GitHub
      Release published with the signed APK attached (20MB,
      `vigilant-v0.1.0.apk`). Ran `tools/record-release.sh v0.1.0 1` to
      record it in `BUILDLOG.md` per the established process.
- [x] Sent Tj the Release page link as plain tappable text, not in a code
      block.

## Tj's request, 2026-09-20T19:45:42Z (his own words — full text in INBOX.md)

> Research online about the novig trading API see if it is free and how to
> use it

### Progress on this request

RESEARCH.md §4.1/§10 item 1 already flagged this as the single biggest
open unknown — the docs.novig.com pages read last session only said
"request your client ID and secret from Novig," with no pricing/process
info. This is a real second pass at answering it, not a restatement.

- [x] Deep-dive: checked Novig's own Developer Relations job posting
      (Dreamwork listing), business-model/funding writeups (Series B press
      coverage, AlleyWatch, etc.), CFTC exchange-rule filings, the help
      center (support.novig.us), and searched for any public waitlist or
      signup form.
- [x] Found no Reddit/Discord/blog posts from anyone who's actually gotten
      API access — nobody's written about using it publicly.
- [x] Concrete-as-possible answer (still not 100% confirmed — Novig has
      never published pricing): **probably not free, probably not
      self-serve.** Novig's own DevRel job posting names the API's target
      users as "market makers and liquidity providers, trading firms, and
      B2B or embedded partners" with "high-touch" relationship-based
      onboarding, not a signup form. Novig's business model explicitly
      charges institutional market makers for access to retail order
      flow — that's what funds commission-free retail trading, so giving
      the API away free works against their own monetization. There's a
      separate, formal "Market Maker" program requiring approval and a
      signed agreement per Novig's CFTC filings. No public pricing, no
      waitlist form, no self-serve dashboard found anywhere.
- [x] Updated RESEARCH.md: new §4.1.1 with the full findings and sources,
      §1's bottom line rewritten to lead with this (kept the original
      framing below it for context, not deleted), §10 item 1 narrowed to
      reflect what's now known vs. still genuinely unconfirmed.
- [x] Checkpoint.

**Bottom line for Tj:** still worth emailing Novig directly and asking —
costs nothing — but go in expecting a sales/partnership conversation, not
a developer signup form. In the meantime, SharpAPI's free 60-second-delayed
tier is the most realistic $0 path to real (if delayed) Novig data.

## Earlier request closed out

**"Where is the apk" (2026-09-20T06:34:37Z) is done.** Vigilant v0.1.0 is a
real, installable, signed APK Tj can download and sideload today.

## Tj's request, 2026-09-20T19:49:13Z (his own words — full text in INBOX.md)

> I found the API. I have to email and request the API.
> Good question — API access is handled by our developer team. Send an
> email to developers@novig.com. Include: who you are (and your
> company/product, if applicable), what you're building, what data or
> functionality you want, expected scale or usage. They review requests
> and guide next steps from there.
>
> Make the email for me and request what I need for this app

### Progress on this request

Tj found the real, concrete process (from Novig's own support widget,
apparently) that this session's web search never surfaced — a real
correction/addition to §4.1.1, not just an email-drafting task.

- [x] Updated RESEARCH.md §4.1.1 with this concrete finding: the actual
      documented process is emailing developers@novig.com with 4 specific
      pieces of info — this resolves "what's the process" even though
      pricing/approval-likelihood is still unknown until they reply.
- [x] Drafted the actual email, covering all 4 requested items honestly:
      individual/personal project (not overselling as a company), what
      Vigilant is, read-only access to live market data (REST + WebSocket
      `tape` feed) as the actual current need (no order-placement access
      requested — the app doesn't auto-trade), and personal-scale usage
      (single account, one connection, well under documented rate
      limits). Sent to Tj as a file (not committed to the repo — a one-off
      communication, not project documentation) with placeholders for his
      name/account email since this session doesn't have those.
- [x] Checkpoint.

**Next real step, not something to act on now:** waiting on Tj to send it
and on Novig's reply. Once that lands, update RESEARCH.md §4.1.1 with the
actual answer (free/paid/approved/conditions) — that's what finally
resolves this open item for good.

## Tj's request, 2026-09-20T20:14:38Z (his own words — full text in INBOX.md)

> Begin making the app functional, start by using SharpAPI free tier and
> the odds api. I have keys but make the app able for me to type in the
> keys. Give me options to add multiple keys and make a system for the
> app to switch keys automatically when my usage runs out on any key.

### Progress on this request

Researched real API shapes before writing clients (learned last session's
lesson about guessing formats): SharpAPI's actual `GET /odds` endpoint
(`https://api.sharpapi.io/api/v1/odds?sportsbook=novig`, `X-API-Key`
header, flat per-selection JSON rows, 429 + `Retry-After`/
`X-RateLimit-*` headers on rate-limit) and confirmed The Odds API's
documented `x-requests-remaining`/`x-requests-used` headers.

Architecture decision: **SharpAPI supplies the Novig leg** (its free tier
uniquely includes Novig among ~40 books — RESEARCH.md §4.2), **The Odds
API supplies the reference leg** (Pinnacle + consensus, RESEARCH.md §4.3)
— each provider used for the one leg only it can actually serve, per Tj's
own instruction to use both together.

- [x] `data` module: `KeyRotator` (pure, provider-agnostic multi-key
      rotation: tries each key in order, marks a key cooling-down on 429
      honoring `Retry-After`, marks a key exhausted on 401, moves to the
      next key automatically, throws a clear `AllKeysExhaustedException`
      only once every key is out) + 9 real unit tests, all green.
- [x] `SharpApiClient implements NovigRepository`, wired through
      `KeyRotator`, based on the real endpoint/response shape found
      (`GET /odds?sportsbook=novig`, `X-API-Key` header, flat per-selection
      rows grouped into 2-outcome markets). 9 real unit tests against
      `MockWebServer` with SharpAPI-shaped fixtures, all green.
- [x] Rewired `TheOddsApiClient` to take a `KeyRotator` instead of a single
      key string, same rotation behavior (429→cooldown, 401→exhausted).
      Existing tests updated (401-rotates, 429-rotates,
      all-keys-exhausted cases added), all green.
- [x] `app` module: `ApiKeyStore` (data-module interface) +
      `EncryptedApiKeyStore` (DataStore Preferences + Android
      Keystore-backed AES/256-GCM via `KeyCipher` — chose this over
      `androidx.security:security-crypto`/`EncryptedSharedPreferences`
      after research found it deprecated with no further releases
      planned), a `SettingsScreen`/`SettingsViewModel` to add/view (masked)/
      remove multiple keys per provider, and `ScannerViewModel` rewritten
      to build real repositories per-leg from whatever keys are currently
      stored, falling back to sample data independently per leg. The
      SAMPLE DATA banner now names which leg(s) — Novig, reference, or
      both — are still on sample data instead of one combined yes/no.
      Wired into `MainActivity` with simple state-based navigation
      (`rememberSaveable`, no nav library) — Settings ⚙ icon in the top
      bar, rescans automatically on returning from Settings so newly-added
      keys take effect immediately.
- [x] Verified what this container can for real:
      `./gradlew --configure-on-demand :engine:test :data:test` green
      (all data/engine tests passing, including the new `KeyRotator`/
      `SharpApiClient`/rewired `TheOddsApiClient` tests). Pushed; CI run
      for the `app` module (no Android SDK locally, so this is the only
      real compile check) — see run linked in the checkpoint this
      completed under.
- [x] Checkpointed through this in stages (data-module pieces, then
      app-module pieces, then the final MainActivity wiring), not just at
      the end.
- [x] Known v1 limitation, resolved by the next request below (sport
      picker): sport was hardcoded to NFL (`americanfootball_nfl`) in
      `ScannerViewModel` — The Odds API's free tier is credit-limited per
      sport queried, so scanning every sport by default would burn
      through it fast.

## Tj's request, 2026-09-20T20:40:31Z (his own words — full text in INBOX.md)

> Make the sports selection picker but do not load any odds at all for
> any sport until I select the sport or sports and press refresh or pull
> down to refresh gesture. Then push and trigger GitHub actions to make
> the apk

### Progress on this request

- [x] `data` module: `Sport`/`SportsCatalog` (curated list of The Odds
      API sport keys, pure Kotlin so it's shared by `EvScanner` and the
      UI picker without an Android dependency).
- [x] `EvScanner` takes a `List<String>` of sport keys instead of one —
      scans reference odds for every selected sport, merges the results,
      matches against Novig's board same as before. Returns immediately
      (no repository calls at all) when the list is empty — proven by a
      new `EvScannerTest` using call-tracking fake repositories. Updated
      the rest of `EvScannerTest` for the new constructor shape.
- [x] `ScannerViewModel`: replaced the `init { rescan() }` auto-scan with
      an explicit `Idle` state — nothing loads on app open. Added
      multi-select sport state (`toggleSport`); `rescan()` only performs
      a scan when at least one sport is selected, and is the single path
      both the refresh button and the pull-to-refresh gesture call.
      Also removed the one-request-old auto-rescan-on-return-from-Settings
      behavior, since it would silently violate this same "nothing loads
      without an explicit refresh" rule whenever a sport was already
      selected.
- [x] `OpportunitiesScreen`: a sport picker (`FilterChip` row, multi-
      select) above the opportunity list, content wrapped in Compose
      Material3's `PullToRefreshBox` for the swipe-down gesture (verified
      its real signature against docs before using it — matches what was
      written), alongside the existing FAB refresh button — both call the
      same `rescan()` — and a new `Idle` state with sport-aware hint text
      ("select a sport" vs. "press refresh") instead of showing anything
      before the user acts.
- [x] Verified what this container can
      (`./gradlew --configure-on-demand :engine:test :data:test`, green),
      pushed, confirmed CI green for the `app` module. First push actually
      **failed CI for real** (run 35536648655): a stray
      `import androidx.compose.foundation.layout.weight` resolved to an
      unrelated internal symbol instead of the `ColumnScope.weight` member
      extension it doesn't need an import for at all. Fixed by deleting
      the import; re-pushed; **CI confirmed green for real** on the fix
      (run https://github.com/tjshea90/novig/actions/runs/35536752615,
      conclusion=success).
- [x] Bumped versionCode 1→2 / versionName 0.1.0→0.2.0 (BRIEF.md's rule —
      v0.1.0/code 1 was already shipped, `release.yml` refuses to
      re-release an existing tag). Triggered `release.yml` via
      `workflow_dispatch`, confirmed it went green for real
      (run https://github.com/tjshea90/novig/actions/runs/35536855588,
      conclusion=success — build, fingerprint verification, tag, and
      GitHub Release all succeeded), recorded it in `BUILDLOG.md` via
      `tools/record-release.sh`, and sent Tj the v0.2.0 Release link as
      plain tappable text (not a code block).

## Tj's report, 2026-09-20T21:05:00Z (screenshots of v0.2.0's "Scan failed" error plus SharpAPI's dashboard/docs/Playground, his own words)

> Look at the screenshots. The app gave an error. Diagnose and fix it. I
> attached screenshots of websites on the sharpapi. Tell me what
> information you need

### Progress on this request

App screenshot showed: "Scan failed — All 1 SharpAPI key(s) are
rate-limited or invalid — add a new key or wait for a reset." (NFL
selected, one refresh attempted). SharpAPI dashboard screenshots showed:
1 key on the free tier ("1/1 keys"), that key's "Last used" timestamp
recent, a successful 200 Playground response with "10/12 remaining"
already shown on the free tier's 12 req/min budget, and confirmed Novig
is genuinely listed under SharpAPI's "Exchanges" ("Novig — commission-free
no-vig exchange") — so `sportsbook=novig` and the base URL/auth header our
`SharpApiClient` already uses are correct per SharpAPI's own docs.

- [x] Read `SharpApiClient.kt`'s actual request shape against the
      Authentication/Base-URL doc screenshots: `X-API-Key` header,
      `https://api.sharpapi.io/api/v1` base URL — both correct, ruled out
      as the cause.
- [x] Found a real, concrete bug while reading `SettingsScreen.kt`: the
      "Add a key" `OutlinedTextField` had no `keyboardOptions` at all, so
      it used Compose's default (autocorrect **enabled**) — a real risk
      for a long random token like an API key, since the IME can silently
      alter what's typed. Fixed: explicit `KeyboardOptions(capitalization
      = None, autoCorrect = false, keyboardType = Password)` — text stays
      visible (no `visualTransformation`), autocorrect/autocapitalize are
      off.
- [x] Found a real diagnostic gap in `KeyRotator`: `AllKeysExhaustedException`'s
      message was generic ("rate-limited or invalid") with no way to tell
      which one actually happened or why — meaning THIS exact bug report
      could only be diagnosed from screenshots, not from the app's own
      error text. Fixed: `KeyAttemptResult.RateLimited`/`Invalid` now
      carry an optional `reason` (the real HTTP status, e.g. "HTTP 429,
      Retry-After=45s" or "HTTP 401"), populated by both `SharpApiClient`
      and `TheOddsApiClient`, and `KeyRotator` includes the last failure's
      reason in the exception message. New test locks this in. Next time
      this happens, the on-screen error itself will say which.
- [x] Diagnosed the most likely root cause from the evidence available:
      SharpAPI's free tier is 12 requests/minute, and the Playground
      screenshots show Tj actively firing test "Recipe" calls (NBA
      Moneylines, NFL Spreads, NHL Totals, etc.) around the same time as
      the app's own refresh attempt — "10/12 remaining" after just Playground
      testing alone means that budget was already most of the way
      consumed before the app's own call. A real 429 from combined
      Playground+app usage on the same 1-key free tier is the simplest
      explanation consistent with every screenshot; the app's rejection
      in that case is correct behavior (refusing to show stale/wrong
      data), not a bug.
- [x] `./gradlew --configure-on-demand :engine:test :data:test` — all
      green, including the new failure-reason test.
- [x] Pushed, confirmed CI green for the `app` module for real
      (run https://github.com/tjshea90/novig/actions/runs/35538013562,
      conclusion=success).
- [x] Told Tj the diagnosis, the two fixes, and what to try next.

## Tj's request, 2026-09-20T21:17:00Z (his own words — full text in INBOX.md)

> Ship v0.2.1 now. I tried again same error

Note: the retry that produced "same error" was still on v0.2.0 — the
improved diagnostic message (which would say HTTP 429 vs. HTTP 401
explicitly) isn't in a built APK yet, so this doesn't yet tell us which one
it actually was. Once v0.2.1 is installed, the next failure (if any) will
say definitively.

### Progress on this request

- [x] Bumped versionCode 2→3 / versionName 0.2.0→0.2.1 in
      `app/build.gradle.kts` (BRIEF.md's rule — v0.2.0/code 2 is already
      shipped).
- [x] Triggered `release.yml` via `workflow_dispatch`, confirmed it went
      green for real
      (run https://github.com/tjshea90/novig/actions/runs/35538568015 —
      build, fingerprint verification, tag, and GitHub Release all
      succeeded), recorded it in `BUILDLOG.md` via
      `tools/record-release.sh`.
- [x] Sent Tj the v0.2.1 Release link as plain tappable text, and told
      him the improved error message will now say HTTP 429 vs. HTTP 401
      if the scan fails again.

## Tj's screenshot, 2026-09-20T21:41:00Z (v0.2.1's improved error message)

Screenshot shows: "Scan failed — All 1 SharpAPI key(s) are rate-limited or
invalid — add a new key or wait for a reset. Last failure: invalid (HTTP
403)." The v0.2.1 diagnostic fix worked exactly as designed — this settles
the open question from the previous report for good: **it is not the 12
req/min rate limit** (that would show HTTP 429). It's a 403 (Forbidden),
meaning SharpAPI is authenticating the key but rejecting this specific
request.

### Progress on this request

- [x] Ruled out rate-limiting definitively — the new diagnostic text did
      its job.
- [x] (SUPERSEDED, checked 2026-09-29: SharpAPI's free tier doesn't include Novig (RESEARCH.md §4.2, confirmed live
      §4.2.2: its $79/mo Hobby plan is needed); `SharpApiClient` was deleted, and Novig comes from its own v3 API since
      v0.4.0.) Two live hypotheses for a 403 specifically (not 401, which would
      mean a flat-out bad/revoked key): (a) the free tier's "Odds"
      access doesn't actually extend to the Exchanges category Novig is
      listed under (RESEARCH.md §4.2.1 already flagged the free tier's
      exact book coverage as unconfirmed beyond the marketing page), or
      (b) our request is missing a parameter SharpAPI's own Playground
      sends automatically (its UI has Sport/League/Sportsbook fields —
      our `SharpApiClient` only sends `sportsbook`+`limit`, no `sport`/
      `league`). Need one piece of information from Tj to tell them
      apart: set the Playground's Sportsbook dropdown to **Novig**
      specifically (his earlier screenshots were mid-test on DraftKings)
      and press Send — does IT also 403, or does it succeed? If it
      succeeds, get the exact cURL from its "Code Snippets" tab (or a
      screenshot) so `SharpApiClient` can be matched to it exactly.
- [x] Answer came back: **Novig 403s in the Playground too** — "may not
      have access." Confirms this is a real account/tier limitation on
      SharpAPI's free plan, not a bug in `SharpApiClient`'s request shape
      — no client-side fix can work around it. SharpAPI's own docs list
      Novig under "Exchanges," and evidently free-tier access doesn't
      extend there even though the endpoint/book is publicly listed.

## Tj's request, 2026-09-20T21:52:00Z (his own words — full text in INBOX.md)

> Novig showed 403 too. It says I may not have access. Research if other
> apis have novig for free. Does the odds api have it?

### Progress on this request

- [x] Updated RESEARCH.md §4.2 (corrected table + conclusion), new §4.2.2
      (the full corrected finding, sourced from SharpAPI's own Novig
      product page: "Available on Hobby plan and above," free tier scoped
      to DraftKings+FanDuel only), §1's bottom line, and §10 item 1
      (bumped to the top open item)/item 2 (resolved — answers the
      Pinnacle question too, since DraftKings/FanDuel-only rules Pinnacle
      out of the free tier as well). Corrected BRIEF.md's "Locked
      architecture decisions" entry that had recorded the wrong premise
      as a settled decision.
- [x] Re-verified The Odds API directly (their own betting-markets page,
      not the earlier session's summary): **confirmed no Novig** anywhere
      in their bookmaker/market documentation — direct answer to Tj's
      question.
- [x] Researched current alternatives fresh (not reusing stale "not
      confirmed" notes): **OpticOdds** — Novig only via a sales-gated
      "trial," no public pricing. **Betstamp** — "trial keys," demo-only,
      no public pricing. **MetaBet** — confirmed to include Novig, but no
      pricing published anywhere (their `/pricing` page 404s). **odds-api.io**
      — free tier is 2 recreational bookmakers (not Novig), and new free
      signups are currently paused entirely.
- [x] Reported findings to Tj: **no $0/mo path to real Novig data exists
      right now from any researched provider.** Novig's own official API
      (still pending their reply) is the only remaining lead that could
      be free; every paid alternative clears $30/mo (SharpAPI Hobby alone
      is $79/mo).

## Tj's question, 2026-09-20T22:05:00Z (his own words — full text in INBOX.md)

> Also is there anything else useful that sharpapi has that I should keep
> in the app

### Progress on this request

- [x] Cross-checked SharpAPI's pricing page and full endpoint/tier table
      directly (not reused from the earlier research). Free tier confirmed
      (again, consistent with the live 403): **12 req/min, exactly 2
      sportsbooks (DraftKings + FanDuel), 60s-delayed, pre-match only, REST
      only** — no live/in-play data, no opportunity-detection endpoints
      (those need Hobby+/Pro+), no streaming (paid add-on only).
- [x] One endpoint-tier claim from the docs page contradicted the pricing
      page and the live 403 evidence (claimed `/odds` etc. cover "all
      sportsbooks, no tier restrictions") — didn't trust it, since it's
      directly contradicted by Tj's own real 403 and the pricing page's
      explicit "2 sportsbooks" wording; noted as an unreliable summary,
      not treated as fact.
- [x] Concluded and told Tj honestly: **no, nothing in SharpAPI's free
      tier is worth adding.** Its free book coverage (DraftKings +
      FanDuel) is a strict subset of what The Odds API's free tier already
      provides (which also includes Pinnacle) — so it would add zero new
      capability to the reference leg, and the one thing it was picked for
      (Novig) isn't available free at all. No code changes made —
      `SharpApiClient` stays as-is, ready to work the moment there's a
      paid key or a policy change, not removed.

## Tj's request, 2026-09-22 (his own words — attachments, full text in INBOX.md)

> Overhaul this app, or if it is more efficient or logical, start fresh
> and delete the old app. Read and review the attachments. Make the app
> use the novig data from the method attached. Build the app using this
> information

Attachments: `novig_ev_scanner_briefing.pdf` (a project briefing) plus the
actual `novig-liquidity` PyPI package (`.tar.gz` sdist + `.whl`) it's based
on — a real, MIT-licensed, third-party Python package (`github.com/
Hurteau101/Novig_Liquidity_Template`) that reverse-engineers direct,
**unauthenticated** read access to Novig's own internal GraphQL backend
(`gql.novig.us/v1/graphql`, Hasura). This finally resolves this project's
long-standing #1 blocker (RESEARCH.md §10 item 1) — no official
Novig API reply has come back, but this is a different, already-working
path. Read the actual package source (not just the PDF summary) to verify
it firsthand before building anything on top of it.

**Real risk, told to Tj plainly, not buried:** this queries Novig's
internal backend directly with no login/account/API key of any kind — so
it can't get *Tj's account* banned the way a ToS violation on an
authenticated endpoint could — but it requires paid rotating residential
proxies specifically to dodge IP-based rate-limiting/anti-bot blocking,
which is a real ToS gray area now that Novig is a CFTC-regulated exchange.
Novig could block or blacklist the proxy IPs at any time without notice.
It's read-only, pregame-only, and mirrors what odds-aggregator tools
commonly do — not something to refuse building — but it must ship as an
explicit opt-in (no proxies configured → sample data, same pattern already
used for every other provider), never a silent default, and Tj sources the
proxy subscription himself.

### Progress on this request

- [x] Extracted and read the actual `novig-liquidity` v1.1.20 source
      (`novig_base.py`, `novig_api.py`, `models.py`) — confirms the PDF's
      claims exactly: `POST https://gql.novig.us/v1/graphql`, header is
      only `Content-Type: application/json` (zero auth), hard-fails
      without a `PROXIES` env var (`user:pass@host:port`, HTTP Basic Auth
      to the proxy itself), queries hardcode `status: "OPEN_PREGAME"`
      (pregame only), and got the exact two GraphQL query strings + the
      `price_to_american`/`calculate_liquidity` formulas + the
      `novig.onelink.me`/`novig.com` deep-link formats verbatim from
      working code.
- [x] Reviewed the existing app's architecture (`engine`+`data`+`app`
      modules) end to end before deciding overhaul-vs-rewrite: the devig/EV
      math (`engine`), the `NovigRepository`/`ReferenceOddsRepository`
      provider-abstraction + `KeyRotator` multi-key-rotation + encrypted
      Settings-screen key storage (`data`+`app`) are all solid, tested,
      already-shipped infrastructure that this new access method slots
      into directly — decided **overhaul, not rewrite**: the problem was
      always "no free Novig data source," never "wrong architecture."
- [x] Add `NovigGraphQlClient` (`data` module): real client for the
      verified GraphQL endpoint/queries, proxy pool reusing the existing
      `KeyRotator`/`ApiKeyStore`/encrypted-Settings-screen infra verbatim
      (a proxy string is just another kind of rotated credential) rather
      than building new plumbing, per-league concurrent event fetch,
      `last`-trade-price-preferred outcome pricing (best-effort — exact
      bid/ask semantics aren't documented anywhere, flagged honestly in
      code), moneyline-outcome-based team-name extraction for cross-source
      matching (Novig's own schema has no explicit home/away team fields,
      only a free-text event `description` — PDF §6 flags this
      "matching/normalization" problem as the real hard part, not the API
      calls). Also added `NovigLeagues` (Sport key → Novig league string
      mapping, best-effort) since the GraphQL API is queried per-league,
      unlike SharpAPI's single "give me everything" endpoint.
- [x] Delete `SharpApiClient`+test (dead: confirmed 2026-09-20 that
      SharpAPI's free tier categorically can't reach Novig — no future
      value, unlike `NovigApiClient` which stays dormant pending Novig's
      still-unanswered official-API email). Updated `NovigApiClient.kt`'s
      and `NovigLiveFeed.kt`'s doc comments to flag that the briefing found
      a second, independently-verified source contradicting the
      docs.novig.com-based OAuth assumption they were built on.
- [x] Update `Fees.kt`'s parlay case from `Unknown` to a real formula — the
      briefing confirms it for the first time (`price × (1-price) × 0.10`,
      same shape as the already-confirmed live-straight-taker fee but a
      0.10 multiplier instead of 0.03) — resolves RESEARCH.md §10 item 3.
- [x] Make `EventMatcher` order-independent (home/away swapped shouldn't
      cause a false non-match across two providers with different
      conventions) — a real correctness bug this new client's team-name
      extraction would otherwise expose, worth fixing regardless of source.
- [x] Wire proxies into `SettingsScreen`/`SettingsViewModel`/
      `ScannerViewModel` the same way API keys already work, with clear
      warning copy directly above the input field (not a separate consent
      toggle — pasting in a real proxy subscription's credentials already
      is the deliberate, informed action).
- [x] Real unit tests against fixture JSON matching the verified query
      shape exactly (proxy rotation, GraphQL parsing, price-source
      fallback, market-type mapping, team-name extraction) — run for real
      in this container (`engine`+`data` are plain Kotlin/JVM). **95 tests
      total across engine+data, all green** — 22 new `NovigGraphQlClient`
      tests, 2 new `NovigLeagues` tests, `EventMatcher`/`Fees`/
      `EvCalculator` tests updated for the behavior changes above.
- [x] Update `BRIEF.md`/`RESEARCH.md` with the new access method, the real
      risk disclosure (new RESEARCH.md §4.4, §9 rewritten to distinguish
      the actually-wired-in gray-area path from the still-dormant
      "official API" clean case, §10 items 1/3 resolved, two new open
      items for what's still best-effort/unconfirmed against real data).
- [x] Verify what this container can for real
      (`./gradlew --configure-on-demand :engine:test :data:test` — done,
      95/95 green), push, confirm CI green for the `app` module (no local
      Android SDK). Confirmed for real:
      https://github.com/tjshea90/novig/actions/runs/35690359093,
      conclusion=success — engine+data+app all compiled, every unit test
      passed, `assembleDebug` produced a real APK.
- [x] Bump version, ship, confirm the release build green, send Tj the
      Release link as plain tappable text — this is real, shippable work
      per CLAUDE.md's "Releasing" section, not left uncommitted-to-a-release.
      versionCode 3→4 / versionName 0.2.1→0.3.0. Triggered `release.yml`,
      confirmed green for real
      (https://github.com/tjshea90/novig/actions/runs/35690476182,
      conclusion=success — signed build, signature verified against
      BRIEF.md's recorded fingerprint, tag created server-side, GitHub
      Release published with the signed APK attached, 21.5MB,
      `vigilant-v0.3.0.apk`). Recorded in `BUILDLOG.md` via
      `tools/record-release.sh`.
- [x] Checkpoint through this in stages, not just at the end.
- **Deliberately not built:** SportsGameOdds as a reference-leg client
      (PDF §5's suggestion) — `TheOddsApiClient` already works, is already
      confirmed live against Tj's own key (RESEARCH.md §4.3), and adding a
      second unverified client for a leg that isn't broken would be scope
      creep beyond what this request actually needed. Worth building later
      if Tj specifically wants it (PDF's pitch: a devigged `fairOdds` field
      supplied directly, permanent free tier) — not done here.

## Tj's question, 2026-09-22T05:38:31Z (his own words — full text in INBOX.md)

> Are there any free proxies I can use for this? I have a VPN , and
> airplane mode gives me a new ip I think

### Progress on this request

Answered honestly in chat: no real free substitute for a rotating-proxy
*pool* exists (a VPN gives one non-rotating IP that may already be on
anti-bot blocklists precisely because VPN IPs are commonly used for this;
airplane-mode IP cycling depends on the carrier not using CGNAT, and can't
be automated by the app). But found and am fixing a real gap this exposed:
`NovigGraphQlClient` currently *requires* at least one proxy string
configured before it'll attempt live data at all (`KeyRotator`'s own
`init { require(keys.isNotEmpty()) }`) — so none of Tj's free options
(VPN, home network, airplane-mode-cycled IP) are even testable today. The
reference package's hard proxy requirement was written for continuous
high-frequency polling; this app only calls Novig on a manual refresh, a
much lighter volume that might just work directly.

- [x] Add a "direct access, no proxy" opt-in path: `NovigGraphQlClient`
      constructed with zero proxies makes requests directly over whatever
      network Android is currently routed through (a system-wide VPN app,
      if active, included automatically — no per-app proxy config needed
      for that to work) instead of refusing to run. A failure in this mode
      has nothing to rotate to, so it propagates immediately as a clear
      error rather than being silently retried (`NovigDirectAccessException`).
      `ApiKeyStore` gained `isNovigDirectModeEnabled()`/
      `setNovigDirectModeEnabled()` (a plain boolean, not a credential,
      folded into the existing store rather than a whole new one) and
      `EncryptedApiKeyStore` implements it via a plain (unencrypted —
      not a secret) DataStore boolean preference.
- [x] New explicit Settings toggle for this (separate from the proxy list
      — empty proxy list + toggle off still means sample data, unchanged
      default; toggle on is the deliberate "try it free" action), with
      honest copy: may get rate-limited faster than a real proxy pool
      would, but costs nothing to try. Wired through
      `SettingsViewModel`/`SettingsScreen`/`MainActivity`/`ScannerViewModel`.
- [x] Real unit tests, run for real — 97 tests green (2 new: constructs
      cleanly with zero proxies / with proxies configured — the first is a
      real regression test, since `KeyRotator` itself throws on an empty
      key list, so direct mode has to be a real branch, not a pass-through).
- [x] Update BRIEF.md/RESEARCH.md (done — new RESEARCH.md §4.4.1, BRIEF.md's
      architecture-decision bullet extended), verify app-module CI green,
      ship as v0.3.1, tell Tj it's ready to try for free. CI confirmed green
      for real (https://github.com/tjshea90/novig/actions/runs/35692029112,
      conclusion=success). versionCode 4→5 / versionName 0.3.0→0.3.1.
      Release confirmed green for real
      (https://github.com/tjshea90/novig/actions/runs/35692115606,
      conclusion=success — signed build, signature verified, tag created,
      GitHub Release published with the signed APK attached, 21.5MB,
      `vigilant-v0.3.1.apk`). Recorded in `BUILDLOG.md`.

## Tj's screenshot, 2026-09-22 (v0.3.1's direct-mode toggle, his own words in chat)

> "Scan failed — Novig rejected this request (no proxy configured to
> rotate to): HTTP 503"

He tried the new free direct-access toggle for real and hit a genuine
HTTP 503 from Novig on the first attempt.

### Progress on this request

- [x] Diagnosed a real bug the screenshot exposed: 502/503/504 (gateway/
      overload codes, very likely a CDN/anti-bot layer in front of
      `gql.novig.us`) were lumped into the same hard-rejection bucket as
      401/403, when they more accurately mean "temporarily unavailable."
      Fixed: reclassified as rate-limited/retryable; the direct-mode error
      message now says "temporarily rejected" for these instead of
      "rejected."
- [x] Added real HTTP-layer tests via MockWebServer for the direct-mode
      path (`NovigGraphQlClientTest`'s new section) — closes a gap
      explicitly flagged as untested when direct mode first shipped. 101
      tests total, all green.
- [x] Shipping this (v0.3.2) surfaced two real, unrelated `release.yml`
      bugs, both found and fixed along the way (see BRIEF.md's 7th build
      trap for the full account): the "refuse to overwrite" safety check
      only looked at an unreliable local shallow-clone tag check instead
      of the remote; a run cancelled mid-flight left a draft GitHub
      Release with no real attached tag that the check didn't know how to
      interpret and wrongly treated as a genuine published release. Fixed
      both — the check now self-heals both provably-safe leftover cases
      while still refusing a real published release.
- [x] versionCode 5→6 / versionName 0.3.1→0.3.2. CI confirmed green for
      real (https://github.com/tjshea90/novig/actions/runs/35693512531 —
      note: earlier in this session I badly misjudged this same run as
      "stuck for 20+ minutes" and repeatedly cancelled/retried it; the
      real GitHub timestamps show it actually completed in under 3 minutes
      each time — Tj caught this, and it's recorded here so a future
      session doesn't trust cumulative `ScheduleWakeup` delays as if they
      were confirmed elapsed real time). Release published
      (https://github.com/tjshea90/novig/releases/tag/v0.3.2, signed APK
      21.5MB, `vigilant-v0.3.2.apk`). Recorded in `BUILDLOG.md`.

## Tj's screenshot, 2026-09-22 (v0.3.2, after trying a free residential-proxy trial)

> "Scan failed — All 1 Novig (direct) key(s) are rate-limited or invalid —
> add a new key or wait for a reset. Last failure: invalid
> (ProtocolException: Too many tunnel connections attempted: 21)."

Progress — a proxy is now actually configured and being used (the error
names the proxy pool, not the zero-proxy direct-mode path). Different,
new error, not the same 503.

### Progress on this request

- [x] Diagnosed and confirmed a real, well-known OkHttp bug in
      `NovigGraphQlClient`'s own proxy `Authenticator`: it blindly
      re-attached the same `Proxy-Authorization` credentials on every 407
      challenge with no check for "already tried this," so a genuinely
      rejected credential caused OkHttp's own tunnel-building safety limit
      (`MAX_TUNNEL_ATTEMPTS = 21`) to trip instead of a clean, diagnosable
      auth failure. Fixed: gives up after one rejected attempt, per
      OkHttp's own documented Authenticator recipe.
- [x] Two new unit tests exercising the authenticator directly (attaches
      credentials on the first challenge; gives up on a repeat challenge)
      — 103 tests total, all green.
- [x] Updated RESEARCH.md §4.4.1 with the finding.
- [x] Shipped v0.3.3. CI confirmed green for real
      (https://github.com/tjshea90/novig/actions/runs/35695873352,
      conclusion=success). Release confirmed green for real
      (https://github.com/tjshea90/novig/releases/tag/v0.3.3, run
      35695667948, every step succeeded: build, fingerprint verification,
      tag, GitHub Release published with the signed APK attached, 21.5MB,
      `vigilant-v0.3.3.apk`). Recorded in `BUILDLOG.md` via
      `tools/record-release.sh`.

## Tj's screenshot, 2026-09-22 (v0.3.3, "Vigilant" app — image only, no text; full-text capture N/A since this message carried no text for capture_inbox.sh to log)

> Screenshot shows: "Scan failed — All 1 Novig (direct) key(s) are
> rate-limited or invalid — add a new key or wait for a reset. Last
> failure: invalid (IOException: Unexpected response code for CONNECT:
> 402)."

### Progress on this request

- [x] Confirmed the v0.3.3 authenticator fix is working as designed: this
      is a clean, single-shot error, not the old
      "ProtocolException: Too many tunnel connections attempted: 21" crash
      — the fix from ckpt 326/v0.3.3 is real and already proven by this
      exact screenshot.
- [x] Diagnosed the new error itself by reading `NovigGraphQlClient.kt`'s
      `executeViaProxy`/`executeGraphQl`: this exception fires from
      OkHttp's own proxy CONNECT-tunnel handshake (`java.net.http`/OkHttp
      internals), before the request ever reaches Novig's GraphQL
      endpoint at all — caught by the generic `catch (e: IOException)`
      block and reported verbatim as `KeyAttemptResult.Invalid(reason =
      "IOException: Unexpected response code for CONNECT: 402")`. HTTP 402
      is "Payment Required," returned by the **proxy provider itself**
      while establishing the tunnel — not a Novig response, not a bug in
      this app's request-building. Ruled out any code defect: the existing
      classification (any non-IOException-caught failure that isn't
      429/502/503/504/401/403 already falls through to a generic `Invalid`
      with the raw reason attached) is already exactly the intended
      "surface it verbatim so it's diagnosable from the error text alone"
      design proven working here.
- [x] Told Tj honestly in chat: this means the specific proxy currently
      configured in Settings is telling OkHttp "payment required" before
      even letting the connection through — almost certainly the free/
      trial residential-proxy plan he tried has run out of trial usage or
      needs a paid plan to continue, not something this app's code can
      route around. Recommended two options: (a) check that provider's own
      dashboard for trial/billing status and either pay or get a fresh
      trial credential, or (b) use the free "direct access, no proxy"
      toggle shipped in v0.3.1 instead (accepting the tradeoff already
      disclosed there — more likely to get rate-limited/blocked, per the
      503s already seen testing that path).
- [x] No code change made — nothing to fix; this is an external proxy
      account/billing state, not an app defect. Checkpointed the diagnosis
      itself so a future session doesn't re-diagnose this from scratch if
      Tj reports the same 402 again before switching proxy providers.

## Tj's message, 2026-09-25 (his own words — full text + 4 screenshots in INBOX.md)

> I now have access to novig API beta. [...] With the Sports Trading API,
> you can programmatically access Novig markets, view live pricing and
> market data, and place and manage orders directly on the exchange. [...]
> Early access is gated. [...] Read-only accounts are supported. You can
> create a separate read-only account to access market data without
> enabling trading functionality. [...] RFQs are not currently supported.
> [...] Record any useful information for this project in your permanent
> memory. Maybe make a file on GitHub for permanent research memory that
> Claude can see and understand even from fresh code sessions.
>
> Next step: review everything I sent and tell me what you need me to do to
> start making this app that scans for positive EV bets on novig

### Progress on this request

- [x] Read the official Novig API v3 docs for real (docs.novig.com
      llms.txt index, Overview, Account model, API keys, Signing,
      Quickstart/echo, Environments, Catalog, Order book / public stream,
      Rate limits, OpenAPI 3.1 spec) — not just the Overview page Tj pasted.
- [x] Write a permanent, cited API reference file in this repo that a fresh
      session can read cold (auth/signing, hosts, endpoints we need for
      read-only EV scanning, websocket channels, limits, what's unknown),
      and point BRIEF.md/RESEARCH.md/CLAUDE.md at it.
- [x] Compare against what's already built (`NovigApiClient`,
      `NovigLiveFeed`, `NovigGraphQlClient`) — note what is now wrong/stale.
- [x] Tell Tj exactly what he needs to do (keys, account type, what to
      paste where) — no app code in this step; he asked for the plan.
      Done: `NOVIG_API.md` (new). Pointers added in CLAUDE.md, BRIEF.md
      and RESEARCH.md §1/§10. Verified live from this container: the public
      `/v3/public/catalog/{events,markets,markets/{id}/book,.../trades}` and
      `/v3/public/types/*` routes return real production data with no key.
      Not verified: any signed route (Tj has no key yet).
- [x] Checkpoint.

### Proposed next build (not started — waiting on Tj's go-ahead, see NOVIG_API.md §9)

- [x] (DONE since v0.4.0, checked 2026-09-29: `NovigPublicClient` (data/novig) over `/v3/public/...`, executable
      taker prices, per-market `MarketFee`; the GraphQL/proxy path is deleted; futures fees modeled (`FeeCharge.ALWAYS`,
      `MarketFee.FUTURES`).) Stage 1, no key needed: add a `NovigV3PublicClient` (`data` module)
      over `/v3/public/...`. Use executable taker prices from the book
      (1 − best opposing bid, with depth) and per-market `fee`. Make it the
      default Novig leg. Retire the GraphQL/proxy path and its Settings UI.
      Fix `Fees.kt` for NFL/MLB/NCAAF futures (0.06, charged pregame).
- [x] (DONE, checked 2026-09-29: `NovigSigning`/`NovigSignedClient` (NovigV3Test, Novig's signing vectors),
      `NovigSetup` (management PEM, then a `trading::read` key), Settings › Novig API (`NovigKeySection`), and the
      websocket `NovigStream` since v0.19.0 (one market subscribe per scan instead of per-event `bbo`). The live check on
      Tj's phone is the "First real Novig key connect" box below.) Stage 2, needs Tj's management key once: `NOVIG-V3` signer
      (unit-tested against Novig's 30 signing vectors), an in-app
      "Connect Novig" setup (import management PEM + key ID, then echo,
      open a "vigilant" subaccount, then create a `trading::read` key held in
      Android Keystore (P-256), then forget the management key), and a
      websocket `bbo` subscription per selected event for real-time repricing.
- [x] (SUPERSEDED, checked 2026-09-29: fair odds come from several free sources instead (v0.6.0 pinnapi, Kalshi,
      Polymarket; v0.16.0 PinnWire, PropLine; BRIEF "Fair odds come from several free sources"), with The Odds API
      optional on its free tier.) Reference leg: Tj decides between staying on The Odds API free tier
      (500 credits/mo) and its $30/mo tier. This is now the freshness
      bottleneck, not Novig.


## Tj's request, 2026-09-25 (his own words — full text in INBOX.md)

> Start making the app with the free public novig routes, with options to
> add my novig API key soon. [...] Show me what you can do. I want an app
> similar to oddsjam that can find me a market "fair" devigged price using
> either sharp sports books or average odds across books or a blend, and
> compare these to real time novig odds to find positive EV. Consider the
> oddsjam app and how it works and its ui. Model it after that. Make sure a
> rugged checkpoint system is in place with frequent saves of progress
> because usage will run out. Make this app as best as you can, with full
> tests of the final app for efficiency and function and optimal code for
> my moto g 2026. Then use GitHub actions to make the APK

Plan (in priority order, so an interrupted session still leaves a shippable
app — ship after milestone A even if nothing else lands):

### Milestone A — official public Novig leg + OddsJam-style fair odds (ship as v0.4.0)
- [x] A1 `data`: `NovigPublicClient` over `/v3/public/...` (NOVIG_API.md §5):
      events → game-line markets → books; executable taker price
      (1 − best opposing bid) + depth; per-market `fee`; ETag/304 book cache;
      decimal (not float-string) price parsing. MockWebServer tests with
      fixtures shaped like the live responses recorded 2026-09-25.
- [x] A2 `engine`: fair-odds source = SHARP / MARKET_AVERAGE / BLEND Tests: `MiniWindowTest` (off by default, schema-10 migration, opt-in), `AltMarketsTest` (schema), `ScreenshotTest.settingsOfferTheMiniWindowSwitch`.
      (sharp weight %), devig per book then combine; `Fees` from the
      market's own fee object (fixes NFL/MLB/NCAAF futures 0.06 pregame);
      Kelly stake. Unit tests. DONE: FairValue/Fees/EvMath + WORST_CASE devig;
      `./gradlew :engine:test` 34/34 green (FairValueTest, FeesTest, EvMathTest,
      DevigTest, OddsTest). Old Consensus/EvCalculator/Models removed.
- [x] A3 `data`: scanner rework — match Novig markets to reference lines by
      team + line (spreads/totals need the same point), fetch books only for
      matched markets, league mapping Odds-API sport key ↔ Novig league.
- [x] A4 `app`: OddsJam-style UI — +EV feed cards (EV%, selection, Novig
      price vs fair, liquidity at price, Kelly stake, market width, age),
      filters (league, market, min EV), detail sheet with per-book odds,
      auto-refresh of Novig books while visible (lifecycle-aware, stops in
      background), manual refresh for reference odds (credit-limited),
      Settings: fair-odds source/blend/devig method/bankroll/Kelly.
- [x] A5 retire GraphQL/proxy path (client, proxy settings, direct toggle).
      DONE (A4+A5): compiles locally; ScreenshotTest renders all screens + 2
      interaction tests (103 tests green). GraphQL/proxy/v2 clients deleted.
- [x] A6 CI green, ship v0.4.0, send link. DONE: CI run 36100221957 green; release run
      36100439547 green; https://github.com/tjshea90/novig/releases/tag/v0.4.0 (2.86MB APK),
      BUILDLOG recorded, link + 4 screenshots sent to Tj (SendUserFile).

### Milestone B — Novig API key (opt-in, "soon")
- [x] B1 `data`: `NOVIG-V3` signer (Ed25519 + P-256), tested against
      Novig's published signing vectors. DONE: NovigV3Test — all 30 vectors'
      string_to_sign exact, Novig's published signatures verify over our strings,
      Ed25519/P-256 PEM round trips (BouncyCastle, deterministic ECDSA, DER).
      NovigSignedClient (echo/subaccounts/keys) + plain-English 401/403/423/451
      advice (NovigSignedClientTest). StreamBooks seq/gap logic (StreamBooksTest).
- [x] B2 `app`: Settings → Novig API: paste key ID + PEM (encrypted at rest),
      "Test connection" via `POST /v3/echo` with plain-English 401/451/423
      messages.
- [x] B3 websocket live book feed (signed `GET /v3/ws`, `book` channel per
      event) used automatically when a trading/trading::read key is set.

### Milestone C — tracker + full tests + ship
- [x] C1 bet tracker (log a bet from a card, settle, P/L + CLV-style stats),
      stored locally.
- [x] C2 full tests per CLAUDE.md (every module, efficiency, battery),
      extend CLAUDE.md's test protocol with the real screens.
      DONE: whole-app sweep found and fixed 4 fake-EV matching risks (school
      qualifiers, city-only pairs, series next-day pricing, started-but-pregame
      games) — 5 tests confirmed failing on pre-fix c8398ff — plus tracker write
      churn, stale feed on league change, stream crash on missing Keystore key,
      persistence crash paths, loop polling on non-price tabs, settings race,
      missing pull indicator, per-card tickers, Compose stability (verified via
      compiler report). 135 tests green (exit code + result XML checked);
      LiveNovigSmokeTest 3872/3872 on the real catalog; R8 release builds.
- [x] C3 ship final version, send link. DONE: v0.5.0 (code 9) — CI 36101866470 green,
      release 36102142715 green, https://github.com/tjshea90/novig/releases/tag/v0.5.0
      (4.6MB), BUILDLOG recorded, link sent to Tj.

### Next, when Tj reports back (not started)

- [ ] First real Novig key connect: confirm NovigSetup + stream against the live API
      (only verified offline/mock so far). If it fails, the on-screen error is
      Novig's own code translated; fix from that.
- [ ] First real The Odds API scan on device: confirm team matching vs real
      sportsbook names (Novig side verified live 3872/3872; the sportsbook side
      only against documented name formats).

## Tj's request, 2026-09-25 ~12:35Z (his own words — full text + screenshot in INBOX.md)

> I like the app UI so far. A few changes:
> 1) make it so it does not pull or request any odds from novig or any API in
> the app at all unless I manually press a button to scan for positive EV or
> manually pull to refresh.
> 2) review the attached screenshot, novig may be rate limiting my requests,
> see how to make the app have better efficiency and less chance of rate
> limiting.
> 3) research and read all of the documentation about the novig and the odds
> API sources used in the app. Optimize the app to meet the limits and specs
> of the providers. Make the app careful not to get banned or severely
> limited or restricted.
> 4) research and tell me the best way to have this app work without severe
> restriction on refreshing and being able to refresh odds many times per
> day. Consider all free apis or other ways to get updated odds from
> different sports books, especially sharp sports books. Research if there
> are other ways to pull these odds for free or very cheap. Consider if I
> should sign up for the odds API key on several email addresses and let the
> app use each key
> 5) do any and all research necessary to obtain the goal: I want this app to
> work for free or cheap by any means to find "fair" odds for many different
> markets and tell me all positive EV bets on novig, just like the oddsjam
> app. Right now it seems to work ok except it is rate limiting

Screenshot (v0.5.0, NFL): banner "Novig books: Novig is rate-limiting this device
(HTTP 429), retry in 1s"; status "Novig updating… · Fair 33s ago · 488 credits ·
slowed 1s"; 6 bets ≥1% EV, 88 prices checked. So the 15s auto-poll of ~44 public
books from a phone IP (likely carrier CGNAT, shared) trips Novig's per-IP edge limit.

### Plan
- [x] R1 Manual-only: no network on launch, league toggle, settings change, tab
      change, or timer. Only the Scan button and pull-to-refresh fetch anything.
      No auto websocket. Settings changes re-price from cache only.
      *Done v0.6.0: `MainViewModel.scan` is the only fetch path; live loop and stream
      wiring removed. Tests: `ScannerTest` "nothing is fetched before the first scan…",
      "changing a pricing setting re-prices from cache with no network", "turning a source
      off … drops its quotes on re-price, without a scan"; `ScreenshotTest`
      "beforeTheFirstScanTheFeedAsksForOneAndTheButtonScans".*
- [x] R2 Rate-limit-safe Novig fetching: client-side token bucket + low
      concurrency on public routes, global Retry-After pause, more paced
      retries, partial results served from cache with a clear message; with a
      Novig key, read books via signed per-key routes / one websocket snapshot
      instead of per-IP public routes.
      *Done: `RateGate` (4/s, burst 10, 2 at a time, pause + halve after 429) shared by
      catalog and books; signed `/v3/catalog/markets/{id}/book` with a key, public fallback.
      Tests: `RateGateTest` (3), `NovigPublicClientTest` "books are paced…", "the catalog
      shares the same pace…", "with a key, books come from the signed route…", "a refused
      key finishes the scan on public routes…". Live: 544 books across NFL/MLB/NCAAF, 0 429s.*
- [x] R3 Provider docs re-read (Novig throttling/errors/public, The Odds API
      limits/credits/terms) → encode limits in code; credits-aware reference
      reuse (don't re-pay for fair odds that are minutes old); only request
      the market families selected.
      *Done: Odds API re-use window (default 15m), only selected families, calls ≥1.5s
      apart; pinnapi shares one call per sport and parks on 429; Polymarket/Kalshi paged
      within their limits. Tests: `ScannerTest` "The Odds API is re-used inside its
      window…", "changing the reference books makes the next scan pay…";
      `TheOddsApiClientTest` "a scan asks only for the market families…", "back-to-back
      calls are spaced out…"; `ExchangeClientsTest` pinnapi share/429 tests.*
- [x] R4/R5 Research: free/cheap sharp + market odds sources (Kalshi,
      Polymarket, Pinnacle routes, other APIs, multi-key idea incl. ToS risk);
      write findings to RESEARCH.md with a clear recommendation; implement the
      best free source(s) if they check out.
      *Done: RESEARCH.md §11 (+ §11.5 live results). `PolymarketClient`, `KalshiClient`,
      `PinnapiClient` + multi-feed merge in `Planner` with a per-game line cap. Tests:
      `ExchangeClientsTest` (17), `PlannerPricingTest` "quotes from every feed … pooled",
      "a book two feeds both carry is priced once", "a date-only feed matches on the
      Eastern date", "spreads and totals are capped per game"; live
      `LiveNovigSmokeTest` "real scan - free sources match Novig games".*
- [x] R6 Tests (unit + screenshots), CI green, ship, report to Tj.
      *Done: 173 tests green locally and in CI run 36141456836; release run 36141782030
      published v0.6.0 (code 10, 4.65MB): https://github.com/tjshea90/novig/releases/tag/v0.6.0.
      Reported to Tj with the Q4/Q5 research answer and multi-key advice.*

## Tj's request, 2026-09-25 ~14:00Z — keys that survive updates, usage meters, per-provider key rotation, then full tests

> For this app, make sure all my API keys are safely stored in the app, even when the app is
> updated to a new version. Make a meter that shows me how much of each api was used after every
> call, so I know how much is left. Make it so for any API I can add multiple keys and the app
> automatically rotates keys when each key is depleted, then automatically resets back to the
> first key in each rotation when a new month or new limit resets (per provider). It has to have a
> smart way to meter this. Make sure to read the policy and rules for each API used, and the app
> should be within each API limit so it doesn't get banned or restricted. Don't worry about
> security on the API keys, they are free keys and I'm not worried about them. They may be saved
> to storage. After all of this is done, run full tests on the app and find ways it can be more
> efficient or better ui. Make sure the logic is in line with popular apps like oddsjam.
> Checkpoint frequently because usage will probably run out

### Plan
- [x] K1 Key storage that survives updates (and restores): plain JSON in app storage (Tj: no
      security needed), migrated from the old Keystore-encrypted store on first launch, included
      in Android backup; export/import of keys to a file as a belt-and-braces copy.
- [x] K2 Re-read each provider's limits/policies (The Odds API quota + reset timing + headers,
      pinnapi limits + reset + headers, Polymarket, Kalshi, Novig) → RESEARCH.md; encode them.
- [x] K3 Persistent per-key usage ledger + smart rotation for every keyed API (The Odds API,
      pinnapi): multiple keys each; server-reported remaining when the API sends it, local
      counting otherwise; skip a key before it runs out (cost of next call > remaining);
      depleted keys wait for their provider's reset (month / day / hour), then rotation starts
      again from key 1; survives app restarts.
- [x] K4 Usage meters UI: per provider and per key (used / limit, remaining, resets in …),
      updated after every call; keyless APIs (Novig, Polymarket, Kalshi) show requests this
      scan/today and any throttling.
- [x] K5 Tests for K1–K4 (ledger periods, rotation order, reset back to key 1, pre-emptive skip,
      persistence round trip, migration), screenshots.
      *Done: `FileApiKeyStore` (api_keys.json, one-time move from the Keystore store in
      `AppContainer.migrateKeys`, backup rules, export/import); `QuotaPolicy`/`UsageMeter`/`KeyPool`
      (usage.json); meters in Settings + feed strip. Tests: `UsageMeterTest` (14: periods, rotation
      from key 1, back to key 1 on the 1st, pre-emptive skip, 6h re-probe, billing-cycle follow,
      pinnapi minute/day counting, refused key, restart persistence, keyless daily reset, pool
      message, burst wait, meter view states), `FileApiKeyStoreTest` (3), `TheOddsApiClientTest`
      "every call's usage headers land in the meter", "a key that can't afford the next call is
      skipped", "a wrong key is reported as refused"; `ExchangeClientsTest` pinnapi daily-429
      rotation + "with every pinnacle key spent…"; `ScreenshotTest`
      "theMetersShowWhatsLeftPerKeyAndWhichKeyIsInUse" + 5b_usage_meters.png.*
- [x] K6 Full tests (CLAUDE.md protocol): whole-app sweep, efficiency + UI improvements, logic
      checked against OddsJam's model; fix with failing-first tests; ship; report.
      *Done: fixes: (1) a Novig key missing from the Keystore (restore to a new phone) failed the
      whole price read; now falls back to public prices (`NovigPublicClientTest` "a key that can't
      sign…", failed before the fix); (2) open bets' lines pinned past the per-game cap so CLV
      keeps updating (`PlannerPricingTest` cap test, pinned assertion); (3) stale-scan banner
      (`ScreenshotTest` anOldScanWarnsBeforeBetting…); (4) Best EV / Soonest sort (`PlannerPricingTest`
      "feed can be ordered by start time", `ScreenshotTest` theFeedCanBeSortedBySoonest);
      (5) one credits number (usage strip) instead of a single key's; clearer summary and section
      names. EV%, Kelly and devig checked against OddsJam's definitions. 190 tests green;
      CI 36151518252; release 36151929213 → v0.7.0 (code 11):
      https://github.com/tjshea90/novig/releases/tag/v0.7.0*

## Tj's request, 2026-09-25 ~15:20Z — alternative markets (props, halves), league order, remove leagues

> Add alternative markets to NFL, wnba, MLB and ncaaf such as player props, halftime odds, etc.
> Put these sports tabs in front of the others (from left to right): NFL, ncaaf, MLB, wnba, nhl.
> remove the following sports from the app entirely:
> All soccer
> Cfl
> Kbo
> Npb

### Plan
- [x] A1 League list: NFL, NCAAF, MLB, WNBA, NHL first (in that order), then the rest; remove every
      soccer league, CFL, KBO, NPB from the app (list, sources, settings migration drops them from
      saved selections).
- [x] A2 Research: which alternative markets Novig lists for NFL/NCAAF/MLB/WNBA (player props,
      1st half / 1st 5 innings, team totals…) and which fair-odds sources price them (pinnapi
      periods + specials, Polymarket, Kalshi, The Odds API event markets and their credit cost).
- [x] A3 Implement the alternative markets that have a real fair-odds source: Novig parsing,
      reference parsing, matching (player names), planner/pricing, settings toggles, per-scan
      request/credit limits.
- [x] A4 UI: market filters for the new families, labels on cards/games.
- [x] A5 Tests (unit, live smoke, screenshots), CI, ship, report.
      *Done: RESEARCH.md §13; `AltMarketsTest` (12: names, props same player/stat/line only,
      props cap, team total with a swapped feed, 1H never vs full game, F5 label, budget, league
      order + removals, migration, Kalshi props/TT/1H parse, pinnapi num_1 + team totals);
      `NovigTextTest` subjectOf. Live (2026-09-25): NFL 14/16 games matched with 1H spread/total,
      team totals and 10 prop stats priced; MLB 17/20 with F5, team totals, 5 prop stats; no
      errors or 429s. Screenshot 1_feed shows a prop and a team total. Shipped as v0.8.0 (code 12).*

## Tj's request, 2026-09-25 ~16:15Z — sportsbook props, market-average devig, robust matching

> Other major sports books offer props. See if you can make a market average then devig for the
> props. Make sure the app matches odds between different sports books, because they may have
> slightly different names of teams or ways of listing props.

### Plan
- [x] P1 Research how to get major sportsbooks' player props (The Odds API event odds: market keys
      per sport, response shape, credit cost; any free alternative and its terms).
- [x] P2 Sportsbook props source: per-game prop odds from DraftKings/FanDuel/BetMGM/Caesars/etc.
      only for games Novig lists props for, soonest first, inside a per-scan credit budget, re-used
      for a while; every call metered and rotated through the key pool.
- [x] P3 Market-average devig for props (each book devigged on its own, then averaged; minimum
      books), blended with sharp sources (Pinnacle, Kalshi) as for game lines.
- [x] P4 Matching across books: player names (suffixes, initials, accents, nicknames, "Last,
      First"), prop listing styles (Over/Under vs "N+" ladders vs Yes/No), stat names per book,
      team names; one-sided and alternate-only markets skipped.
- [x] P5 Settings (on/off, credits per scan, time window, re-use), tests, live check, ship, report.
      Shipped v0.9.0 (code 13): https://github.com/tjshea90/novig/releases/tag/v0.9.0

Done (tests): P1 → RESEARCH.md §14 (market keys re-checked against their page; Novig stat names
checked live). P2 → `OddsApiPropsTest` (game list free + props metered; only games with Novig
props, inside the window, soonest first across leagues, within credits; per-game re-use; props
off = no calls; partial failure keeps what was bought) and `ScannerTest` (needs-catalog source
gets the board; partial answer priced and reported; book-only stats fetched only when on).
P3 → `OddsApiPropsTest` "a Novig prop prices at the books' devigged average…". P4 →
`PlayerNamesTest` (3 of 4 fail on the old matcher) + parsing tests (Yes/No, one-sided, alternates).
P5 settings → `CreditEstimateTest`, `ScreenshotTest.settingsOfferSportsbookPropsWithTheirCreditBudget`.
Live: Novig + Kalshi real scan still clean (NFL 14/19, MLB 17/20); no Odds API key here, so the
props calls themselves are fixture-tested only.

## Tj's request, 2026-09-25 ~18:05Z — background scanning, faster scans / streaming results, OddsJam-like market coverage

> A few things to investigate or change for this app:
> 1) Make sure it can run in the background without stalling, because I will run the scan then
> switch apps and let it scan in the background.
> 2) research safe ways to speed up the scanning. Oddsjam refresh is very fast. This app is very
> slow. If it is not possible to speed up, make the results show up in the app as they come in
> (instead of showing all the results at the end of the scan)
> 3) oddsjam scans a wide range of props and halftime / f5 markets. Try to make this app like
> oddsjam and include markets most likely to have positive EV.

### Plan
- [x] B1 Background: a scan started in the app keeps running when Tj switches apps (foreground
      service while a scan runs, stops itself when done; no wake lock or polling when idle).
      *Done: `ScanRunner` (app-lifetime scope) + `ScanService` (dataSync FGS, progress
      notification, 10-min-capped wake lock, "scan done" note when off screen, stops itself).
      Tests: `StreamingScanTest` "the runner scans in the app's scope…refuses a second start",
      "a scan that blows up keeps the last good result…"; `ScanTextTest` (2).*
- [x] B2 Research why a scan is slow (where the time goes: Novig catalog/books pacing, reference
      calls, sequential waits) and which speed-ups stay inside each provider's limits.
      *Done: RESEARCH.md §15 (books waited for the slowest source; no bulk book route in v3; the
      websocket needs a key; live pacing check 240 books, 0 refusals).*
- [x] B3 Implement the safe speed-ups found in B2.
      *Done: book reads pipelined with the fair odds, most-promising-first order, public pace
      4→6/s ramp (3 in flight), keyed 14/s. Tests: `StreamingScanTest` "Novig's prices are read
      while a slow fair-odds source is still answering", "a rescan reads last scan's +EV lines
      first…", "open bets are read before anything else", "a scan never reads more than the
      per-scan limit"; `RateGateTest` (3 new ramp tests).*
- [x] B4 Stream results: the feed fills in as each league/book batch is priced, not only at the end.
      *Done: partial result every 8 books; mid-scan feed shows only prices read this scan. Tests:
      `StreamingScanTest` "results stream in as Novig's prices land…", "mid-scan, last scan's
      prices stay off the feed…"; `ScreenshotTest` feedStreaming, feedScanningBeforeFirstPrices.*
- [x] B5 Market coverage like OddsJam: widen props / 1H / F5 / other alt families that have a fair
      source and are most likely +EV; default them on where sensible.
      *Done: MLB NRFI/YRFI (`FIRST_INNING_TOTAL` vs Kalshi `KXMLBRFI`), pitcher outs / earned runs
      / walks, NFL pass completions from Kalshi; props per game 4→8, reads per scan 200→300
      (migrated only from old defaults). Tests: `AltMarketsTest` "kalshi's first-inning run
      market…", "a Novig first-inning total prices against the 1st inning only…", "saved settings
      still on the old coverage defaults widen…". CI 36174449917 green.*
- [x] B6 Tests (unit + screenshots), CI, ship, report.
      *Done: CI 36174449917 green (engine, data, app incl. Robolectric); release 36174969512 →
      v0.10.0 (code 14, 4.77MB): https://github.com/tjshea90/novig/releases/tag/v0.10.0.
      ship.sh's local Gradle step couldn't run in this container (Maven Central 429s; the
      mirror init script was blocked by the auto-mode classifier), so CI was the test gate.*

## Tj's request, 2026-09-26 ~01:47Z — full tests, better features/scanning, OddsAssist + CrazyNinjaOdds research

> Run full tests on this app, try to improve the features and scanning, then research the following
> websites:
> https://pro.oddsassist.com/advantages/plus-ev
> https://crazyninjaodds.com/site/tools/positive-ev.aspx
> Figure out if these websites truly offer positive EV bets, and for novig. If so, can they somehow
> be incorporated in my app or improve the app in any way?

### Plan
- [x] C1 Full tests (CLAUDE.md protocol): automated floor, whole-app sweep (feed, games, tracker,
      settings; engine, data/novig, reference, match, scanner, keys, store, background scan), fix
      with failing-first tests.
      *Done: floor run locally for the first time with Robolectric (build trap 6 corrected): 256
      tests green. Fixes: (1) stale fair odds priced the final result and re-prices when a metered
      source ran out mid-scan (`ScannerTest` "a metered source that runs out never prices a later
      league from an hours-old snapshot", failed before); (2) tracker averages counted voided bets
      (`BetTrackerTest` "a voided bet counts toward nothing…", failed before); (3) old Novig prices
      (served from cache) weren't flagged per card: "old price" label + banner by book age
      (`ScreenshotTest` oldPricesWarnBeforeBetting…); (4) screen clocks tick only while on screen
      (`rememberNow` + `repeatOnLifecycle`; source-level); tests pin the screen clock (`LocalClock`).
      Engine math cross-checked against CrazyNinjaOdds' devigger (`CrossCheckTest`, 4 lines equal).*
- [x] C2 Improve features and scanning (what the sweep and the research below turn up).
      *Done: outlier guard (min of mean/median, 3+ books; `FairValueTest` 2 new), longest-odds cap
      (+1000 default; `ResearchFeaturesTest`), Recheck (feed ≤40 books or one bet, no fair-odds
      calls; `ScannerTest` 2 new, `ScreenshotTest` 2 new), maker bid on Novig's grid (`EvMathTest`
      2 new, `ResearchFeaturesTest`, `ScreenshotTest.detailMaker`), CrazyNinjaOdds double-check link
      (`ResearchFeaturesTest`), settings for both (`ScreenshotTest.settingsOfferTheOutlierGuard…`).*
- [x] C3 Research pro.oddsassist.com plus-EV: data sources, devig method, Novig coverage, whether
      its +EV is real (hands-on in Chromium where possible), terms on reuse.
      *Done: RESEARCH.md §16.2 (headline edges are +2400..+4900 longshots; Pinnacle page sane
      2.7-4.9%; ToS forbids bots/scraping). Novig-only filter not re-run: second Chromium session
      blocked by the permission classifier.*
- [x] C4 Research crazyninjaodds.com positive-EV tool: what it computes (devig calculator vs. a
      live feed), methods, Novig coverage, terms.
      *Done: RESEARCH.md §16.1 (live feed incl. Novig with $ liquidity; 3 Novig rows 4.8-6.1% at
      $5-$15; disclosed worst-case avg/median method; devigger URL autofill; odds from OddsBlaze).*
- [x] C5 Verdict + what (if anything) to incorporate into Vigilant; write RESEARCH.md section.
      *Done: RESEARCH.md §16.3-16.5; BRIEF.md fair-odds and recheck decisions.*
- [x] C6 Tests, CI, ship, report.
      *Done: local 256 tests green (exit 0, 0 failures); CI 36211240206 green; ship.sh gate green;
      release 36211424686 → v0.11.0 (code 15, 4.78MB):
      https://github.com/tjshea90/novig/releases/tag/v0.11.0*

## Tj's request, 2026-09-26 — see scans while Novig is open

> Is it possible to make a floating widget for this app or a picture in picture type view so I can
> see the scans while I have novig open

### Plan
- [x] D1 Research the options on Android 16 / Moto G: picture-in-picture, a draggable overlay
      ("display over other apps"), split screen, notifications; limits of each (touch, size,
      permissions, Novig touch-blocking risk, battery).
      *Done: RESEARCH.md §17 (table); BRIEF.md decision: picture-in-picture.*
- [x] D2 Build the recommended one: a mini window that shows scan progress and the top +EV bets,
      live, while Novig is open (auto when leaving Vigilant, a button to open it, actions to scan,
      recheck and page through bets), with a setting to turn it off.
      *Done: `MiniWindow` (params, auto-enter rule, paging, Novig app intent), `MiniFeed`,
      `MainActivity` wiring (auto-enter; Android 11 fallback; Scan/Recheck/Next receiver), manifest
      (PiP, resizeable, `us.novig.app` query), Settings › Mini window, feed button; whole settings
      rows now toggle. Tests: `MiniWindowTest` (4: paging, auto-enter rule + old settings,
      params/buttons, manifest), `ScreenshotTest` miniWindow/miniWindowSmall/NextShowsTheNextPage/
      Enlarged/BeforeAnyScan, theFeedHasAMiniWindowButton…, settingsOfferTheMiniWindowSwitch.*
- [x] D3 Tests (paging, when it opens, screenshots of the mini view), CI, ship, report.
      *Done: local 267 tests green (exit 0, 0 failures); CI 36214953291 green; ship.sh gate green;
      release 36215186173 → v0.12.0 (code 16, 4.83MB):
      https://github.com/tjshea90/novig/releases/tag/v0.12.0. Not device-tested (no emulator here).*

## Tj's request, 2026-09-26T15:34Z — CrazyNinjaOdds' scanned odds inside the app

> I like the following website for positive EV odds when I choose novig and a couple filters.
> Consider all possible ways to make this site's scanned odds display in this app, especially in
> the floating widget
>
> https://crazyninjaodds.com/site/tools/positive-ev.aspx

### Plan
- [x] E1 Research every way to get CNO's positive-EV rows (Novig + Tj's filters) into Vigilant:
      plain HTTP fetch + parse (how the filters are applied: URL, form postback, cookies), an
      in-app WebView (tab), a WebView that reads the table for the native feed and mini window,
      any CNO API / export / alerts / supporter feature, OddsBlaze direct, share/clipboard,
      overlay. Terms, robots.txt crawl delay, freshness, fragility. Hands-on in Chromium/curl.
      *Done: RESEARCH.md §18 (filters ride in CNO's Shared View URL; table comes from an ASP.NET
      AJAX postback, 100 Novig rows with $ available; CNO's terms PDF forbids bots/scrapers, but
      CNO features Chrome add-ons on this page; OddsBlaze is $299/mo first-hand; 9 ways compared).
      Corrected §16.1/§16.3 (said no terms page and OddsBlaze $29/mo).*
- [x] E1b Tj picks the way (asked 2026-09-26 ~16:00Z): in-app CNO tab + mirror (user-driven,
      grey under CNO's terms), background reader after Mike's written OK, or split screen only.
      *Tj: "Whatever the best way is, disregarding the terms of service. I am friends with the
      owner." → build the background reader (RESEARCH.md §18.3 way 4): polite (≥30 s, CNO's
      robots.txt crawl delay; 60 s default), only while the feed/mini window is on screen.*
- [x] E2 Build the recommended way: CNO rows in the app and in the mini window (floating widget),
      with Tj's filters, polite polling, clear "from CrazyNinjaOdds" labelling and age.
      - [x] E2a data layer `data/cno/`: `CnoView` (Shared View link → URL, filters in words),
            `CnoPage` (form, delta, table by header names), `CnoClient` (GET + loader postback,
            then one Refresh postback per read; session reuse, fallback, 429/503 pause),
            `CnoFeed` (≥30 s between reads, interval timer only while watched, error back-off,
            disk cache). *Tests: CnoPageTest 8, CnoViewTest 5, CnoClientTest 6, CnoFeedTest 7 (all
            green); LiveCnoSmokeTest (VIGILANT_LIVE=1) green against the real site 2026-09-26
            ~17:05Z: 100 Novig rows, refresh = 1 request.*
      - [x] E2b app: settings (switch, link, refresh interval, mini window source), CNO tab + sheet,
            mini window rows mixed with Vigilant's (tagged), Refresh button in the mini window,
            watch only while started (incl. PiP), BRIEF.md exception to manual-only for CNO.
            *Done: `ui/CnoScreen` (tab, sheet, ¼-Kelly from CNO's fair capped at $ available),
            `MiniWindow.items` + `MiniFeed` (both/Vigilant/CNO, CNO tagged, $ available, EV now
            rounded), PiP Refresh+Next when CNO-only, `MainActivity` watch while STARTED, Settings
            section + link editor, cno.json out of backup. Tests: MiniWindowTest 8 (4 new, incl.
            rounding which fails on the old truncation), ScreenshotTest +10 (cno tab/light/reading/
            error/off/refresh/sheet, mini both/CNO-only, settings). Full floor: 308 tests, 0 failed,
            3 skipped (live), exit 0. Docs: BRIEF.md decision, RESEARCH.md §18.5, CLAUDE.md surface.*
- [x] E3 Tests (parser on a saved page, mini window screenshots), CI, ship, report.
      *Done: CI 36258578827 green on 84d75e5; ship.sh gate green (full suite); release
      36258827478 → v0.13.0 (code 17, 4.93MB): https://github.com/tjshea90/novig/releases/tag/v0.13.0.
      Release-notes template no longer says "nothing is fetched until you tap Scan". Not
      device-tested (no emulator here): the PiP refresh and CNO reads on the phone need Tj.*

## Tj's request, 2026-09-26 ~18:20Z — make the CNO scanner accurate, standalone and fast

> Make sure it is actually comparing the cno odds to fair odds based on the cno feed. Consider if
> the bets it is showing me are truly positive EV. I don't want to take dangerous bets, especially
> if only one or two other sports books offer the odds then it may be just a small market with
> inaccurate odds. Optimize the cno scanner and make sure it is giving me good positive EV bets for
> novig. I should be able to go to the cno scanner and it will work without using the other parts
> of the app, for example if I choose the cno scanner, the other parts of the app using the other
> apis should be asleep and not loading, and I should be able to use the cno scanner on the
> floating widget as well. Make an option so I can set the odds to no more than +150, meaning I
> want to take odds that are negative or up to +150. I don't like longshots. The cno scanner should
> use worst case devigging if possible. The goal is to show me accurate, true positive EV bets,
> regardless of the sport or market. Any market or sport is fine as long as it is positive EV.
> Allow an option for 15 second refresh, 5 second refresh, and real time refresh for the cno
> scanner if this is possible. Make it so if I press on any bet that it scanned, I can see the odds
> for the same bet at whatever other sports books it found, even on the widget. Then run a full
> test on this cno scanner to make sure it is working properly and efficient and smart. Look for
> and fix bugs.

### Plan
- [x] F1 Research (live, first-hand): check CNO's EV = its fair odds vs the Novig price on real
      rows; what CNO's devig choices mean (worst case, conservative, complete sportsbook, min
      books, market sides) and whether posted form values are honored; what CNO's game page shows
      (every book's odds for the bet); how often CNO's data actually updates (for 5 s / 15 s /
      real time); whether a row leads to its Novig market.
- [x] F1 *done: RESEARCH.md §19.*
- [x] F2 Accuracy: CNO scanner filters sent to CNO and enforced in the app: worst-case devig,
      longest odds (+150 option, Tj's default), min books (no 1–2-book markets), market sides,
      min EV; re-check each row's EV from its fair odds and price; flag thin/suspect rows.
- [x] F3 CNO-only mode: choosing the CNO scanner puts Vigilant's scan and its APIs to sleep (no
      scan, no Novig/odds-API calls, tabs that need them hidden), and the floating widget runs on
      CNO alone.
- [x] F4 Refresh: 5 s, 15 s and "real time" (read as soon as CNO publishes new odds) options.
- [x] F5 Every book's odds for a tapped bet (CNO's game page), in the app and in the widget.
      *F2-F5 done: CnoFilters (CnoClientTest "every read posts the scanner's filters…", "a stricter
      value in the Shared View link wins…"), CnoChecks (CnoChecksTest 6), ScannerMode + migration +
      scan guard (MiniWindowTest "the scanner mode decides what runs…", ScreenshotTest
      cnoOnlySettingsHideWhatsAsleep / bothScannersSettingsShowVigilantsSections), refresh
      (CnoFeedTest real time / 5 s / stuck / back-off), CnoBooks (CnoBooksTest 10, CnoClientTest books
      + Novig link), CNO tab/detail/widget Books (ScreenshotTest cno*, miniWindowBooks*). Live smoke
      green twice.*
- [x] F6 Full test of the CNO scanner (CLAUDE.md protocol, scoped to CNO and all it touches), fix
      bugs with failing-first tests, CI, ship, report.
      *Done: fixes in RESEARCH.md §19.1 (UW-WC label, judged-book check, stuck polling, cache write
      throttle, widget Books pinned + self-loading, compound age, badge recomposition, "$it" chips);
      new CnoFeedTest stuck/disk tests confirmed failing on the pre-fix CnoFeed. Forced full rerun
      348 tests, 0 failed, 3 skipped (live), exit 0; live smoke green; CI 36264649883 green;
      release → v0.14.0 (code 18, 5.0MB): https://github.com/tjshea90/novig/releases/tag/v0.14.0.
      Not device-tested (no emulator here).*

## Tj's request, 2026-09-26 (screenshot of the mini window over his home screen)

> Review the screenshot. Notice the floating widget doesn't say what the pick actually is. I need
> to be able to see the exact pick for each positive EV bet in the widget so I can choose it in
> novig without opening the full vigilant app

### Plan
- [x] G1 Cause: the pick name (e.g. "Jahmyr Gibbs Over 4.5") is drawn without a color; the mini
      window (picture-in-picture) renders MiniFeed with no Surface behind it, so the text falls
      back to black on the dark window. The market/price/EV lines set their own colors, so they
      show. Screenshot tests wrapped MiniFeed in a Surface and never saw it.
- [x] G2 Fix: MiniFeed supplies its own background and text color; the pick name full-contrast
      and bold, the line under it keeps market and game; a test that renders the window exactly
      as MainActivity does (no Surface) and checks the pick name's pixels are light, confirmed
      failing on v0.14.0's code.
      *G1-G2 done: MiniFeed draws its own Surface (text color), the pick name bold at full contrast;
      its side and line never cut (MiniWindow.splitPick): "J. Jefferson Under 69.5" when the full
      name doesn't fit (MiniWindow.nameChoices), and in the smallest window the name on line 1 and
      the line in bold leading line 2. Tests: ScreenshotTest miniWindowPickNamesAreReadableAsTheWindowDrawsThem
      + miniWindowBooksViewPickNameIsReadableAsTheWindowDrawsIt (pixel contrast; both FAILED on
      v0.14.0: luminance 0.00..0.05), miniWindowPickNamesAreReadableInLightThemeToo,
      miniWindowSmallKeepsThePicksLine, miniWindowShortensNamesThatDontFitAtTheUsualSize (no "…" on
      the name); MiniWindowTest splitPick/nameChoices; widget screenshots now drawn like the real
      window (no Surface). Forced full rerun: 354 tests, 0 failed, 3 skipped (live), exit 0.*
- [x] G3 Full floor, CI, ship, send the link.
      *Done: release v0.14.1 (code 19, 5.0MB): https://github.com/tjshea90/novig/releases/tag/v0.14.1;
      recorded in BUILDLOG.md by the next session (the shipping session was cut off after the
      release went green).*

## Tj's request, 2026-09-26 ~20:15Z — CNO widget: scroll buttons, no background refresh, teams, agreement checks, placed bets, deep link

> For the cno scanner in this app, do the following:
> 1) make it so there are permanent up and down buttons on the bottom of the cno scanner widget
>    that scroll the bets instead of the current next page button.
> 2) make sure if I close the cno scanner or the app that nothing is refreshing in the background.
> 3) on the cno scanner widget, put small team designations next to player names. For example,
>    d. Schultz (hou). That way I know what team to look for in the novig app.
> 4) put small green checks next to bets in the cno widget where several books agree on the fair
>    value price, but only do this if it doesn't slow down the scanning a lot.
> 5) if possible, in the cno scanner widget, make it so I can press a bet to let me know that I
>    already placed that bet. I want to be able to track which bets on the scanner I already made,
>    so I don't place them twice. Maybe a button to remove the bet from the scanner widget so I
>    don't see it anymore after I place the bet, but this has to persist even through refreshes
>    so the bet doesn't come back up after a refresh if I already placed the bet.
> 6) if possible, on the cno scanner widget, make it so I can tap a bet and it will bring me to
>    that exact bet in the novig app so I can place it immediately.
> After all of this is done, run full tes protocol on the app

### Plan
- [x] H0 Research (2026-09-26 ~20:15-20:45Z, live, first-hand; RESEARCH.md §20 to write):
      - The widget is picture-in-picture: it takes **no touches** (Android draws its own menu on
        a tap, max ~3 buttons, and only while tapped). Permanent up/down buttons, tapping a bet,
        and a "placed" button per bet are impossible there. They need a real floating overlay
        ("Display over other apps", TYPE_APPLICATION_OVERLAY), which RESEARCH.md §17 already
        named as the upgrade. Decision: build the overlay as the widget whenever CNO is on
        (Both / CNO only) and the permission is granted; PiP stays as the fallback (and for
        Vigilant only, unchanged).
      - Novig's app routes (from its Expo/React Navigation linking config in app.novig.us's JS
        bundle): `novigapp://events/:orderslip_outcomes?/:partner_id?/:amount?` (Home + bet slip),
        `event-markets/:event_id`, `autofill/:market_id?outcome_id=&wager=`, `players/:player_id`.
        CNO's deeplink (mobile UA) answers `novigapp://events/<id>/cno` and the id is a Novig
        **outcome id** (checked: Dalton Schultz Over 5.5 receptions), so "Open in Novig" already
        drops that exact bet into Novig's bet slip; RESEARCH.md §19 misread it as an event id.
        A desktop UA gets `https://novig.com/events/<id>/cno` instead (normalize to novigapp://).
      - Player teams: not in Novig's public catalog (markets say "Jadarian Price 53.5
        RUSHING_YARDS", no team) nor in CNO's list or game page. ESPN's free site API has them:
        `/apis/site/v2/sports/{sport}/{league}/teams` (32 NFL teams, abbreviations) and
        `/teams/{id}/roster` (~30 KB gzipped, displayName + shortName "D. Schultz"). Two rosters
        per game, cached a day.
      - "Books agree": CNO's list has only fair odds + a book count; every book's price is on
        CNO's game page (2 requests per bet). So the green check needs a background lane that
        reads the top bets' game pages slowly, after the list, cached 5 min, only while the
        scanner is on screen; `CnoBooks.check` already devigs each two-sided book.
- [x] H1 Data: placed bets (`PlacedBets`, placed.json, kept through refreshes/restarts/backup,
      expire after the game) + tests. *PlacedBetsTest 4 (restart, undo, expiry, pick family).*
- [x] H2 (DONE, checked 2026-09-29: the data side noted below; CnoAgreementTest (18 tests) and CnoWatchTest `nothing is
      read until something watches, and nothing after the last watcher closes`, which runs `keepBooksFresh`.) Data: books agreement: per-book +EV count, new SPLIT verdict (green ✓ = CONFIRMED = 3+
      two-sided books whose consensus is +EV and 3+ of them individually +EV); background
      agreement lane in CnoFeed (top bets, one game page at a time, ≥2 s apart, 5-min TTL,
      only inside the watch) + tests incl. "never runs when not watched".
      *Data done: CnoBooks.agreeing/SPLIT/agrees, CnoFeed.keepBooksFresh (+ books 429 pauses list
      reads, cancellation no longer leaves "loading", memory capped at 150 bets). CnoAgreementTest 8.*
- [x] H3 Data: player teams (`PlayerTeams`: ESPN teams + rosters, disk cache 24 h, lane inside
      the watch, soft-fails) + MockWebServer tests. *PlayerTeamsTest 5.*
- [x] H4 Data: Novig links: CNO https form → novigapp://, Vigilant rows → novigapp://events/<outcomeId>.
      *CnoFeed.appLink (CnoAgreementTest), MiniWindow.novigLink (MiniWindowTest "a Vigilant bet opens
      Novig's bet slip on its own outcome"); MainActivity.launchNovig pins us.novig.app when installed.*
- [x] H5 App: nothing refreshes when the scanner is closed: CNO reads only while the CNO tab is on
      screen or a widget is showing (and the screen is on); closing the widget / the app (swipe
      away, back out) stops everything; tests.
      *CnoWatch (data) + MainViewModel.watchCno("tab"/"pip"/"overlay"); CnoTab + MiniFeed use
      LifecycleStartEffect; FloatingWidget reports watching only while up, not a bubble, screen on
      and unlocked; MainActivity.onDestroy hides it. CnoWatchTest (nothing before, nothing after
      the last watcher; hand-over tab→widget doesn't restart), PlayerTeamsTest keepFresh cancel.*
- [x] H6 App: floating widget (overlay): drag/resize/minimize/close, rows (tap = open that bet in
      Novig, ✓ = placed/hidden with Undo, long-press = every book), permanent bottom bar
      (Refresh / ▲ / ▼ / Books; Scan/Recheck in Both), team tags, green checks; permission
      flow; shows on leaving Vigilant / Open in Novig, hides on return; PiP fallback gets ▲/▼.
      *FloatingWidget + FloatingFeed; ScreenshotTest floating* 7 (buttons always there + Down/Up
      scroll, tap opens + ✓ placed + Undo, teams + ✓ + light-theme contrast, long-press books,
      bubble, Both bar, header buttons); MiniWindowTest PiP Books/Up/Down + no-wrap paging.
      Not device-tested: overlay window, drag/resize, deep link into Novig need Tj's phone.*
- [x] H7 App: CNO tab + sheet: team tags, green checks, Mark placed / Undo, placed list; Settings
      (widget style, background books check, player teams). *CnoScreen (snackbar Undo, "Show the N
      bets you placed" + "Not placed", overlay-permission banner), CnoDetail "I placed it",
      SettingsScreen switches; ScreenshotTest settings/cno tests updated and green.*
- [x] H8 Screenshots + tests, docs (RESEARCH.md §20, BRIEF.md, CLAUDE.md surface, NOVIG_API.md
      deeplinks), light pass. *Done: RESEARCH.md §20, NOVIG_API.md §9.1, BRIEF.md "The CNO widget,
      and when CNO is read", CLAUDE.md surface list; 7 floating-widget screenshots checked by eye.*
- [x] H9 Full test protocol on the whole app (CLAUDE.md), fix, CI, ship, send the link.
      *Fixes so far (each failing-first where a test can prove it): ESPN 403s a User-Agent naming
      Vigilant (live) → OkHttp's own (PlayerTeamsTest UA test); a capped roster pass waited 30 min
      for the rest of the slate (PlayerTeamsTest full-slate test, failed on old code); a books read
      cut short by a new list counted as a failure (2-min wait) and re-priced lists restarted the
      lane (CnoAgreementTest 2, failed on old code); teams lane restarted on re-order; widget
      reopened as a paused bubble; CNO badge counted placed bets; +EV tab flags placed Vigilant
      bets; teams.json pruned after 7 days; widget clamps on rotation; overlay permission cached;
      stale "while Vigilant or its mini window is on screen" copy (Settings, CNO tab, docs);
      "I placed it" hint only with its button. Forced full rerun: 388 tests, 0 failed, 3 skipped
      (live), exit 0; live smoke (VIGILANT_LIVE=1): CNO 50 rows C-WC, top bet CONFIRMED 8 of 8,
      novigapp:// outcome link, ESPN 39/40 player bets tagged. Shipped: ship.sh gate green, CI
      36271444846 green on 0d37c74, release 36271632576 → v0.15.0 (code 20, 5.1MB):
      https://github.com/tjshea90/novig/releases/tag/v0.15.0. Not device-tested (no emulator here):
      the overlay window, its permission flow, and where Novig's app lands need Tj's phone.*

## Tj's request, 2026-09-26 ~23:45Z — resize/move the floating widget more easily; "CNO error"

> Make it so I can easily resize the widget by pulling out two finger gesture to enlarge or two
> finger pinch to shrink or by easily accessed corners that I can pull out to enlarge or in to
> shrink. Look at the bottom right of the widget in the screenshot. Something is there but it is
> cut off. If possible find an easier way for me to drag and move the widget around the screen
> because the top bar is the only way right now and it is kind of small
>
> Also it says cno error at the top. Figure that out

### Plan
- [x] J1 Cut-off thing at the bottom right = v0.15.0's resize grip, drawn inside the 12 dp rounded
      corner (the corner clips it). Replace it.
- [x] J2 Resize: two fingers anywhere on the widget (spread = bigger, pinch = smaller), and four
      big corner handles drawn in a frame around the widget (drag out = bigger, in = smaller).
- [x] J3 Move: two fingers anywhere also move it; the whole frame border and a taller top bar
      drag it with one finger. Screen coordinates (MotionEvent raw x/y), not the widget's own,
      so it follows the finger exactly (the v0.15.0 top-bar drag used local deltas, which lag
      a moving window).
      *J1-J3 done: WidgetGestures/WidgetGeometry + FloatingWidget.TouchFrame + FloatingWindow frame.
      WidgetGesturesTest 10 (zones, exact-follow drag, tap stays a tap, list scroll untouched,
      corner out/in with opposite corner fixed, min/screen clamps, pinch spread/shrink/pan, lift
      ends pinch, bubble no pinch, off-screen clamp); ScreenshotTest
      floatingWindowHasAFrameWithFourCornerHandlesAndATallerTopBar (9h screenshot checked).*
- [x] J4 "CNO error": find the real cause (live soak of the app's own read pattern: list + books
      lane + teams), fix it, and make the widget say what the error is instead of "CNO error".
      *Done: soak 3 min / 106 requests clean; fixes: cancelled reads no longer errors (CnoWatchTest
      2, failed on old code), bodies read off the main thread (Call.awaitText), short reasons
      (ScreenshotTest theWidgetSaysWhatWentWrongWithCnoNotJustCnoError). RESEARCH.md §20.1.*
- [x] J6 Tj's screenshot (CNO tab, 7:45): "notice the refresh symbol … Sometimes it is getting
      stuck. I'm not sure if the data is refreshing." The pull-to-refresh arrow sits half-pulled
      under the header. Make it always go away, and make it obvious when the list was last read
      and that it's reading now.
      *Done: VigilantPullToRefresh (CNO, +EV, Games); ScreenshotTest thePullToRefreshArrowLetsGo…
      2 (both failed on the old box); CNO status "Read 4s ago · odds 20s old · every 15 s".*
- [x] J5 Tests (geometry + gestures + error cases), screenshots, full floor, ship, send the link.
      *Shipped v0.15.1 (code 21): CI 36281404595 green, release 36281636243,
      https://github.com/tjshea90/novig/releases/tag/v0.15.1.*
      *Tests: WidgetGesturesTest 10, FloatingWidgetTest 4 (the real window driven by MotionEvents:
      bottom-left corner in/out with the right edge fixed, remembered; frame drag moves, list tap
      doesn't; two-finger pinch shrinks), ScreenshotTest +4. The frame now handles touches in
      dispatchTouchEvent (a scrolling list asks parents not to intercept, which would have blocked
      a pinch started mid-scroll). SampleScan.usage pinned to NOW (a date-dependent meter test
      failed on 09-27). Forced full rerun: 409 tests, 0 failed, 4 skipped (live), exit 0 after
      that fix; v0.15.1 code 21.*
      ("Make sure you complete all tasks including the last two prompts I sent.")

## Tj's messages, 2026-09-27 ~00:05Z (while v0.15.1 shipped) — bet slip taps, CNO unreachable, Milwaukee

> When I click on bets, sometimes they pull up the novig bet slip, but sometimes they don't. It
> may be because it says cno could not be reached. Figure out and fix both problems, I think cno
> is restricting or slowing me down.
> Do this all in addition to everything else I asked before
> Also in my notifications it says the best bet is Milwaukee, but this bet isn't even shown in the
> widget. See the screenshots
> Every message I send make sure you are still completing all prior tasks as well

Screenshots: widget over the home screen (v0.15.0: "CNO 1m · 45 +EV", Thornton's row showing the
link spinner instead of its ✓, the bottom bar's "Books" cut to "Bo" at that width); notification
shade: "Scan done: 7 +EV bets · Best: Milwaukee Brewers -3.5 · Spread · +3.4% EV" (3m), and
"Vigilant is displaying over other apps" (Android's own, silent). Quick settings show a VPN tile
("Secure my con..").

### Plan
- [x] K1 Tap → bet slip every time: the link comes from a CNO request made on the tap (the spinner
      in the screenshot), which fails or hangs when CNO is slow. Resolve links ahead of time for
      the listed bets (paced, cached on disk, a line's link never changes), give the tap a short
      timeout with one retry, and when CNO can't answer, find the outcome in Novig's own catalog
      (event by teams, market by player/line) so the bet slip still opens; say so when it can't.
- [x] K2 "CNO could not be reached": measure what CNO does under the app's load (throttling,
      slow replies, Cloudflare?), then cut the app's CNO traffic: one shared pace for every CNO
      request (list first), a lighter books lane, back off all lanes after a failure, and
      explain it in the app.
- [x] K3 Milwaukee: find why the notification's "best bet" isn't in the widget (mode? feed filter
      vs. notification count?) and make them agree.
- [x] K4 Bottom bar fits at any width (icons only when narrow; "Bo" cut off).
- [x] K6 Tj, 00:10Z: "Sometimes it says unable to resolve cno sometimes it says timeout." (DNS
      failures and timeouts: the phone's network/VPN, not an HTTP refusal) → keep CNO's last good
      address for when DNS fails, drop dead connections and retry a failed read once at once.
- [x] K7 Tj, 00:10Z: "make an x option next to each check mark on the right side on the cno
      widget. If I press the x, it will remove the bet from the list even if I didn't bet it"
      → ✕ = hide (not placed), kept through refreshes/restarts like placed, with Undo; CNO tab
      lists hidden and placed bets separately.
- [x] K8 Tj, 00:20Z: "Include an option in the cno settings to only include bets where multiple
      books agree (the check mark bets), and where both sides of the bet have odds at different
      sports books for the most accurate odds." → Settings switch "Only bets the books agree on":
      the tab, badge and widget list only ✓ bets (3+ books pricing both sides, 3+ of them +EV
      alone); bets whose books aren't read yet are held back and counted ("N being checked"); the
      green-check lane then covers more of the list (paced within K2's budget).
      ("Make sure on every new request I send you log and still finish the prior requests.")
- [x] K9 Tj, 00:25Z: "See if there is a way to safely and repeatedly refresh cno odds without
      timeout or unable to resolve or any other restrictions whether that is using a specific dns
      server, or my nordvpn, or any cheap service that could help, or any other way" → research
      (DNS-over-HTTPS, Android Private DNS, NordVPN, a relay/cache service, CNO's own limits),
      build what's safe in the app (DoH fallback so DNS never blocks a read), write it up in
      RESEARCH.md §20.2 and recommend the rest with costs.
      Done (v0.15.2), proved by: K1 `TapLinkTest` (4: cached link needs no request; CNO hanging
      → Novig's catalog after 5 s; CNO failing → Novig first; nothing → null within both limits),
      `NovigBetFinderTest` (7), `CnoAgreementTest` links lane + `cno_links.json` survives restart;
      K2/K6 `CnoNetworkTest` (DoH/remembered DNS, pace, retry, and "network failures say which one it
      was"), `ScreenshotTest.theWidgetTellsDnsFailuresFromTimeouts`; K3 cause: the scan's results
      live in memory and the notification outlived them (process restarted, or CNO only) → cancelled
      on a new process (`VigilantApp.onCreate`), on switching to CNO only, on a new scan, and not
      posted when CNO only was picked mid-scan (Android side, no unit harness: source pin in the
      code comments); K4 `ScreenshotTest.floatingWidgetBarFitsAtAnyWidth` (250 dp: icons only,
      labels for TalkBack) + `floatingWidgetBarKeepsItsLabelsWhenTheyFit`, default 360 dp
      (`MiniWindowTest`); K7 `MiniWindowTest` "x removes a bet like placing it…",
      `ScreenshotTest.floatingWidgetXRemovesABetWithoutPlacingItAndCanBeUndone`,
      `cnoCardXRemovesItAndTheRemovedListPutsItBack`; K8 `MiniWindowTest` "only bets the books agree
      on…", `ScreenshotTest.cnoTabWithOnlyAgreedBetsSaysWhatsHeldBack`,
      `settingsHasTheOnlyAgreedSwitch`; K9 RESEARCH.md §20.2.
- [x] K5 Tests, full floor, ship, send the link; confirm J1-J6 (v0.15.1) are in the release.
      Shipped v0.15.2 (code 22, 438 tests, CI green on b39913d, Release published 2026-09-27T00:54Z;
      built from main, which carries v0.15.1's J1-J6).
- [x] K10 Tj, 00:30Z: "When all of the features and fixes I asked for are finished, run a full test
      protocol and find ways to improve the UI and speed and efficiency and bug fixes, but without
      sacrificing any accuracy" → after K1-K9 ship: CLAUDE.md full-test protocol on the whole app,
      improvements (UI, speed, efficiency) + bug fixes with failing-first tests, accuracy
      untouched (EV math, devig, checks, matching), then ship again and send the link.
  - [x] K10a Sweep data/cno + data/teams + data/tracker (CnoFeed lanes, CnoClient, CnoBooks,
        CnoChecks, NovigBetFinder, PlayerTeams, PlacedBets): races, lost state, wasted requests.
        Found+fixed (each test failed on the old code): CNO's HTML and ESPN's JSON were parsed on
        the main thread every read (widget stutter) → `CnoClient`/`PlayerTeams` parse on a work
        dispatcher (`CnoClientTest` "the page is parsed off the caller's thread", `PlayerTeamsTest`
        "ESPN's JSON is parsed off…"; old code had no such path); a refused/busy link lookup didn't
        pause anything, and a list read ending wiped a pause CNO had just asked for (`CnoAgreementTest`
        "a busy or refused answer to a link lookup pauses…", "a pause CNO asks for during a list
        read isn't wiped out…"); the sheet and the widget's Books view judged the game page's older
        Novig price while the card and ✓ judged the list's newer one (`ScreenshotTest.
        theSheetJudgesTheSamePriceAsTheGreenCheck`, `theWidgetsBooksViewJudgesTheSamePriceAsItsGreenCheck`);
        "Open in Novig" on the CNO tab gave no sign of working for up to 13 s (`openInNovigSaysItsOpening`);
        INBOX.md logged the harness's background-task notices as Tj's words (`tools/test_resume.sh`).
        CI: `CnoNetworkTest` retry test resolved "localhost" (IPv6-first on runners) → pinned to 127.0.0.1.
  - [x] K10b Sweep app: MainViewModel flows (recomputation per state change), MainActivity
        (widget/PiP wiring, taps), FloatingWidget/WidgetGestures, FloatingFeed/MiniFeed, CnoScreen,
        Settings; the other tabs (Feed, Games, Tracker) and ScanService.
        Found+fixed: with "only bets the books agree on" and none agreed yet, the widget said "No +EV
        on CrazyNinjaOdds right now" → "No bets the books agree on yet · N held back, M being checked"
        (`ScreenshotTest.theWidgetSaysWhenBetsAreWaitingForTheBooks`, failed on the old text). Checked
        and left: the VM's lanes re-screen CNO's rows per state change (≤100 rows of arithmetic, not
        worth a cache); the widget's clocks stop with its lifecycle when hidden/bubble/screen off;
        corner zones overlap the header ✕ and "Books" but taps under the slop still reach them
        (`WidgetGesturesTest`). Feed/Games/Tracker unchanged since v0.15.0's full test.
  - [x] K10c Sweep engine + scanner (FairValue, Devig, Fees, EvMath, Planner/Pricing, Scanner,
        RateGate): accuracy untouched, only efficiency/bug fixes with failing-first tests.
        `git diff v0.15.0..HEAD` on engine, scanner, novig, reference and match: only ScanSettings'
        new switch; nothing changed since v0.15.0's full test, suites re-run green (engine 39).
  - [x] K10d Fix everything found with named tests; forced floor + screenshots looked at;
        v0.15.3 ship, release, record, send link. (Forced floor 446 green: engine 39, data 290,
        app 117; new PNGs 9i-9m, 8e looked at.) Shipped v0.15.3 (code 23): CI green on 7b9636f,
        Release published 2026-09-27T01:06Z, recorded in BUILDLOG.md.

## Tj's request, 2026-09-27 ~01:18Z (after v0.15.3) — bet slip without CNO, both scans in the widget, CNO under load

> 1) I think it won't open the bets slips in novig if the cno server is not responding. Can you
> make it so vigilant can open the bet in novig even if it can't reach cno servers?
> 2) on the widget, include an option to also use the regular scan in addition to cno and put all
> the results in the widget together, if it doesn't already do this.
> 3) consider ways to make cno respond even with high traffic and rapid refreshing. Is there a
> workaround? Dns? Free or cheap service? Vpn? Use proxies to get around the limit, it is ok
> If I add more requests while you are still working, and them to the list and finish everything,
> do not stop work on any prior requests

### Plan
- [x] L1 Bet slip without CNO: v0.15.2 asked CNO first (up to 5 s) and Novig's catalog only after,
      unless CNO's list was already failing; a CNO that hangs (not yet failed) cost the tap 5 s+
      and its links lane never used Novig at all. → Ask CNO and Novig's catalog at the same time
      (first exact link wins), resolve the listed bets' links ahead of time from Novig's catalog
      too (not only CNO), so a tap needs neither; verify NovigBetFinder live against the real
      CNO list and Novig catalog (league names, market types) and fix what doesn't match.
      Done: live check (`LiveNovigBetFinderTest`, VIGILANT_LIVE=1) on CNO's real 60-row list: 56
      exact before, 60 exact after (Novig's INTERCEPTIONS_THROWN for "Passing Interceptions";
      soccer's MONEYLINE_3_WAY_WIN/DRAW Yes/No markets), every one the same outcome CNO's own link
      opens, 0 wrong (`NovigBetFinderTest` "passing interceptions and soccer's 3-way…"). Taps race
      CNO and the catalog, first exact wins (`TapLinkTest` 5, 3 failed on the old sequential code);
      the links lane asks the catalog first and CNO only for what it can't name, and fills every
      link with CNO down (`CnoAgreementTest` "links come from Novig's catalog first…", "with CNO
      down, the catalog still fills every link"); 30 bets ahead instead of 15; exact catalog finds
      from taps are kept. Also fixed: cno_links.json kept an arbitrary 400 links, not the newest
      (`CnoAgreementTest` "the links file keeps the newest links…", failed on the old code).
- [x] L2 Widget with both scanners: Both mode already merges Vigilant's scan and CNO's list in the
      widget (best EV first). Add the switch in the widget itself ("+ Vigilant scan" on/off, i.e.
      Both ⇄ CNO only), show the same bet once when both scanners list it, and an opt-in "scan
      again every N min while the widget is open" (off by default: scans spend API quotas).
      Done: top-bar switch "CNO only" / "Both" (`ScreenshotTest.theWidgetsTopBarSwitchesVigilantsScanOnAndOff`;
      switching on scans at once when the last scan is missing or >5 min old, `WidgetRescanTest`);
      a bet both scanners list (same Novig outcome via CNO's link) shows once as Vigilant's row
      tagged "CNO +x%", with CNO's books/✓/team (`MiniWindowTest` "a bet both scanners list shows
      once…", `ScreenshotTest.theWidgetWithBothListsABetBothScannersFoundOnce`); placed/removed
      from either side hides both (`MiniWindowTest` "marking a bet both scanners list placed…",
      `PlacedBetsTest` aliases); Settings › Mini window "Vigilant's scan again while the widget is
      open: Off/5/10/15/30 min" (`WidgetRescanTest`, `settingsOfferVigilantsScanAgainWhileTheWidgetIsOpen`).
- [x] L3 CNO under load: measure CNO under rapid refreshing (latency, errors, compression) from
      here; build what helps (list and taps independent of CNO's speed; Novig's own live price for
      listed CNO bets so a slow CNO doesn't leave stale prices); write up DNS / VPN / relay /
      proxy findings with costs in RESEARCH.md §20.3.
      Done: `LiveCnoBurstTest` (VIGILANT_BURST=1): 61 reads in 60 s, 0 errors, 0 refusals, p50
      258 ms, uncompressed ~30 KB each: no limit to get around, so no proxies/VPN/relay (§20.3).
      Built `NovigLive`: the 10 best listed CNO bets on Novig priced from Novig's own book every
      15 s while on screen, CNO reads re-priced without a request, EV = CNO's fair vs Novig now,
      "was +117" / orange EV when it moved (`NovigLiveTest` 4, `MiniWindowTest` "CNO bets show
      Novig's price now…", "the green check judges Novig's price now…", `ScreenshotTest.
      theWidgetShowsNovigsPriceNowAndWhatCnoHad`, `cnoCardShowsNovigsPriceNowAndWhatCnoHad`,
      `settingsHaveTheNovigPriceNowSwitch`); live: 10 of 10 prices identical to CNO's Novig price.
      Also: the bet finder is paced (≥350 ms) and honors Novig's 429 Retry-After, and "couldn't
      look" (Novig busy) is no longer reported as "only the game" (`NovigBetFinderTest` 10).
- [x] L4 Tests, full floor, ship, release, record, send link. v0.15.4 (code 24): forced floor 474 green,
      CI green on 9ebfebe, Release published 2026-09-27T01:59Z, recorded in BUILDLOG.md.

## Tj's request, 2026-09-27 (mid-L3) — live +EV bets, fast (next version, after L4 ships)

> For the next version, after releasing the version you are working on now, consider if it is
> possible and if there is an online feed fast enough to tell me positive EV live bets on novig. I
> have to be able to bet these very fast because the odds change. Also, it has to scan for live
> odds on games very fast to find positive EV live bets that are not based on stale odds. If this
> can be done, make it. If not, tell me your findings

### Plan
- [x] M1 Research (after L4 ships): what's fast enough for live +EV on Novig: Novig's websocket
      (NOVIG_API.md §6) vs polling books; live sharp reference odds (Pinnacle via pinnapi, Kalshi /
      Polymarket live markets, The Odds API live, CNO's live view) with their real update latency
      and cost; how stale each is vs Novig's book; Novig's live taker fee. Measure what can be
      measured from here.
- [x] M2 If feasible: build a live +EV mode (fresh-only: every leg's age shown and capped, stale
      legs dropped), fast enough to bet from the widget; if not, write up the findings for Tj.
- [x] M3 Tests, ship, send link (or findings).
      Done 2026-09-27 ~02:05Z: RESEARCH.md §21 (`research/live_leadlag.py`). Not feasible on free
      feeds: Novig's live book leads Kalshi (9/7/2 Novig jumps vs 1/2/0 Kalshi in 150 s, Novig
      first each time), 0 of 440 samples +EV after the live fee (best −0.2%); CNO live is 13–33 s
      stale; paid Pinnacle live (pinnapi $149/mo) is the only candidate and manual betting is
      likely too slow for its 1–2 s windows. Not built (would show stale-odds false positives);
      findings sent to Tj with the paid-trial option. Also corrected RESEARCH §3's live-fee note
      (≈1.5% of stake at even odds, not 0.75%; the app's math was already right).

## Tj's request, 2026-09-27 ~03:00Z — every bet tracked, auto-settled, rechecked; a stats section

> The app tracker tab only shows 8 open bets. I placed almost 60 bets. I want to be able to track
> every single bet I placed and whether it won or lost. It should move all of the bets I made into
> the tracker section automatically. Also, if possible this section should have an option for me to
> scan for up to date average odds against sports books for each of the bets I made and to show
> whether the value of the bets I placed is still positive EV. For example, if I place 5 bets today,
> the tracker section can show me the current updated odds of each of those bets (a newly calculated
> fair odds value based on up to date odds across sports books and devigged) compared to what the
> odds were at the time I bet it. And it will show an updated EV value percentage, for example "now
> +3% ev" (in color green) or "now -2% ev" (in color red).
> 1) for every bet that I check on the cno scanner, log it permanently in the vigilant app, and keep
> track whether each bet was a win or a loss. this will require a background scores system to keep
> track of final scores and events. if possible, when I bet something on novig, automatically log the
> amount and type of bet into the vigilant app and check the box for that bet on the widget. but if
> that is not possible, log each bet as a $1 wager in the app.
> 2) make a stats section of the app that provides clean easy view of my percentage of actual bet
> wins and losses, total money gained or lost, and a running percentage of profit made, red number if
> negative and green if positive.
> After these features are built, run full tests on the app and make sure the features work well and
> do what they were designed to do, then ship.

### Plan
- [x] N1 (DONE: `BetTracker.logCno/untrack/edit/setStake/importPlaced` + `MainViewModel.markPlaced →
      logBet`, `unmarkPlaced → untrack`, one-time import (flag file `tracker_imported`); tests
      `BetTrackerTest` "CNO check logs $1…", "import from placed.json once", "FMV payout + old JSON").) Why only 8: the Tracker holds only bets tracked from Vigilant's own +EV cards; the widget's
      and CNO tab's ✓ went to placed.json (hide-only, dropped 12 h after the game). → every ✓ (widget,
      CNO tab, sheet) logs a permanent Tracker bet (CNO's bet, market, game, league, price, fair, EV,
      start, CNO links, Novig outcome/market when known), $1 stake by default (editable); Undo removes
      it; ✕ (removed) never logs. One-time import of the ✓ marks still in placed.json.
- [x] N2 (DONE: `data/tracker/BetSettler`, `NovigBetFinder.findEnded`, `app/SettleWorker` (WorkManager
      2.10.1, 3 h, network), VM `settleBets()` on init + Tracker tab; tests `BetSettlerTest` (5),
      `NovigBetFinderTest` "a finished game's bet is found…".) Auto-settle ("background scores system"): Novig's own catalog settles each outcome (WIN /
      LOSS / PUSH / fair-market value); pending bets whose game has started are checked on app open,
      on the Tracker tab, and by a periodic background job (network only, every few hours), the Novig
      outcome found through the catalog when not already known. Manual Won/Lost stays as an override.
- [x] N3 (ANSWERED: not possible, API reads subaccounts only; $1 per ✓, stake editable in the Tracker.) Auto-log from Novig (amount and type): check what Novig's API can read of Tj's own bets
      (NOVIG_API.md: keys, subaccounts, positions); build it if possible, else $1 per ✓ (N1) and say so.
- [x] N4 (DONE: `data/tracker/BetRecheck` + `BetTracker.observe` nowEv, VM `checkOdds()`, Tracker button
      and "now ±x% EV" line; tests `BetRecheckTest` (3), `ScreenshotTest.trackerBetsShowTheirEvNowGreenOrRed`.) "Check odds now" in the Tracker: for open bets, the current fair odds from every book
      (CNO's game page, devigged worst case, the same consensus as the green check) vs the price
      bet, "now +3% EV" green / "now −2% EV" red, and the odds at bet time.
- [x] N5 (DONE: TrackerScreen Stats|Bets, periods, running-profit line, by scanner, Open/Settled/All,
      stake dialog; tests `ScreenshotTest.trackerStats`, `trackerStakeIsEditable`, `BetTrackerTest`
      "stats count wins and losses…".) Stats section: win % (W-L-P), total money won/lost, running profit % (ROI), green/red; by
      source (CNO / Vigilant) and period.
- [x] N6 (DONE: full floor 496 tests 0 failures, assembleRelease OK; shipped v0.15.6 code 26, https://github.com/tjshea90/novig/releases/tag/v0.15.6.) Full tests (CLAUDE.md protocol) incl. these features end to end, then ship + link.
- [x] N7 VERIFIED LIVE 2026-09-27 ~07:45Z: **404** for all three market ids below, and
      `/v3/public/catalog/events?league=MLB&startsAfter=<14h ago>` lists only open games + futures (the
      night's finished MLB games are gone under every status filter). So Novig's public catalog can
      never settle a bet: BetSettler as built in v0.15.6 settles nothing. Fix = TASKS P4a (the
      2026-09-27T06:14Z request's full test). Original question: does
      `GET /v3/public/catalog/markets/{id}` return a finished market with outcome status WIN/LOSS, or
      404 (the public list already hides settled markets)? Check these, in-game at 04Z (MLB):
      `01a0db6d-bda2-7440-98f8-a9643f6c5145` (OAK/HOU), `01a0d9d1-f4cf-7063-a5e7-c3b75b930d7c`
      (SEA/LAA), `01a0d9d1-f48c-7e71-92de-ef88cf6c19ca` (SD/ARI). If 404: BetSettler needs another
      source (ESPN scoreboard for game lines; box scores for props), tell Tj; manual Won/Lost works meanwhile.

### Findings and design (written 2026-09-27 ~03:10Z so a fresh session can build it cold)
- N3 answered: NOT possible. docs.novig.com: every account route (positions, fills, orders,
  transactions, balance) targets an API **subaccount** (`{keyId}` = subaccount trading key; errors
  NOT_A_SUBACCOUNT_KEY); bets placed in the Novig app come from the main cash wallet, which no API
  scope reads. So per Tj's fallback: every ✓ logs a **$1** bet, stake editable in the Tracker. Tell Tj.
- Why only 8: `BetTracker` (tracker.json) only gets bets from Vigilant's +EV card "Track"; the
  widget/CNO-tab ✓ goes to `PlacedBets` (placed.json, hide-only, pruned 12 h after start), so most
  of his ~60 ✓ marks are already gone from disk; import the ones still in placed.json (N1).
- Data model (`data/tracker/BetTracker.kt`, all new fields optional for old files):
  `TrackedBet` + `source: String = "vigilant"` ("cno"), `placedKey: String? = null` (widget key
  "cno:<row key>" or "<market>/<outcome>", links the ✓ mark: Undo/"Not placed" deletes the pending
  bet), `american: Int? = null`, `book: String = "Novig"`, `gameUrl`/`betUrl: String? = null` (CNO
  links: rechecks + finding the Novig outcome), `nowFair`/`nowEv: Double?`, `nowAtMs: Long?`,
  `nowBooks: Int?` (N4), `settleValue: Double? = null` (payout per $1 contract for a Novig
  fair-market-value settlement), `settledBy: String? = null` ("novig" auto / "you" manual),
  `imported: Boolean = false` (from placed.json: EV/fair unknown). Make `fairAtBet` and
  `evPercentAtBet` `Double?` (imported bets have none); `BetStatus` + `FMV`. Update users:
  TrackerScreen, BetTracker.stats, SampleScan.kt:194-198, BetTrackerTest.kt:89-93.
  CNO bet from a ✓: price = 1/decimal(odds) (the price shown, live one if any), cost = price +
  Novig fee if the game had started (Fees.takerFee(price, MarketFee.GAME, true)), fairAtBet =
  CnoChecks.fairProbability(row) ?: (1+ev)/decimal, evPercentAtBet = pick.ev, league/event/
  market/bet/book/startsAt from the row, marketId/outcomeId from `NovigBetFinder.find` (Bet has
  both since v0.15.4) or "" until resolved. Vigilant item ✓: find the Opportunity in
  state.result by key → `tracker.track(o, 1.0)` + placedKey.
- Wiring: `MainViewModel.markPlaced(item)` also logs (not `markHidden`); `unmarkPlaced(key)` also
  deletes the PENDING tracked bet with that placedKey. One-time import on VM init after both
  stores load: every non-hidden PlacedBet whose key isn't a tracked placedKey → imported bet
  (title→selection, detail "market · event[ · book]", odds string → american, startsAtMs,
  placedAtMs → createdAtMs, stake $1).
- N2 auto-settle, new `data/tracker/BetSettler.kt`: for PENDING bets whose game started ≥ 1 h
  ago: resolve marketId/outcomeId if blank (NovigBetFinder; for imported bets without league,
  search `/v3/public/catalog/events` by time window without league, statuses incl. closed ones),
  then GET `/v3/public/catalog/markets/{id}` (`NovigPublicClient.market`): outcome status WIN →
  WON, LOSS → LOST, PUSH → PUSH, decimal → FMV with settleValue; TBD → leave. Paced ≥ 400 ms.
  Runs: VM init, Tracker tab shown, and a WorkManager periodic job every 3 h with a network
  constraint (add `androidx.work:work-runtime-ktx` to gradle/libs.versions.toml + app deps;
  Worker gets `(applicationContext as VigilantApp).container`). Manual Won/Lost stays (settledBy "you").
- N4 "Check odds now" button (Tracker): pending, not-started CNO bets with gameUrl →
  `CnoFeed.loadBooks(row, force = true)` (paced, CNO pauses respected) → `CnoBooks.check(view,
  american at bet)` → fairProbability → nowFair, nowEv = fair/cost − 1, Novig's current price from
  the view; before the start also write it as closingFair (CLV for CNO bets). Vigilant bets: their
  latest scan fair (closingFair/closingSeenAtMs, `observe`) and the button starts a scan when
  Vigilant's scanner is on. UI: "now +3.0% EV" green / "now −2.0% EV" red, "bet at +117 · fair now
  +105", checked "Xm ago".
- N5 Stats: Tracker tab gets a top switch "Stats | Bets". Stats: profit $ and ROI % (green/red),
  win % (W/(W+L)), record W-L-P, open bets, expected profit, avg EV (known only), avg CLV; period
  chips All / 30 d / 7 d / Today; split CNO vs Vigilant. Bets: filter chips Open / Settled / All,
  stake editable (tap → dialog), newest first.
- Tests to write: BetTrackerTest (CNO log, undo deletes, import, old JSON reads), BetSettlerTest
  (MockWebServer market statuses → WON/LOST/PUSH/FMV, TBD untouched, unresolved resolved),
  MiniWindowTest/VM-level where possible, ScreenshotTest (stats colors, now-EV colors, filters).

## Tj's request, 2026-09-27T06:14Z — full tests; every free odds API / sportsbook API; faster, more accurate scanning

> Run full tests on this app, research all available odds apis and sports books offering free apis
> and see if any of them can be incorporated into the vigilant app for better accuracy or faster
> scanning. Look for any ways to improve speed and accuracy and efficiency of the app code or UI or
> scanning

### Plan
- [x] P1 Local build/test floor ready (BRIEF.md build trap 6: SDK + mirror); run the full floor
      `:engine:test :data:test :app:testDebugUnitTest` (+ `-Pscreenshots`) and record the baseline.
      Baseline 2026-09-27 06:25Z: 496 tests, 0 failures, 0 errors, 7 skipped (live tests), exit 0.
- [x] P2 (DONE, checked 2026-09-29: RESEARCH.md §22 "Every free odds API / sportsbook feed, re-surveyed 2026-09-27",
      ranked, and P3 built from it.) Research: every odds API / sportsbook / exchange with a free tier or free public data NOT
      already in RESEARCH.md §4/§11 (e.g. Odds-API.io, SportsGameOdds, OddsPapi re-check, API-Sports
      odds, The Rundown, BALLDONTLIE, ESPN core odds, Action Network, SX Bet, ProphetX, Sporttrade,
      Betfair/Smarkets/Matchbook, BetDEX, Pinnacle guest API, Kambi/Bovada public JSON). Measure live
      from here what can be measured: coverage (NFL/MLB/NCAAF/NBA/NHL/WNBA, props), books, latency,
      limits, terms. Write RESEARCH.md §22 with a ranked verdict.
- [x] P3 Build what the research says is worth it (free, legal, adds sharp books or speed) as a
      reference source behind a Settings switch, with tests; say what was rejected and why.
      Design (RESEARCH §22.4), steps:
  - [x] P3a (data layer done + tested: ExchangeClientsTest PinnWire tests on a REAL fixture
        `data/src/test/resources/pinnwire-football.json`, ScannerTest "a source that prices a few book-only
        prop stats"; app wiring is P3c) Pinnacle via PinnWire: `ApiProvider.PINNWIRE` + `QuotaPolicy.PINNWIRE` (100/day, 20/min);
        `PinnapiClient` takes hosts in order (PinnWire pool first with `x-api-key` +
        `include_specials=1`, then pinnapi pool with `x-portal-apikey`, no specials); specials rows
        "Player Props" -> `LineKind.PLAYER_PROP` (bookKey "pinnacle", subject = player, stat via
        `PinnacleProps.stat(sport, units)`); Scanner.catalogTypes keeps book-only prop types when a
        source `pricesBookProps`. Tests: parse a trimmed REAL PinnWire fixture (lines + props),
        host fallback, header per host.
  - [x] P3b (data layer done: `reference/PropLineClient.kt`, PropLineClientTest 8 tests; app wiring is P3c) PropLine: `ApiProvider.PROPLINE` + `QuotaPolicy.PROPLINE` (1,000/day UTC, headers
        X-Daily-*), `PropLineClient` (game lines per league) + `PropLinePropsSource` (per game,
        needsCatalog, capped games per scan, reuse window); books = reference books mapped to
        PropLine keys, no exchanges/DFS/novig; withdrawn (`last_seen_at` < market `last_update`)
        and suspended outcomes dropped; team totals via `team`. Settings `usePropLine`. Tests from a
        schema-shaped fixture (MockWebServer), quota headers -> meter.
  - [x] P3c (done: ScreenshotTest.settingsTakePinnWireAndPropLineKeys, theMetersShowWhatsLeftPerKey… (4 keyed
        providers); full floor 512 tests green 2026-09-27 ~07:30Z) App: keys generalized (UiState.keys map), Settings key editors + meters for PinnWire and
        PropLine, source list/FeedScreen hints, SOURCE_ORDER, requestKey, maxFairAgeMs, planFor book
        filter for propline. Screenshot test for the Settings sources section.
- [x] P4 (DONE 2026-09-27 ~07:05Z. Found and fixed, each with a test that fails on the old code:
      (1) settle broken: Novig forgets finished games (N7) -> P4a score feeds, live 8/8; (2) planning
      re-tokenized team names on every comparison: TeamMatcher cache, 165 ms -> 16 ms per plan for 61
      NCAAF games x 4 feeds (`TeamMatcherTest` "a name is tokenized once"); (3) Novig's board failed
      the whole scan on one short 429: now waited out (`NovigPublicClientTest` "a short 429 on the
      board"); (4) a second corrupt JSON file replaced the first copy set aside, and saves weren't
      flushed before the rename (`JsonFileStoreTest` "a second corrupt file never replaces the
      first"); (5) stale wording (Novig settles, Kalshi-only props hint). Reviewed with nothing to
      fix: engine (grid, devig, fees), Polymarket/Kalshi clients, CnoFeed lanes, NovigLive,
      ScanRunner, ScanService (wake lock bounded, released). Proposals for Tj (not built): PinnWire
      `since` deltas to cut the ~700 KB NFL props download per scan; using PropLine's Novig prices to
      order Novig reads.) Full tests (CLAUDE.md protocol) over the whole app: engine, data (novig/reference/match/
      scanner/cno/keys/store/tracker), app (VM, service, widget, PiP, screens). Fix every bug found,
      each with a named test that fails before the fix; speed/efficiency/UI improvements (no major
      UI change without Tj's OK — list those as proposals instead).
- [x] P4a (DONE: `tracker/Scores.kt` FreeScores (ESPN + MLB Stats API), `tracker/BetGrader.kt`, BetSettler rewired;
      tests FreeScoresTest 6 (real fixtures), BetGraderTest 7 (CNO's real market names), BetSettlerTest 6; LIVE:
      `VIGILANT_LIVE=1 ... --tests '*LiveScoresTest'` settled 8/8 real bets (Mets@Nats, Falcons@Packers) in 4 requests;
      dead `NovigBetFinder.findEnded` removed) SETTLE FIX (found in this full test, N7): results from ESPN's free scoreboard (game lines:
      moneyline, spread, total, team total, 1st half / F5 / 1st inning from line scores) and ESPN's box
      score (player props: the stats Novig lists), no key; Novig's catalog kept first for FMV/void.
      `data/tracker/EspnResults` + BetSettler wiring; tests from real ESPN replies (fixtures).
- [x] P5 (SHIPPED v0.16.0 code 27, 2026-09-27T07:16Z: CI green on 9bb3d4a, ship.sh gates green, release.yml
      green, https://github.com/tjshea90/novig/releases/tag/v0.16.0, recorded in BUILDLOG.md; link sent.) (floor DONE 2026-09-27 ~07:10Z: 528 tests, 0 failures, 8 skipped live, exit 0; assembleRelease OK 5.5 MB;
      49 screenshots, settings/meters/tracker looked at. Next: CI green on head, ship.sh, release.yml v0.16.0 code 27,
      record, link) Full regression (exit code AND output), screenshots looked at, ckpt, ship, release, record,
      send link + findings.
- [ ] P6 (PARTLY VERIFIED 2026-09-27 ~14:45Z on Tj's phone with his own key: the league board (`/odds`) parsed,
      33 games matched; `/events` sent `bookmakers: null`, fixed in v0.16.1. Still to see live: one game's props.)
      VERIFY PropLine LIVE (couldn't on 2026-09-27: the shared demo key was at its daily cap; it resets at
      00:00 UTC). With the demo key from prop-line.com/llms-full.txt (or Tj's own), read
      `/v1/sports/americanfootball_nfl/odds?markets=h2h,spreads,totals&bookmakers=pinnacle,draftkings,fanduel`
      and one game's `/events/{id}/odds?markets=player_receptions` and check `PropLineClient.parseEvents` /
      `parseEvent` read them (a gated live test like `LiveScoresTest`); fix the parser if the real shape differs.

## Tj's screenshot, 2026-09-27 ~14:45Z (v0.16.0 +EV feed) — "Review and fix the error in the screenshot"

> Error banner: "PropLine props NFL: Unexpected JSON token at offset 537: Expected start of the array '[',
> but had 'n' instead at path: $[0].bookmakers JSON input: ....._event_ids":null,"bookmakers":null},
> {"home_team_key":"colts"....". (Same scan: "PropLine 33" games matched, so the league board parsed live.)

- [x] Q1 (DONE: PlEvent/PlBook/PlMarket/PlOutcome all optional, lenient Json, each game decoded alone;
      `readableError` for banners. Tests: PropLineClientTest "PropLine's real game list, with null bookmakers…",
      "props are bought from the real game list's ids", "nulls anywhere in a board…" (all 3 failed on v0.16.0),
      "an unreadable reply shows a short plain message".) Cause: PropLine's `/v1/sports/{sport}/events` (the props source's game list) sends
      `"bookmakers": null` (and other nulls); `PlEvent.bookmakers` was a non-null list, so the whole
      list failed and no PropLine props were bought. Fix: null-tolerant PropLine decoding (every list
      and string may be null), one bad event never sinks the rest; test from the real shape that fails
      on the old code.
- [x] Q2 (SHIPPED v0.16.1 code 28, 2026-09-27T15:01Z: floor 532 tests 0 failures, CI green, release.yml green,
      https://github.com/tjshea90/novig/releases/tag/v0.16.1, recorded.) Light tests, floor, ship v0.16.1, link. Record that PropLine's /odds board parsed live on Tj's
      phone (P6 partly verified).

## Tj's request, 2026-09-27T15:10Z — placed bets hidden everywhere; Vigilant scanner widget + exact bet slip

> Right now some bets are showing up in the vigilant positive EV scanner which I already placed in the
> cno scanner widget. Make sure the bet tracker works across all parts of the app and hides bets I
> already placed throughout the whole app regardless of scanner. Also, on the regular vigilant scanner,
> make it also have a widget and be able to open the exact bet slip in novig. Right now I see bets and
> it has an open novig button but the button only opens the app, not the exact bet slip like the cno
> scanner does

### Plan
- [x] R1 (DONE: cause = each list filtered only its own keys; a CNO ✓ saved `cno:<row>` + wording, which Vigilant's
      feed never looked at.) Investigate: how placed/tracked bets are keyed per scanner (PlacedBets keys "cno:<row>",
      "<market>/<outcome>", aliases; TrackedBet placedKey/marketId/outcomeId), where each list filters
      them (+EV feed, Games, CNO tab, widget, PiP), and why a CNO ✓ still shows in Vigilant's feed.
- [x] R2 (DONE: `data/tracker/PlacedIndex` (key, Novig outcome id, or game + BetGrader pick within 12 h; tracked bets
      on games within 36 h), `UiState.placedIndex/feedOf/indexed/hasCno`, MiniWindow.items, ScanService counts; marks now
      save event/market/outcomeId. Tests: PlacedIndexTest (5), PlacedEverywhereTest "a bet placed on CNO's widget leaves
      Vigilant's +EV feed and widget", "…Novig outcome itself…", "a bet only in the tracker…", "…already in the tracker…",
      "a same-named bet on a different day's game still shows". Games tab left as a full price board.) One "already placed" rule for the whole app: a bet placed/tracked from ANY scanner (✓ in the
      widget/CNO tab, Track on a card) is hidden from the +EV feed, the CNO tab, the floating widget and
      the mini window, matched by Novig outcome id when known, else by the same game+market+side; tests.
- [x] R3 (DONE: `OpportunitySheet.betSlipLink`, "Open this bet in Novig" via `LocalOpenNovig(link)`; widget/mini window
      already used `MiniWindow.novigLink`. Test: PlacedEverywhereTest "Vigilant's bet sheet opens the exact bet slip",
      ScreenshotTest theWidgetWorksOnVigilantsScanAlone.) Vigilant's "Open Novig" opens the exact bet slip (`novigapp://events/<outcomeId>`) from the
      card, the bet sheet, the mini window and the widget; test.
- [x] R4 (DONE: widget no longer needs CNO on; top-bar switch CNO only → Both → Vigilant only (`nextScanner`,
      `MainViewModel.setScanner`); Settings offers the widget and its rescan chips in every mode. Tests:
      theWidgetWorksOnVigilantsScanAlone (9p), theWidgetsTopBarSwitchesVigilantsScanOnAndOff (9n),
      theWidgetWithBothListsABetBothScannersFoundOnce (9o), settingsOfferTheFloatingWidgetOnVigilantsScanAlone,
      PlacedEverywhereTest switch order.) Vigilant scanner gets the floating widget too (Vigilant-only mode as well as Both): tap = exact
      bet slip, ✓ placed (tracked + hidden everywhere), ✕ remove, Undo; tests + screenshots.
- [x] R5 (SHIPPED v0.16.2 code 29, 2026-09-27T15:45Z: floor 549 tests 0 failures, CI green, release.yml green,
      https://github.com/tjshea90/novig/releases/tag/v0.16.2, recorded.) Light tests (+ screenshots), floor, ship, link.

## Tj's request, 2026-09-27 (mid R-work) — outlier bets left out of the Tracker's stats
> For the stats/tracker sections, do not count any bets that are outliers (currently + or - over 6% ev)
> as wins or losses. Ignore them completely. I don't want the average skewed by a single bet that is an
> outlier

- [x] O1 (DONE: `BetTracker.OUTLIER_EV` = 0.06, `TrackedBet.isOutlier`, `stats()` drops them first,
      `TrackerStats.outliers`. Test: BetTrackerTest "bets over 6% EV either way are left out of every stat"; void test
      kept meaningful with a 5% EV.) Outlier = a tracked bet whose EV when placed (`evPercentAtBet`) is over +6% or under −6% (one
      constant, `BetTracker.OUTLIER_EV`, so the line can move). `BetTracker.stats` leaves them out of
      everything: record, win %, profit, profit %, staked, expected, avg EV, avg CLV, beat-the-close, open
      count, by-scanner rows; the running-profit line too. Bets with no EV (imported) are not outliers.
      Tests (data): stats with and without outliers; the ±6% edges.
- [x] O2 (DONE: note above the stats, "Outlier (over ±6% EV): not in stats" on the card, running-profit line skips
      them. Tests: trackerLeavesOutlierBetsOutOfTheStats (4c), anOutlierBetSaysItIsNotInTheStats.) Tracker UI: the Stats tab says how many bets were left out as outliers; each outlier's card in
      Bets says "Outlier: not in stats". Screenshot test.
- [x] O3 (SHIPPED in v0.16.2 code 29.) Ships with R5 (v0.16.2).

## Tj's request, 2026-09-27T15:59Z — full tests; best APIs first, overlapping ones as automatic fallbacks
> Run full tests. Read docs on the apis  and see which ones are best to use and optimize the usage of
> them if needed. If apis overlap odds from the same sports books, use the best/fastest API first and
> the others as automatic fallbacks

- [x] S1 (DONE: RESEARCH.md §23.1 table; PinnWire llms-full.txt + PropLine llms.txt/freshness + The Odds API guide re-read
      today; Kalshi/Polymarket timed live.) Map every odds API the app reads (Novig, PinnWire, pinnapi, Polymarket, Kalshi, PropLine, The
      Odds API, CNO, ESPN/MLB scores): which books each one carries, what it costs per call and per day,
      how fast it answers, and how the scan orders them today (Planner/Scanner/Pricing, key pools).
      Re-read each provider's docs (RESEARCH.md §22, NOVIG_API.md first; web for anything changed).
- [x] S2 (DONE: RESEARCH.md §23.2: Pinnacle = PinnWire → pinnapi → PropLine's copy → The Odds API's copy (merge order,
      no extra calls); sportsbooks = PropLine, then The Odds API as fallback; props = PropLine props, then Odds API props for
      what PropLine didn't price; exchanges direct; PinnWire since= rejected (no deletions on REST).) Decide the order per book: where two APIs give the same book (Pinnacle: PinnWire, pinnapi,
      The Odds API, PropLine; others: PropLine vs The Odds API), the best/fastest one goes first and the
      rest are fallbacks used automatically only for what the first couldn't give (failure, quota out,
      league or market missing). Write the decision into RESEARCH.md.
- [x] S3 (DONE: ReferenceSource.fallbackFor/needed, ScanContext.covered/firstAnswered, Scanner.covering, TheOddsApiClient.needed,
      OddsApiPropsSource skip, standby drops stale snapshot, Settings/feed copy. Tests: ScannerTest "a fallback waits for the API it
      backs up…", "when the first API can't answer…", "without the first API in the scan…", "a fallback standing by drops its own
      older answer" (failed before the fix); OddsApiFallbackTest (6); OddsApiPropsTest "behind PropLine, credits go only to…";
      CreditEstimateTest backup estimates, standby summary, feed names; ScreenshotTest settingsSayTheOddsApiBacksUpPropLine,
      settingsSayPropCreditsOnlyBackUpPropLine.) Build it: one ordered fallback chain per book/league, no duplicate spend on a book another API
      already returned this scan; usage optimizations found in S1 (caching, batching, fewer calls).
      Tests that prove the fallback kicks in and that the second API isn't called when the first
      answered.
- [x] S4 (DONE: floor 567 tests 0 failures + screenshots checked (5_settings sources, usage cards); fixes with tests:
      PlacedIndex doubleheader/unknown-start (PlacedIndexTest; both checks FAIL on v0.16.2's code), stale fallback snapshot,
      roster lane gated on CNO (PlacedEverywhereTest "with CNO asleep…"), Odds API list failure = standby (OddsApiFallbackTest).
      RESEARCH.md §23.5.) Full tests (CLAUDE.md protocol): automated floor + screenshots, sweep every tab and subsystem,
      fix what's found with named tests.
- [x] S5 (SHIPPED v0.16.3 code 30, 2026-09-27T16:41Z: floor 567 tests 0 failures (exit 0 + XML counts), ship.sh suite green,
      CI green, release.yml green, https://github.com/tjshea90/novig/releases/tag/v0.16.3, recorded.) Regression (exit code + output), ckpt, ship, release, link.

## Tj's request, 2026-09-27 (~16:50Z) — PropLine's Novig prices order the Novig reads, with automatic fallback
> Yes, use PropLine's Novig prices to order the reads, but if there is any failure or delay, make the app
> automatically fallback to the original novig read

- [x] T1 (DONE: PropLineClient asks `novig` in the same /odds and per-game calls, `parseBoard`/`parseEventBoard` split it into
      RefSnapshot.novig; PropLinePropsSource passes on Novig quotes ≤3 min old. Test: PropLineClientTest board test asserts
      the bookmakers param and the split.) Read Novig's own prices from the PropLine league call already made (add `novig` to its books: no
      extra request), kept apart from the fair-odds books (never priced, never counted as coverage).
- [x] T2 (DONE: Scanner.preview/previewOf stand-in books through Pricing; fetchOrder uses preview EV before last scan's. Test:
      NovigPreviewTest "PropLine's Novig prices put the likeliest +EV line first and the worst last" (also: nothing priced from
      them).) Scanner: before and while reading Novig's books, estimate each planned line's EV at PropLine's
      Novig price and read the likeliest +EV lines first. The preview only orders reads: the feed is always
      priced from Novig's own books.
- [x] T3 (DONE: NovigPreviewTest "with no relay…", "a relay that answers late never holds the reads back", "a relay that fails…",
      "a relay older than three minutes orders nothing"; preview errors → empty map.) Automatic fallback: no PropLine key, PropLine failed, answered late (the book reads don't wait for
      it past a short limit), its Novig prices too old, or a line it doesn't quote → the original order
      (open bets, last scan's EV, props/periods, main lines). Tests for each.
- [x] T4 (DONE 2026-09-27 ~18:00Z: light review of every file changed since v0.16.3 + callers (feedAt used by feed/tab badge/mini window/widget; Recheck on old odds scans instead); fixed stale KDoc (ScanSettings.staleReferenceMinutes, enabledSources) and RESEARCH §24.2 re-read interval; RESEARCH.md §23.6 present. Floor: engine 39, data 385 (8 live skipped), app 161, 0 failures, exit 0 + XML counts; screenshots checked (1_feed, 5_settings reuse 1m/2m chips, 8_cno, 9_floating_widget); assembleRelease OK.) Light tests + floor with screenshots, RESEARCH.md §23 note, ship, link.

## Tj's request, 2026-09-27 (~17:00Z, after T1–T4) — never stale sportsbook odds in a comparison
> Do a thorough scan of the app and make sure it never gives me stale odds when comparing odds from other
> sports books. This is important because it can give me false positive EV. The other sports books odds
> MUST be current or at most a few minutes old.
>
> Do this after the process you already started

- [x] U1 (DONE: RESEARCH.md §24.1, 7 findings.) Audit every place a fair line is built from other books' odds (Vigilant's Planner/Pricing from
      every ReferenceSource and its re-use windows/stale limit; recheck and re-price; props caches;
      CNO's books, CnoBooks, NovigLive, BetRecheck; the tracker's "now EV"): list each place an old
      quote can price, and how old it can be today.
- [x] U2 (DONE: Freshness.MAX_QUOTE_AGE_MS = 5 min per quote by the feed's own last-seen time, MAX_REUSE_MS = 2 min; RESEARCH.md
      §24.2 with the Odds API/PropLine doc quotes.) One hard age limit ("a few minutes"): no book quote older than it ever feeds a fair line or an
      EV shown as current — by the time it was fetched AND by the book's own last update where the feed
      says it. Settings / re-use windows can't raise it. Decide the number with the evidence (RESEARCH).
- [x] U3 (DONE, RESEARCH.md §24.3. Tests that FAILED on v0.16.3 code: FreshOddsTest "a book price the feed last saw over five minutes
      ago…", "a feed's answer is re-used for two minutes at most…", "a failed call's last answer stops pricing…", "a recheck judges the
      fair odds' age now…"; plus PropLineClientTest "each price carries when PropLine last saw it", FreshOddsAppTest (4),
      ScreenshotTest oldOddsLeaveTheFeedAndAskForAScan (replaces the test that expected old bets shown), agingOddsAreFlagged…,
      cnoTabHidesItsBetsWhileCnosOddsAreOld.) Fix every path found, each with a test that fails on the old code.
- [x] U4 (floor above; the floor caught PropLineClientTest "each price carries when PropLine last saw it" contradicting the parser's withdrawn-side rule: fixture rewritten (market 9 min, older side 8 min -> 8 min; fails on v0.16.3 code, which used the market's time). Ship: see BUILDLOG v0.16.4.) Floor + screenshots, ship, link.

## Tj's request, 2026-09-27 (~18:55Z) — the same +EV scanner for BetMGM, without disturbing Novig
> Be very careful not to break or disrupt anything in this app, but make it also do the same exact functions
> to find positive EV on betmgm. It should do exactly what the app already does for novig, but at the ability
> to do the same for betmgm. Do not scan for both at the same time unless this can be done without wasting too
> much api usage. If needed or if smart, make this a totally separate app for the betmgm scanner so as not to
> disturb novig, and copy the logic you already built from vigilant

**Decision (this session):** a separate app, **Vigilant MGM** (`com.tjshea.vigilant.betmgm`), built from the
same code as Vigilant rather than a hand-copied fork: a second Android module `mgm` compiles `app`'s own
sources and resources with one build-time switch (`BuildConfig.BOOK`: `novig` in `app`, `betmgm` in `mgm`).
Why: the Novig app keeps its applicationId, storage, keys, placed bets, tracker and every behavior (its code
paths never see BetMGM at runtime), both can be installed side by side, and every future fix reaches both
(a copied fork drifts: CLAUDE.md's "duplicated normalizer" lesson). One scan targets one book, so nothing
scans both at once. BetMGM costs no extra requests: its prices ride in the PropLine / The Odds API calls that
already fetch the fair-odds books (BetMGM asked for alongside them, split off as the board, never in the fair line).

- [x] V1 (DONE: data/book Sportsbook/BookBoard/SportsbookScanner, scanner/OddsScanner (Scanner implements it unchanged),
      NovigBook.posted, RefQuote/RefBookMarket book ids, PropLineClient(relayNovig, bookIds), BetTracker(ownBook). Tests:
      SportsbookScannerTest (10: pricing vs fair never its own, one request serves both, BetMGM ids, stale BetMGM quote never
      priced, BetMGM's own age ages the EV, recheck = 1 request, reprice no network, reversed home/away, Novig's PropLine
      request unchanged, no-BetMGM feed); engine 39 / data 399 green.) Data: `data/book/Sportsbook` (NOVIG / BETMGM: names, feed keys, CNO site id and column), `MgmBoard`
      (BetMGM's quotes in a feed's snapshot → an event/market/outcome board with exact prices, no fee),
      `MgmScanner` (same `scan/recheck/reprice/unscannedLeagues` as `Scanner` behind one `OddsScanner`
      interface; PropLine league boards + props per game, PinnWire, Kalshi, Polymarket, The Odds API fallback;
      BetMGM never prices its own fair line; recheck re-reads PropLine for the feed's leagues/games only).
      Tests: BetMGM's price vs fair → EV; BetMGM left out of the fair line; one PropLine request serves both;
      stale BetMGM quote never priced; recheck request count; Novig's `Scanner` untouched (all old tests green).
- [x] V2 (DONE: BetMgmLinks + AppBook.betLink; CNO rows use CNO's own deeplink (TapLink with no Novig catalog). Tests:
      BetMgmLinksTest (4), MgmAppTest bet-slip/widget links. NOT verified live: PropLine demo key capped all session.) Bet slips: BetMGM's link from PropLine's ids (`includeBookIds`/`includeLinks`: fixture, market,
      option → `sports.<state>.betmgm.com/en/sports?options=f-m-o`), else its event page, else BetMGM's
      site; state picked in Settings. CNO rows follow CNO's own BetMGM deeplink. Tests for each fallback.
- [x] V3 (DONE: AppBook, mgm module, book-aware copy, Novig-only parts gated, BetMGM state picker, CNO site 4, CNO live fee
      only on Novig rows, MGM-only widget merge by game/side/line. Tests: app 161 Novig tests unchanged, MgmAppTest (9),
      MgmBuildTest (2, real mgm build: app id, "Vigilant MGM", BetMGM); both release APKs built and verified locally
      (aapt2: com.tjshea.vigilant "Vigilant" / com.tjshea.vigilant.betmgm "Vigilant MGM", same cert AB:22:…).) App: `BuildConfig.BOOK` in `app` (novig) and new module `mgm` (betmgm, own name/icon, same keystore,
      versionCode/Name shared); every "Novig" the user reads comes from the book; Novig-only parts off in
      Vigilant MGM (Novig key, maker bid, order-book depth/width, Novig fee checks, NovigLive, Novig catalog
      finder); CNO view defaults to site_id=4 (BetMGM). Tests: Novig build's screenshots/strings unchanged;
      mgm screenshots say BetMGM.
- [x] V4 (DONE: release.yml builds both, verifies both certs, attaches vigilant-vX.apk and vigilant-mgm-vX.apk; ci.yml uploads
      both debug APKs; ship.sh's `./gradlew test` already covers :mgm.) Build/release: ci.yml covers `:mgm` (root `test`/`assembleDebug`), release.yml builds both and
      attaches both APKs to the same Release (Novig's APK name unchanged); ship.sh gate covers both.
- [x] V5 (SHIPPED v0.17.0 code 32, 2026-09-27T19:56Z: docs in BRIEF.md/CLAUDE.md/RESEARCH.md §25; light review of the whole diff +
      callers of every changed shared function (CnoView.normalize, CnoChecks, NovigBook.takeLadder incl. NovigLive, BetTracker,
      PropLineClient, ScanRunner); floor engine 39 / data 400 (8 live skipped) / app 170 / mgm 2 = 611, 0 failures, exit 0 + XML
      counts; ship.sh suite green; CI 36345562068 green on f68ecd4; release.yml 36345843461 green; both APKs on
      https://github.com/tjshea90/novig/releases/tag/v0.17.0.) Docs (BRIEF.md decision, CLAUDE.md surface, RESEARCH.md §25), light tests, ship, link.

## Game start-time filter (Tj, 2026-09-27T21:17Z)

> "for the regular version of the NoVig Vigilant app. Make it so I can add a filter to only show games that start within the next 24 hours or 12 hours or 48 hours."

- [x] G1 (DONE: StartsWithinTest (6): feed, widget, CNO list/shown/count text, Games board, clock, saved setting; ScanService counts use it too.) `ScanSettings.startsWithinHours` (0 = any, 12 / 24 / 48): applied at `now` in `UiState.feedAt` and
      `cnoCandidates`, so the +EV feed, CNO tab, badges, mini window and widget all obey it; the Games board too.
      Display filter only (no scan or API change). Tests for each list.
- [x] G2 (DONE: ScreenshotTest.theFeedCanShowOnlyGamesStartingSoon + 1c_feed_starts_within_24h.png looked at; MgmAppTest asserts no row in Vigilant MGM; chips say "Any time" ("Any" clashed with the odds cap).) UI: chip row on the +EV feed (next to the sort) + Settings; Vigilant only (hidden in Vigilant MGM). Screenshot check.
- [x] G3 (DONE 2026-09-28T01:23Z: release.yml run 36365476060 green on main 06de419 (code = a0a82b7, CI green); Release v0.17.1 has vigilant-v0.17.1.apk only; record-release.sh done; link sent.) Light tests, ckpt, ship, link. (HELD 21:35Z: v0.17.1 not released yet; it now ships with H1-H4 below, Vigilant only.)
      PAUSED 21:58Z at Tj's request (switching model): code shipped to main by ship.sh (3a66035, v0.17.1 code 33); ONLY the
      release is left: wait for CI green on main's head, trigger release.yml, confirm tag v0.17.1 has vigilant-v0.17.1.apk only,
      tools/record-release.sh, send link. Tj's 21:58Z re-send of the H1-H4 message is the same request (already done).

## MGM dormant, CNO time picker, Maven 429s, full tests (Tj, 2026-09-27T21:34Z)

> "1) from now on, everything in this repo and anything I ask you to do will always be for the regular vigilant app
> for novig, unless I explicitly request something for novig mgm. Novig mgm should be dormant and no changes made at
> all unless I ask for it. All future work and versions and GitHub releases will be for regular vigilant for novig
> only unless I say otherwise.
> 2) make sure on the app I can select the time periods 12h 24h 48h and anytime for the cno scanner and cno widget as well.
> 3) research and figure out why Claude code very frequently gets maven central 429 errors. Find ways to fix this
> online and let me know if there is anything I can do to fix it.
> Run full tests and see how vigilant can be improved"

- [x] H1 (DONE: CLAUDE.md "Vigilant MGM is dormant" section + BRIEF.md; settings.gradle.kts includes :mgm only with -Pmgm (checked: `gradlew projects` lists app/data/engine, with -Pmgm also mgm); release.yml builds, verifies and attaches vigilant-vX.apk only; ci.yml uploads app debug APK only; mgm/ untouched.) Standing rule written into CLAUDE.md + BRIEF.md: all work/versions/releases are Vigilant (Novig) only; Vigilant
      MGM frozen at v0.17.0, `mgm/` untouched. Build: `:mgm` included only on request (`-Pmgm`), release.yml builds and
      attaches Vigilant's APK only, ci.yml uploads Vigilant's debug APK only.
- [x] H2 (DONE: CNO tab row (only while CNO is on) + widget top-bar switch cycling Any time/12h/24h/48h, same setting; CNO count line and widget empty text say what the window hides. Tests: StartsWithinTest (8), ScreenshotTest cnoTabPicksTheStartTimeWindow, cnoTabHidesTheWindowWhenCnoIsOff, theWidgetsTopBarPicksTheStartTimeWindow, theWidgetSaysWhenTheWindowHidesEverything; screenshots 8e/9q/9r looked at.) "Starts within" (Any time / 12h / 24h / 48h) selectable on the CNO tab and in the floating CNO widget
      (same setting as the +EV tab). Tests.
- [x] H3 (DONE: cause = Central rate-limits by shared egress IP, empty caches every session; fix = tools/setup-android.sh (SDK + Gradle mirror init + vigilant.mavenMirror for Robolectric, which app/build.gradle.kts passes to test JVMs; CI unchanged). Verified: bogus mirror + cleared jar cache fails, Google mirror passes. BRIEF.md trap 6 rewritten with sources + what Tj can do (paste the script as the environment setup script).) Research Maven Central 429s in Claude Code on the web; write findings + fixes (what Tj can do) into BRIEF.md
      build trap 6 / RESEARCH, and apply any in-repo fix that's safe.
- [x] H4 (DONE 2026-09-27 ~21:55Z. Floor engine 39 / data 400 (8 live skipped) / app 185 = 624, 0 failures, exit 0 + XML
      counts; live Novig smoke (2), Novig bet finder (2), CNO smoke (1), scores (1) green; :app:assembleRelease built, cert AB:22:…
      verified, com.tjshea.vigilant 0.17.1/33. Swept every change since v0.16.3 (Scanner freshness + relay preview, Pricing,
      MainViewModel, WidgetRescan, VigilantApp, MiniWindow, Games, OpportunitySheet, ScanService, CNO checks/feed/view, tracker)
      + screenshots. Fixed: F1 Recheck (+EV tab, widget, PiP) re-read EVERY market in the scan, shown or not: hidden-by-window
      markets spent Novig requests and the recheck cap, and one hidden stale EV turned the recheck into a full scan →
      feedMarketIds reads UiState.feedAt (StartsWithinTest "Recheck re-reads only the bets shown", fails pre-fix). F2 a11y: the
      sort and start-time buttons now tell TalkBack which is picked (ScreenshotTest.theFeedCanShowOnlyGamesStartingSoon
      assertIsSelected, fails pre-fix). F3 widget chip says "Any" (not "Any time") so the status text keeps room.)
      Full tests per CLAUDE.md: floor, sweep, fixes with named tests, improvement list for Tj; then ship v0.17.1
      (Vigilant only) with G1-G2 + H1-H4, link.

### Suggestions from the 2026-09-27 full test (wait on Tj; not started)

- [x] S1 (DONE in v0.19.6 with V5.) Let the scan follow "Starts within": with 12/24/48h picked, a scan still reads Novig books and spends API
      credits on games up to "Days ahead" (3 days by default). Scanning only the window would cut Novig reads,
      PropLine / The Odds API usage and scan time; widening the window would then need a new scan.
- [ ] S2 Show the start-time window in the picture-in-picture window's header (it can't take taps, so today
      there's no sign there that bets are being hidden).
- [x] S3 (DONE, seen 2026-09-28 ~22:40Z: the environment has a setup script that ran at 19:12:04-19:12:21Z
      ("Running init script" / "Successfully executed init script" in /tmp/environment-manager.out), and a fresh
      session starts with /opt/android-sdk and ~/.gradle/init.d/mirror.gradle.kts from that run.) Tj's side: paste
      `tools/setup-android.sh` into the cloud environment's Setup script (BRIEF.md trap 6) so
      every session starts ready to build without touching Maven Central.

## Faster scans, 1,200 prices, background auto-scan, +EV alerts, odds cap (Tj, 2026-09-28T03:19Z)

> "See if you can make the scans better or faster or find more bets. Also increase the limits on the amount of novig
> prices per scan so I can select 500 600 700 800 up to 1200. Make it so I can run the app in the background and it will
> continue scanning and also have an option to auto scan either cno or both cno and vigilant every 5 10 20 30 or 40
> minutes in the background, even if the app is not open on the screen. And make an option that if it is scanning in
> the background and at any time it finds positive EV bets of 3% or higher and multiple books agree on the price that it
> sends me an android push notification and I can click on the notification and it will open the exact bet in novig
> immediately, just like the cno widget already does. Make in the options I can select automatic notifications for a
> minimum of 2%, 3%, or 4% positive EV finds. Also in the options for vigilant, right now the longest odds shown option
> stops at +300. Let me choose +200 +150 and +120 and get rid of any option over +300.
> Run full tests protocol on this app after all work is done"

- [x] P1 (DONE: MAX_BOOKS_CHOICES 100…1,200 by 100, props/game adds 16 and 24; scan wake lock 20 min; hint gives time per size. BiggerScansTest "Novig prices per scan go up to 1,200", "a scan set to 1,200 reads 1,200 prices, never more", budget 1,500 → 1,200.) "Novig prices per scan" choices up to 1,200 (500, 600, 700, 800 … 1,200); nothing else (planner, pacing,
      wake lock, notification) caps a scan below what's picked. Test.
- [x] P2 (DONE: MAX_ODDS_CHOICES +120/+150/+200/+300, default +300, schema 6 moves a saved longer cap or none to +300 once. BiggerScansTest odds-cap tests; ScreenshotTest.settingsOfferTheOutlierGuardAndAnOddsCap (no Any/+500/+1000/+2000, +120 picks 120).) Vigilant's "Longest odds shown": +120 / +150 / +200 / +300 only (nothing over +300, no "Any"); a saved
      longer cap moves to +300 once. Test.
- [x] P3 (DONE: FairMemo: each plan's fair lines devigged once, not per partial result (1,200 markets × 25 books: ~160 ms → 3.4 ms per partial, measured); FairLine.booksUsed/usedUpdates computed once; power/Shin bisection stops at 1e-13 (~45 steps, not 100); end-of-scan re-read of edges read over 60 s before the end (≤40 books) so the feed and alerts use current prices, vanished ones dropped. BiggerScansTest memo + re-read tests (8 total).) Scans better / faster / more bets: read Scanner, Planner, RateGate, NOVIG_API.md; do what's safe and
      measurable (tests for each change).
- [x] P4 (DONE: ScanSettings.autoScan Off/CNO/CNO + Vigilant + autoScanMinutes 5-40; AutoScanService (specialUse FGS, ongoing note with Scan now/Stop), AutoScanAlarm (exact while idle, USE_EXACT_ALARM), AutoScanReceiver (alarm, boot, update), AutoScanner.cycle (CNO list + top bets' books + Novig live, then Vigilant's scan via AppContainer.startVigilantScan); Settings section with notification/battery prompts. AutoScanTest (settings, clock, ongoing text, alarm set/cancel, manifest), ScreenshotTest.settingsOfferBackgroundAutoScanAndAlerts (5d png looked at), autoScanOffHidesTheInterval.) Background auto-scan: Settings option Off / CNO / CNO + Vigilant, every 5 / 10 / 20 / 30 / 40 min, running
      with Vigilant closed (foreground service with its own notification, woken by alarms, wake lock only while a
      scan runs). Tests.
- [x] P5 (DONE: alertMinEv Off/2/3/4% (3% default); AlertPicks (CNO: CnoBooks CONFIRMED at Novig's price now; Vigilant: Agreement 3+ two-sided books, 3+ agreeing, worst-case devig, price ≤3 min old); AlertLog alerts.json (once per bet, by Novig outcome across scanners); EvAlerts high-importance notification whose tap opens novigapp://events/<outcome> in Novig's app; also after a Scan left running in the background. AutoScanTest (CNO/Vigilant picks, placed/window/unread books excluded, exact link, notification + intent), AgreementTest 3, AlertLogTest 3, NovigLiveTest readNow.) +EV alerts: a push notification for each new bet at or over 2% / 3% / 4% EV (option; 3% default) that several
      books agree on; tapping it opens that exact bet slip in Novig (as the widget does). Never the same bet twice,
      never a placed/hidden bet, only inside "Starts within". Tests.
- [x] P6 (DONE 2026-09-28 ~04:15Z: floor 656 tests, 0 failures, 8 live skipped, exit 0 + XML counts; live Novig smoke (2), bet finder (2), CNO smoke (1: 14 of 14 books CONFIRMED a real bet, exact link), scores (1) green; 110 screenshots green, 5d/5e looked at; release APK built, manifest checked (specialUse service + subtype, receiver, exact-alarm/boot/notification permissions). Fixes: F1 two scans ending together could alert the same bet twice → AutoScanner.send serialized (AutoScanTest "two scans ending at once alert each bet once", fails pre-fix 4 vs 2); F2 notification permission asked every time Vigilant opened with auto-scan on → once per turn-on (source pin); F3 stale "nothing in the background" copy (BRIEF rule, Settings About, VM/Scanner docs, release.yml notes) updated; RESEARCH.md §26 + CLAUDE.md surface. Shipped v0.18.0 code 34: ci.yml green on 1db8616, release.yml run 36376581625 green, tag v0.18.0 has vigilant-v0.18.0.apk only, BUILDLOG recorded.) Full tests protocol (CLAUDE.md), then ship, release, link.

## Only 7 games scanned; use the Novig API key fully; find every +EV bet; copy CNO? (Tj, 2026-09-28, screenshot)

> "Review the screenshot. Why did it only scan 7 games? I now entered a novig api key and it is much faster. Make
> sure the app is taking full advantage of the novig API key. Also make sure the app is finding as many positive EV
> bets on novig as possible. It may be missing many games and bets. For the cno scanner, can't it just copy what is
> already on cno website, or is this not a good idea?"

Screenshot (v0.18.0 +EV tab): "Scanned 24s ago", leagues NFL/NCAAF/MLB/WNBA/NHL…, meters PinnWire 64 left, pinnapi 100
left, PropLine 893 left, Odds API (amber); Starts within 48h; "No +EV right now — 440 prices checked across 7 games.
Nothing at or above 1.0% EV."

- [x] K1 (DONE: RESEARCH.md §27.1.) Find why the scan covered only 7 games and write the answer down with evidence. FOUND (live catalog
      2026-09-28 06:25Z, next 4 days): in the picked leagues Novig lists only 9 real games inside "Days ahead" (3):
      NFL 1 (MNF; TNF is Fri), MLB 4 (regular season over, Wild Card starts Tue), WNBA 4 (playoffs), NCAAF 0
      (next Thu/Fri); "Series Winner"/futures aren't games. 7 of 9 matched fair odds. Not picked: NHL (7 preseason).
      Not supported at all: tennis (ATP 12 + WTA 38 matches, 763 markets), MLS 2, NPB 3. And the per-game caps
      (2 lines per group, 8 props) held the scan to ~220 markets (440 prices) whatever the per-scan budget.
      Write it into RESEARCH.md §27.
- [x] K2 Use the Novig key fully. Verified in docs 2026-09-28: no batch book route; the key's extras are the signed
      REST book route (already used, 14/s) and the websocket: one `subscribe` of up to 2,048 markets on `book`
      costs at most the 512-token `stream` bucket (a request over the cap passes when the bucket is full), so a
      fresh socket can load every planned book ~8 s after connecting, then keeps them current by push.
  - [x] K2a (DONE: NovigStream rewritten: PushedBooks interface, market `book` subscriptions, first subscribe waits for a full bucket, later ones when affordable, 2,000 cap, unsubscribe, gap snapshots, RATE_LIMIT/SUBSCRIPTION_LIMIT handling, idle close 2 min, 5 min REST after failure, OkHttp ping 20 s. NovigStreamTest 8.) NovigStream: market subscriptions in bulk (whole plan in one request once the bucket allows; small
        additions at once when tokens cover them), 2,048 cap (SUBSCRIPTION_LIMIT_EXCEEDED handled), unsubscribe what
        the plan dropped, gap snapshots, RATE_LIMIT retry, idle close. Tests on the mock socket.
  - [x] K2b (DONE: NovigSource.watch/pushed/pushProblem; NovigPublicClient.stream serves held books with no request (BookBatch.viaPush), only while the key route is usable; BookPump watches the plan and takes pushed books in one pass; ScanReport.booksViaPush; "Novig live feed: …" error once. Tests: NovigPublicClientTest (2 new), BiggerScansTest "with a key, the scan's plan goes to the websocket…" (1,200 prices: 2 REST batches then 1,184 pushed, ≤4 partials) + REST-only control.) NovigPublicClient serves books the socket holds (no request), REST for the rest; Scanner tells it the
        plan (watch) and takes every socket-held book in one pass. Socket trouble = today's REST path, said once.
        Tests (StreamingScanTest / NovigPublicClientTest).
  - [x] K2c (DONE: AppContainer.useConnection builds NovigStream(http, key, appScope), closes the old one; ScanStatus.booksViaPush; Settings › Novig API explains the live feed + "Last scan: X of Y prices came by live feed"; scan-size hint updated; NOVIG_API.md §6/§11.1/§12, BRIEF.md manual-scan rule, CLAUDE.md surface, RESEARCH.md §27.) App: stream built with the key (useConnection), closes when idle (2 min) and when Vigilant leaves the
        screen with no scan running; scan status says how many prices came by push. NOVIG_API.md §6/§11.1 updated.
- [x] K3 Find as many +EV bets as possible.
  - [x] K3a (DONE: ScanSettings.fillBudget (default on; old files get it), PlannedMarket.spare, Planner.fill; Scanner reads filler after the picks (fetchOrder group 5); Settings switch "Fill the scan with every quoted line". Tests: PlannerPricingTest "budget left after the per-game picks goes to every other quoted line, best-covered first", "filling is on by default…", "a scan reads the per-game picks before the filler lines" (fails pre-fix: [p1, p2]); cap tests pinned to fillBudget = false.) Planner fills the per-scan budget: after the per-game picks, every other line a fair source quotes
        (alternate spreads/totals, more props), best-covered first, up to "Novig prices per scan". Setting
        (default on). Tests.
  - [x] K3b (DONE: Leagues ATP/WTA (🎾, pinnacleSportId 2, Kalshi KX{ATP,WTA}{,CHALLENGER}MATCH, oddsApiListed=false, 24 h start gap), Kalshi "MATCH" = winner, Pinnacle tennis (ATP/WTA league prefixes, "(Sets)"/doubles rows skipped, 1st-set winner from period 1, sets-only rows keep the winner only, no specials asked), Novig round suffix cut from matchups, Planner FIRST_SET_MONEYLINE ("1st Set Winner") + PLAYER_GAMES_WON ("Games Won"), "Games Spread"/"Total Games" labels, The Odds API skips tennis, schema 7 turns ATP+WTA on once. Tests: TennisTest (7), AltMarketsTest/BiggerScansTest migrations; LIVE: LiveTennisTest (VIGILANT_LIVE=1) 48 real matches, 375/375 sides resolved, 37/46 paired with Kalshi, priced end to end.) Tennis: ATP + WTA leagues (Kalshi match series, free; Pinnacle sport 33 via PinnWire/pinnapi):
        moneyline, games spread, games total, player games won (team total), 1st-set winner. Round names cut from
        matchups; Pinnacle "(Sets)"-style rows skipped; The Odds API / PropLine skip tennis. Tests on real Kalshi
        and Novig shapes (fixtures from 2026-09-28).
- [x] K4 (DONE: RESEARCH.md §27.4; in the reply.) Answer "can the CNO scanner just copy CNO's website?" (what it already does, what copying more would
      cost/gain) in the reply and RESEARCH.md §27.
- [x] K5 (DONE 2026-09-28 ~07:06Z: light tests (touched files + callers, screenshots 6b/5e/1 looked at, new UI tests for the fill switch and tennis chips, fix: live feed closed at once off screen + AutoScanTest pin); floor 677 tests, 0 failures, 9 live skipped, exit 0; release APK built locally, cert AB:22:… verified; ci.yml green on 4fca994; ship.sh green; release.yml run 36389431089 green; tag v0.19.0 has vigilant-v0.19.0.apk only; BUILDLOG recorded.) Light tests on everything touched, ckpt, ship, release, link.

## "Way more than 7 games"; research Novig's API docs fully and use the API as they describe (Tj, 2026-09-28, after v0.19.0)

> "Baseball is not over. Mlb still has games , college football has games. There are way more than 7 total games for
> it to scan
> Research novig API docs too. Make sure the app is taking full advantage of the API and using it efficiently and as
> the docs describe"

- [x] M1 (DONE: live catalog 07:10Z: 55 NCAAF (Thu–Sat) + 15 NFL Week 5 games were 4–6 days out, past Days ahead 3 (my first check had the same 4-day bound: wrong answer, corrected in RESEARCH §27.5); MLB = 4 Wild Card games (season over, FINAL). Fix: daysAhead 7 default + schema 8 (3 -> 7 once), all events read so the feed counts games past the window (ScanStats.laterGames, "N more games on Novig start later…" + Days ahead button), DELAYED games scanned. Tests: ScanReachTest (4 of 5), ScreenshotTest.anEmptyFeedSaysItsWindowAndHowManyGamesStartLater (1h png looked at).) Re-check Novig's catalog properly (every league, status, page, date; public AND the parameters the app sends)
      and find why a scan saw 7 games when Tj sees many more. Evidence, not assumption; fix whatever drops games.
- [x] M2 (DONE: 140 pages + spec read; NOVIG_API.md §13 table. Built: strike guard (live 6,541/6,541 agree; ScanReachTest strike test fails without it), GET /v3/limits paces key REST + websocket (NovigPublicClientTest 2), board via the key's signed catalog w/ public fallback + canonical query encoding (NovigPublicClientTest 3). Checked, unchanged: book channel (no bbo on the route page), error frames, fees, deep links, limits/paging. Not usable: GraphQL odds screens (allow-listed, "query is not allowed").) Read every page of docs.novig.com (llms.txt index + OpenAPI spec) and compare with every Novig call the app
      makes (params, limits, paging, caching/ETag, throttles, signed vs public, websocket verbs/channels, errors).
      Write the gaps into NOVIG_API.md and fix them, with tests.
- [x] M3 (DONE 2026-09-28 ~07:41Z: floor 689 tests, 0 failures, 10 live skipped, exit 0; release APK built locally (0.19.1/36); ci.yml green on d6e8948 (main); release.yml run 36392649770 green on d6e8948; tag v0.19.1 has vigilant-v0.19.1.apk only; BUILDLOG recorded. ship.sh itself was NOT run: the container's command check failed ~8 times in a row, so the release was triggered from GitHub with every ship gate already verified by hand (tests, CI, versionCode 36 > 35, pushed).) Light tests, ckpt, ship, release, link; answer Tj plainly (what was wrong in the last answer, if anything).

## "+EV bets appeared while scanning, then quickly disappeared. Is this supposed to happen?" (Tj, 2026-09-28, on v0.19.1)

- [x] Q1 (DONE: RESEARCH §28. Measured live (LiveFlickerTest): a Mystics ML shown 19 s in from Polymarket alone, gone when Kalshi answered. Also the 5-min rule counted from the feed's own "last seen" (a 4.5-min-old sportsbook quote hid its bet 30 s later), and the intended end-of-scan re-read.) Trace every way a bet can leave the feed during/after a scan (re-plans as fair sources answer, freshness
      rules, re-reads, window/filters) and say which one Tj saw, with evidence; fix anything that isn't intended.
- [x] Q2 (DONE: ScanResult.waitingFor holds a league's bets mid-scan until its sources answered (props until props-only sources); scan prices only with quotes <= 3 min old (Freshness.MIN_SHOWN_MS) so a shown bet lasts >= 2 min; feed counts bets hidden for old odds. Tests: SteadyFeedTest 3 (each fails without its fix), ScreenshotTest.betsHiddenForOldOddsAreCountedNotJustDropped (1i png looked at); live after: 10 shown, 0 gone. v0.19.2 (37).) If the cause is showing edges before the fair line is complete (or any other misleading flicker): fix, test,
      ship, link. Otherwise answer plainly.

## "Did this latest version change anything with the novig API scan because now it is reading the API very slow" (Tj, 2026-09-28, on v0.19.2)

- [x] R1 (DONE: v0.19.2 changed no Novig request code (git diff v0.19.1 v0.19.2); it holds each league's bets until
      Kalshi answers, measured live (LiveSourceTimingTest) at 8-28 s: Kalshi 57 series at 2/s, 27.6 s, leagues one after
      another. v0.19.1: 7 days (board 8,319 markets vs 4,004, 0.9 s either way) + fill = every scan reads the whole
      budget (1,200 vs v0.18's 440); keyed pace re-set from /v3/limits; board via the signed catalog (not CDN-cached).
      Pump CPU between reads: 0.6 s per 1,200 prices here (PumpCostProbe), not the cause. Keyed batches of 8 with 6 in
      flight leave slots idle every batch.) List every change since v0.18.0 that affects how long a scan takes or how fast Novig prices come in, and
      measure what can be measured here (board size and time with 7 days + tennis, reads per scan with the full
      budget, when bets first show with the hold-back). Find the real cause(s).
- [x] R2 (DONE: R2a-e, shipped v0.19.3 (38).) Fix what's slower than it needs to be, with tests; give Tj a way to see where scan time goes (so the next
      "slow" report comes with numbers). Ship, link.
  - [x] R2a (DONE: three real caps found and fixed. (1) OkHttp's default 5 requests a host, one held by the open
        websocket (OkHttp 4.12, probed): the key read 4 at a time; now `vigilantHttpClient()` allows 16 (HttpClientTest,
        fails on OkHttp's default). (2) One refused wave (every read in flight comes back 429 together) halved the pace
        once per refusal, 14.4/s to 1/s for a minute, and counted each toward the 8 that stop a scan: now once per burst
        (RateGateTest `refusals arriving together…`, NovigPublicClientTest `with a key, a refused wave…`, both fail
        before). (3) 10 in flight with a key (was 6) and 30-book batches (`NovigSource.batchSize`, BiggerScansTest,
        NovigPublicClientTest). `/v3/limits` checked against Novig's OpenAPI spec: same shape, 1 token a book; pacing
        by it stays.) Keyed reads at the key's full pace: more in flight and bigger batches with a key (8-price batches left
        slots idle), and `/v3/limits` can only raise the pace above the proven 14/s (a 429 still slows it).
  - [x] R2b (NO CHANGE, measured: the public board isn't served from cache either: CloudFront "Miss" on every read,
        0.3-1.6 s for a 5,000-market page, same as a signed read would be. The key's board stays.) Board from Novig's CDN-cached public routes first; the key's signed catalog only when those are throttled.
  - [x] R2c (DONE: `ReferenceSource.linesFirst`/`lines`; Kalshi reads every league's game-line series first (29 of 57,
        ~15 s at 2/s), then props, re-using what `lines` read (nothing asked twice); `Scanner.linesFirst` pass and
        `waiting()` let a league's game lines show once Kalshi's lines are in. Tests: SteadyFeedTest `a league's game
        lines show once Kalshi's lines are in…` (fails without the Scanner pass), ExchangeClientsTest `kalshi reads a
        league's game-line series first…`.) Kalshi game lines first: every league's game-line series before any props series, so game lines stop
        waiting on Kalshi's props under the v0.19.2 hold-back.
  - [x] R2d (DONE: `ScanTiming` on every ScanReport (board, fair odds, Novig prices from/to, first bet, total,
        Novig's refusals via `BookBatch.refused`), `ScanStatus.timing`/`keyReadPerSec`, one line under Settings › Novig
        API. Tests: ScanTimingTest (text + a timed scan), ScreenshotTest `novigKeySaysWhereTheLastScansTimeWent`,
        6b png looked at. The feed's own line left as is.) Where scan time goes, in Settings › Novig API and the scan's done line: board, fair odds, Novig prices
        (count, seconds, per second; by key / live feed / public), first bet shown, the key's limits.
  - [x] R2e (shipped v0.19.3 (38), release.yml green, tag has vigilant-v0.19.3.apk; docs done: RESEARCH §29, NOVIG_API §11.1, CLAUDE.md surface; Kalshi quotes re-used from `lines` stamped
        with their read time (ExchangeClientsTest); full floor 709 tests, 0 failures; live Kalshi lines-first: all
        leagues' lines by 13 s.) Docs (RESEARCH §29, NOVIG_API), ship v0.19.3 (38), link.

## "The app is telling me I have a proxy or vpn when I test the novig key, but I don't. Research online and reconsider the 5 minute stale odds cutoff … For the fewest books behind the fair price filter, add options for 1 and 2 books. Remove any option over 4 books" (Tj, 2026-09-28, on v0.19.2)

- [x] S1 (DONE: Novig's 451 ANONYMIZED_NETWORK is its screen's verdict on the request's internet address ("VPN, proxy,
      or Tor exit"; docs.novig.com/api/errors), not the phone, and the app read it as "Turn the VPN off". Also
      RESTRICTED_NETWORK_REGION was told "open the Novig app". Now: advice per documented code, with the code;
      Test key says which connection it used (Wi-Fi / mobile data), whether a VPN is really up (TRANSPORT_VPN), and on a
      network refusal tries the other connection and reports it (`NovigKeyTest`, `PhoneNetworks`, CHANGE_NETWORK_STATE);
      scan and live-feed banners say the same in one line (`NovigApiException.brief`). And yes: a refused key sends every
      scan to the public route (4-6/s) for 10 minutes at a time, the likeliest cause of "reading the API very slow".
      api.novig.com is IPv4-only (no IPv6 angle). Tests: NovigKeyTestTest (5), PhoneNetworksTest (2), NovigStreamTest.) "The app is telling me I have a proxy or vpn when I test the novig key, but I don't." Find exactly what Novig
      answered and why the app calls it a VPN/proxy (which status/code, which message); check Novig's docs for what
      triggers it (ANONYMIZED_NETWORK, iCloud Private Relay / Private DNS / carrier NAT / IPv6, Novig app location
      check); fix the wording and anything that wrongly turns a refusal into "VPN". Also: does a refused key make every
      scan fall back to the slow public route (Tj's "reading the API very slow")?
- [x] S2 (DONE: RESEARCH §30.2. The rule measures when a feed last *saw* a price, so still lines never age out
      at scan time; it bites 3-5 min after a scan. 31-min live recording: >=1 pt fair moves 1.6/2.7/3.7/6.9% at
      5/10/15/30 min (Novig 1.8/3.8/6.8/11.5%), NFL moneylines and MLB totals far more; moves bunch near the start.)
      "Research online and reconsider the 5 minute stale odds cutoff. Should this be altered? Right now it hides bets
      if the odds from other books are over 5 minutes old. Figure out if that is a good or needed filter. It may be that
      odds do not change that rapidly and are still positive EV bets even if the odds from other books are over 5
      minutes old". Research how fast sportsbook/exchange lines really move (pregame vs live, by sport/market, props),
      what other +EV tools do, what the app's sources' own update cadences are; decide, change if warranted, test.
- [x] S3 (DONE: CNO's "Fewest books behind the fair price" = 1/2/3/4, default 4 (was 5); schema 9 moves a saved 5+
      to 4 once; the app's choice is always posted to CNO's form (before, a Shared View link's bigger number won, so 1
      or 2 would have done nothing). Tests: CnoBooksChoicesTest (3), CnoClientTest `a stricter odds cap … fewest books
      is always the app's`, MiniWindowTest default; sample Buehler row 4 -> 3 books.) "For the fewest books behind the fair price filter, add options for 1 and 2 books. Remove any option over 4
      books". Choices become 1-4; a saved 5+ moves to 4 once (schema bump); test.
  - [x] S2a (DONE: `Freshness.maxAgeMs`, `LIMIT_TEXT`; planFor per-event limit (date-only start = strict; a game
        with no fresh line drops from the fair side, as before); cards "odds N min old"; copy. Tests: FarOffOddsTest (4),
        SteadyFeedTest, FreshOddsTest (now 2 h before kickoff), FreshOddsAppTest, ScreenshotTest (aging label, 1i png
        looked at), SportsbookScannerTest. Full floor 724 tests, 0 failures.) (decided from the 30-min live recording, RESEARCH §30.2): other books' quotes may be 10 minutes old when the
        game is over 3 hours away, 5 minutes within 3 hours or live; one rule for scan pricing, re-pricing, Recheck
        and how long a found bet stays listed (`Freshness.maxAgeMs`); cards show the odds' age; copy updated; tests.
- [x] S4 (DONE: v0.19.3 (38): CI green on 94339f0, ship.sh gates green, release.yml published vigilant-v0.19.3.apk, BUILDLOG recorded.) Ship with the v0.19.3 work (R2e) and send the link.

## "Now consider if the 1200 Max prices per novig scan is enough … no limit … alternate lines and player props per game … credits per scan on props … automatically enter 1 dollar on every betslip … pinnwire … pinnapi … one press buttons … Open the bet in novig" (Tj, 2026-09-28 ~18:40Z, on v0.19.3)

- [x] T1 (DONE: RESEARCH §31.1. Live board 8,620 markets; free sources price 1,168 (1,200 covers them); keys price more.
      Budget choices up to 2,000 (one live-feed connection watches 2,048); no "No limit" (past 2,000 each price is a
      request at ~14/s, scans run many minutes, early reads age out, background scans repeat it). Guard: a long scan
      leaves lines whose odds would be too old (`canStillShow`, `booksTooLate`, Settings timing line). Tests:
      BiggerScansTest (choices; `a scan running long leaves lines…` fails without the guard; `…a day off reads them
      all`), LiveBudgetTest.) "consider if the 1200 Max prices per novig scan is enough for me to find most or all positive EV bets available,
      and if increasing this number could be beneficial or dangerous in any way. If I can have no limit on the prices
      safely, then make that option." Measure how many Novig prices a full 7-day board has with fair odds (how much
      1,200 leaves out), what more reads cost (time, Novig's limits, the websocket's 2,048 watch cap, data, battery),
      and decide; add "No limit" if safe.
- [x] T2 (DONE: with fill on the caps only order reads (budget bounds them): lines 1-10, props 0-48. BiggerScansTest.)
      "consider if I can safely raise the max alternate lines and player props per game safely." Same question for
      `linesPerGame` / `propsPerGame`; raise the choices if safe.
- [x] T3 (DONE: safe as far as The Odds API plan's credits go (PropLine first, Odds API only for gaps): up to 192, hint
      `creditWorstCase` says how few scans 500 / 20,000 credits last at that cap. BiggerScansTest.) "Can I raise the most credits per scan on props safely?" Check The Odds API credits per scan
      (`bookPropCreditsPerScan`) against the free/paid quotas; raise if safe.
- [x] T4 (DONE: yes. Novig's deeplinking docs: `novig.com/events/<outcomes>/<partner_id>/<wager_amount>` pre-fills the
      wager in dollars; its app's link config has the same `:amount?` (unverified on a device). Settings › Bankroll &
      Kelly › "Amount in Novig's bet slip": Off (default) / $1 / Kelly (never under $1) / My amount; `NovigLinks.withStake`
      on every bet opened: +EV card button, bet sheet, widget and mini window (`MiniWindow.Item.kelly`), CNO tab, alerts
      (`EvAlert.stake`). Partner tag: CNO's `cno` kept, else Novig's own `novig` (its docs' example). Tests:
      NovigLinksTest (3), AutoScanTest `an alert's bet slip opens with the stake…`, ScreenshotTest
      `everyBetHasAOneTapOpenInNovigButtonWithTheChosenStake`.) "Can the app automatically enter 1 dollar on every betslip inside novig when I click on a bet? If it can, make
      an option to automatically enter 1 dollar per bet, the kelly value per bet, or an amount I can type into the
      settings." Research whether Novig's app/links accept a stake (deep link params, the web bet slip), and build the
      option if it can.
- [x] T5 (DONE: already built since v0.16.0 for PinnWire's daily limit (a 429 `window:day`, or the app's own count of
      100/day): pinnapi answers and the pool rests PinnWire's key until its reset, then PinnWire goes first again, now
      pinned by ExchangeClientsTest `a spent PinnWire key isn't asked again until its day resets…`. Gap fixed: any
      other PinnWire failure (5xx, odd status, dropped connection) failed Pinnacle for the scan; now pinnapi answers
      (ExchangeClientsTest `any other PinnWire failure falls to pinnapi…`, fails before).) "When pinnwire api usage runs out, automatically switch to pinnapi until the usage resets." Check what happens
      today when PinnWire's keys are spent (`PinnapiClient`, `KeyPool`), and make it switch to pinnapi and back.
- [x] T6 (DONE: `OpenBetButton` on every +EV card ("Open in Novig", "· $1" when a stake is set), the widget's exact
      bet-slip link through the same `LocalOpenNovig` path; tap on the card still opens details. ScreenshotTest
      `everyBetHasAOneTapOpenInNovigButtonWithTheChosenStake`, 1j png looked at.) "On the vigilant +ev scan tab when the app is in full screen, make easy one press buttons next to each bet to
      Open the bet in novig, just as the widget does". A one-tap "Open in Novig" on each +EV card (the widget's exact
      bet-slip link), test + screenshot.
- [x] T7 (DONE: v0.19.4 (39): fixed a timing-flaky NovigPublicClientTest first (CI red on 43f033e, refusals now
      counted not timed); ship.sh 735 tests/0 failures, CI green on 23c4b10, release.yml published
      vigilant-v0.19.4.apk, BUILDLOG recorded.) Ship and send the link.


## "Make an option in the app to pause all scanning … put these same buttons in the cno scanner in full screen" (Tj, 2026-09-28 ~19:36Z, on v0.19.4)

- [x] U1 (DONE: `ScanSettings.paused` (saved): Settings › Scanner switch, ⏸/▶ on the +EV and CNO top bars and the widget
      header, a Resume banner. Paused: a running scan stops (`ScanRunner.stop`, no "scan done" note), CNO's list and lanes
      held even on screen (`CnoWatch.hold`, also until settings load), auto-scan off (`activeAutoScan`: service, alarm,
      boot receiver, cycle; resuming restarts it, from the widget too), widget rescans stop, Scan/Recheck/Refresh toast and
      their buttons grey out. Opening bets, books on tap and settling still work. Tests: PauseScanningTest (2), CnoWatchTest
      `a hold stops every read…`, StreamingScanTest `stop ends a running scan…`, PauseScanningAppTest (2), ScreenshotTest
      pause tests (6; 1k/8h/9p/9q png looked at). Full floor 752 tests, 0 failures.) "Make an option in the app to pause all scanning." One switch that stops every scan the app runs on its own
      or keeps running: CNO's feed (tab, widget, pip, keepBooksFresh/keepLinksFresh), background auto-scan
      (alarm + AutoScanService), widget rescans, a Scan left running; manual Scan disabled or asks to resume while
      paused. Shown where Tj will see it (+EV/CNO tabs, widget, Settings); survives restarts; test.
- [x] U2 (DONE: every CNO tab card has the +EV card's button (shared `OpenInBookButton`, tag openBet): MainActivity.openInNovig,
      the widget's path (TapLink / NovigBetFinder), stake via `cnoSlipStake` ("· $1"), spinner while the link is found; the
      card tap still opens its books. Tests: ScreenshotTest `everyCnoBetHasAOneTapOpenInNovigButtonWithTheChosenStake`,
      `cnoCardsOpenButtonSaysItsOpening`, `aCnoBetsSlipStakeIsItsKellyStake`, 8g png looked at.) "just like the vigilant positive EV tab has buttons to open the bet in novig, put these same buttons in the
      cno scanner in full screen (it already works in the widget)". The +EV card's one-tap `OpenBetButton` on every
      CNO tab card, same link path as the widget (TapLink / NovigBetFinder, stake), test + screenshot.
- [x] U3 (DONE: v0.19.5 (40): ship.sh 752 tests/0 failures, CI green on 22e5276, release.yml published vigilant-v0.19.5.apk, BUILDLOG recorded.) Ship and send the link.

## "Add unlimited options in the vigilant app for all types of scans that can benefit from unlimited … make sure the app doesn't just scan continuously" (Tj, 2026-09-28 ~20:2xZ, on v0.19.5)

- [x] V1 (DONE: RESEARCH §32.1: every cap, which got No limit/All and which not, costs, how a scan ends.) Research: every per-scan cap (Novig prices per scan, props credits per scan, lines/props per game, sportsbook
      props hours, PropLine / Pinnacle requests, …), which ones "can benefit from unlimited", what unlimited costs
      (Novig's read bucket / websocket 2,048, The Odds API credits, PropLine's 1,000/day, scan time vs the odds-age rule
      that RESEARCH §31.1 turned "No limit" down for), and how an unlimited scan ends.
- [x] V2 (DONE: `ScanSettings.NO_LIMIT` in MAX_BOOKS_CHOICES; reads every priced line in the window once; freshness guard
      bounds it (~8 min); lines left too late read first next scan (`leftLastScan`). BiggerScansTest `with no limit a scan
      reads every priced line…`, `lines a long scan left too late are read first…`.) "unlimited novig prices per scan": a "No limit" choice for Novig prices per scan. Every line a fair source prices
      in the selected time period is read once, then the scan ends; long scans keep their odds fresh enough to show
      (not the §31 "early reads age out" problem).
- [x] V3 (DONE: No limit credits; hint `creditWorstCase(NO_LIMIT)`. OddsApiPropsTest `no credit limit and All hours…`.) "unlimited credits per scan": a "No limit" choice for The Odds API props credits per scan, bounded by the games in
      the window × prop types and by the key's credits left; the hint says the worst case.
- [x] V4 (DONE: lines/props per game "All", props hours "All" (`bookPropWindowHours`), PropLine games per scan
      12/24/48/No limit (was a fixed 12). PropLineClientTest `PropLine props follow the games-per-scan setting…`, ScreenshotTest
      `settingsOfferNoLimitOnEveryScanCapThatCanUseIt`, `theNoLimitHintsSayWhatBoundsTheScan`.) The other caps that benefit ("etc."): lines / props per game and sportsbook-props hours get an "All"/"No limit"
      choice where it changes what a scan can find.
- [x] V5 (DONE: `scanWindowHours` = Days ahead or Starts within when shorter (S1), `Planner.horizon`, props too; feed banner
      when the window is wider than the last scan. BiggerScansTest `a scan reads only the games in Starts within…`, ScreenshotTest
      `theFeedSaysWhenTheWindowIsWiderThanTheLastScan` (1l png looked at).) "make sure the app doesn't just scan continuously, it should stop the scan when all the markets are finished
      scanning for the selected time period". An unlimited scan's plan is finite: the markets starting within the
      selected time period (Days ahead, or "Starts within" when narrower: open task S1), each read once, then it stops;
      background auto-scan and widget rescans still only repeat on their own timers. Tests prove each market is read
      once and the scan ends.
- [x] V6 (DONE: v0.19.6 (41): ship.sh 760 tests/0 failures, CI green on 036f664, release.yml published vigilant-v0.19.6.apk (new code checked inside the APK), BUILDLOG recorded.) Ship and send the link.

## "Research the new claude-api skill and hillclimb and figure out if it can improve this app or development. Then research other skills or plugins including from third parties that can improve the app or Claude ability to make the app better. Tell me anything I need to do" (Tj, 2026-09-28T22:16Z, on v0.19.6)

- [x] W1 (DONE: RESEARCH §33.1: skill read in full incl. eval-hillclimb.md and prompt-audit.md; hillclimb needs an LLM app + eval, so no use in Vigilant; prompt-audit is the useful dev tool.) Research: the claude-api skill (what it is, what's in it) and "hillclimb" (what it is, where it comes from);
      verdict on whether either improves the app itself (at runtime) or how Claude builds it (the dev loop), and
      what each would cost (Tj: nothing that costs money or lowers accuracy).
- [x] W2 (DONE: RESEARCH §33.3-33.4: cloud sessions load repo skills, not plugins (docs); official kotlin-lsp tested here; claude.ai catalog, official marketplace and third-party Android skills checked.) Research: other skills / plugins / MCP connectors, Anthropic's and third parties', that could improve the app
      or Claude's work on it (Kotlin / Compose / Gradle / Android, tests, review, security, library docs); which ones
      actually work in this cloud container and survive into the next session.
- [x] W3 (DONE: RESEARCH.md §33 (33.1-33.5, incl. 33.4.1 Kotlin LSP probe results); reported to Tj in chat.) Write the findings down (RESEARCH.md, new section) so no later session redoes this; tell Tj plainly what he
      needs to do (settings, installs, costs), links as plain text.
- [x] W4 (DONE: session-start briefing fixed, 215,548 -> 7,186 chars (ckpt 605, test_resume.sh 2 new checks); S3 ticked; third-party skills and the prompt audit left for Tj's go-ahead.) Adopt on the repo side only what is clearly safe, free and reversible without Tj's say-so; list the rest for
      his go-ahead.

## "run the prompt audit and add the Compose skills" (Tj, 2026-09-28T22:53Z, on v0.19.6; his yes to RESEARCH §33.5's two offers)

- [x] X1 (DONE: PROMPT_AUDIT.md (report) + PROMPT_AUDIT.patch (every proposed edit): 21 findings with edits, 13 high / 8 medium (A1-A11 CLAUDE.md + briefing scripts, B1-B10 BRIEF.md), 11 flags left to Tj (rules, prohibitions, the keystore question); patch checked with `git apply --check`, and on a scratch checkout with it applied: `bash -n`, test_resume.sh all green, briefing 6,352 chars. Nothing applied. Also fixed directly: toobig.sh's "well under a hundred lines", left stale by the ckpt 605 briefing fix.) Prompt audit, following the claude-api skill's `shared/prompt-audit.md` (what `/claude-api prompt-audit` runs):
      scope = this repo's instruction surface (CLAUDE.md, BRIEF.md, bootstrap.sh's rules block, the text the hooks print:
      resume.sh / ckpt.sh / toobig.sh / capture_inbox.sh); target = the model running it (Claude Opus 5.5). Deliverables:
      a report (file:line, evidence, pattern, why, confidence, action) and a proposed diff, one finding per hunk, saved in
      the repo. Nothing applied until Tj picks: "run" is not "apply", and the audit's rules keep stale-fact and
      contradiction fixes proposal-only.
- [x] X2 (DONE: `.claude/skills/` compose-performance, compose-state-and-effects, kotlin-concurrency-and-flow, compose-ui-testing-patterns, unchanged from chrisbanes/skills @359126d (diff -r identical), all 16 files read first: guidance only, no allowed-tools / shell blocks / hooks / scripts / URLs; Apache-2.0 license + THIRD_PARTY_NOTICES.md; 6 links to upstream skills not copied dangle harmlessly. Loading can't be seen from this session; they should list from the next one.) Compose skills: read Chris Banes' `compose-performance`, `compose-state-and-effects`,
      `kotlin-concurrency-and-flow`, `compose-ui-testing-patterns` in full first (third-party instructions: check for
      `allowed-tools`, inline shell blocks, hooks, network fetches, anything that fights CLAUDE.md); commit the ones that
      pass to `.claude/skills/` unchanged, with the Apache-2.0 license and a note naming the source commit.
- [x] X3 (DONE: test_resume.sh green via ckpt, secretscan clean, audit patch re-checked with `git apply --check` after the skills landed; reported to Tj.) Checks + checkpoint; tell Tj the top audit findings (what applying each would change) and which skills landed.

## "Fix all the things you can fix without breaking anything and make sure to take advantage of the new skills for all future tasks on this app" (Tj, 2026-09-29T00:28Z)

- [x] Y1 (DONE: PROMPT_AUDIT.patch applied (ckpt 2000): test_resume.sh green, briefing under the cap, CLAUDE.md
      35,855 -> 18,114 chars, test protocols now `.claude/skills/test-protocols`.) Apply the prompt audit's 21 fixes (PROMPT_AUDIT.patch: A1-A11, B1-B10); checks green, briefing under the cap.
- [x] Y2 (DONE: BRIEF.md F1 source order -> pointer to the BLEND decision, F2 keys -> plain JSON + one-time migration,
      F3 fee rule kept with today's code, F4 no sample path, F5 two dead bullets -> one pointer, F6 every timed
      exception named (widget rescan, SettleWorker) + Tj's 2026-09-20 quote, F7 revisit condition recorded as met, key
      NOT changed, F10 history note, F11 Moto G 2026 specs (GSMArena review); F9 in the test-protocols skill. Name
      check: no stale code names left but the one history note. F8 (history stories) left: they carry the reasons.)
      Settle the flags whose answer is already Tj's own later decision or the code (F1-F6, F9, F10), and fill F11
      (Moto G 2026 specs) if a reliable source exists. F7 (the keystore) is written up but NOT changed: a new signing
      key forces an uninstall that wipes the app's saved data, which breaks things.
- [x] Y3 (DONE: `MarketFee.GAME` KDoc says who uses it and why; NOVIG_API.md §8 no longer says Fees.kt ignores
      futures; test_resume.sh header; bootstrap counts "commits" and finds `.git` in a worktree. `./gradlew :engine:test`
      39/0. The fee math itself is Y7: a real fix needs each CNO bet's own Novig market, so it's a feature change
      with a release, not a safe quick edit.) Side findings: `MarketFee.GAME` used for CNO books / tracked bets (a futures bet under-charged): fix it if that
      can be done safely with a test, and ship if app code changes; `tools/test_resume.sh`'s stale header; bootstrap's
      "checkpoints" label and its `.git` check in a worktree.
- [x] Y4 (DONE: CLAUDE.md "Skills for this app" table (what to load before which work, with paths), the briefing's
      rules list them, light test step 2 and full test step 3/4 review with them. The four Chris Banes skills show in this
      session's skill list, so repo skills do load in cloud sessions.) The skills in every future task: CLAUDE.md says when to load each one (with its path), the session briefing
      lists them, the light/full test protocols use them. (The four already show up in this session's skill list.)
- [x] Y5 (DONE: six resolved with evidence (SharpAPI 403, Stage 1, Stage 2, Reference leg, H2, P2); four stay open
      because they need Tj's phone or his go-ahead (first live Novig key connect, first Odds API scan on device, P6
      PropLine props live, S2 PiP header).) Stale open TASKS items: tick or mark superseded where the code shows it's done (evidence for each); leave real
      ones open.
- [x] Y6 (DONE: test_resume.sh green, briefing 6,270 chars, secretscan clean, `:engine:test` 39/0 (the only source
      change is a KDoc comment, so no app behavior changed and no release is needed); PROMPT_AUDIT.md marked applied,
      the patch deleted (git keeps it); reported to Tj.) Checks (test_resume.sh; the Gradle floor if app code changed), ship + Release link if the app changed, report.
- [x] Y7 (DROPPED 2026-09-29, Tj: "I'm not interested in futures bets. Leave those out of the app and don't
      investigate them further." Futures are left out instead: Z2.) Charge CNO bets their own Novig market's fee: carry the
      market's `fee` (NovigLive's cache / the bet-slip link lookup) into `CnoChecks.netEv`, `CnoBooks.check` and
      `BetTracker.logCno`, keeping `MarketFee.GAME` only when the market is unknown. Today they assume the game
      schedule, so an NFL/MLB/NCAAF futures market at +150 or shorter on CNO's list shows EV ~3-4 points too high
      before the game (6% × P × (1−P) not taken out). Tests: a futures row is charged pregame; a game row isn't.

## "As far as keys, I'm not worried about app security. Public is fine … Are the compose skills installed in the repo to use between different Claude accounts? … I'm not interested in futures bets. Leave those out of the app and don't investigate them further. Think of and implement any other clean up or optimization for this repo so that future work is efficient and Claude can use skills for the best coding. The setup script for each cloud session should load a maven central script, does this work well?" (Tj, 2026-09-29 ~01:0xZ; full text in INBOX.md)

- [x] Z1 Record Tj's decisions: the public committed keystore and plain-JSON API keys are fine (closes PROMPT_AUDIT F7);
      futures are left out of the app, with no further futures work (replaces Y7). BRIEF.md + TASKS.
      DONE (ckpt 2015): BRIEF.md keystore section "Decided 2026-09-29: keep the public committed key" + locked decision
      "No futures"; PROMPT_AUDIT.md F7 closed; Y7 dropped.
- [x] Z2 Futures out of the app: find where a futures market could show up (CNO's list, Vigilant's scan, the Games
      board, alerts) and leave it out there, with tests. No fee work, no futures research.
      DONE: Vigilant's own scan only plans matchups (Novig events with two sides, explicit game/prop market types), so
      futures never reach the +EV feed/Games/alerts; CNO's list was the one door: CnoChecks.reject now drops any row
      that isn't a two-sided game first (Reason.NOT_A_GAME via shared Picks.sides/isGame; PlayerTeams delegates), and
      every CNO surface (tab, widget, mini window, alerts, auto-scan) reads CnoChecks.screen. Test: CnoChecksTest
      "only games are listed - a futures market on CNO's list is left out and counted" (failed before, passes after);
      full floor green cold 2026-09-29 (engine 39, data 494, app 228).
- [x] Z3 Answer: do the skills carry across Tj's three Claude accounts (they're committed in the repo), and what, if
      anything, each account needs before working on this repo (its own cloud environment's setup script, network).
      DONE: RESEARCH.md §33.6 + BRIEF.md build trap 6 ("Every account's cloud environment"): skills/CLAUDE.md/hooks
      travel with the repo; each account's environment needs the one-line setup script and dl.google.com allowed.
- [x] Z4 The setup script: does the Maven Central mirror script work well? Prove it (a full test run here, timed) and
      improve what's weak (e.g. every new session re-downloads ~1 GB of Gradle dependencies).
      DONE: works (cold floor 210 s, 1.5 GB via the mirror, 0 x 429; warm 110 s). tools/setup-android.sh now never exits
      non-zero (WARN; tested with dl.google.com blocked + unwritable Gradle home: exit 0), installs build-tools 35.0.0
      (the one AGP 8.13 builds with), and --prewarm fills the caches inside the setup script (cold total 210-235 s,
      budget 250 s; cut test: WARN after 42 s, exit 0, nothing left running); first floor after it 106 s, 2 MB fetched.
      One-line environment script in BRIEF.md build trap 6.
- [x] Z5 Other cleanups so future work is efficient and uses the skills: a compact test runner (short output),
      Gradle speed settings if safe, CI caching, any other upstream skill worth adding.
      DONE: tools/test.sh (+ tools/gradle_summary.py; fast check tools/test_gradle_summary.sh, 11 checks, fails on a
      summarizer that ignores Gradle's exit code) used by ship.sh, CLAUDE.md "Skills for this app" and test-protocols;
      data tests in parallel JVMs (71 s -> 38 s); VIGILANT_* live switches are data:test inputs (proven: before, a
      changed switch left the task "up to date"; after, it reruns). Not adopted, with reasons (RESEARCH.md §33.6):
      build/configuration cache, chrisbanes gradle-run skill, CI changes.
- [x] Z6 Full test floor, ship, Release link.
      DONE: ship.sh (via tools/test.sh) 761 tests: 746 passed, 15 live skipped; CI green on 7ad5dfc1 (run 36507659205);
      release.yml run 36508005071 published v0.19.7 (code 42, certificate verified), recorded in BUILDLOG.md. Light test of
      this session's changes: LiveCnoSmokeTest (VIGILANT_LIVE=1) failed on a stale assertion (5+ books; the default is 4
      since v0.19.3), fixed to follow the filters; live run then 13 CNO rows, 13 kept, none hidden as futures.

## "Review the bet tracker in vigilant app. When I press the button to "check odds now" for my current bets, it says it check 40 out of 40 open bets, but I have 101 open bets. I want it to check all open bets. …" (Tj, 2026-09-29; full text in INBOX.md)

Tj's words, in order (each becomes a job below; the design notes come after reading the code):
- "it says it check 40 out of 40 open bets, but I have 101 open bets. I want it to check all open bets."
- "make it so I can click on any of my open bets and it shows the current odds for that same bet across other sports books, and other relevant information such as the odds I bet it at, the calculated difference in the odds I bet from the current fair, devigged odds based on current odds, etc."
- "Some bets are still pending in the "open" bets tab that are final. Figure out how to make sure every bet is properly graded win or loss after the event is final. Look into the apis already used in the app, because one of them claims that the API can grade all props markets. Research this and see if vigilant can use this."
- "make sure that the bet tracking system is properly keeping track of accurate stats for wins, losses, push, and total profit."
- "Think of any other ways to make the tracking section better coded, more efficient, or more accurate. The goal is to see how well my positive EV bets profit with vigilant."
- "For open bets, add a button next to each one to replace the bet. This button will open novig with that exact bet in the betslip and any dollar amount preset in the options."
- "Think of the best way for me to be able to quickly and easily mark a bet as placed so vigilant tracks it if I select the bet from a push notification. Right now, if I click on the notification, it opens novig, but there isn't a fast way for me to add the bet as a tracked bet in vigilant."
- "After all these features are built, run the full test protocol looking for ways to improve the app and the UI and code and fix bugs."

- [x] T1 Read the tracker code end to end. FINDINGS + PLAN (2026-09-29; a resumed session builds from here, T2 first):
      **Why "40 of 40":** `BetRecheck.MAX_PER_RUN = 40` caps a pass; `due()` also keeps only bets with a CNO game page (`gameUrl`)
      and a start under 4 h ago, so Vigilant-tracked bets and games already over were never read, and the toast counted only
      the 40 it took. 101 open = CNO bets (each 2 CNO requests: page + postback) + Vigilant bets (`track()` sets no gameUrl;
      they're priced only when a scan pins their market: `startVigilantScan(pinned)` -> `BetTracker.observe`) + games over.
      **Why bets stay open after the game is final** (I can't see Tj's phone data, so each cause is shown on the card, T4): (1) a
      market `BetGrader.pickOf` can't read (3-way, quarters/periods, "alternate total" wording, combo props); (2) a stat the
      box-score parsers don't produce (ESPN hockey has NO box parser at all; basketball has no steals/blocks/turnovers/P+R/P+A/R+A;
      football no sacks/tackles; tennis/soccer/other sports not wired); (3) game not found/called; (4) `BetTracker.settle(id,
      PENDING)` (Undo) sets `settledBy = "you"` and the auto-grader then never touches that bet again; (5) retries live in
      memory (`nextTry`) and the settler runs only when the app opens, the Tracker shows, or the 3-hour worker fires.
      **PropLine's grading (research, 2026-09-29, openapi.json + llms-full.txt):** `GET /v1/sports/{sport}/events/{id}/results`
      (each prop won/lost/push/void + actual value, per book) is PAID (Hobby $9/mo+; the free key gets `redacted: true`, no
      resolution). FREE: `/v1/sports/{sport}/scores?days_from=N` (id, teams, status final/postponed/..., scores) and
      `/events/{id}/stats` (box score rows `player_name/stat_type/stat_value`: MLB hits, HR, RBI, runs, K, walks, SB; NBA/WNBA
      points, rebounds, assists, threes, steals, blocks, turnovers; NFL/NCAAF passing/rushing/receiving yards, TDs, anytime TD;
      NHL goals, points, SOG, blocked shots, saves; soccer goals, assists, shots, cards; tennis sets won, aces, total games);
      1 request each of the key's 1,000/day; event ids join to the odds calls. Verdict: don't pay for `/results` (Vigilant grades
      from box scores itself, which is more auditable); use the FREE `/scores` + `/stats` as a second score source behind ESPN/MLB
      (hockey, soccer, tennis, anything ESPN can't parse), only with Tj's PropLine key, never on failure worse than today.
      (Unverified live: the demo key answers 401 on /scores; schema is the published one.)
      **Stats audit (`BetTracker.stats`):** `expectedProfit` sums OPEN bets too, so "Expected" is compared with a "Profit" that
      only has settled bets (apples to oranges); voided bets have no count; the running-profit line orders by when Vigilant
      graded (`settledAtMs`), not by game date; outliers are left out of everything with no line for the real bankroll total; FMV
      counts as "pushed". Fix each (T5), with tests.
      **Build order (each a checkpoint):** T2 (BetRecheck: no cap, one honest Report with every open bet accounted for, live
      progress, Vigilant scan for Vigilant-only bets) -> data model (`TrackedBet.books` snapshot, `gradeNote`) -> T4 (reasons,
      NHL/NBA/tennis parsers, PropLine fallback, Undo re-enables auto-grade with a button) -> T5/T6 (stats + breakdowns + expected
      vs actual + batched saves + memoised UI) -> T3 (bet sheet: odds bet at vs fair now, every book's odds, EV now, CLV) + T7
      (Replace: `novigapp://events/<outcome>` + Settings' bet-slip stake; resolves a missing outcome via `AppContainer.betLink`)
      + price edit -> T8 (notification: `✓ Placed $X` / `Skip` action buttons that track without opening the app via a
      BroadcastReceiver, the tap opens Novig but the notification stays (no auto-cancel); EvAlert carries league/gameUrl/
      betUrl/marketId so the tracked bet rechecks and grades) -> T9 full test protocol, v0.20.0 (code 43), Release.
- [x] T2 "Check odds now" checks every open bet (101 of 101, not 40 of 40).
      DONE: `BetRecheck` has no cap; one `Report` where read + current + failed + not-tried + over + Vigilant-only always adds
      up to the open count, and the toast says it in words (`Report.summary`); count on the button ("Checking 12/61…");
      CNO pages read 2 s apart, results saved in batches of 5 (`BetTracker.editMany`), a cancelled run keeps what it read, a
      run stops when CNO asks for a pause or 5 reads in a row fail; Vigilant-only bets start a Vigilant scan (their markets
      pinned) when that scanner is on. Tests: BetRecheckTest (101 bets, accounting, pause/failure stop, cancel, pace, one bet).
- [x] T3 Tap an open bet: a sheet with the bet's odds across the other books, the odds Tj bet at, the difference from the current devigged fair odds, etc.
      DONE: `TrackerBetSheet.kt` (`BetSheet`/`BetSheetContent`) over `BetInsight`: odds bet at + implied chance, fair when bet, fair now,
      EV now at the price bet, the gap in points, how far the market moved, break-even odds, Novig's price now, CLV, and every
      book's odds with its own devigged fair and the EV of the price bet at against it; books kept on the bet (`TrackedBet.books`),
      re-read on opening when over 2 minutes old (CNO bets), Vigilant bets fill from scans. Tests: BetInsightTest, TrackerTextTest, TrackerUiTest.
- [x] T4 Every final event grades every open bet win/loss/push: research the API that claims to grade all props markets; use it if it works.
      DONE (RESEARCH.md §34): the API is PropLine; its per-prop grading (`/events/{id}/results`) is paid ($9/mo+), the free key gets
      it redacted; its free `/scores` + `/stats` duplicate what ESPN/MLB give the app for free and would spend the 1,000/day key
      the scans use, so it is NOT wired (option kept in §34). What was actually leaving final bets open, fixed: no hockey box parser
      (NHL props never graded), basketball steals/blocks/turnovers/P+R/P+A/R+A/S+B/double+triple double, football tackles, tennis
      (ESPN scoreboard: sets/games; retired/walkover = called), alternate/game/match totals, "Games Won", "Games Spread", "1st Set
      Winner", set spread/total sets, every Novig prop type in `statOf`. Every bet a pass can't grade now says WHY on the bet
      (`gradeNote`, `gradeManual`: game not over / market can't be read / player not in the box score / no feed / called off / over 30
      days) and every graded one says what it rests on ("Final: Mets 7, Nationals 1"); "Grade now" forces a pass; an undone result
      shows "Grade automatically". Tests: BetGraderTest (+7), BetSettlerTest (+6), FreeScoresTest (+6, real ESPN fixtures).
      NOT verifiable from here: which of Tj's real open bets were stuck (his bets.json is on his phone): each shows its reason now.
- [x] T5 Stats are right: wins, losses, pushes, total profit (audit, fix, test).
      DONE (`BetTracker.stats`): Expected is over the same won+lost bets as Profit (open bets had been in it), open bets have their
      own at-risk / pays / expected, voids counted as voids, pushes and FMV as pushes, outliers out of everything with the real
      bankroll's `profitAll` shown beside it, running-profit line ordered by game date (was: when Vigilant graded). Tests: BetTrackerTest (+5).
- [x] T6 Other tracker improvements (coding, efficiency, accuracy), aimed at "how well do my +EV bets profit with Vigilant".
      DONE: "Are the edges real?" (expected vs actual, difference, luck in standard deviations with a plain verdict), "Where it's working"
      (`TrackerBreakdown`: by scanner / league / market kind / edge band / price band, each with record, profit, ROI, avg EV, CLV),
      open-bet summary line, open list sorted by need (started + needs a tap first, then soonest game), price edit
      (`BetTracker.setPrice`: the price really got), batched saves (one fsync per batch, not per bet), lists and stats memoised
      (`remember`) instead of recomputed each recomposition. Tests: TrackerBreakdownTest, BetTrackerTest.
- [x] T7 A Replace button on every open bet: opens Novig with that exact bet in the bet slip and the stake preset from Settings.
      DONE: `BetReplace` (link `novigapp://events/<outcome>` + Settings' $1 / Kelly (from fair now vs Novig's price now) / my amount);
      a bet with no known outcome is looked up like a widget tap and the outcome is kept; on every open bet's card and in the sheet.
      Tests: BetReplaceTest, TrackerUiTest.
- [x] T8 A fast way to mark a bet placed from its push notification.
      DONE: the alert now has a "✓ Placed $X" button (X = Settings' bet-slip amount, else $1) that tracks the bet at the alert's price
      and hides it everywhere without opening Vigilant (`AlertActionReceiver` -> `EvAlerts.handle` -> `AlertPlacement`), turning the alert
      into a quiet "Tracked ✓ … Undo"; tapping the alert still opens the bet in Novig but no longer dismisses it, so the button is
      there when the bet is in. `EvAlert` carries league/market/CNO page/fair so the tracked bet rechecks and grades. Price got differs?
      "Change price" in the Tracker. Tests: AlertPlacementTest (5), AutoScanTest (+3).
- [x] T9 Full test protocol (test-protocols skill) after T2-T8: improve the app, UI, code; fix bugs; ship + Release link.
      DONE (full test 2026-09-29): floor 837 tests (engine 39, data 529, app 254; 15 live skipped by design), every screenshot rendered
      (`-Pscreenshots`, 72 PNGs; the Tracker's five, Settings, feed, CNO, widget looked at), live check `VIGILANT_LIVE=1 ... --tests
      '*LiveScoresTest'` against ESPN/MLB for real (old MLB/NFL bets still settle; new: real NHL Wild@Red Wings, NBA Raptors@Cavaliers,
      ATP Muller d. Pavlovic all grade right), R8 release build. FOUND AND FIXED in the sweep (each with a test):
      a recheck landing after a tap re-attached books to a settled bet (BetRecheckTest race); Vigilant's stored "price now" carried the
      fee so Replace's Kelly counted it twice (now the price without it); Check odds now / Re-read ignored the Pause switch even though
      they read CNO (now held, toast); a CNO alert's tracked bet never got Novig's market so scans couldn't follow its line
      (`AlertPlacement.attachMarket`); the sheet's one-bet re-read queued behind a whole minutes-long pass (`checkOne` no longer takes
      the pass's lock; test proves it); Replace could stay "Opening…" forever if the screen closed mid-lookup (finally); four compiler
      dead-code warnings (Pricing, BetRecheck, GamesScreen, MiniFeed: now none). IMPROVED: CLV is now captured without Tj doing anything:
      each background auto-scan cycle reads the books of open bets starting within the hour (`BetRecheck.captureClosing`; the last read
      before the start is the closing line); Settings copy for the alert's ✓ Placed button, the Pause switch and auto-scan.
      CONSIDERED, NOT CHANGED: 140 `runCatching` blocks that also catch cancellation (all short, view-model scoped, no harm found; a
      rewrite would risk more than it fixes); PropLine's free box-score feed as a second grading source (RESEARCH.md §34).
      Not testable here: a real device (notification actions, the overlay over Novig, the phone's own data): Tj's first "Check odds now"
      over his 101 bets and first "✓ Placed" from a real alert are the live checks.
      SHIP: v0.20.0 (code 43).

## "When I pressed check odds now in the tracker, it scanned very slow. Slower than before. And a lot of bets can't be tracked, see the screenshot. If they can't be tracked, how did the app know it was positive EV to begin with? And it said it only updated 61 bets, but I have 100 or so open. Investigate how to make all this work" (Tj, 2026-09-29, after v0.20.0; screenshot: Kade Anderson Over 1.5 "Player Earned Runs Allowed" = "Couldn't read … well enough to grade it"; Erick All Jr. Under 0.5 Player Receptions = "isn't in the box score"; KC Concepcion Over 5.5 Player Rushing Yards = "The box score has no Rushing Yards")

- [x] U1 Investigate with real data: (a) why Check odds now is slower than v0.19.7 (per-bet CNO page cost, the 2 s gap I added, one page per bet); (b) why those three bets don't grade (market wording, box score naming, missing stat); (c) what the "61 of ~100" really was. DONE: RESEARCH.md §35 (live speed test, real ESPN/MLB box scores for all three bets, the 61-of-100 breakdown).
- [x] U2 Answer Tj's question in plain words: positive EV comes from the odds (CNO's list / fair price vs Novig's price), grading needs the RESULT from a box score: two different data sources. DONE: RESEARCH.md §35.3 and the reply to Tj.
- [x] U3 Make Check odds now fast: read pages in parallel and/or fewer pages, no wasted waiting. DONE: bulk pace 500 ms + 3 at a time; `BetRecheckTest` (concurrency, failure stop, cancel), `LiveCnoBooksSpeedTest` 1041 vs 2120 ms a bet.
- [x] U4 Make grading work for the markets CNO really lists: every CNO market label read, box-score misses handled (a player who played but has no stat in that group = 0; a player who didn't play = void only when it's certain), names matched. DONE: `RealBoxGradingTest`, `BetGraderTest`, `NovigBetFinderTest` aliases, `LiveUngradedBetsTest` (the three screenshot bets settle live).
- [x] U5 Make the count honest and complete: every open bet is either checked, or says why not (game over, no CNO page, waiting for a scan), and the ones that can be checked all are. DONE: `TrackerTextTest` (odds read on N of M upcoming, oddsNote), `BetRecheckTest` summary with the grading pass; Check odds now grades finished games in the same tap.
- [x] U6 Full test (test-protocols), ship, Release link. DONE: floor 833 passed / 19 live skipped (exit 0), screenshots looked at (4d_tracker_open_bets), live checks (LiveUngradedBetsTest, LiveCnoGradableTest, LiveCnoBooksSpeedTest), shipped v0.20.1 (code 44): CI 36535348703 green, release run 36535722571, https://github.com/tjshea90/novig/releases/tag/v0.20.1

## "New research projects: 1) review in depth the entire novig API docs. I read somewhere that it can show the bets I actually placed and grade them and I can place bets through the API. Find all the features and rules of the API and optimize the vigilant app to take full advantage of all features and speed and accuracy and grading of final bets if possible. Let me know if I need to do anything. Right now the novig scan is slow, even though I tested my key and it says it works. 2) research these API: moneylineapp.com/sports-betting-api, opticodds.com/sportsbooks/novig-api, predictiondata.io/us/api-data/novig, livefeedapi.com/offerta, sharpapi.io/sportsbooks/novig-odds-api, betstamp.com/odds/novig, odds-api.io/sportsbooks/novig. Are any of these free or very cheap that can help get odds quickly or improve the speed or accuracy of vigilant. Can any of the features help grade bets" (Tj, 2026-09-29, after v0.20.1)

- [x] V1 Read Novig's WHOLE API docs again (docs.novig.com: every page, the OpenAPI/spec if there is one) and list every route, scope, rule, limit and field; write the findings into NOVIG_API.md (verified vs. from docs vs. unknown). DONE: NOVIG_API.md §14 (86 pages + 51-route OpenAPI spec read from the schemas; every route/scope/throttle in one table).
- [x] V2 Answer Tj's question: can the API show the bets he actually placed, grade/settle them, and place bets? (routes, scopes, what his key can do, what's beta-only); test what can be tested with no key (only public routes here). DONE: NOVIG_API.md §14.2 and RESEARCH.md §36.1: the API sees only API-subaccount bets (separate wallet); app-placed bets are invisible; Novig grades subaccount bets via the ledger; placing is possible (not built).
- [x] V3 Find why the Novig scan is slow with a working key: read where a keyed scan's time goes (ScanTiming, websocket subscribe ~8 s, throttles, batch sizes, RateGate) against the docs' limits; measure live what can be measured. DONE: RESEARCH.md §36.2 (live: Kalshi 27 s, Polymarket 13 s set "first bet at"; Kalshi 429s at 4/s on the real route). Fixed: Polymarket pages in waves (13 s -> 5.6 s live), scan timing line names the slowest sources (`ScanTimingTest`), Test key runs `NovigLiveCheck` (`NovigLiveCheckTest`).
- [x] V4 Build what helps and is safe: use the API's own features for speed and accuracy (whatever V1-V3 turn up); no major changes to behavior Tj hasn't approved (report those as offers). DONE (what is safe): the three fixes above; `ExchangeClientsTest` (Polymarket paging, Kalshi two at a time and 429 retry). NOT built, offered: API betting, Kalshi key, MoneyLine source.
- [x] V5 If the API lists his placed bets and their results: use it to import placed bets, and to grade final bets from Novig's own settlement (the best ground truth), keeping the score-feed grading as the fallback. DECIDED: app-placed bets are invisible to the API, so there is nothing to import; settlement from Novig applies only if bets are placed through the subaccount (offer, RESEARCH.md §36.4).
- [x] V6 Research the seven third-party APIs (MoneylineApp, OpticOdds, PredictionData, LiveFeedAPI, SharpAPI, BetStamp, odds-api.io): price/free tier, whether they carry Novig odds and other books' odds, latency, player props, whether any grade bets; verdict per API in RESEARCH.md §36. DONE: RESEARCH.md §36.3 (seven APIs: price, Novig, props, grading; only MoneyLine free is worth testing, only OpticOdds has a grader and it is sales-gated).
- [x] V7 Tell Tj what he needs to do (keys, scopes, settings), and which of the findings need his approval before I build them. DONE: RESEARCH.md §36.5 and the reply.
- [x] V8 Full test, ship (only if code changed), Release link. DONE: floor 839 passed / 19 live skipped (exit 0); shipped v0.20.2 (code 45): CI 36539455804 green, https://github.com/tjshea90/novig/releases/tag/v0.20.2

## "Attached is the novig test. Here is the moneylineapp.com API key: [in INBOX.md, 2026-09-29T15:14Z] Build the betting through the API function, and include the API grading bets feature for the tracker system in vigilant" (Tj, 2026-09-29 ~15:14Z, after v0.20.2; screenshot: Settings › Novig API: "Last scan took 29 s: board 1.2 s · fair odds 27 s (Kalshi 27 s, Sportsbook props 9.0 s, PropLine 8.8 s) · 1,151 Novig prices in 28 s (41.8 a second: 803 by live feed, 348 through the key) · first bet at 10 s · Novig refused none · the key's limit is 16 a second"; Test key: "Limits: read 64 (16 a second), stream 512 (4 a second), up to 2048 markets watched. Signed catalog: 10 open markets in 0.1 s. Books: 5 through the key 123 ms each, 5 public 73 ms each. Live feed: first books after 8.4 s, 10 of 10 after 8.4 s.")

- [x] W1 Read the test for Tj: the key, the live feed and the signed reads are all healthy (live feed works against the real API for the first time; 803 of 1,151 prices by push); the scan's 29 s is Kalshi's 27 s, and the first bet showed at 10 s. Say so plainly, and what would still speed it up. DONE: the reply to Tj (live feed 8.4 s = the documented bucket fill; scan 29 s = Kalshi 27 s; first bet at 10 s).
- [x] W2 MoneyLine: test Tj's free key sparingly (a handful of the 1,000 requests): props payload, Pinnacle props, latency, box scores; verdict in RESEARCH.md. The key is in INBOX.md only (public repo, Tj: "public is fine"); never write it into source. DONE: RESEARCH.md §36.6 (3 requests used; stale 2 h, no Pinnacle, 3 MB for 3 events: not a source).
- [x] W3 Design API betting + API grading with the docs' rules (NOVIG_API.md §14) and write it down before code: money safety (pregame only, IOC at the executable taker price, per-bet and per-day caps, a confirm tap, paused blocks it, re-read the book, edge must still be positive, uncertain results resolved by clientId), the trading key on the phone, funding through the management key (never stored), what grading reads (ledger SETTLEMENT rows, positions, fills), and cross-checking against the score feeds. DONE: NOVIG_API.md §15, RESEARCH.md §37.
- [x] W4 Data: `NovigTradingClient` (place order, get order, fills, positions, orders, balance; every refusal in plain words: 403 KYC, 422 wallet/position cap, 423, 451 network/geolocation, 429), tested against a mock Novig. DONE: `NovigTradingClient`; `ApiBettingTest` (wire, refusals, fills paging).
- [x] W5 Data: `ApiBetPlacer`: fresh book, taker price on the grid, contracts from the stake, caps, edge check, IOC order, wait for the fill, real fill price/fee recorded on a `TrackedBet` (orderId, contracts, fee), never a double order; tests. DONE: `ApiBetPlanner`/`ApiBetPlacer` + `TrackedBet` API fields; `ApiBettingTest` 15 tests (ladder walk, every refusal, IOC ceiling, real fills, partial, moved price, 422/451, lost answer by clientId, unconfirmed, duplicates, caps, Undo).
- [x] W6 Connection: "Enable betting" (management key once, memory only): find the Vigilant subaccount, make sure the phone holds its `trading` key (the setup-time key if its Keystore entry exists, else a replacement), save it; show the balance; fund and withdraw with the management key; caps in Settings (default off). DONE: `NovigBettingSetup` (`NovigBettingSetupTest` 5: use the phone key, revoke+mint with 409 retries, cleanup, fund/withdraw), connection store, container wiring.
- [x] W7 API grading for the Tracker: settle API-placed bets from Novig's own ledger (SETTLEMENT rows: win, push, fair-value void) and positions, cross-check with the score feeds (a disagreement is left to a tap and says why); import fills that never became a tracked bet (crash between order and record). DONE: `ApiSettler` + `ApiBetSync` (`ApiSettlerTest` 10: win, push, fair value, waiting, loss cross-check, unreadable Novig, tapped bet, fill/order ids, sync), `BetSettler.leaveApiBets`.
- [x] W8 UI: Settings section (enable, balance, fund/withdraw, caps), a "Bet" button with a confirm sheet on the +EV card and the CNO card, "Placed through Novig's API" and "Graded by Novig" on the Tracker's bet card/sheet; screenshot tests. DONE: `ApiBettingUiTest` (13 incl. screenshots 4f_api_bet_sheet, 5f_settings_api_betting), `ApiBettingControllerTest` (4: plan, one order on a double tap, refusal, off), `ApiBetTargetsTest` (3).
- [x] W9 Full test (test-protocols) + sweep, ship v0.21.0, Release link, and tell Tj exactly what to do first (QA-first? fund $, first bet). DONE 2026-09-29: floor 911 tests (892 passed, 19 live skipped), screenshots 4f/5f checked, sweep found and fixed two money-path holes (bets sharing a market graded together: ApiSettlerTest; closing the sheet mid-order no longer cancels it: ApiBettingControllerTest `closing the sheet while an order is being placed`, verified failing without the fix); CI green on 57fa81d3, Release v0.21.0 (code 46) built by release.yml. Still UNVERIFIED live (Tj's phone): RESEARCH.md §37 list.

## "When I check the updated odds for the bet tracker to see the current EV: 1) right now it only checks cno scanned EV. Make it update the EV for every single open bet, including bets added from vigilant scanner. 2) add filter options on the top of this bet tracker section, including date placed (orders bets placed by date and time), current EV (orders bets by current EV with the best current EV at the top of the list compared to the odds I placed the bet), amount of bet, scanner used to place bet 3) make sure the tracker is telling me the current, up to date EV, which is devigged and compared to the actual odds that I placed the bet at." (Tj, 2026-09-29 ~16:46Z, after v0.21.0; raw text in INBOX.md)

- [x] X1 Investigate how Check odds now works today: which bets get a current EV (CNO-found bets via CNO pages) and which don't (Vigilant-scanner bets: `BetRecheck`, `BetInsight`, `NovigBetFinder`), and where the current fair odds for a Vigilant bet could come from (the scan's own fair sources: Pinnacle/Polymarket/Kalshi/props; what `TrackedBet` stores about them). Write findings into RESEARCH.md. DONE: RESEARCH.md §38 (Vigilant bets have no `gameUrl` so Check odds now never read them; `observe` only prices what a feed scan happens to plan and doesn't advance the age; the card says "now" whatever the age).
- [x] X2 Check odds now updates the current EV of EVERY open bet, Vigilant-scanner bets included (fresh Novig price + fresh devigged fair odds for that exact market/outcome), and says honestly per bet when a fresh fair price couldn't be found (why), never leaving a stale number that looks current. DONE: all four parts above; the toast counts add up to the open bets (`BetRecheckTest`).
  - [x] X2a `Scanner(betsOnly = true)`: a second scanner instance for bets: catalog cut to the pinned markets/games, no `watch()`, no end-of-scan re-read, only the pinned markets planned. DONE: `Scanner(betsOnly)`: `OpenBetPricerTest` (only the bets' books read, no `watch()`, fresh snapshots every pass, catalog cut).
  - [x] X2b `TrackedBet.nowVia` / `nowNote` / `nowNoteAtMs` (persisted, defaults keep old files loading); `BetTracker.applyPricing` (every open pregame bet found gets fair/EV/age/books/price now; a fair-less one gets its reason; the CNO/scan writers set `nowVia`); `observe` advances the age even when the fair is unchanged. DONE: `TrackedBet.nowVia/nowNote/nowNoteAtMs`, `BetTracker.applyPricing/applyFair`, `observe` moves the age on: `BetTrackerTest` (2 new).
  - [x] X2c `BetsScope` (settings widened for the bets: their leagues, window to the last start, all families, props caps) + `BetPricingReasons.explain` (pure) + `OpenBetPricer` (runs the betsOnly scan, applies it, reports priced / unpriced). DONE: `BetsScope`, `BetPricingReasons`, `OpenBetPricer`: `OpenBetPricerTest` (7) and live `LiveOpenBetPricerTest` (10 real bets in 5 leagues priced in 26.7 s).
  - [x] X2d `BetRecheck.Report` counts the Vigilant part; `MainViewModel.checkOdds` runs the CNO read and the pricing pass together (progress adds up), a CNO bet whose page failed falls to the pricing pass; scanner "CNO only" says why Vigilant bets aren't updated; `VigilantApp` wiring. DONE: `BetRecheck.Plan/Report/withPricing` (`BetRecheckTest`), `MainViewModel.checkOdds` runs both together and rescues CNO-unread bets, single-bet "Price now", `VigilantApp.betPricer`; CNO-only scanner says why.
- [x] X3 The current EV shown on every open bet is up to date, devigged, and compared to the odds the bet was actually placed at (EV of the price paid vs the current fair, next to the EV at placement), with when it was read; API bets use their real fill price. (Card: "now +X% EV" only when read within `FRESH_LABEL_MS`, else "as of 2h ago" muted; whose fair and how many books; the reason in place of a number; the summary line counts what's current.) DONE: `TrackerText.nowLine/currentEv/oddsNote` + sheet NowCard (`TrackerTextTest`, `TrackerUiTest` old-EV and reason tests, screenshots 4d/4g).
- [x] X4 Filters/sort at the top of the Tracker's bets: date placed (by date and time), current EV (best current EV at the top, versus the placed odds), amount of bet, scanner used to place the bet (Vigilant / CNO / both, filterable and sortable). (`BetSort` + `ScannerFilter` in `TrackerText`/`TrackerScreen`, saved across rotation; UI test + screenshot.) DONE: `TrackerSort`/`BetSort`/`ScannerFilter` + Sort and Scanner chip rows + "placed" date on the card (`TrackerSortTest`, `TrackerUiTest`).
- [x] X5 Tests (data + UI + screenshots), full test protocol + sweep, ship v0.21.1 or v0.22.0, Release link. DONE 2026-09-29: floor 939 tests (919 passed, 20 live skipped), screenshots 4d/4g looked at, live LiveOpenBetPricerTest (10 real bets in 5 leagues priced in 26.7 s), sweep (shared Kalshi gate and props locks, settings widening, catalog reset per pass, wording of reasons, stale labels on the sheet); CI green on 8af737ae, Release v0.21.1 (code 47) built by release.yml.

## Screenshots from Tj's phone (2026-09-29 ~17:50Z, after v0.21.1; no text): the first REAL API bets, on Taylor Trammell Under 0.5 (Hits + Runs + RBIs) and Michael King Over 4.5, both answered "Novig said no: clientId: UUID parsing failed: invalid character: expected an optional prefix of `urn:uuid:` followed by [0-9a-fA-F-], found `v` at 1 at line 1 column 148"

- [x] Y1 Root cause: Novig parses `clientId` as a UUID; the placer sent `"vigilant-" + UUID` (and `NovigBettingSetup.transfer` sent `clientTransferId` the same way). Nothing was placed (rejected while parsing, so no money moved, and the app said so). Fix: a plain UUID for both; a client-side check so a non-UUID id can never be sent again; tests assert the wire format; NOVIG_API.md notes the real rule. DONE: plain UUID for `clientId` and `clientTransferId` (`NovigTradingClient.newClientId/isUuid`), `placeOrder` refuses a non-UUID before sending; `ApiBettingTest` (mock Novig now refuses a non-UUID like the real one; new refusal test), `NovigBettingSetupTest`.
- [x] Y2 Look for the same class of mistake elsewhere in the placing path before it costs Tj another round trip (field formats the docs leave loose: `price` string on the grid, `qty` integer, `tif`, transfer `amount`), and say what else is still unverified live. DONE: spec re-read for orders, fills, positions, ledger, balance, transfer, key mint: only `clientId` was wrong; lost-answer lookup now covers PENDING, this outcome, all pages; OPEN with 0 remaining is finished (`ApiBettingTest` +2). NOVIG_API.md §15 and RESEARCH.md §37.3a updated.
- [x] Y3 Sweep, full test, ship v0.21.2, Release link, tell Tj to try again. DONE 2026-09-29: floor 942 tests (922 passed, 20 live skipped), CI green on ec0c2cc9, Release v0.21.2 (code 48) built by release.yml.

## "The betting from vigilant now works. Tell me what you need me to do or show you to make sure the grading works after the bets are done to track my wins and losses automatically. Also tell me what you need me to do or show you to optimize the app and make sure everything is working as designed. Ensure that if I have cno only turned on in the settings that it doesn't scan vigilant in the background and waste api usage." (Tj, 2026-09-29 ~18:20Z, after v0.21.2; raw text in INBOX.md)

- [x] Z1 CNO only means Vigilant is asleep, everywhere, in the background too: audit every path that can read Vigilant's APIs (auto-scan service/alarm/boot receiver/cycle, widget rescan, closing-line capture, Tracker's Check odds now / Price now, SettleWorker, alerts, Novig live prices, the API-bet sync/settler, key usage) with the scanner set to "CNO only"; fix any that still reads a Vigilant fair-odds source or Novig's board for Vigilant; add a test per path that proves it makes no Vigilant request in CNO-only mode (and that Both / Vigilant only still do). DONE: audit in RESEARCH.md §39.1: the background cycle's Vigilant part ran on Both even with the scanner on CNO only; now gated (`autoScansVigilant`), refused at `ScanRunner.start` and `OpenBetPricer.run`, running scans stop on the switch; `CnoOnlyAsleepTest` (+ pricer, AutoScanTest, PauseScanningAppTest).
- [x] Z2 What to show me to verify grading of API bets after the games: write down exactly which screens/values/logs prove the ledger grading works (the SETTLEMENT row shape, `ref`, loss with no row, positions after settlement, ledger time-filter units) and, if the app can capture it, add a small in-app "Grading check" report Tj can screenshot (what Novig's ledger/positions returned for each open API bet, how each was graded and why). DONE: Settings › Diagnostics › Grading check (`ApiGradingCheck`, `ApiGradingCheckTest`, `ReportUiTest`); what to show and when: RESEARCH.md §39.4.
- [x] Z3 What to show me to optimize the app and confirm it works as designed: a short, concrete checklist for Tj (screens to screenshot, numbers to read, Settings values), including a diagnostics view or export if that would spare him the guesswork; nothing that needs a key pasted into chat. DONE: Settings › Diagnostics › Show report (`Diagnostics.report`, `DiagnosticsTest`, screenshot 5g); checklist RESEARCH.md §39.4.
- [x] Z4 Full test, sweep, ship, Release link, and answer Tj's two "what do you need from me" questions plainly. DONE 2026-09-29: floor 961 tests (941 passed, 20 live skipped), CI green on 523dda5e, Release v0.21.3 (code 49) built by release.yml; the two "what do you need from me" answers are RESEARCH.md §39.4.

## Tj's two reports (Diagnostics after a regular scan, and after Tracker "Check odds now", 2026-09-29 ~19:00Z: e7815890-checkoddsnow.txt, c55d09fe-regscan.txt) + "See what you can optimize from the diagnostics. Also: 1) often when I switch from vigilant to another app, the widget opens automatically. Only open the widget if I press the icon to open it in the app. 2) tell me which apis deplete too quickly for daily use so I can add more keys 3) the settings section is getting very long. See how you can organize it. Maybe tabs on the top. 4) for any section with tabs on the top, such as the bet tracker section, keep the top navigation tabs "sticky" to the top. When I scroll down through the long list of my active bets, I still want to have the filters at the top without having to scroll all the way back up." (Tj, raw text in INBOX.md)

- [x] A1 Read both reports and find what to optimize (RESEARCH.md §40): per-scan and per-Check-odds cost by API (Kalshi +103 calls, PropLine +24, Polymarket +44, PinnWire +4 for one Check odds now), what is slow, what is wasted; fix what is safe. DONE 2026-09-29: the pass asks only the bets' market families (`BetsScope.familiesFor`, tests in `OpenBetPricerTest`: families, unreadable = all, sources and Novig board asked for those only); Diagnostics shows the last scan's and Check odds now's cost per API and timing (`UsageDelta`/`RoundCost`, `DiagnosticsTest`, `RunwayTest`); RESEARCH.md §40.1-40.2 (Kalshi empty-series memory measured and dropped: 4 of 57 empty).
- [x] A2 The floating widget only opens when Tj taps its icon in the app, never on its own when he leaves Vigilant (find the auto-open on leaving/backgrounding; keep the picture-in-picture behaviour off too unless asked); a button/icon in the app opens it; tests.
- [x] A3 Tell Tj which APIs deplete too quickly for daily use (per provider: allowance, used, pace, days left) and add a "Runway" block to Diagnostics so the next report says it for him. DONE 2026-09-29: the answer is RESEARCH.md §40.3 (PinnWire 100/day is the one to add keys to, The Odds API 500/month is only a backup behind PropLine, PropLine fine, Kalshi/Polymarket/Novig unmetered); Diagnostics has a Runway block (`Runway.lines/roundsNote`, `RunwayTest` incl. Tj's own report, `DiagnosticsTest`).
- [x] A4 Settings organised into tabs at the top (sticky), sections grouped by what they do; nothing lost; tests + screenshots. DONE 2026-09-29: seven Settings tabs in a sticky scrolling tab row (`SettingsTab`; CNO only drops three), choice survives rotation, each opens at its top; `SettingsTabsTest` (every old section on exactly one tab, restore, sticky, CNO only), existing Settings tests moved to their tabs, screenshots 5_settings_tab_*.
- [x] A5 Sticky top navigation on every screen that has top tabs/filters: the Tracker (Stats | Bets + Open/Settled/All + Sort + Scanner), the new Settings tabs, and any other screen with a filter row (look at +EV, CNO, Games); tests + screenshots. DONE 2026-09-29: Tracker (Stats|Bets, periods or Open/Settled/All, Sort and Scanner as compact menu chips: pinned bar 161 dp vs 311 dp as wrapped chips), +EV (league chips + start window), Games (league chips), CNO (scanner chip, filters, start window) pinned with `stickyHeader`/`StickyBar`; new list starts at its top; `StickyHeadersTest`, `TrackerUiTest`, `TrackerSortTest`; screenshots 4h, 1, 8.
- [x] A6 Full test, sweep, ship, Release link, and answer plainly (what the reports show, which APIs run short, what changed). DONE 2026-09-29: floor 997 tests (977 passed, 20 live skipped), CI green on c1e67e57, Release v0.22.0 (code 50) built by release.yml; the answer is in chat and RESEARCH.md §40.

## Tj's request 2026-09-29 ~20:51Z (raw text in INBOX.md): "For betting through the API, allow me to add custom amounts to the vigilant wallet in the app settings by typing in an amount. If my wallet is too low when I go to place a bet in the app, add a button to go directly to the setting to add money to the wallet. Make it so I only input the API key and file one time and the vigilant app saves it permanently in the settings so I don't have to keep entering it. Make this and all keys persist even through app updates"

- [x] B1 Wallet top-up in Settings: Tj types a custom amount and adds it to the Vigilant wallet (the balance used for betting through the API); validation (positive, sane max, cents), shows the new balance; tests. DONE 2026-09-29: Settings › Betting › "Add money to the Vigilant wallet" has an Amount field ($, commas, cents; $0.01 to $10,000; says what's wrong) plus quick chips; Add / Take back send the typed amount; Take back can't exceed the balance (`WalletAmount`, `WalletAmountTest`, `ApiBettingUiTest` "any amount can be typed in", controller transfer of $12.34 in `ApiBettingControllerTest`).
- [x] B2 Wallet too low when placing a bet in the app: the bet sheet shows a button that goes straight to the Settings tab/section where he adds money (lands on the wallet field), then back to the bet; tests. DONE 2026-09-29: "Add money to the wallet" on the sheet (also after a 422 "not enough money" refusal) opens Settings on the Betting tab scrolled to the wallet with the shortfall typed in (whole dollars, ≥ $1) and a banner with "Back to the bet" (same bet and amount, re-priced) / "Not now"; the wallet section now also shows in CNO only (`requestTopUp`/`backToBet`/`dismissTopUp`; `ApiBettingControllerTest` top-up round trip, `ApiBettingUiTest` Add money + banner, `SettingsTabsTest` opens on the wallet scrolled into view, CNO only keeps it; screenshots 4g, 5g).
- [x] B3 Novig API key + key file entered once and saved permanently in Settings (never asked again unless he removes/replaces it); find why it is being re-asked today and fix; tests. DONE 2026-09-29: cause = the management key was deliberately never stored (every transfer asked for it). Now `ManagementKeyStore` saves it once Novig accepts it (Connect, Enable betting, a transfer, or "Save key" = one signed echo), PEM sealed by a Keystore AES key (`KeystoreSecretBox`); Settings shows "••••last4 saved on this phone" with Replace / Forget; a refused key is never saved; Diagnostics says saved or not (`ManagementKeyStoreTest`, `ApiBettingControllerTest` saved key signs the next transfer / refused key not saved / Save key / Forget, `ApiBettingUiTest` saved-key flows incl. Connect, `DiagnosticsTest`).
- [x] B4 That key and ALL keys (every API key/credential the app stores) and the wallet persist through app updates (versionCode bumps, same signing key): storage survives an update, no migration wipes them, no encrypted store that loses its master key; backup/restore rules checked; tests. DONE 2026-09-29: odds keys (files/api_keys.json), Novig connection (DataStore + Keystore read/trading keys) and the management key (files/novig_management_key.json + Keystore) all live in app storage an update never touches; nothing on update clears them; the wallet balance lives at Novig. `KeysSurviveUpdateTest` (a new AppContainer over the same files = the updated app's first process gets every key back, also after ensureLoaded) + backup rules checked (odds keys restore, the phone-bound ones are excluded). NOVIG_API.md §14 updated.
- [x] B5 Sweep, full test, ship, Release link, answer plainly. DONE 2026-09-29: floor 1031 tests (1011 passed, 20 live skipped), CI green on a65d1c12, Release v0.23.0 (code 51) built by release.yml; answer in chat.

## Tj's request 2026-09-29 ~22:57Z (raw text in INBOX.md): "Add a stats function for when I check odds now in the bet tracker section, as the refreshed odds come in, there is a counter at the top of the section that shows how many of my open bets are currently positive EV and how many are currently negative EV plus a percentage of bets that are positive EV. This counter should refresh back to zero every time I do a new check for odds so that it only shows me the number of current positive EV bets that I placed which are still open. Then next to that, make an average EV stat that shows the average EV percentage of all of my current open bets but not counting any outliers such as any bets showing a current EV of more than 5% positive or a current EV of more than 5% negative."

- [x] C1 Check-odds counter at the top of the Tracker (pinned with the tabs): during and after "Check odds now", how many OPEN bets are currently +EV and how many −EV (current, devigged EV vs the price Tj got), plus % of the checked bets that are +EV; counts live as each bet's refreshed odds come in; resets to 0/0 at the start of every new Check odds now, so it only counts bets re-priced in THIS check; settled bets never count. DONE 2026-09-29: one row pinned under Stats|Bets on both views ("2 +EV 1 −EV 67% +EV … +0.5% avg EV"), a caption just below says what it counts; counts open bets whose nowEv was read since the check began (`CheckOddsStats.of`, start saved in last_check.json so it survives a restart); updates with each saved batch of 5 (`CheckOddsStatsTest`, `TrackerUiTest` counter/zero/Stats, `StickyHeadersTest` pinned + bar 189 dp, `CheckOddsCounterAppTest`, `DiagnosticsTest`; screenshot 4i).
- [x] C2 Next to it, an average current EV % across the open bets re-priced in this check, excluding outliers (current EV above +5% or below −5%); says how many were left out; tests. DONE 2026-09-29: "avg EV" at the right of the counter row, plain average of this check's EVs within ±5% (exactly 5% counts), outliers still counted as +/−, "N over ±5% left out of the average" in the caption; Diagnostics carries the line too (`CheckOddsStatsTest` average/outliers, `TrackerUiTest`, `DiagnosticsTest`).
- [x] C3 Sweep, full test, ship, Release link, answer plainly. DONE 2026-09-29: floor 1046 tests (1026 passed, 20 live skipped), CI green on c1a35dc8, Release v0.24.0 (code 52) built by release.yml; answer in chat.

## Tj's request 2026-09-29 ~23:59Z (raw text in INBOX.md): "Do any of these stats check true closing line value based on the closing line for each bet? If not, make a system that finds the true closing odds for each of my bets (keep in mind many bets will not have closing odds yet because the games are too far in the future). Add somewhere in the stats or bet tracking system a feature that shows the percentage of my bets that beat closing line value (the percentage of my bets that I bet at more favorable odds for me than the closing line). Also include the average percentage that my bets beat the closing line (the percentage difference of each of the bets at the odds I placed them vs the closing line odds, averaged together). Keep this stat line running forever, it does not reset. New bets will add to this statistic. Also make a filter system where I can see the statistics in this section based on time period (all time, today, yesterday, last 3 days , last week), and an option to remove outliers (bets over 5% different than closing line value)."

- [x] D1 Answer first: do today's stats use a TRUE closing line? (audit TrackedBet.closingFair / closingSeenAtMs, TrackerStats CLV, where the close is written, how close to start the last read is) and say plainly. DONE 2026-09-30: no (the last pregame read, however early, open bets included); RESEARCH.md §41; answered in chat.
- [x] D2 A system that finds each bet's true closing odds: the devigged fair line read as close to the game's start as possible (a capture just before start for every open bet, and/or a historical price at start time from a source that keeps history); a bet is "closed" only when its close was read near enough to start; bets whose games are far off show "no close yet"; survives app closed (background) as far as Android allows; tests. DONE 2026-09-30: true close = pregame read in the last 15 min, final at start (`ClosingLine`); exact alarm 6 min before each start → expedited `ClosingWorker` → `ClosingCapture` (CNO page / Vigilant pricing, retry 2 min, paused = nothing read), re-armed on bet changes and reboot (`ClosingLineTest` 11, `BetRecheckTest` captureClosing(ids), `ClosingLineAppTest` alarm follows bets + paused capture). History back-fill not built (§41).
- [x] D3 Stats: % of bets that beat the closing line (placed at better odds for Tj than the close) and the average % they beat it by (each bet's placed price vs the closing odds, averaged); a running, never-resetting stat line that new bets add to as their close is found. DONE 2026-09-30: Stats › "Closing line value" card over every bet ever (`ClvStats`): beat the close % (n of m), avg vs close, avg EV at bet, waiting / started-without-close counts; old CLV row + bet card column now true closes only; Diagnostics line (`ClosingLineTest`, `ClosingLineAppTest`, `BetTrackerTest`).
- [x] D4 Filters for this section: time period (all time, today, yesterday, last 3 days, last week) and an option to remove outliers (bets more than 5% different from the closing line); tests + screenshots. DONE 2026-09-30: the card's own wrapped period chips (`ClvPeriod`, local calendar days by placed time) and a Hide outliers switch (|CLV| > 5%) (`ClosingLineTest` periods/outliers, `ClosingLineAppTest` chips/switch; screenshot 4j).
- [x] D5 Sweep, full test, ship, Release link, answer plainly. DONE 2026-09-30: floor 1065 tests (1045 passed, 20 live skipped), CI green on 52e3dbc6, Release v0.25.0 (code 53) built by release.yml; answer in chat.

## Tj's request 2026-09-30 (raw text in INBOX.md): "My phone will not always be on. The app has to be able to find clv from closing lines after the games started or even days later. Espn may have the closing lines information. Check for sources that the app can use for this and implement it"

- [x] E1 Research (live, from this container where reachable) every free source of historical closing lines usable after the start or days later: ESPN (scoreboard / summary / core odds: open/close per provider), Kalshi (candlesticks / trades history), Polymarket (prices-history), Novig's own API (trades/history?), The Odds API historical (paid?), PinnWire / PropLine / pinnapi (any closing/settled data?), CNO. Coverage per market kind (moneyline, spread, total, props), how long it's kept, cost, matching. RESEARCH.md §42. DONE 2026-09-30: ESPN core odds keep DK/ESPN BET closes (ML/spread/total, past seasons); Novig's data.novig.com trade files keep every market (props too) from 2026-08-03; Kalshi candles for game winners; ESPN props = lines only; Odds API historical paid; table + cross-check in §42.
- [x] E2 Implement a closing-line back-fill: for every bet whose game started without a captured true close, find its close from the historical sources (after the start, hours or days later), devig it, record where it came from (TrackedBet close source), and use it for CLV; captured closes still win when present; lines that differ from the bet's line aren't compared blindly; tests with recorded payloads. DONE 2026-09-30: `EspnCloses` (exact line only, devig), `NovigTradeCloses` (30-min VWAP by byte-range search), `CloseBackfill`; TrackedBet closeFair/closeVia/closeNote/closeLookedAtMs/closeFinal; `ClosingLine.closeOf` captured > history > at-bet (`HistoricalClosesTest` 11 on recorded ESPN/Novig payloads, `LiveClosesTest` against the real feeds).
- [x] E3 Run it without the phone being on at game time: on app open, in the 3-hourly background worker, with Grade now / Check odds now; cheap (one request per league-day, cached); Diagnostics says what was back-filled and from where; the CLV card counts back-filled closes; tests. DONE 2026-09-30: runs in `gradeAll` (app open, Grade now, Check odds now) and `SettleWorker`; ESPN any network, Novig trade files on unmetered only; card counts closes by source, sheet shows each close + source + CLV or why none; Diagnostics lines (`ClosingLineAppTest`, `DiagnosticsTest`, `HistoricalClosesTest` Wi-Fi gate).
- [x] E4 Sweep, full test, ship, Release link, answer plainly (which sources work for what, what can't be back-filled). DONE 2026-09-30: floor 1081 tests (1060 passed, 21 live skipped), CI green on 92cd491e, Release v0.26.0 (code 54); NovigPublicClientTest's wave test made load-proof; answer given with F's.

## Tj's request 2026-09-30 (mid-turn, raw text in INBOX.md): "Research these two sources and see if they can help improve anything in the app, whether it is speed or accuracy or grading or finding historical closing lines to calculate clv. https://github.com/the-odds-api/apps-script/blob/master/ClosingLinesAnyMarket.gs https://therundown.io/api https://www.reddit.com/r/ParlayAPI/comments/1t8vtbl/the_complete_sports_betting_data_stack_for_2026/ https://github.com/DeliciousPipe1326/edge-scanner After researching those, implement into the app anything from these sources that can help or improve the app in any way. Then research online if there is anything I can buy, such as api subscriptions, that will greatly improve the app and is worth the price. My budget is around $40 per month, but only if this money can be put to great use. Also, I noticed in your last prompt that you were considering mobile data usage. My mobile data is fast and unlimited and my phone storage is large. Choose accuracy and speed over mobile data or phone storage always."

- [x] F0 Standing rule (BRIEF.md + CLAUDE.md): accuracy and speed over mobile data and phone storage, always. Remove the Wi-Fi-only gate on Novig's trade history (v0.26.0) and any other data/storage economies that cost accuracy or speed. DONE 2026-09-30: rule in BRIEF.md (locked decisions, first) and CLAUDE.md; `backfillCloses` reads Novig's trade files on any network; grep found no other data/storage gates (the `metered` flags are API-credit budgets, kept).
- [x] F1 Research the four sources (The Odds API ClosingLinesAnyMarket.gs, TheRundown API, the r/ParlayAPI "complete sports betting data stack for 2026" post, DeliciousPipe1326/edge-scanner) plus, added by Tj mid-turn: https://skills.rest/skill/odds-api-historical : what each offers for speed, accuracy (fair odds / devig), grading, historical closing lines / CLV; free tiers, limits, coverage (props?), what Vigilant lacks. RESEARCH.md §43. DONE 2026-09-30: RESEARCH.md §43 (all five incl. skills.rest; Reddit unreadable from here, judged via ParlayAPI itself).
- [x] F2 Implement whatever from them helps (e.g. TheRundown closing lines / scores for grading, edge-scanner's devig or matching ideas, closing-line method), with tests. DONE 2026-09-30: ParlayAPI as a feed (OddsFeed.PARLAY) + ParlayCloses (Pinnacle closes, first in CloseBackfill); nothing to port from edge-scanner/TheRundown/Apps Script (§43). Tests: ParlayClosesTest, TheOddsApiClientTest.
- [x] F3 Research paid APIs worth buying within ~$40/month (only if it's put to great use): what each would add (sharp books incl. Pinnacle, props, historical closes, speed), price, and a recommendation. DONE 2026-09-30: ParlayAPI Starter $5 recommended (§43); TheRundown/SGO/Odds API paid not worth it.
- [x] F4 Sweep, full test, ship, Release link, answer plainly (what each source is, what was built, what to buy or not). DONE 2026-09-30: v0.27.0 (code 55), CI 36659840921 green on 07ce7847, release.yml 36660165667 green, Release confirmed.

## Tj's request 2026-09-30 01:42Z (raw text in INBOX.md): "I will buy the parlay-api $5 per month starter plan to try it out. You can code vigilant to take full use of what the starter plan offers. Make sure it takes full advantage of the paid API and everything it offers, and prioritize its use if it can do anything better than the apis that vigilant already uses. However, in the options, make sure the app can fall back if I don't have the paid parlay-api anymore, and consider if the free API is still worth using for the app. Then after everything is built and completed, do full test protocol on the app to make sure everything works well and is fully optimized. Make sure the features are well coded as designed. Make sure when the app is closed and not in use, it properly sleeps, unless I have the background scanner turned on."

- [x] G1 Take full advantage of ParlayAPI Starter ($5: 20,000 credits/month, 7 days of history, 10,000 req/s): research every Starter-reachable endpoint (odds, props, closing lines, historical closing-odds, scores/results for grading, line movement, Kalshi/prediction markets, Novig's own prices in its feed, alt lines, periods) and use each where it beats what Vigilant already uses (Pinnacle anchor, props, closes, grading). RESEARCH.md §43. DONE 2026-09-30: bulk /props (ParlayPropsSource, 3 credits a league, pages), alternate spreads/totals on /odds, closes via daily file + closing-lines (7 days on Starter), canonical book keys; scores/exchange/SSE judged not better (§43). Tests: ParlayPropsTest, ParlayClosesTest.
- [x] G2 Prioritize ParlayAPI where it's better, with a budget that fits 20,000 credits a month (scan spacing, reserve for closes), and fall back cleanly to the existing APIs (PinnWire, pinnapi, PropLine, The Odds API, ESPN, Novig trades) when the paid key is gone, spent or refused; an option in Settings for it; decide whether the free ParlayAPI tier (1,000 credits, 48 h history) is still worth using and set the app's behaviour for it accordingly. DONE 2026-09-30: merge order parlay before propline; CreditPace (day's share, carry-over, 300 reserve for closes, background auto-scan keeps half the day for Tj, free key = closes only, unknown plan = first call tells); CreditsHeldBackException = quiet standby; Settings/meter text; switch gates closes too; fallback = other feeds unchanged. Tests: CreditPaceTest, ScannerTest paced standby, ScreenshotTest meter line.
- [x] G3 Tests for every new path (Starter use, credit budget, fallback on a missing/spent/refused key, free-tier behaviour). DONE 2026-09-30: floor 1106 (1085 passed, 21 live skipped).
- [x] G4 Full test protocol (`.claude/skills/test-protocols`), fix what it finds, optimized and coded as designed. DONE 2026-09-30: floor 1106 green, -Pscreenshots rendered and read, release APK (R8) built; found+fixed: background auto-scans could spend the day's ParlayAPI share (CreditPaceTest), CLV card copy missing ParlayAPI + nested-paren label (ClosingLineAppTest +2), meter line untested (ScreenshotTest), sleep audit clean (G5).
- [x] G5 When the app is closed and not in use it properly sleeps (no scans, sockets, alarms or workers doing work) unless the background scanner is on; verify with tests. DONE 2026-09-30 (audit, no change needed): with auto-scan Off nothing loops; CNO/widget/rescan/clocks/stream are screen-bound; what still wakes is SettleWorker (3 h, grading + closes, Tj 2026-09-27/30) and the closing alarm before each open bet (Tj 2026-09-29), both bounded by open bets and paused-aware (CnoOnlyAsleepTest, PauseScanningAppTest, AutoScanTest).
- [x] G6 Ship (version bump), Release link, answer plainly. DONE 2026-09-30: https://github.com/tjshea90/novig/releases/tag/v0.27.0

## Tj's request 2026-09-30 ~03:07Z (raw text in INBOX.md): "Review this diagnostic report: VIGILANT DIAGNOSTICS · Sep 29, 11:06:57 PM · Version 0.27.0 (code 55) … [full report in INBOX.md]"

- [x] H1 Read the report against the code: every number that looks wrong, every stat that says SHORT or refused, what can be faster or more accurate; list findings (and per Tj's standing preference, fix the bugs and optimizations found without being asked again, no major changes without approval). DONE 2026-09-30: RESEARCH.md §44 (ParlayAPI held back by an out-of-order reply read as a new cycle; a mid-month plan paced from the 1st; CNO pause reason lost by the rescue; 69 closes final before ParlayAPI; PinnWire SHORT ignoring pinnapi).
- [x] H2 Fix what H1 finds, each with a test that fails before the fix. DONE 2026-09-30: CreditPaceTest (out-of-order, real cycle, first day), BetRecheckTest round note, CnoAgreementTest lastPause, ParlayClosesTest reopen, RunwayTest pinnapi behind PinnWire.
- [x] H3 Floor, ship, Release link, answer plainly (what the report shows, what was wrong, what changed). DONE 2026-09-30: floor 1124 (1103 passed, 21 live skipped), CI 36665243975 + release.yml 36665596308 green, v0.28.0 (code 56).
- [x] H4 (Tj 2026-09-30 ~03:20Z: "Let me know exactly what you need to make sure I'm using parlayapi to its fullest extent but also efficiently and not wasteful, whether that is diagnostics or the API key itself, which I don't mind sharing") Say exactly what's needed; make pasting a key into chat safe first (INBOX.md is committed to a public repo: tools/redact_keys.py in capture_inbox.sh, tools/test_redact_keys.sh). A key shared is used only in the container (never written into the repo), and Tj rotates it after. DONE 2026-09-30: tools/redact_keys.py in capture_inbox.sh (test_redact_keys.sh) so a pasted key never reaches the public INBOX.md; what's needed is in the answer (a second, dedicated ParlayAPI key to verify the unverified shapes, then delete it).
- [x] H5 (Tj 2026-09-30 ~03:25Z: "Look at parlayapi docs and use whatever they have in my starter api that can help the vigilant app") Read ParlayAPI's docs/OpenAPI/Postman examples for every Starter-reachable endpoint not yet used (api-key-check, meta/usage, prediction-markets (Kalshi+Polymarket, 1 credit a league), /odds with kalshi/polymarket/novig books, scores, injuries, maxAgeSec, verification, line-movement, consensus); use what beats the current feeds, with tests; verify shapes with a real key when Tj shares one. DONE 2026-09-30: maxAgeSec on /props, commenceTimeTo on /odds, source-quality guard; prediction-markets/scores/consensus/clv judged not better (§44). Tests: ParlayPropsTest, ParlayAccountTest.
- [x] H6 (Tj 2026-09-30 ~03:35Z: "Also study this: https://parlay-api.com/docs/best-practices Make sure the app follows these best practices. It allows the API key to tell the app how many credits I have left. Add this to the app so the meter is accurate") Read the best-practices page; make ParlayAPI calls follow it; read the key's own credits left (free endpoint) into the meter so it's exact, not counted. DONE 2026-09-30: X-API-Key header, CreditHeaders (X-RateLimit-* + reset), retry 502-504 once, request id in errors, ParlayAccount api-key-check into the meter (scan start, API usage tab, Diagnostics, key added). Tests: ParlayAccountTest 9.

## Tj's request 2026-09-30 04:06Z (raw text in INBOX.md, key masked): "Here is the parlayapi key for you to use: [key redacted …15d0] Make sure the app is making full use of parlayapi's features and speeds. Study the docs if needed"

- [x] I1 With the real key (kept only in the scratchpad, never in the repo): verify every call the app makes (api-key-check, /odds with alternates + commenceTimeTo, /props bulk + maxAgeSec, closing-lines, the closes file, headers incl. X-RateLimit-*), fix field names/shapes to the real answers, measure speed and credits. DONE 2026-09-30: every call probed with the key (scratchpad only); shapes fixed (ParlayMarkets, flat closing-lines, closes-file game lines + 2h snapshot window, NHL props), costs measured (RESEARCH §45). Tests: ParlayClosesTest, ParlayPropsTest, ParlayAccountTest, ParlayBooksTest.
- [x] I2 Study the docs for any Starter feature or speed option not yet used (e.g. /odds books incl. kalshi/polymarket/novig, grouped props, include=verification, event-level calls, compression); use what makes the app faster or more accurate, with tests on recorded real payloads (no key in fixtures). DONE 2026-09-30: include_live, history by plan (/v1/meta/limits), alternates only without a Pinnacle feed, X-Rate-Limit-* spelling; the rest judged in §45 and offered to Tj.
- [x] I3 Floor, ship, Release link, answer plainly; remind Tj the key shared is his main one (…15d0) and how to rotate it. DONE 2026-09-30: v0.29.0 (code 57) released (CI 36671050482, release.yml 36671393817).
- [x] I4 (Tj 2026-09-30 04:18Z: "Make sure the app can read my parlayAPI usage credits remaining because right now in the app it says 20,000 credits left even though it used credits") Real-key finding: credits run on the calendar month (/v1/usage period_start/period_end), api-key-check has credits_total (not read) and only the Stripe billing period. Fix: ParlayAccount reads /v1/usage (credits_remaining/used/total, period_end = reset) + api-key-check (valid/tier); meter shows the exact remaining; CreditPace spreads the remaining pool over the remaining period. Test on trimmed real payloads (no key, no email). UPDATE 04:25Z Tj: "Nevermind it works now" (the meter read right once refreshed). Still doing: /v1/usage for the credits month (reset Oct 1, not the Oct 30 billing date), credits_total first, re-read at most every minute (was 5), the pace over what's left of the month; no meter UI change. DONE: ParlayAccount /v1/usage first (credits_total, period_start/end), key check fallback, 60 s refresh, stale guard; CreditPace over the days left. Tests: ParlayAccountTest, CreditPaceTest.
- [x] I5 (Tj 2026-09-30 04:18Z: "Thoroughly research parlayapi docs to get endpoints and everything matched and all commands and usage correct") Go through every documented endpoint/param the app uses and match paths, params, headers, costs and field names to the docs and the real answers; drop what's wasteful (alternates on /odds: Pinnacle-only, PinnWire/pinnapi already cover them); record in RESEARCH.md §45. DONE: RESEARCH §45.
- [x] I6 (Tj 2026-09-30 ~04:27Z: "Still do the through research of parlayapi docs to make sure the app is using it correctly and to full advantage and if the API offers any other features I may want in the app let me know") Folded into I5 for the checks; also list, in the answer, every ParlayAPI feature not used that Tj may want (report only, build nothing new without his yes). DONE: features listed in §45 and in the answer.

## Tj's request 2026-09-30 ~04:40Z: "For the +ev vigilant scan tab, give me the x option for each bet to remove the bet from the list permanently, even through refreshes and rescans, exactly like the cno section already does"

- [x] J1 Find how the CNO section's X works (what it stores, how it keys a row, how it survives refreshes/rescans/restarts) and reuse the same mechanism for the +EV scan tab's rows (Vigilant scan feed).
- [x] J2 An X on each +EV bet card: tapping it removes that bet from the list and it stays gone across refreshes, rescans and app restarts (same key/lifetime rules as CNO's). Tests (a ViewModel/data test + a UI test that the X is there and hides the row). DONE 2026-09-30: same placed.json record as CNO's ✕ (MainViewModel.hideOpportunity -> markHidden), Undo snackbar, "Show the N bets you removed" with Put back. Tests: FeedRemoveTest (3).
- [x] J3 Ship it with the ParlayAPI work (v0.29.0); answer plainly. DONE: in v0.29.0.

## Tj's request 2026-09-30 ~04:55Z: "When I just tried to get updated odds to see if my bets are EV using the check odds now button, it started and scanned a few then it said crazyninjaodds didn't answer. See if there is a fix to get cno to always respond, or if there is a good backup that does the same exact odds check, I think parlayapi can do this same odds check"

- [x] K1 Find why Check odds now's CNO reads stop ("CrazyNinjaOdds didn't answer"): which call, what error (timeout, 429/one-read-per-N-s gap, Cloudflare), and whether a retry/wait/longer timeout fixes it. Tests. DONE 2026-09-30: CnoBooks.parse returning null (CNO answered, but its game page no longer lists the bet at its line: moved lines, pulled props, soonest games first) counted as a failure; 5 in a row stopped the pass and the toast said CNO didn't answer. Now: no-answer (thrown) vs not-listed (null) told apart, only no-answers stop CNO, each missed bet gets a nowNote why. Tests: BetRecheckTest (pages that answer without the bet…, 19 total).
- [x] K2 A backup for the same check when CNO doesn't answer: re-price the bet from ParlayAPI (every book's price for that game/prop, devigged the same way; Pinnacle first) so each open bet still gets a current EV. Budget: credits per check. Tests on recorded shapes. DONE 2026-09-30: ParlayBooks (one /odds 5 cr + one /props 3 cr per league, kept 2 min) builds a CnoBooksView judged by CnoBooks.check (identical math); used for bets CNO didn't answer or didn't list, and for the rest of the pass once CNO stops; nowVia=parlay ("ParlayAPI's books"). Tests: ParlayBooksTest 3, BetRecheckTest backup test.
- [x] K3 Ship with v0.29.0; answer plainly (what failed, what the fix/backup does). DONE: in v0.29.0.

## Tj's request 2026-09-30 ~05:10Z (raw text in INBOX.md): "Checkpoint everything because I'm going into a new Claude session with no context. On that session I'm going to have Claude build everything you just listed. Save everything you need for a new session to begin building it all"

"Everything you just listed" = the ParlayAPI features offered in the v0.29.0 answer (RESEARCH.md §45 end). **Start by reading PARLAY_API.md
(all of it, §6 is the build guide) and the real answers in `data/src/test/resources/parlay-*.json`.** Every paid call goes through
`parlayPool` (metered, paced) and only while ParlayAPI is on with a key; no ParlayAPI key is in this repo (Tj rotates his; ask him for the
new one only if a live probe is needed, keep it in the scratchpad only). Load the skills CLAUDE.md names before each UI/coroutine change.
Build free ones first; checkpoint after each box; ship as one release (v0.30.0) or two if it runs long.

- [x] M1 Injury tags on prop bets (PARLAY_API.md §6.1): an index of players' injury status from the `injury` object on every `/props` row (free; `ParlayProps.parse` currently ignores it), plus `/v1/sports/{s}/injuries` (1 credit a league, cached 10 min, 5 sports only) for listed/open prop bets not covered. Tag when not Active (Out/IR red, Doubtful/Questionable amber, comment on tap) on +EV prop cards + sheet, CNO prop cards, the widget, and the Tracker's open prop bets. Tests on `parlay-props-with-injury.json` and `parlay-injuries-nfl.json` + a UI test for the tag. DONE 2026-09-30: `/props` rows' `injury` kept free in `InjuryIndex` (players listed without one count as covered), `/injuries` (`ParlayInjuries`, 1 cr, 10 min a sport, only uncovered players, only ParlayAPI on + key) through the new generic metered `TheOddsApiClient.parlayGet`; `InjuryTags` → `UiState.injuries` by item key; `InjuryTag` chip (tap: ESPN's report) on +EV card + sheet, CNO card + sheet, widget row (short tag), Tracker open bets + sheet. Tests: ParlayInjuriesTest 7, InjuryTagTest 5.
- [x] M2 Per-day credit chart (§6.2): `/v1/meta/usage?days=30` (free) → a small bar chart of credits a day and the top endpoints under ParlayAPI's meter in Settings › API usage; read with ParlayAccount's refresh (at most once a minute). Ignore its credits_* fields (read 0). Test on `parlay-meta-usage.json` + UI test. DONE 2026-09-30: `ParlayAccount.refreshHistory` (free, ≤1/min a key, keys added together; only when Settings › API usage is shown, not on every scan) → `UiState.parlayHistory` → `ParlayUsageChart` under ParlayAPI's meter: 30 bars ending today (UTC days), tap a bar for its day, total, peak, top endpoints in words. Tests: ParlayUsageHistoryTest 4, UsageChartTest 4 (+ screenshot 5c).
- [x] M3 Biggest line moves (§6.3): `/v1/meta/movers` (free, Pinnacle moneyline, 90 s cache) for the picked leagues: a "Line moves" card (suggest the Games tab) and a "Pinnacle moved toward/against" note on +EV cards, CNO cards and open Tracker bets whose game moved. Test on `parlay-movers-nfl.json`. DONE 2026-09-30: `ParlayMovers` (public, no key, ≤90 s a league, every 3 min while on screen + ParlayAPI on) → `UiState.movers`; `LineMoves.notes` (moneyline + full-game spread only) → `UiState.lineMoves`; Games tab "Line moves at Pinnacle" card, notes on +EV cards, CNO cards, open Tracker bets. Tests: ParlayMoversTest 4, LineMovesTest 4.
- [x] M4 One-bet verdict (§6.4): a "Second opinion (ParlayAPI, 5 credits)" button in the +EV sheet, CNO bet sheet and Tracker bet sheet → `/v1/verdict` (books=novig, price = the bet's, player prop key reversed from PropStats.parlayMarkets); show verdict, fair, Vigilant's EV at the bet's price, books compared, summary; record the body's `credits.monthly_remaining` into the meter; one retry on a 503 busy. Tests on `parlay-verdict-*.json`. DONE 2026-09-30: `ParlayVerdicts` (/verdict via parlayGet: 5 cr, body credits into the meter, one retry 2 s after a busy 503), `VerdictQueries` for +EV opps, CNO rows, Tracker bets (team by full name; prop key from the /props board else canonical); `SecondOpinion` in the +EV sheet, CNO sheet, open not-started Tracker bets' sheet via `LocalOpinions` (only when ParlayAPI on + key); shows verdict, fair, Vigilant's EV at the price, best/books/confidence/movement, summary. Tests: ParlayVerdictTest 4, SecondOpinionTest 6.
- [x] M5 ParlayAPI's own +EV list at Novig (§6.5): `/best-bets?books=novig` (10 credits a league, props only) as a third scanner beside CNO and Vigilant (tap/pull only, never on a timer unless Tj asks), each play re-priced from Novig's own book before it's shown (the sample had Novig +2122 vs fair +900), `edge_alerts` shown as "verify first", ✓/✕/Open in Novig/Tracker logging like CNO's. Parse the `bet` text. Tests on `parlay-best-bets-*.json`. DONE 2026-09-30: +EV tab section "ParlayAPI's picks at Novig" (button only: 10 cr × leagues with props; Recheck = Novig only, free); plays → CNO-shaped rows → NovigBetFinder (outcome + start) → NovigLive.readNow (Novig's book): shown only +EV at Novig now vs ParlayAPI's fair, in EV range/odds cap (capped ones counted)/window, not already marked; alerts "Verify first" with fair from their pp edge; ✓/✕ with Undo (placed.json `parlay:` keys), Tracker source `parlay` + Tracker filter; injury tags too. Not in the widget. Tests: ParlayBestBetsTest 4, ParlayPicksTest 4 (+ screenshot 1g).
- [x] M6 Line-movement chart (§6.6): **first probe `/line-movement` once for a live prop (2 credits; it 503'd three times and is charged anyway) and keep the answer as a sample**; then a tap-only chart in bet sheets (≤ 6 h window, one retry after retry_after_seconds, then "busy"). If it never answers, tell Tj and leave it out. LEFT OUT 2026-09-30 after probing with Tj's key (told Tj): 6 h window → 503 again (charged 2); 1 h window answers, but only pick'em apps (Underdog, Pick6), no sportsbook (a `bookmaker=caesars` lookup came back empty). Not worth a chart or the credits; Pinnacle's moves come free from M3. Sample `parlay-line-movement-prop.json`, PARLAY_API.md §6.6.
- [x] M7 Period lines from more books (§6.7): `/live/period_markets?period=1H` (2 credits a league) as a ReferenceSource for Novig's 1st-half (and baseball first-5) markets, pairing each book's two sides; check MLB/NHL period keys against Novig's catalog first (one probe each, keep samples). Tests on `parlay-period-markets-nfl-1h.json`. DONE 2026-09-30 for football + basketball: `ParlayPeriodSource` (`parlay_1h`, 2 cr a league, only when Novig lists a SPREAD_1H/TOTAL_1H in the league and the 1st-half family is on; registered in SOURCE_ORDER + enabledSources), each book's sides paired (home +x / away −x, over/under), timed by own age. MLB added after M9's probe: ParlayAPI calls the first 5 innings `F5` (Pinnacle only; asked only while no Pinnacle feed is on); NHL answers P1–P3, which Novig doesn't list: left out. Freshness fixed to ParlayAPI's `observed_age_seconds` (Pinnacle's 1H price unchanged 5.4 h but seen 9 s before was wrongly dropped). Tests: ParlayPeriodsTest 3 (incl. planner prices Novig's 1H spread/total from the real sample).
- [x] M8 Sweep (Tj's standing rule: bugs, UI, efficiency), full floor, ship, Release link, answer plainly: what each feature does, where it is, what it costs in credits. DONE 2026-09-30: sweep fixes (a fresh no-report answer clears an old injury; second-opinion local remembered; Diagnostics line for the extras), full floor 1170 green, v0.30.0 (code 58) released and recorded: https://github.com/tjshea90/novig/releases/tag/v0.30.0
- [x] M9 Key-gated probes (this session had no ParlayAPI key; Tj rotated it): with his new key (scratchpad only, never a repo file) probe once each and keep trimmed keyless samples: `/line-movement` for a live prop (2 cr; then build M6 or tell Tj it's unreliable), `/live/period_markets?period=1H` for MLB and NHL (2 cr each; add them to `ParlayPeriodSource.SPORTS` only if "1H" is the first 5 innings / a period Novig lists), `/verdict` with a canonical prop key such as `player_pass_attempts` (5 cr; if it answers, `ParlayMarketKeys` fallback is proven). ~11 credits in all. DONE 2026-09-30 (15 credits): line-movement (→ M6 left out), MLB F5 / NHL P1–P3 (→ M7), /verdict canonical key answers (→ ParlayMarketKeys fallback proven; its fair can be Novig's own: flagged on the card). Samples kept keyless; the key lives only in the session scratchpad. Tests: ParlayPeriodsTest 4, ParlayVerdictTest, SecondOpinionTest 7.

## Tj's request 2026-09-30 ~06:50Z (raw text in INBOX.md): "Schedule an automatic full tests protocol on this app 4 hours from now. It should start and finish the full tests automatically with no input from me. First, change the check odds now feature that shows me stats on the percent of my bets that are positive EV and beat clv In the tests to always scan relevant vigilant odds in addition to the cno scan. The goal is to always get full updates on all of my bets and an accurate stats reading. Make sure the apis are being used to their full potential, especially my paid parlayapi. Research API docs and make sure the app is well tuned to use the apis, especially parlayapi and novig API. Make sure the app functions as designed, with efficient code and optimized for my moto g 2026 with unlimited fast mobile data."

- [x] O1 Check odds now always prices every open bet from Vigilant's own odds too (not only CNO's page for CNO bets): each bet gets CNO's read AND Vigilant's (all relevant sources, ParlayAPI included), so every bet is updated and the +EV % / beat-CLV % stats read from the fullest data. Tests. DONE 2026-09-30: `OpenBetPricer.run(alongside = true)` over EVERY open bet still to start beside CNO's page reads; each read kept (`TrackedBet.cnoFair/cnoAtMs`, `vigFair/vigAtMs/vigBooks`); `BetTracker.mergeReads`: both → average of the two fair lines (`VIA_BOTH`) as the current EV and the close so far, one → it, neither → why; the closing capture does the same (both reads → averaged close, for beat-the-close); toast/Diagnostics say how many were read both ways; card/sheet show both reads. Tests: BothReadsTest 5, OpenBetPricerTest, TrackerTextTest.
- [x] O2 API tuning pass: re-read ParlayAPI's docs (openapi.json, best practices) and NOVIG_API.md / Novig's docs; make sure Vigilant uses them fully and efficiently (paid ParlayAPI especially: every useful endpoint, batching, freshness, no wasted credits); fix what's found; note it in PARLAY_API.md / NOVIG_API.md. DONE 2026-09-30: best practices + all 214 endpoints + the free cost catalogue re-read; every assumed cost matches; fixed: 503 Retry-After honored (scan retry and busy /verdict); what's not used and why, and Novig's side (fully re-read 09-28/29, NOVIG_API.md §13-15; its open items need Tj's phone), in PARLAY_API.md §6.9. Tests: ParlayRetryAfterTest 4.
- [x] O3 Schedule the automatic full tests 4 h from now (SCHEDULED 2026-09-30 06:43Z for 10:43Z: `send_later` routine trig_01Dv1VFgugYCLYbL4t8dPjzW into this session; tick when that run has shipped): a routine that starts a session, runs `.claude/skills/test-protocols` "Full tests" end to end (incl. functions as designed, efficient code, Moto G 2026, unlimited fast data per CLAUDE.md), fixes, ships and records the Release, with no input from Tj. DONE: the routine fired at 10:43Z into this session, which built P1/P2/P4, ran the full tests and shipped v0.32.0 with no input from Tj.

## Tj's request 2026-09-30 ~07:25Z (raw text in INBOX.md): do at the START of the scheduled 10:43Z full-tests session, NOT before: "For the "parlayapi's picks at novig" section, add a button where I can bet each bet inside the app using the same logic as the other parts of the app such as the cno scanner where I can bet inside the app using my novig API. Then, next to parlayapi's percent positive EV number in that section, put cno/vigilant's percentage so I can compare and see if it is truly positive EV on each bet. After this is done, run the full tests protocol exactly how I already explained that was scheduled for this session."

- [x] P1 ParlayAPI's picks cards get the in-app Bet button (Novig API betting), the same logic as CNO's cards: `ApiBetButton` + `LocalApiBet` (`ApiBetActions.betCno(row)` takes the pick's CNO-shaped row at Novig's price now, `ParlayPick.row`), same guards and Bet sheet (`ApiBettingController`, `ApiBetTargets`), logged to the Tracker as source `parlay`. Read the real-money rules first (test-protocols "Betting through Novig's API", NOVIG_API.md §14-15). Tests (UI + targets). DONE 2026-09-30 ~11:10Z: `ApiBetActions.betParlay` → `ApiBettingController.bet(pick)` (CNO's own `betRow`: exact Novig outcome, Bet sheet, planner guards; ParlayAPI's fair, as old as its board read), `ApiBetTargets.of(pick, …)` source `parlay`, key `parlay:…`. Tests: ApiBetTargetsTest (ParlayAPI pick target), ParlayPicksTest P1 ×2 (Bet button bets that pick; none without betting set up), screenshot 1g.
- [x] P2 Each ParlayAPI pick card shows, next to its EV badge (ParlayAPI's fair vs Novig's price now), CNO's EV and Vigilant's own EV for the same Novig outcome, so Tj can see whether it's truly +EV: Vigilant's from the last scan's opportunity for that outcome, else a bets-only pricing pass over the picks (`OpenBetPricer`/bets-only `Scanner` on their Novig market ids from `NovigBetFinder`); CNO's from CNO's list row for the same outcome, else its game page (`CnoBooks.check`, CNO's pace). Say "—" with why when one has no line. Tests. DONE 2026-09-30: `ParlayCompare` (CNO: its list row for the same bet by `PlacedIndex.identity` + same-game window, fresh list only; Vigilant: last scan's same outcome (live price now carries Novig ids) or `OpenBetPricer.fairs`, a bets-only read of the shown picks after each Scan/Recheck, nothing written), both at Novig's price now; "—" + why. CNO's game page is reachable only through a CNO row, so its books' worst case shows as "Books" in the P4 sheet. Tests: ParlayCompareTest 6, OpenBetPricerTest fairs ×2, ParlayPicksTest P2, screenshot 1g.
- [x] P4 (Tj, 2026-09-30 ~07:15Z, raw text in INBOX.md; also before the full tests) "Make it so in the parlayapi pick section, if I click on a bet, it opens a screen that shows other sports books odds on the same bet, exactly how other sections of this app such as cno scanner do it": tapping a ParlayAPI pick card opens a bet sheet like CNO's (`CnoSheet`/`CnoDetail`: every book's price for that bet and the other side, per-book EV, Vigilant's worst-case verdict, Open in Novig, ✓) instead of going straight to Novig; the books from CNO's game page when CNO lists the same bet, else ParlayAPI's own books for it (`ParlayBooks`-style: `/props` + `/odds` shaped as `CnoBooksView`, judged by `CnoBooks.check`). Keep the card's Open in Novig button. Tests (UI: tapping a pick opens the sheet with the books). DONE 2026-09-30: `ParlayPickSheet`/`ParlayPickDetail` (three EVs + Books, CNO's `VerdictCard` + `BookTable`, Bet, Open in Novig, I placed it, Re-read books, ParlayAPI's second opinion); `MainViewModel.loadPickBooks` (CNO's page when CNO lists it, else/fallback `ParlayBooks.view(league, event, start, market, bet)`). Tests: ParlayPicksTest P4 ×3, ParlayBooksTest (non-Tracker bet), screenshot 1m.
- [x] P3 Then the full tests protocol (O3's scheduled run, exactly as set: test-protocols "Full tests" end to end, ship, Release link). FULL TESTS RUN 2026-09-30 ~11:20-12:00Z (the scheduled session): floor 1217 green (1196 passed, 21 live skipped), all 91 screenshots looked at (MGM dormant, skipped); sweep of v0.30/0.31/P1-P4 code, CNO/Vigilant both-reads merge and closing capture (sound), ParlayAPI month-end pace (last day: all but the 300 reserve spendable, sound), API bets on picks hide their card (sound). Fixed: ParlayAPI movers read only on screen and at once on return (was a 3-min timer in the background and up to 3 min stale on return; `refreshWhileOnScreen`, ScreenPacingTest 2), Vigilant's pick read re-asked picks read <2 min ago (`vigilantAsks`, ParlayPicksTest), a dropped pick's sheet could pop open by itself on a later read (ParlayPicksTest), pick sheet recomputed its books per redraw (remembered), ParlayAPI usage text (pick books + Vigilant read), pick sheet price row wrapped, ParlayAPI badge labelled, screenshot 1m. Each fix's test confirmed failing on the old code. Regression 1221 green (1200 passed, 21 skipped, exit 0, no FAILED in the log). SHIPPED v0.32.0 (code 60): CI 36707885476 + release.yml 36708419706 green, Release + APK confirmed.

## Tj's request 2026-09-30 ~14:35Z (raw text in INBOX.md): "When I'm using the parlayapi picks section and I click on a bet to see the current odds from different sports books, most of the time it says parlayapi couldn't find other sports books with this bet. Is there a backup fall back provider or api that can be used so that I always see other sports books odds for any bet when I click on it"

- [x] Q1 Find why a ParlayAPI pick's sheet so often says "ParlayAPI has no other book pricing this exact bet" (ParlayAPI's own list says 3+ books compared each pick) and fix the cause. DONE 2026-09-30 ~15:10Z (live, Tj's key, ~15 credits): the scans' readers drop one-sided lines and HR/anytime-TD props are one-sided at most books (MLB HR: 138 of 146 rows the scan's request got; NFL anytime TD: every book "Yes" only), and the books pricing both sides (bet365, Hard Rock, Fliff, betPARX) weren't asked for. Details in PARLAY_API.md §6.5. Test: OtherBooksTest (real rows fixture parlay-props-hr-books.json).
- [x] Q2 Fall back through every other odds source Vigilant has (PropLine, The Odds API, Pinnacle via PinnWire/pinnapi, Kalshi/Polymarket, and Vigilant's own read of the pick) so a tapped pick shows other books' odds whenever any source has the bet; one-sided books shown too (as CNO's page does); say which source(s) the books came from, and why when truly none has it. Tests. DONE: `OtherBooks` (display only): ParlayAPI /props for that market at every real book + PropLine's game board side by side, The Odds API only when neither has another book; one line per book; older prices apart with age, never counted; Novig's live price as the judged row; sheet says "from ParlayAPI + PropLine"; busy board retried once. The unused ParlayAPI-only row path removed. Tests: OtherBooksTest 10, ParlayPicksTest P4 (sources, Novig row, older list).
- [x] Q3 Ship, send Tj the link and a plain answer. SHIPPED v0.33.0 (code 61): CI 36733884668 + release.yml 36734558692 green, Release + APK confirmed.

## Tj's request 2026-09-30 ~15:01Z (raw text in INBOX.md): "Also when I press a notification and the app opens full screen, that notification should be removed"

- [x] R1 Every Vigilant notification that opens the app when tapped (+EV alerts, scan done, auto-scan's, any other) is removed once tapped and the app is open full screen; the ongoing service notes that must stay while a service runs are handled as Android requires (they can't be swiped away while running) and say so. Tests. DONE 2026-09-30 ~15:10Z: the +EV alert was the one that stayed (setAutoCancel(false) by design, so ✓ Placed stayed after opening Novig): now auto-cancels on tap. Scan done and auto-scan paused already did. The scan-in-progress and auto-scan-running notes are foreground-service notes Android keeps while they run. Test: AutoScanTest (a tap removes the alert).
- [x] R2 Ship with Q1-Q2 as one release, send Tj the link. SHIPPED v0.33.0 (code 61): CI 36733884668 + release.yml 36734558692 green, Release + APK confirmed.

## Tj's request 2026-09-30 ~15:20Z (raw text in INBOX.md): "Two changes: 1) when I click on anything in the push notifications for vigilant, instead of opening the bet, it opens the vigilant app in full screen 2) when I press check odds now to get updated EV and stats in the bet tracker, all the vigilant results show stale odds and aren't refreshed. When I press this button I want every single open bet refreshed regardless of what scanner found the bet, so that I can see the current odds and positive EV for every open bet I have in the tracker"

- [x] T1 Tapping any Vigilant notification opens Vigilant full screen (out of the mini window too), never Novig's bet slip; +EV alerts included; the notification is removed (v0.33.0). Buttons (✓ Placed, Undo, Scan now, Stop) keep doing their job. Tests. DONE: +EV alerts (the only ones that opened Novig) and the "Tracked ✓" confirmation (tapping did nothing) now open Vigilant (`EvAlerts.openVigilant`: MainActivity, NEW_TASK|SINGLE_TOP|CLEAR_TOP, which also brings it out of the mini window); scan done, auto-scan and paused already did; copy updated (channel, Settings alert hint); the Novig-link intent removed. Tests: AutoScanTest (alert tap opens Vigilant, not Novig; confirmation tap opens Vigilant; a tap removes the alert).
- [x] T2 Find why Check odds now leaves Vigilant-found bets with stale odds (not refreshed) and fix it: every open bet, whatever scanner found it (Vigilant, CNO, ParlayAPI, API bets, imported), gets current odds and current EV on each tap, games under way included where a price exists; say why for any bet that truly can't be priced. Tests that fail on the old code. DONE (live-measured with Tj's ParlayAPI key and real Novig prop markets: 6 of 15 refreshed before, 10 after; the other 5 are lines no book prices): (a) games under way were never re-priced for bets with no CNO page (CNO's are read up to 4 h in): now priced (live odds; never written as the close); (b) the scans asked ParlayAPI for 6 books and kept two-sided lines only, so most home-run/strikeout/outs lines had no price: book list widened to every valid real sportsbook (bet365, BetMGM, Fanatics, Hard Rock, Fliff, betPARX…); (c) bets with no CNO page now also get the every-book read CNO's bets get (`BetRecheck.readWithoutPage`: ParlayAPI's books, CNO's check), merged with Vigilant's (both averaged); (d) Vigilant's read replaced nothing in a bet's older every-book list: now it does unless this check's page read just wrote it. Closing capture reads both ways too. Tests: OpenBetPricerTest (live game re-priced), BetTrackerTest, BetRecheckTest (plan + readWithoutPage), BothReadsTest (books refresh), LiveCheckOddsPropsTest (VIGILANT_LIVE).
- [x] T3 Ship, send Tj the link. SHIPPED v0.34.0 (code 62): CI 36737500463 attempt 2 + release.yml 36739656421 green, Release + APK confirmed.
- [x] T4 Harden `NovigPublicClientTest` "with a key, a refused wave is waited out once and every book still comes": it failed once on CI under load (run 36737500463 attempt 1, passed on re-run; 12/12 locally under 6-core CPU load; its third flake after 2026-09-28 and 2026-09-30 04:25Z). Its fake edge refuses "the first 10 distinct books whenever they arrive", not a time-bound wave: make the fake refuse only first attempts that arrive before any retry does, and assert what the test is about (every book comes, none failed, all via the key). DONE: the fake edge refuses first reads only until the client's first retry arrives (one wave, as the edge sends it); asserts 1-10 refused, none failed, all 16 via the key. 8/8 under 6-core CPU load, full NovigPublicClientTest green.

## Tj's question 2026-09-30 (raw text in INBOX.md): "Can this help the app https://apify.com/mrdoe/bet-clv-tracker/api"

- [x] U1 Research the Apify actor "bet-clv-tracker" (what it returns, sources, cost, freshness, reliability) against what Vigilant already has for CLV (closing capture both ways, ParlayAPI Pinnacle closes, ESPN, Novig trades); answer plainly whether it adds anything, and build it only if it does and Tj wants it. ANSWERED 2026-09-30: no. It's a calculator, not a data source: you hand it your bets plus closing-odds datasets from some other scraper actor (none named); it devigs one sharp book (Pinnacle default) proportionally and returns CLV %/beat-the-close. Moneyline and totals only (no spreads, no props), a "close" up to 6 h before kickoff, fuzzy team matching, $0.80/1,000 results on Apify. Vigilant already computes the same from better closes (last read inside 15 min of the start, both reads averaged, worst-case devig, props and spreads included, ParlayAPI's Pinnacle closes/ESPN/Novig trades after the fact). Not built.

## Tj's request 2026-09-30 ~19:30Z (raw text in INBOX.md): "1) can I add more sports books to scan on vigilant either for cno scanner or vigilant scanner? Can parlayapi do it? Would it make the app more accurate? If so, add sports books to each scanner. 2) change it so anywhere in the app where I use the vigilant wallet to place bets in the app, I can type in a custom account for any bet manually. And if I have less than one dollar in the wallet, it automatically enters whatever is left in the wallet as the bet amount 3) make the diagnostics section in settings as smart as possible so that when I output it to Claude, Claude can run deep analysis on the app and know what is working or broken and how to improve the app either in code or ui or scanning or accuracy or function. 4) review the clv stats and positive EV stats for current open bets. Make it so this feature does not count any bets in which the game or bet is currently live. The odds move rapidly when a game is live and this should not skew the EV stats for open bets. Then make sure these sections accurately capture actual positive EV percentages and true line closing values"

- [x] V1 More sportsbooks: find what each scanner can add (CNO: what its view/form lets Vigilant choose; Vigilant: ParlayAPI game lines and props, PropLine, The Odds API, sharp books like Bookmaker.eu), whether it makes fair odds more accurate (sharp vs soft books, coverage), and add the ones that do to each scanner. Answer Tj plainly. Tests. DONE (RESEARCH.md §46): CNO: its form has no book choice (the one book field is where you bet; its fair line comes from ~20-25 books incl. Pinnacle, Circa, ProphetX, Kalshi), so nothing to add. ParlayAPI: props already ask every real sportsbook it has (the rest are pick'em apps); for game lines its BookMaker/Superbet/Betr/Polymarket keys returned no MLB/NFL lines, and the only extras (BetRivers, Hard Rock, Fliff, betPARX) are soft, would double game-line credits (3 → 6 a league) and BetRivers/betPARX are one Kambi line (94% identical). Changed, free: reference books add Hard Rock, Bovada, Fliff (PropLine reads them at no cost; 8 → 10 independent books) and drop LowVig (BetOnline's twin) where BetOnline is picked (schema 11 migration); picker uncapped (The Odds API still asks 10); "ESPN BET" → theScore Bet. Tests: MoreBooksTest.
- [x] V2 Every Bet sheet (Vigilant wallet, Novig API) takes a typed custom amount for any bet ("account" = amount); when the wallet holds less than the starting amount (under $1 included), the amount starts at whatever is left in the wallet. Tests. DONE: one Bet sheet serves every Bet button (+EV cards, CNO cards, ParlayAPI picks), and it now has an Amount field (`StakeField`, tag `betAmount`): any dollars-and-cents amount up to the per-bet limit (`BetAmount.parse/problem`; over the limit is said, not sent), priced once typing pauses 400 ms (`ApiBettingController.typeStake`); the chips still work. A sheet opens at Settings' amount, or at what's left in the wallet (rounded down to the cent) when that's less (`BetAmount.starting`), and the wallet is read as the sheet opens (`checkWallet`) so an old balance can't decide it; an amount Tj picks or types is never changed by that read (`BetSheetUi.stakeChosen`). Tests: BetAmountTest, ApiBettingControllerTest (wallet under the amount; picked/typed amount kept; typed priced), ApiBettingUiTest (typing; over the limit; "All that's left in the wallet").
- [x] V3 Diagnostics as smart as possible for Claude's deep analysis: health checks that say PASS/WARN/FAIL with the evidence, each source's recent calls/errors/latency, pricing coverage and why bets aren't priced, data-quality checks on the Tracker, permissions/battery/device, recent errors log, and a "what to look at" list; still never a key. Tests. DONE: Diagnostics opens with a "For Claude" line (repo, layout, no keys) and "Health checks (worst first)": every part judged FAIL/WARN/OK with its evidence [in brackets] and the code or setting that owns it (→): scan age/errors/speed/refusals/lines left too late, game matching %, share of Novig prices judged, each fair-odds source, API keys refused/spent/throttled, runway, ParlayAPI key validity, CNO, background auto-scan (on but service not running, late, errors), phone (notifications, exact alarms, battery, Data Saver, online), open bets' EV now coverage + top reason, grading overdue, true-close coverage last week, capture alarm, duplicate/no-EV/outlier bets, edge accuracy from CLV (lose to the close = FAIL, EV overstated by >2 pts = WARN), results vs edges (±2 SD), wallet under $1 (`HealthChecks`). New blocks: accuracy by scanner, market and edge band (record, ROI, EV when bet, CLV, beat-close %, expected vs actual); open bets' edge now vs when bet (fair moved toward/away, overall and by scanner); Phone; Recent problems saved across restarts (`data/diag/ProblemLog`, problems.json: scan errors, failed sources, CNO errors, auto-scan errors, failure toasts; repeats counted; keys masked). Tests: DiagnosticsTest (6 new), ProblemLogTest.
- [x] V4 Open bets' current-EV stats and CLV stats leave out any bet whose game is live (odds move fast in play); then audit that the +EV % and closing-line value shown are the true ones (EV at the price paid vs fair; close = last pregame fair). Tests. DONE: `CheckOddsStats.of(bets, sinceMs, now)` leaves out every bet whose game has started (`live` count; caption "N live games left out"; Diagnostics line too), recomputed each minute on the Tracker; a bet's own EV line says "live … in-play odds, not in the counter" when read after its start. Audit: EV = devigged fair / cost (fee included) − 1 everywhere (CNO page, Vigilant, ParlayAPI, merged) ✓; CLV = close fair / cost − 1 over pregame closes only (captured ≤15 min before start, else ESPN/Novig trades/ParlayAPI Pinnacle last pregame price) ✓, so a live game's CLV is fixed at the start and can't be skewed by in-play odds. Fixed: a close is now dated by the OLDEST price behind it (`Opportunity.fairAsOfMs`, a CNO page's `fetchedAtMs`, the older of a merged pair), so a stale line saved in the last minutes can't pass for the true close (the capture reads it again); the bet sheet labels a started game's number with no true close "Vs last pregame read", not "CLV so far". Tests: CheckOddsStatsTest (live left out), BothReadsTest (live reads never become the close; close dated by oldest price), BetRecheckTest (old page isn't a close), TrackerTextTest (live EV line), CheckOddsCounterAppTest (caption).
- [x] V5 Ship, send Tj the link. DONE: v0.35.0 (code 63) released 2026-09-30 ~20:55Z, CI green, recorded; the ship gate also caught a real race in `ApiBettingController.placer()` (fixed: made under a lock).


## Tj's v0.35.0 Diagnostics, pasted 2026-09-30 ~21:34Z (raw text in INBOX.md; no words beside it: the V3 purpose applies, "Claude can run deep analysis on the app and know what is working or broken and how to improve the app either in code or ui or scanning or accuracy or function")

- [x] W1 Read the report end to end: what works, what's broken, what the numbers say about accuracy (CNO +1.3% CLV / 68% beat vs Vigilant −1.2% / 37%; totals −2.6% / 22%; closes on 58% of started bets). Answer Tj plainly with the findings and the fixes.
- [x] W2 Fix what the report shows is wrong in the code or in Diagnostics itself (false alarms, wording, missing sample sizes, anything a check got wrong).
- [x] W3 Find why Vigilant's own bets lose to the close (by market and by fair-odds source) and fix what's clearly a bug; bring Tj any strategy change (what Vigilant should stop betting) as a recommendation, not a silent change.
  DONE W1-W3 (RESEARCH.md §47): Diagnostics' false alarms fixed (busy source = WARN/OK, backup source matched nothing = OK, spent key with another = OK and worded right, Novig throttles judged by share and recency, imported ✓ marks out of close coverage and their note fixed); round cost from `KeyUsage.charged` (the "600 credits a scan" was the month's count landing in one round); per-scanner CLV health checks; CLV averages say their n; matching by league with unmatched names; each scanner by market; bets keep `FairBasis` (what made their fair odds) and Diagnostics splits CLV by it; Vigilant's bets against the close listed one by one. Why Vigilant loses to the close can't be proven from this report (bets didn't record their fair's books until now); recommendation to Tj in §47. Tests: DiagnosticsTest (4 new), RunwayTest (charged), BetTrackerTest (fair basis), ParlayClosesTest (import note).
- [x] W5 (offered; Tj said build it 2026-10-01: TASKS.md Z1-Z5) Tennis from ParlayAPI's tour keys (`tennis_atp`/`tennis_wta`, 3 credits a tour): handle Pinnacle's set lines vs the books' game lines per book before it can price anything (RESEARCH.md §47).
- [x] W4 Tests; ship; send Tj the link. DONE: v0.36.0 (code 64) released 2026-09-30 ~22:01Z, CI green, recorded.

## Tj's crash report 2026-09-30 ~22:10Z (raw text in INBOX.md): "The app just crashed a couple times. Both times it was scanning vigilant and I tried to switch tabs, which got very laggy then crashed"

- [x] X1 Find why switching tabs during a Vigilant scan gets laggy and then crashes (main-thread work per streamed result, memory held per scan, what a tab builds on first show) and fix it. Tests.
- [x] X2 Make the next crash say why by itself: keep Android's own record of how the app last ended (crash with its stack, not responding, out of memory) and the stack of any crash the app sees, in Diagnostics. Tests.
  DONE X1-X2 (RESEARCH.md §48): every Novig read during a scan sent a progress tick and a usage-count tick into the screen's state on the main thread, each recomposing the whole app (the tab badges recount the +EV feed and CNO's list every time) and `follow()` rebuilding the feed even for progress-only ticks: ~20-30 full recompositions a second through a 105 s scan. Now `followThrottled` (at most every 350 ms for scans, 1 s for usage; the last state always arrives), the feed rebuilt only when the result changed and off the main thread. `AppExits`: a crash's stack saved as the app goes down (→ Recent problems "App crash"), Android's exit record (crash / not responding with the main thread's stack / low memory) in Diagnostics' "How the app last ended" and an "App stability" check. Tests: ScanMirrorTest, AppExitsTest, DiagnosticsTest (stability), ProblemLogTest (crash time/length).
- [x] X3 Ship, send Tj the link. DONE: v0.36.1 (code 65) released 2026-09-30 ~22:31Z, CI green, recorded.

## Tj's request 2026-10-01 (raw text in INBOX.md): "I set the settings to put the Kelly value in my bet slips within vigilant automatically, but it is still entering only $1 on every bet. Make sure it enters the Kelley value if I select it. Also make sure it is accurately calculating Kelly values when I input my total bankroll and select kelly. The math must be accurate. I think Kelly values change depending on the odds of the bet. Make sure it is all correct. Then see if the code is optional for when I scan odds, because I used the check odds now function and the list of open bets got very laggy. This is not a big problem if it is normal, but other apps don't do this, such as oddsjam. Then run full test protocol to make sure the app functions well and is well optimized and coded."

- [x] Y1 Find why the bet slip amount stays $1 with the Kelly setting on; every bet slip Vigilant fills (the in-app Bet sheet, Novig's slip links, alerts, widget) takes the Kelly stake when Kelly is chosen. Tests.
- [x] Y2 Audit the Kelly math against the textbook formula with fees and the odds of each bet (f* = (b·p − q)/b on the price paid, fraction, bankroll, caps, rounding); fix anything off. Tests with worked numbers.
  DONE Y1-Y2: two causes. Vigilant's own Bet sheet (the wallet) never used the bet-slip Kelly setting: it opened at "Amount a bet starts at" ($1). Now `BetAmount.base`: with Kelly chosen it opens at that bet's Kelly stake to the cent (the +EV card's `suggestedStake`; CNO and ParlayAPI picks via `cnoStake`), held to the per-bet limit, the wallet's remainder when that's less, and says where the amount came from; "My amount" likewise. And Novig's slip links floored every Kelly stake at $1 (`NovigLinks.stake`): now the Kelly amount to the cent. The math was right: full Kelly (p − c)/(1 − c) = (b·p − q)/b for the price paid with its fee, × the Kelly fraction × bankroll, capped at what Novig has at +EV; worked examples (+100 $25.00, +233 $17.86, −300 $50.00, 1¢ fee $20.41 on $1,000 at ¼ Kelly) in EvMathTest. Diagnostics shows bankroll, Kelly fraction and the slip amount. Tests: EvMathTest, NovigLinksTest, BetAmountTest, ApiBettingControllerTest.
- [x] Y3 Make Check odds now smooth: find what makes the open bets list lag while it runs (saves per batch, work on the main thread, list rebuilds) and fix it. Tests.
  DONE Y3 (the same disease as the mid-scan crash, RESEARCH.md §48): each bet read during a Check odds now sent "n of N read" into the screen state (~500 a check) and each save of 5 bets rebuilt the placed index and the feed on the main thread; every one recomposed the whole app and the open-bets list's counts, filters and sort. Now the progress shows at most every 300 ms (always at the end) and the Tracker's saves reach the screen at most every 300 ms (`followThrottled` takes any Flow), the index and feed rebuilt off the main thread. Tests: ScanMirrorTest (saves), the app suite.
- [x] Y4 Full test protocol (.claude/skills/test-protocols): automated floor + screenshots, sweep every tab and subsystem, fix what's found.
  DONE Y4: floor with screenshots green (1,279 tests: 1,257 passed, 22 live-only skipped, exit 0); all 91 screenshots looked at, no visual defects. Sweep found two optimisation gaps and one stale hint, all fixed: the tab bar's +EV and CNO badges re-filtered the feed and re-screened CNO's whole list (with each pick's books) on every state the app got, now remembered on their inputs (`TabIconWithCount`); the CNO tab screened its list three times per recomposition, now once per change of snapshot/settings/placed/index/links/books/live prices/clock (`CnoScreen`); the wallet's "Amount a bet starts at" hint now says Kelly or My amount wins when chosen. ParlayBooks already shares one answer per league per pass. Tests: RecompositionCostTest (source pins), ApiBettingUiTest (Kelly note shown, dropped once another amount is picked).
- [x] Y5 Ship, send Tj the link.
  DONE Y5: v0.36.2 (code 66) shipped: ship.sh gate green (1,279 tests), CI green on 2c501b03, release.yml green, Release confirmed with vigilant-v0.36.2.apk.

## Tj's request 2026-10-01 (raw text in INBOX.md): "Build tennis through parlayapi"

The offer it answers (W5, RESEARCH.md §47): ParlayAPI's tour keys `tennis_atp`/`tennis_wta` price Novig's tennis, but its Pinnacle rows put
SET lines (±1.5 sets, 2.5 sets) in the match event and GAME lines in a separate "Name (Games)" event, while bet365/Caesars put game lines in
the match event: a sets spread must never price Novig's games spread.

- [x] Z1 Read real ParlayAPI tennis answers (both tours, every market it has); save trimmed keyless fixtures; write the shapes per book in PARLAY_API.md.
  DONE Z1 (10 credits): PARLAY_API.md §6.11. Pinnacle's match event = SET lines, its "(Games)" twin = games lines; other books' match event = games (BetMGM/DK use ±1.5 for games, so it's per book); 28/157 matches split over events (FanDuel/ProphetX start times up to 3 h off); doubles mixed in; no tennis team totals; Pinnacle alternates are set lines only. Novig lists SET_SPREAD and TOTAL_SETS (unpriced until now). Fixtures `parlay-tennis-atp.json`, `parlay-tennis-wta.json`.
- [x] Z2 Data: ATP/WTA scanned through ParlayAPI's tour keys; each book's lines classed as sets or games (the "(Games)" events, line sizes) so only games lines price Novig's games markets and set lines price nothing they don't match; tests from the fixtures, incl. the fake-edge case.
  DONE Z2: `data/reference/ParlayTennis.kt` (doubles dropped; the "(Games)" twin and FanDuel/ProphetX's own listings merged by the two players within the tour's start gap; a book's spread/total in the match event is SETS when its total is under 6, or with no total when the book is Pinnacle, else games; impossible lines dropped) runs on every ParlayAPI tennis /odds answer; `TheOddsApiClient.supports` takes ATP/WTA on ParlayAPI only, 3 credits a tour (no alternates: they're Pinnacle's set lines again); `RefBookMarket.PERIOD_SETS`; Planner prices Novig's SET_SPREAD and TOTAL_SETS (in the Spread and Total families) from set lines only. Tests: ParlayTennisTest (7, real fixtures, incl. Pinnacle's +1.5 sets never pricing BetMGM's +1.5 games). Live (`LiveParlayTennisTest`, 6 credits, 2026-10-01 ~02:00Z): 63 of 64 Novig matches paired; lines with a fair price MONEY 63, SPREAD 50, TOTAL 60, SET_SPREAD 41, TOTAL_SETS 36; median |EV| per kind 1.7-4.0% (a unit mix-up would be 20+).
- [x] Z3 The same tennis prices for Check odds now (`ParlayBooks`) and closing lines where ParlayAPI already closes other sports; credits counted; tests.
  DONE Z3: `ParlayBooks` reads ATP/WTA bets (3 credits a tour, kept 2 min), each in its own unit (games `period` 0, sets `PERIOD_SETS`), tennis' one-day start gap. Closes: ParlayAPI's closes file has the same split (2 credits to read); a games-spread bet was closing at Pinnacle's SETS line (38% vs 52% on the sample: a real pre-existing CLV bug), now each tennis bet closes from rows in its own unit, and set bets are looked up. Tests: ParlayBooksTest (tennis units), ParlayClosesTest (2 tennis tests; mutation-checked: without the filter the games close reads 0.382), RESEARCH.md §49, PARLAY_API.md §6.11.
- [x] Z4 Full app tests for what changed (light protocol + screenshots of anything shown), Diagnostics' matching counts tennis.
  DONE Z4 (light protocol): data + app suites green; who-else check: the Spread/Total families' new types only add two cheap types to the catalog read, `PERIOD_SETS` is matched exactly by `LineKey.matches` (never folds into games), Pinnacle from PinnWire and ParlayAPI is still one book per line (`Pricing` distinctBy book), ParlayAPI's movers skip non-full-game lines. Diagnostics' game matching counts tennis as matched now (its hint names ParlayAPI). New screenshot 5h_settings_fair_parlay_on (ParlayAPI row with a key: tennis 3 a tour) looked at; Kalshi's row now says it prices tennis winners (stale copy). Screenshots can't show the live network: the live check is LiveParlayTennisTest.
- [x] Z5 Ship, send Tj the link.
  DONE Z5: v0.37.0 (code 67) shipped: ship.sh gate green (1,291 tests: 1,268 passed, 23 live-only skipped), CI green on c94e2078, release.yml green, Release confirmed with vigilant-v0.37.0.apk. ParlayAPI credits spent building it: 18 (10 probe, 6 live check, 2 closes file).


## Tj's request 2026-10-01 (raw text in INBOX.md): "For this app, for the cno scanner background auto-scan feature, add to the settings options for it to scan every 3 minutes, 1 minute, 30 seconds, and 15 seconds. Make sure the app is properly tracking clv based on real closing lines and the actual odds I placed the bet at."

Findings before any code (2026-10-01): the interval is `ScanSettings.autoScanMinutes` (whole minutes, 5-40) behind `AutoScanClock` (30 s minimum gap) and an
exact alarm armed when a cycle STARTS; an alarm that fires while a cycle is still running is dropped and nothing re-arms it, so a cycle longer than the
interval (certain at 15 s) would end the schedule. "CNO + Vigilant" runs Vigilant's whole scan (~100 s, API credits) inside each cycle. CLV (`ClosingLine`):
cost = price paid + fee ✓, ✓-marked bets log Novig's live price shown (correctable in the Tracker), API fills exact ✓, hand-placed Novig-app bets can't be
read by the API (NOVIG_API.md §14.2); but a bet placed in the last 15 min with no read counts as closing at its own line (CLV = its EV at bet: not a real
close), and the one pre-start read is taken ~6 min before the start.

- [x] AA1 Settings › Background auto-scan › Every: add 15 sec, 30 sec, 1 min, 3 min beside 5/10/20/30/40 min. The interval is kept in seconds (a saved file's minutes move over once, nothing lost) and every place that says it (notification, Settings hint, Diagnostics, health checks) reads it right. Tests.
- [x] AA2 Make the fast cycles safe: the schedule re-arms when a cycle outlasts its interval; the minimum gap drops under 15 s; Vigilant's own scan (API credits) never starts more than every 4 minutes inside a fast cycle; the Tracker's closing reads of bets about to start use the cycle's pace. Tests.
- [x] AA3 CLV audit, end to end, with worked numbers: the price used is the price paid (fills, ✓ at the live price shown, a correction in the Tracker, the fee), and a close is only ever a real pregame line: a bet with no read is no longer "closed at its own price"; the closing read nearest the start wins (a second read ~100 s before the start). Tests, RESEARCH.md §50.
- [x] AA4 Full app tests for what changed (light protocol + screenshot of the Settings row).
- [x] AA5 Ship, send Tj the link.
  DONE AA1: `ScanSettings.autoScanSeconds` (15 s, 30 s, 1, 3, 5, 10, 20, 30, 40 min), schema 12 moves a saved file's minutes over once; notification title ("every 15 sec", seconds in "Next at"), Settings hint (reads/day, credits), Diagnostics, health checks (late after max(3 intervals, 3 min)) say it right. Tests: AutoScanTest (choices, labels, migration incl. a later pick sticking, hint/notification wording), ScreenshotTest 5d + new 5d2 (15 sec), MiniWindowTest/MoreBooksTest/AltMarketsTest (schema 12).
  DONE AA2 (RESEARCH.md §50): found a real latent bug: an alarm that fired while a cycle ran was dropped and nothing re-armed it, so any cycle longer than its interval ended the schedule; the cycle's end now arms the next one (from the live interval, never while stopping). Min gap 30 s → 5 s. Vigilant's own scan (credits, ~100 s) starts at most every 4 min inside fast cycles (`AutoScanClock.vigilantDue`; Scan now forces it); bets in their last 15 min re-read at the cycle's pace, once a minute each at most (`closingFreshMs`). Tests: AutoScanTest (clock, vigilantDue, closingFreshMs, service source pins: re-arm, stopping flag, forceVigilant).
  DONE AA3 (RESEARCH.md §50): price used = price paid, pinned with worked numbers (ClvPlacedPriceTest: ✓ at +141, a corrected price, API fills + fee, live bets, averages); hand-placed Novig-app bets can't be read by the API (NOVIG_API.md §14.2) so they stay at the ✓ price unless Tj corrects it. FIXED: a bet placed in the last 15 min with no read "closed at its own line" (CLV = its own EV at bet, so every +EV bet beat the close): now no close until the back-fill finds a real one. FIXED: close = the read nearest the start (a second read ~110 s before the start, `needsFinalRead`; an older read never replaces a fresher close, `supersedes`). Tests: ClosingLineTest (final read schedule, no at-bet close), BothReadsTest (older read never replaces a fresher close, two paths), ClvPlacedPriceTest.
  DONE AA4 (light protocol): floor with screenshots 1,307 tests (1,284 passed, 23 live-only skipped, exit 0) after bumping three schema pins; screenshots 5d, 5d2, 4j looked at. Who-else: `closeOf`/`clv` readers (bet sheet, HealthChecks, Diagnostics) only see a bet with no close where it used to see an at-bet one; nothing else read `autoScanMinutes`. Not shown by screenshots: the service/alarm on a phone (a 15 s schedule holding with the screen off is unverified), notifications.
  DONE AA5: v0.38.0 (code 68) shipped: ship.sh gate green (1,308 tests: 1,285 passed, 23 live-only skipped), CI green on 687eeb9c, release.yml green, Release confirmed with vigilant-v0.38.0.apk, recorded in BUILDLOG.md. release.yml's notes now say "every 15 seconds to 40 minutes" (v0.38.0's own notes still say 5-40: GitHub's text for that Release was already built).

## Tj's request 2026-10-01 ~05:30Z (raw text in INBOX.md): "Build the auto get feature, which would automatically bet each bet without me doing anything at all, including automatic bets in the background as the cno scanner is on in the background. The option is off by default, but I can turn it on in the settings and choose the following criteria in the options: 1) number of books agreeing- 2, 3, 4, 5+ 2) minimum ev- +2%, +2.5, +3, +3.25, +3.5, +3.75, +4, plus an option to manually type in an amount 3) cno scanner only 4) option for automatically entering ⅛ Kelly stake ¼ Kelly stake, ½ Kelly stake, $1 stake , or a manual amount i type in 5) option to require at least one, two, or three books to offer both sides of a bet 6) maximum stake amount per bet that I can type in manually 7) use the same options for cno scanner refresh time intervals. The feature must be aware of the amount of money I have left in the vigilant wallet and stop placing bets when there is no more money left. The feature should add all bets placed into the tracker system just as if I were to manually bet it. It should only bet on pre game odds, live betting is not available"

How each point is read (written before the code, so a wrong reading is caught): "auto get" = auto-bet. It places through the betting API already built (v0.21.0: Vigilant subaccount wallet, `ApiBetPlacer`, every `ApiBetPlanner` check), so it needs "Enable betting" set up. 1) = `CnoBooks.Check.agreeing` (two-sided books whose own worst-case fair alone makes the price +EV) at least 2/3/4/5; 5) = `Check.twoSided` at least 1/2/3. 2) = the EV the CNO card shows at Novig's live price (the planner re-checks it at the real book price). 3) = bets from the CNO scanner only, never Vigilant's own scan or ParlayAPI picks. 4)/6) the stake before the cap; the per-bet cap is its own typed amount. 7) = it runs inside the background CNO auto-scan cycle, on the same "Every 15 sec … 40 min" choice (shown inside the auto-bet card and the same setting, not a second timer). Pregame only; the wallet's balance is read before every bet and caps it; stops when it can't fund a $1 bet.

- [x] AB1 Data: `ScanSettings` auto-bet fields (off by default: on, books agreeing, minimum EV, books offering both sides, stake mode + typed amount, max stake per bet, a halt reason), pure `AutoBet` rules (the criteria test on a CNO pick + its books check, the stake: ⅛/¼/½ Kelly via the existing Kelly math with the bankroll, $1, typed; capped by the per-bet cap and the wallet; minimum $1) and the placer's `placeAuto` (same lock as the Bet sheet; limits override: Tj's max stake, his minimum EV). Tests with worked numbers.
- [x] AB2 App: `AutoBettor` in the background cycle after the CNO reads (CNO's books read for every bet at or over the lowest of the alert and auto-bet minimums, then bets best EV first, one order at a time): wallet read before each bet, stop when empty / daily limit / location / key problems, an order whose answer is lost HALTS auto-bet until Tj resumes (never re-sent), each placed bet hidden from the lists and logged to the Tracker exactly like a Bet-sheet bet, a notification for every bet placed and once for each stop; alerts computed after it so a placed bet doesn't also alert. Tests against a fake Novig.
- [x] AB3 Settings › Betting: the Auto-bet card (switch with a confirm, the criteria, the interval chips = the background CNO scan's, wallet balance, status of the last check, resume after a halt), hints that say what runs; Diagnostics line + health check. Tests + screenshots.
- [x] AB4 Full app tests (light protocol; this is real money so read the diff adversarially) + RESEARCH.md §51.
- [x] AB5 Ship, send Tj the link. DONE with AC4: released as v0.39.0 (release.yml run 36825708662 green on `ca12c25a`, asset vigilant-v0.39.0.apk, recorded in BUILDLOG.md).
  DONE AB1: `ScanSettings` autoBet* fields (off by default; careful defaults: 3 books, 3%, 2 both-sided, $1, max $10; `autoBetsNow` = on + not halted + CNO scanner running in the background), `AutoBetStake`, `data/novig/trading/AutoBet.kt` (criteria `judge`, `stake` with Kelly worked from each bet's own odds/edge, held to the max, Novig's available and the wallet, floored to the cent, never under $1, `WalletEmpty`; `priceMatches`), `ApiBetPlacer.placeAuto` (the plan's own ceiling, Tj's own limits, the order-book price must match the price judged at: guards a wrong-outcome match; a lock shared with the Bet sheet). Tests: AutoBetTest (10), ApiBettingTest (+6, stable over 5 forced reruns).
  DONE AB2: `AutoBettor` (app/AutoBettor.kt) runs inside `AutoScanner.cycle` after the CNO reads and before the alerts (`cnoRead` reads books at the lowest of the alert and auto-bet minimums, Novig's price now forced on when auto-bet runs; `cnoAlerts` after, so a placed bet doesn't also alert), best edge first, at most 5 a cycle, wallet read each pass and caps each stake, `AlertPicks.cnoChecked` shared with the alerts. Safeguards beyond the planner's: Novig price must be read in the last minute; the book's price for the outcome must match the price judged (±3 pts); Novig's price outcome must equal the outcome found; one bet per Novig market; games under a minute from the start skipped; a refused bet waits 2 min; Novig refusing an order waits 5 min; a lost answer HALTS auto-bet (saved in settings, never re-sent); `markPlaced` shared with the Bet sheet; one `orderLock` shared by every placer; `TrackedBet.auto` + "Auto-bet through Novig's API" note; notifications per bet and per stop. Tests: AutoBettorTest (18) against a fake Novig, MUTATION-CHECKED (each of 11 safeguards removed in turn makes a test fail).
  DONE AB3: Settings › Betting › Auto-bet card (`ui/AutoBetUi.kt`): switch with a plain confirm ("REAL bets … with nobody asking you", the criteria, the wallet, the limits), turning it on switches the background scan Off → CNO; the seven choices (books agreeing 2/3/4/5+, minimum edge +2 … +4% or typed (floor 0.5%), CNO only (fixed), ⅛/¼/½ Kelly / $1 / typed, books pricing both sides 1/2/3, most per bet typed, the check interval = the background CNO scan's own 15 sec … 40 min); why it can't run (betting not set up, Vigilant-only scanner, paused, scan off), the wallet, the last check, Resume after a halt; Diagnostics line + health checks (halt = FAIL); Tracker marks bets "auto-bet through Novig's API". Added after a reviewer pass: a daily-limit stop backs off 5 min; the Kelly confirm names the bankroll; an edge over 15% (`MAX_SANE_EV`, stale/mismatched price) is never bet unattended (mutation-checked). Tests: AutoBetUiTest (14), AutoBetDiagnosticsTest (3), AutoBettorTest (19), screenshots 5k/5k2.
  DONE AB4 (light protocol, read adversarially): floor with screenshots 1,360 tests (1,337 passed, 23 live-only skipped, exit 0); one regression of my own found and fixed (SettingsTabsTest looked for the text "1" on the whole Betting page, which the new card also has: now asks the wallet field); screenshots 5k, 5k2 looked at (a duplicate halt line and a meaningless "0 looked at" fixed). Who-else: `AlertPicks.cno` is now `cnoChecked` + a filter (alert tests unchanged); `ApiBetPlacer`'s new `lock` param is last with a default; `cnoCheck` split with identical behavior when auto-bet is off; `TrackedBet.auto`/`BetTarget.auto` default false. RESEARCH.md §51, BRIEF.md "Auto-bet" (locked rules), the test-protocols skill.


## Tj's v0.38.0 Diagnostics, pasted 2026-10-01 ~05:41Z (raw text in INBOX.md; no words beside it: the V3 purpose applies, "Claude can run deep analysis on the app and know what is working or broken and how to improve")

What it shows (read before any code): 2 FAIL. (1) **App crash 1:40:41 AM: `java.lang.OutOfMemoryError: Failed to allocate a 32 byte allocation with 32112 free bytes … target footprint 268435456` (the 256 MB heap limit) on the MAIN thread, from `ScanService`'s progress path, "on screen"**; settings: no limit on Novig prices / lines per game / props per game, fill the budget, 7 leagues (ATP, WTA, MLB, NCAAF, NFL, NHL, WNBA), 375 tracked bets. (2) Vigilant's own bets lose to the close (CLV −1.1% on 27, 37% beat; totals −2.8% on 10 / 20% beat, team totals and 1st-half totals negative; props +0.3%) while CNO's beat it (+1.5% on 87, 66%), same as the W1 finding. Also: **123 API bets and 21 settled by Novig's ledger, wallet $14.24: live API betting and ledger grading HAVE worked**, which corrects what I told Tj and wrote in RESEARCH.md §51 (no live fill on record); closes 71% of the week's started bets; 105 started bets still looking for a close (Novig's trade file for those days publishes the next morning).

- [x] AC1 Answer plainly: what works, what's broken, what the numbers say (above), and the corrected record on live API betting.
- [x] AC2 The OutOfMemoryError: find what the app holds in memory through a no-limit scan (result, reference snapshots, books states, caches), cap or release it, raise the heap ceiling (`largeHeap`), report the heap in Diagnostics and in the crash record so the next one says what filled it. Tests.
- [x] AC3 Auto-bet crash safety (found re-reading it against this crash): the process can die after an order is sent and before the Tracker has it; the next cycle would bet the same bet again. An order in flight is marked before it's sent (auto-bet halted, saved) and cleared only once the result is known; a restart finds it and stays stopped. Tests (mutation-checked).
- [x] AC4 Ship v0.39.0 (auto-bet + these), send Tj the link. DONE: CI green on `ca12c25a`, release.yml run 36825708662 green, Release v0.39.0 confirmed (get_release_by_tag), `record-release.sh v0.39.0 69`.
  DONE AC1-AC3 (RESEARCH.md §51-52): the crash is the 256 MB heap filling during a no-limit scan: every partial re-priced the whole plan (thousands of markets, ~100 times a scan), the previous finished result was held strongly beside the new scan, and every source's parsed boards plus 3,000 Novig books are kept between scans. Fixed in layers: `largeHeap`; `MemoryGuard` (trim at 75%, a scan ENDS with its read-so-far and says so at 90% after a collection); big plans publish partials at most every 2 s; the fallback result is soft; Diagnostics' Memory block + WARN and the heap in the crash record. No heap dump exists, so the cause is reasoned from the code, and the next report will say (the Memory block). Tests mutation-checked (graceful stop, trim, throttle). AC3: an order is marked in flight (saved halt) before it can be sent and cleared only on a definitive answer, so a crash mid-order leaves auto-bet stopped and the bet unsent again (2 tests, mutation-checked). AC1: the record corrected (123 API bets, 21 graded by Novig's ledger: live betting and grading work).



## Tj, 2026-10-01 ~06:3xZ: "After finishing the release, Investigate and fix the app crashes in the diagnostic here:" (the same v0.38.0 Diagnostics: the OutOfMemoryError)

AC2 was the first layer (a bigger heap, a guard that ends a scan gracefully, fewer partials). Tj asked again, so this is the layer under it: stop the scan from allocating so much in the first place. Cause (RESEARCH.md §52, reasoned from the code; no heap dump exists): every partial result re-prices the WHOLE plan, building an `Opportunity` for every planned outcome again (~17k with no limits), though a batch changes only ~30 books; the previous result is alive beside the new one; and every source's parsed boards plus 3,000 cached books stay between scans.

- [x] AD1 Price only what changed: keep each planned market's priced `Opportunity` list from the last pass and reuse it while its book (same object), the plan's market, the fair settings, the bankroll and the Kelly multiplier are unchanged; only the batch's own books are priced again. Same results as pricing everything (a test compares both ways), far less garbage per partial.
- [x] AD2 Let go of what a new scan doesn't need: reference snapshots of leagues no longer selected (and ones past the stale limit) are dropped at scan start, so the 256 MB isn't spent on boards for leagues Tj turned off.
- [x] AD3 Tests mutation-checked: unchanged markets reuse the same objects; a changed book, bankroll, Kelly multiplier or fair setting prices again; the memo drops what a newer plan replaced; stale references dropped at scan start.
- [x] AD4 Floor green with screenshots, ship v0.39.1, send Tj the link, say plainly the cause is reasoned (no heap dump) and what the next Diagnostics' Memory block will show.
  DONE AD1-AD3: `FairMemo.pricedFor` / `PricedMarkets` (Pricing.kt): the last plan's priced markets, re-used while the book is the same object and the bankroll, Kelly and fair method are unchanged; `Scanner.dropUnusedReferences` at scan start. 9 tests in BiggerScansTest (same objects for untouched markets; result identical to pricing everything across five book scenarios; bankroll/Kelly/fair method re-price; a different plan never served from another's slots, same size or bigger; one plan held; no memo = nothing shared; partials of a scan share outcomes; boards of a league turned off let go; allocation measured: 40 partials of a 1,200-market scan 212 MB -> 23 MB). Seven mutants killed (book identity, bankroll, Kelly, fair settings, plan identity, no put, no prune); one survived first (the fair-method test also changed the bankroll) and was fixed. Floor 1,380 tests (1,357 passed, 23 live-only skipped) green with -Pscreenshots. RESEARCH.md §52 second layer.
  DONE AD4: v0.39.1 shipped (CI green on `f185e97f`, release.yml run 36827444502 green, Release confirmed, APK checked: versionCode 70, classes.dex differs from v0.39.0's; recorded in BUILDLOG.md).


## Tj, 2026-10-01 ~12:49Z: "So far the auto bet is working well, but add an option for longest odds of any auto bet. For example, I don't want it to bet anything that is more of a longshot than +130 odds, unless ¼ Kelly betting automatically puts a much lower stake on longshots. Does Kelly do this?"

Answer first (from `AutoBet.kellyStake`): Kelly's stake at the same edge falls as the odds get longer (stake = bankroll × fraction × edge ÷ the profit on a $1 bet), so yes, a longshot gets a smaller stake: at +100 the full edge, at +300 a third of it. It does NOT cap longshots: a $1 or typed stake ignores the odds, a big fake edge on a longshot still stakes, and the fair price of a longshot is the least reliable (the favorite-longshot bias, RESEARCH.md §8.1). The CNO page's own "max odds" filter (default +150) already trims what auto-bet sees. So a separate longest-odds limit is worth having.

- [x] AE1 Setting `autoBetMaxOdds` (American, 0 = no limit; default no limit so what runs today doesn't change): choices +100 … +300 and "No limit", plus a typed amount (floor +100), in the Auto-bet card; every stake mode (Kelly, $1, typed) obeys it.
- [x] AE2 The rule, twice: a bet whose judged price is longer than the limit is skipped with its reason (Settings/Diagnostics say how many), and the order book's own best price read just before the order is checked too (the planner refuses it), so a market that drifted out to +141 after being judged at +125 isn't bet. Favorites always pass. Manual bets are unaffected.
- [x] AE3 Tests, mutation-checked; the card says how Kelly treats longshots; full floor; ship v0.39.2; link to Tj with the Kelly answer in numbers.
  DONE AE1-AE3: `ScanSettings.autoBetMaxOdds` (0 = none, default) + `AUTO_BET_MAX_ODDS_CHOICES`; `AutoBet.Rules.maxOdds`, `tooLong`, `judge(..., american)`; `BetLimits.maxOdds` checked by `ApiBetPlanner` on the book read before the order; the Auto-bet card's "Longest odds to bet" chips + typed field + hint + Kelly note; Diagnostics/confirm sentence carries it when set (`AutoBetText.criteria`). Tests: AutoBetTest (+2: the limit, Kelly vs longshots in numbers), ApiBettingTest (+2), AutoBettorTest (+3: skip, every stake mode, drift on the order book), AutoBetUiTest (+2); 7 mutants killed (run in the foreground: a first background run let the autosave hook commit two mutated checkpoints to main, restored at once; no release was built from them). Floor 1,389 (1,366 passed, 23 live-only skipped) with screenshots. RESEARCH.md §53, BRIEF.md.
  SHIPPED: v0.39.2 (CI green on `c133deb1`, release.yml run 36867009531 green, Release confirmed, APK checked: versionCode 71 and the new option's string present; recorded in BUILDLOG.md).


## Tj, 2026-10-01 ~13:2xZ: "Investigate whether it is possible for this auto bet feature to work even with my phone turned off. For example, is there a simple and free way to run it on the cloud? Can Claude run it? How can I run the vigilant cno auto bet feature with my phone off"

A question, not a build: answer from what the code and docs say (what auto-bet needs to run: CNO access, Novig trading key, location check, the Android-only parts), plus what free hosts really allow today. Build nothing until Tj picks an option.

- [x] AF1 Read what auto-bet depends on: how CNO is fetched (login/IP limits), the Novig key and signing, the location check (NOVIG_API.md §14), and which of the cycle's code is Android-free (`engine`/`data`) and which lives in `app`.
- [x] AF2 Check the options against today's facts: a spare Android phone, a home computer/Raspberry Pi, GitHub Actions on a schedule, a free cloud VM, Claude Code routines ("can Claude run it"), serverless cron.
- [x] AF3 Answer plainly, with a recommendation, the risks (location check, secrets in a public repo, an LLM placing real bets) and what building the recommended one would take; offer it, don't start it.
  DONE AF1-AF3: RESEARCH.md §54 (verdict, the 3-day Novig location rule, the Keystore key that can't leave the phone, which code is already plain JVM, the host comparison, the risks). Answered Tj; nothing built, waiting for which option he wants.


## Tj, 2026-10-01 ~13:5xZ: "I don't want a $1 minimum bet for the auto bet feature. It can bet as low as 1 cent, whatever the number is that I have in options. Usually it will be a Kelly number and often under $1"

Find every place the auto-bet treats $1 as a floor (`AutoBet.MIN_STAKE`: the stake floor, the wallet-empty stop, the typed-amount and maximum rules, the card's and confirm's words, the Diagnostics lines, BRIEF.md's locked rule), first checking Novig's own minimum order (contracts pay 1¢ each, so a cent should buy a contract or two; verify against the docs, not assume).

- [x] AG1 Check Novig's minimum order size in NOVIG_API.md / docs.novig.com and the planner's one-contract check; what a 1¢ stake buys at long and short prices.
- [x] AG2 Make the floor one cent everywhere in auto-bet: Kelly/typed/maximum stakes, the wallet stop (empty = under a cent), the skip reasons, the card, confirm and Diagnostics text. Manual Bet-sheet bets unchanged.
- [x] AG3 Tests (mutation-checked) incl. sub-dollar Kelly stakes now placed, a 1¢ stake, the wallet remainder under $1, and the fixed $1 stake still $1; full floor; ship v0.39.3; link to Tj.
  DONE AG1-AG3: Novig's documented minimum is 1 contract (1¢ payout), no minimum fee; `ORDER_TOO_SMALL` exists with no published threshold (RESEARCH.md §55). `AutoBet.MIN_STAKE` = $0.01; wallet stop under a cent; skip words "under a cent"; `PlaceResult.Refused(tooSmall)` from an `ORDER_TOO_SMALL` 400 (was a `Failed`, which would have stopped auto-bet for 5 minutes); `AutoBettor.tooSmallBelow` learns the refused size for the run and skips stakes no bigger. The card, confirm and BRIEF.md say it. Tests: AutoBetTest (2 rewritten, the Kelly longshot one now 92¢/61¢/37¢/20¢), ApiBettingTest (+2), AutoBettorTest (+3, 1 rewritten); 7 mutants killed (run in the foreground).
  SHIPPED: v0.39.3 (CI green on `b2ad578c`, release.yml run 36870123717 green, Release confirmed, APK checked: versionCode 72 and the ORDER_TOO_SMALL handling present; recorded in BUILDLOG.md).


## Tj, 2026-10-01 ~17:5xZ: "Make a button in all the bet slips for the vigilant app for an option to add money to the vigilant wallet in amounts of $1, 2, 5, 10, 15, 20, or an amount I type in. Right now if I have one cent, there is no option to add money in the bet slip. Also make it so the auto bet feature can bet stakes all the way down to 1 cent, even if there is only 1 cent left in the wallet. It is allowed to completely deplete the wallet. If a Kelly stake is more than the available balance in the wallet, bet the remainder of the wallet balance on that bet. If a kelly amount is more than the maximum allowed bet in the options, bet the maximum allowed. Add an option to scan cno every 5 seconds for the auto bet function. Make a push notification for every automatic bet, so I can see each bet placed and the stake and EV."

Four things. (1) Add money from every Bet slip, not only when a bet is short of money. (2) The auto-bet's stake rules as Tj states them (v0.39.3 already floors at one cent and caps a stake at the wallet and at his maximum: verify it with tests that pin each sentence, fix what doesn't hold). (3) A 5-second check interval. (4) One push notification per automatic bet carrying the stake and the EV (today: check what the existing one says, whether each bet gets its own, and its importance).

- [x] AH1 Read how the Bet slips (feed, CNO, mini window, Tracker's) show "Add money" today (`TopUp`: only on a shortfall) and how money moves into the wallet (management key, `walletAmount`).
- [x] AH2 An "Add money" control in every Bet slip, always there while betting is set up: chips $1, $2, $5, $10, $15, $20 and a typed amount, moving money from the cash wallet to the Vigilant wallet; works at 1¢ and at $0; tests.
- [x] AH3 Auto-bet stake rules pinned by tests: down to one cent, a one-cent wallet is bet (and emptied), a Kelly stake over the wallet bets the remainder, a Kelly stake over the maximum bets the maximum. Fix anything that doesn't hold.
- [x] AH4 A 5-second check interval (the CNO read pace, the alarm/service loop, the Vigilant scan's 4-minute floor all hold); label in both places the interval is chosen.
- [x] AH5 One push notification per automatic bet, its own notification each, with the stake and the EV (and the odds and book count), importance high enough to show; tests.
- [x] AH6 Full floor with screenshots, ship v0.40.0, send Tj the link with what is verified and what is not.
  DONE AH1-AH5 (RESEARCH.md §56): the Bet sheet (one composable for every entry: feed, CNO, ParlayAPI, widget) now always has "Add money to the wallet · $x in it" (chips $1/2/5/10/15/20 + typed; open with the shortfall when short; not on a placed bet); with a saved management key it sends the transfer in the sheet (`fundFromSheet`/`transferNow`) and re-reads the wallet, else Settings opens with the amount (`requestTopUp(amount)`); AH3 needed no change (v0.39.3 already floored at a cent and capped by wallet and maximum): pinned by 3 AutoBettorTests; 5 s interval (+ `AutoScanClock.minGapMs`: the cadence was a cycle + 5 s); a pop-up notification per auto-bet on a new HIGH channel with stake + EV + odds + wallet left, test button, blocked-notification warning and health check. Tests: ApiBettingUiTest (+7, 3 rewritten), ApiBettingControllerTest (+5), AutoBettorTest (+6), AutoBetUiTest (+3), AutoScanTest (+1, 3 updated), AutoBetDiagnosticsTest (+1); 11 mutants killed (foreground).
  SHIPPED AH6: v0.40.0 (CI green on `039249ed`, release.yml run 36907026370 green, Release confirmed, APK checked: versionCode 73 and the new notification channel present; recorded in BUILDLOG.md). Release notes text in release.yml now says 5 seconds.


## Tj, 2026-10-01 ~18:4xZ: "Also make it so if I "check odds now", make sure it gets all available closing line data, and make it pause other parts of the app such as the cno scanner so that it focuses on refreshing the current odds and EV and stats"

Two things. (1) "Check odds now" (the Tracker's button) must also collect every closing line that is available: the closes that the app's other paths (the pre-start capture, the next-morning Novig trade files, ParlayAPI's Pinnacle closes, the CNO page's last read) would eventually fill, so one tap leaves no bet that has a close available without it. (2) While it runs, the other parts of the app that compete with it (the CNO scanner's reads, the background auto-scan cycle, Vigilant's own scans, the widget/feed refresh, the Novig live-price reads) pause, and resume when it's done, fails or is cancelled, so the check gets the whole of CNO's pace and the API budget. Note for Tj: auto-bet runs inside the CNO cycle, so it doesn't bet while the check runs.

- [x] AI1 Read what "Check odds now" does (`recheck`, `BetRecheck`, the Tracker button), every closing-line source (`ClosingLine`, `CloseBackfill`, `captureClosing`, Novig trade files, ParlayAPI closes, CNO), and every other loop that would compete (CNO feed/watch/books, AutoScanService cycles, scans, widget refresh, live prices, websocket).
- [x] AI2 Make "Check odds now" collect all available closing data in the same run (the closes for bets that have started or are inside the close window), with a plain report of what it got and what is not available yet and why.
- [x] AI3 A "focus" pause around the check: the competing loops wait, the check runs with the whole pace, everything resumes after (done, error, cancel, or a ceiling), with a visible state (a banner and the foreground notification), safe if the app dies mid-check.
- [x] AI4 Tests (mutation-checked), full floor with screenshots, ship v0.40.1, report incl. that auto-bet pauses for the check.
  DONE AI1-AI4 (RESEARCH.md §57): `CloseBackfill.run(force)` (every started bet without a close, up to 1,000, not one looked at in the last 10 minutes; the report now carries what is missing and why) called by every Check odds now through `gradeAll(forceCloses = true)`, with `CloseText` putting it in the toast; `FocusGate` held while a check runs: `AutoScanner.cycle` skips (auto-bet with it), the CNO watch (`cnoReadsHeld`), Scan/Recheck/Refresh, the widget's rescans, movers and injury look-ups wait, a running scan stops, everything resumes in the `finally` (and a skipped background cycle runs at once); Tracker banner and notification text. Tests: HistoricalClosesTest (+4), CheckFocusAppTest (6: gate and ceiling, the held loops, the cycle, release, the forced look, the close sentence, source pins), TrackerUiTest (+2); 14 mutants killed (foreground).
  SHIPPED: v0.40.1 (CI green on `ebea8af1`, release.yml run 36910624132 green, Release confirmed, APK checked: versionCode 74 and the new focus text present; recorded in BUILDLOG.md).


## Tj, 2026-10-01 ~19:1xZ: "For the auto bet feature, include an option in the settings where I can require that every sports book scanned agrees the bet is positive EV (for example, 5 of 5 books agree positive EV)"

Today the auto-bet needs at least N books (2, 3, 4, 5+) that each say +EV on their own, and at least 1-3 books pricing both sides; a bet with 3 of 5 agreeing passes a "3" setting. Tj wants a switch that makes it ALL of them: the books scanned for the bet (the ones that price both sides, "x of y books agree" in the notification) must every one say +EV.

- [x] AJ1 Read what "agree" and "scanned" count (`CnoBooks.Check.agreeing` / `twoSided`, which books are included, whether Novig counts, how a book pricing one side is treated).
- [x] AJ2 A setting `autoBetAllAgree` (off by default: what runs today doesn't change): when on, a bet passes only if every book that prices both sides says +EV on its own (agreeing == twoSided), on top of the other criteria; the skip reason says "3 of 5 books agree (all must)".
- [x] AJ3 The switch in the Auto-bet card with a plain sentence, the confirm text, Diagnostics line; tests (mutation-checked); full floor; ship v0.40.2; link with what "every book scanned" means.
  DONE AJ1-AJ3 (RESEARCH.md §58): `ScanSettings.autoBetAllAgree` (off by default), `AutoBet.Rules.allAgree`, `judge` skips unless agreeing == twoSided (on top of every other criterion); the Auto-bet card's switch "Every book scanned must agree (5 of 5, not 3 of 5)" with its note, the criteria/confirm/Diagnostics sentence. Tests: AutoBetTest (+1, defaults/round trip), AutoBettorTest (+1, a book that disagrees: placed off, skipped on; all agree: placed), AutoBetUiTest (+1); 5 mutants killed (foreground).
  SHIPPED: v0.40.2 (CI green on `8f849aa9`, release.yml run 36939075877 green, Release confirmed, APK checked: versionCode 75 and the switch's text present; recorded in BUILDLOG.md).


## Tj, 2026-10-02 ~00:2xZ: "Research if this app stays awake and auto bets if the option is turned on even through screen lock and an idle android 16 moto g 2026. If not, research if there are ways to keep it alive robustly to keep auto bet on and scanning even if the phone is idle and the screen is turned off and locked. Maybe wake lock or a don't sleep or keep screen awake function (but I still want the screen turned off of possible)"

Two parts: (1) find out, from the code and from Android's own documentation and source, whether the background auto-scan (and so auto-bet) keeps running with the screen off and locked on an idle Android 16 phone (Doze, App Standby, the foreground service's rules, alarms, Motorola's own battery management); (2) if it doesn't reliably, build the ways to keep it alive that leave the screen OFF (a wake lock, the battery-optimization exemption, whatever else the research supports), and a way to SEE whether it held (a lateness meter in Diagnostics), since nothing here can be tested on the phone from this session.

- [x] AK1 Read what the app does today: the service's type and start path, its wake lock (only during a cycle?), the alarm and how it re-arms, what happens if the process dies, the battery-optimization prompt and status, what Diagnostics already reports. — done: AutoScanService read in full; the alarm-only design and its Doze limits are RESEARCH.md §59.
- [x] AK2 Research (Android's docs and source, Motorola reports): Doze and App Standby with a foreground service, wake locks and network in Doze, exact-alarm limits in Doze, Android 15/16 foreground-service rules and timeouts for `specialUse`, Wi-Fi power save, Motorola's battery management. — done: RESEARCH.md §59 (what is established, the two alarm-quota numbers that disagree, what is not verified on the Moto).
- [x] AK3 Build what the research supports: (a) hold the CPU awake with the screen off while auto-scan/auto-bet is on (an option), (b) ask for the battery-optimization exemption and show its status, (c) restart paths if the service is killed, (d) a meter of how late cycles ran (persisted) in Diagnostics and a health check. — proved by KeepAwakeTest, CycleLogTest (data), KeepAwakeServiceTest (the real service on a screen-off, Doze-on phone), KeepAwakeDiagnosticsTest, ScreenshotTest.settingsKeepAwakeSwitch; mutation-checked.
- [x] AK4 Tests (mutation-checked), full floor, ship v0.41.0, answer with what is established, what is not, and what to look at in Diagnostics after a night idle. — full floor 1441 passed / 23 skipped; CI green on ce28edc3; Release v0.41.0 (code 76) APK verified (aapt2, apksigner, strings).


## Tj, 2026-10-02 ~00:0xZ: "Also, make it so anytime I close the app and reopen it, auto bet and background scan is turned off by default. Nothing should auto bet or background scan unless I specifically set it in the settings. Research if there is a way to have a setting for the cno scanner and auto bet feature to require bets to be proven positive EV by a current, devigged sharp book such as Pinnacle, and then how to properly implement this function. For example, in addition to the other settings, this setting will require at least one sharp sports book (usually pinnacle) to show that the bet is positive EV by fresh (within the last few minutes) odds from the sharp book(s) devigged and compared to the current novig odds for the same exact bet. Does cno already have this information in its feed? If not, can pinnapi or any other Pinnacle api be used in addition to cno to compare the odds? If this is possible, implement it in the app in the most efficient and accurate way possible."

Two parts. (1) Every time the app is closed and opened again, auto-bet and background auto-scan are OFF until Tj switches them on in Settings (so nothing bets or scans in the background by itself from an earlier session). (2) Research, then build if possible: a setting (CNO scanner alerts/list and auto-bet) that requires a bet to be proven +EV by at least one sharp book (Pinnacle first) whose odds are fresh (last few minutes), devigged, and compared with Novig's current taker price for the exact same bet.

- [x] AL1 Auto-bet and background auto-scan are turned OFF each time Vigilant is closed and opened again (a fresh launch, not a rotation or a return from Home); nothing runs in the background from an earlier session; Tj switches them on in Settings. Say so on screen when something was switched off. — proved by LaunchResetTest (the fresh and restored activity, the file on disk, the toast), mutation-checked.
- [x] AL2 Research: what a CNO row and CNO's game page already carry about Pinnacle/sharp books (which book, both sides, how fresh) and how the bet's exact line and side match; what PinnWire/pinnapi, ParlayAPI and any other Pinnacle source give (real-time or delayed, both sides, exact lines, props, cost per call, rate limits); how Vigilant already uses them. Write it up in RESEARCH.md §60 with sources and what is not verified. — RESEARCH.md §60.2 (what CNO's row and game page carry, the Pinnacle feeds and their limits; PinnWire demo board checked 2026-10-02).
- [x] AL3 Design the check: a setting "Require a fresh sharp book to confirm +EV" (which sharp books, how fresh, minimum edge), the exact match rule (same game, market, line, side), devig method, comparison with Novig's current taker price, what happens when no sharp book has the bet (skip, say why), cost in API calls and time per cycle. — RESEARCH.md §60.3 (the rule, the match, the veto, the cost, what a no says).
- [x] AL4 Build it in the most efficient and accurate way the research supports (CNO scanner alerts/list as well as auto-bet), shown in Settings (CNO + Auto-bet card), in the bet's row/sheet, notifications and Diagnostics. — proved by SharpConfirmTest, SharpBooksTest, ParlayBooksTest (data), SharpConfirmAppTest, SharpConfirmUiTest, SharpDiagnosticsTest (app); mutation-checked.
- [x] AL5 Tests (mutation-checked), full floor with screenshots, ship v0.42.0, answer Tj: what CNO has, what it lacks, what was built, what it costs, what is not verified. — full floor 1486 passed / 23 skipped; CI green on ab65fde9; Release v0.42.0 (code 77) APK verified (aapt2, apksigner, strings).


## Tj, 2026-10-02 ~01:0xZ: "For the diagnostics feature, make it output a file that I can send directly to Claude which Claude can understand and easily diagnose and improve the app. The diagnostic feature in the app should be very comprehensive and log all types of events, code, failures, connection speed and issues, API usage and issues, etc. make it so when I output the diagnostic file, it opens an android "share with" prompt, and I can share it directly with Claude app. This file should tell Claude comprehensive data about the app and signal to Claude what to optimize, what bugs or failures there are to fix, how to make features smarter or faster or better coded. Basically I want a smart diagnostics feature that can improve the app with every upload to Claude."

Plan (what "smart" means here): a flight recorder that is on all the time and survives restarts (events, failures with the code location, every network call's host/status/latency/bytes, API usage and errors, scan/cycle/auto-bet/sharp results, service and power events, the app's own warnings from logcat), a findings engine that turns it into a ranked "fix / optimize / improve" list with evidence and the file to open, a comparison with the previous upload (so Claude sees whether its last fix worked), and a file shared straight from the Android share sheet. Never a key, token, or account id in it.

- [x] AM1 Read what Diagnostics, ProblemLog, AppExits and the HTTP clients already record; decide the file's layout (a read-me for Claude, findings first, the evidence after, a JSON block of numbers for comparing uploads) and what can never be in it.  
  Done: layout and the never-in-the-file rules are RESEARCH.md §61.1/§61.3; `DiagnosticsFileTest` (`the sections come in the order a reader needs them`, `no key, token or secret is ever in the file, whatever put it in a message`, `a key in a rate-limit answer, a log line or a stack frame is masked in the file`).
- [x] AM2 The flight recorder: `EventLog` (persisted ring buffer of notable events with category, level, numbers, and for errors the app frames of the stack), fed by every existing `problems.add` and by the places that already know (scan and cycle ends, auto-bet and sharp-check runs, alerts, service start/stop/restart, CNO reads and pauses, Novig live feed, grading, settings reset on launch).  
  Done: `EventLog`, `AppRecorder`, hooks in AutoScan/AutoBettor/AutoScanService/MainActivity. Tests: `EventLogTest`, `AppRecorderTest`, `CycleRecorderTest`, `SharpConfirmAppTest` (`a run is written to the flight recorder…`, `what a run counts…`), `KeepAwakeServiceTest` (`starting and stopping the service are events in the flight recorder`), `ProblemLogTest`.
- [x] AM3 Network and API meter: one OkHttp interceptor on the shared client records every call's host, path shape (no query, no ids), status, time, bytes, network type and failure kind into per-host hourly stats and logs failures and slow calls as events; connection speed per host (p50/p95/max, bytes per second), status-code counts per provider, rate limits and credit runway.  
  Done: `NetStats`, `NetShape`, `NetInterceptor`, `NetKind`. Tests: `NetStatsTest`, `NetInterceptorTest` (MockWebServer: body read, closed unread, 429/503/404, timeout, cancel with and without the word, slow 8 s call, no query reaches the stats), `NetKindTest`, `DiagnosticsShareTest` (`a call through the app's shared client is recorded…`).
- [x] AM4 Performance and phone: scan and cycle durations (percentiles), cold-start time, heap trend, file sizes in the app's storage, the app's own warnings and errors from logcat, battery and power state.  
  Done: `PerfStats` (cycle.ms, cycle.step.*, scan.ms, cold start), `LogcatTail`, battery/thermal/storage in the file. Tests: `PerfStatsTest`, `LogcatAndTrendTest`, `CycleRecorderTest`, `NetKindTest`.
- [x] AM5 The findings engine: rules over all of the above that write a ranked list for Claude (BUG / FAILURE / OPTIMIZE / IMPROVE / WATCH) with the evidence, the code to open, and a suggested change; plus "since the previous upload" (what got better, worse, new, resolved), kept in a small history.  
  Done: `Advisor`, `DiagHistory`/`Trend`. Tests: `AdvisorTest` (18, including the edge of every threshold), `LogcatAndTrendTest`, `DiagnosticsFileTest` (`the findings, the comparison with the last upload…`).
- [x] AM6 The file and the share: write one text file to the app's cache, open Android's "Share with" sheet (FileProvider, read permission, a ready prompt as the message) from Settings › Tools and from the Diagnostics screen; tests (mutation-checked), full floor with screenshots, ship v0.43.0, answer Tj.  
  Done (v0.43.0): `DiagnosticsFile`, `DiagnosticsShare`, FileProvider, buttons. Tests: `DiagnosticsFileTest`, `DiagnosticsShareTest`, `DiagnosticsUiTest`. Mutation-checked: every recorder, advisor, file, share, wiring and UI rule above was broken one at a time and a test failed each time (about 200 mutations; two JSON-block masking/cap lines are defensive and equivalent in practice). Floor 1582 passed / 23 skipped with `-Pscreenshots`. Not verified: that the Claude app is in the phone's share sheet, `logcat --pid` on the Moto G, real-world thresholds (RESEARCH.md §61.5).


## Tj, 2026-10-02 ~02:51Z: "I read somewhere that sharp bets can be found on novig by analyzing liquidity on certain bets, and if large liquidity is offered on certain bets that it is probably betting syndicates or sharps. Investigate whether this is true or not. If it is true and a good betting strategy, implement in vigilant a way to scan for this liquidity and follow the sharp bets. Basically a scanner for sharp action. Only make this if you discover that it has merit and is a good strategy. Then find the most effective and efficient way to incorporate it in vigilant using the best sources and keep in mind the apis I already have"

Research first; build only if the research says it has merit.

- [x] AN1 Research: how Novig's book works (every order is a resting bid; who posts large size: retail, market makers, syndicates), what big resting size actually signals on a peer-to-peer exchange (a maker *offering* liquidity vs a taker *hitting* it), what the evidence says (betting-exchange and prediction-market literature, Betfair/Kalshi/Polymarket experience, sharp-money/steam research), what Novig's API exposes (order book depth, trades, BBO, websocket) and what Vigilant already reads. Write it up in RESEARCH.md §62 with sources and a verdict: merit or not, and why.
  Done: RESEARCH.md §62. Measured on Novig's own published trades (59 days, 9.5M straight trades, 10,225 markets with a pregame close; NFL also against the true close) by `tools/research/novig_size_study.py` (re-runnable), plus a live snapshot of 57 books. **Verdict: no merit.** Big resting liquidity is market makers quoting both sides ($10,500 a side on NFL moneylines, ~$4.8k MLB); the big resting orders that get hit lose to the close (−0.78¢ at $5k+, −1.81¢ at $10k+), and copying them is −1.05% EV (CLV −0.57¢ [−0.84, −0.37]). Whale takers ($10k+) +0.55¢ for a follower, interval includes zero, NFL −0.24¢ at the true close, ~9 a day.
- [x] AN2 If it has merit: design the most efficient version on the APIs Tj already has (Novig key, CNO, ParlayAPI, PinnWire/pinnapi, the free sources), with the exact rule, what it costs per cycle, and how it is checked against the sharp-book fair price. If it does not: say so plainly and record what (if anything) from the idea is worth keeping.
  Done: it does not (§62.4). Kept: the study script (re-run when NBA/NHL months are published); the finding that makers earn the spread (supports the maker-bid line, §16.4); big depth at a price already +EV against Pinnacle is good for Tj as the taker (already shown as "$X fillable at +EV"). Sharp action is already followed at its source: devigged Pinnacle/Circa, and since v0.42.0 the fresh-Pinnacle confirmation (§60).
- [x] AN3 Build what AN2 decides (only if merit), tests (mutation-checked), full floor, ship, answer Tj.
  Done: nothing to build, per Tj's condition ("Only make this if you discover that it has merit"). No app change, no version, no Release.


## Tj, 2026-10-02 ~03:50Z: "Run full tests on this app, look for ways to improve the app and scanners. Look into these issues: 1) when I start the vigilant scanner the list of bets gets laggy. This is ok if it's supposed to but not ok if it's a sign of bad code. 2) look at the screenshot, most odds say 9 minutes old. Is there a way to get fresh odds during a scan? Is 9 minute old odds still good data? 3) consider ways to use free apis such as ESPN apis and also my paid parlayapi to their full extent and get as much benefit as possible from the apis. Research API features and docs if needed. Search for free apis that can improve this app. Look at their docs. I'm about to send a diagnostics file from the app, review that too"

The screenshot: +EV feed while "Scanning" (Novig prices 1883/4588), three NCAAF cards (Total UCF @ Houston, NC State Under 27.5 team total, Gardner-Webb -7.5) each "odds 9 min old" in amber.

- [x] AO1 Full tests (test-protocols skill): the whole floor with screenshots, every PNG looked at, the sweep of every tab and subsystem; fix what's found, each fix with a test that fails without it.
  Done: floor 1,604 passed / 23 skipped / 0 failed (Gradle exit 0) with `-Pscreenshots`; all 97 PNGs looked at (contact sheets). Found and fixed besides AO2-AO5: three stale texts (Settings › About missed ParlayAPI; the Novig key and "Novig prices per scan" notes still said the live feed loads "about 8 seconds in"; `ScreenshotTest` pin updated), `ScanLagTest` made independent of the app container (its coroutines outlived tests in the shared Robolectric sandbox), release build checked locally (R8 keeps `com.tjshea.vigilant.app.ScanService`; APK 7.9 MB).
- [x] AO2 Lag: find out why the +EV list gets laggy while a Vigilant scan runs (recomposition per streamed price, sorting/filtering on the main thread, unstable params, the progress bar, the "odds N min old" ticker). Say whether it's expected or bad code; fix what's bad code, with a test or a measurement.
  Done (RESEARCH.md §63.2): bad code. The root built a new `ApiBetActions` for the STATIC `LocalApiBet` on every state (3+/s mid-scan): everything under it recomposed with skipping off. `ProvideApiBet` (remembered), `LocalOpenNovig` remembered, `FloatingActions` per window, `ParlayPickActions` remembered (root + feed), `KeyActions` a data class. `ScanLagTest` (a card with unchanged inputs: 6 draws in 5 ticks before, 1 after; source pins), mutation-checked.
- [x] AO3 "odds 9 min old": what that age measures (the reference book's quote age vs. Novig's price), why most cards show 9 min mid-scan, whether a scan can get fresher odds (re-read reference books for the cards on screen, PinnWire/ParlayAPI freshness, the end-of-scan re-read), and whether 9-min-old odds are still good data (by market, by time to start; RESEARCH.md §24/§30 freshness rules). Build what the answer supports.
  Done (RESEARCH.md §63.1, §63.3): ParlayAPI quotes were dated by their market's last price MOVE, not their book's last sighting (its docs: the bookmaker `last_update` is the verified-at heartbeat); 10% of its quotes passed a 5-minute check, now 99%. Fixed in `TheOddsApiClient.parseEvents(seenByBook)` (`ParlayFreshnessTest`, 4 mutants killed). Faster scans: the live feed handed the filled-in plan once (`Scanner.BookPump.feedStream`, `NovigStream.open`, capped unsubscribe; `LiveFeedPlanTest` 5 + `NovigStreamTest` +2, mutation-checked). No mid-scan fair re-read (cost vs. a ~2.5-3 min scan; §63.1).
- [x] AO4 APIs to their full extent: ParlayAPI (every endpoint and feature on Tj's plan vs. what Vigilant uses, PARLAY_API.md), ESPN's free APIs (scores, injuries, lineups, odds, rosters), and a search for other free APIs worth adding (with their docs read). Write it up (RESEARCH.md) and build the ones with the most benefit per cost.
  Done (RESEARCH.md §63.5): the biggest gain was AO3's ParlayAPI stamp (its game lines now count). ESPN probed live: DraftKings-only odds, prop lines without prices, free injury report, FPI model: nothing that adds a fair price. SportsGameOdds free (10-min delay, no Pinnacle), MoneyLine free (1,000/mo), OddsPapi (250/mo): none worth adding.
- [x] AO5 Review the diagnostics file Tj sends from the app; fix what it shows.
  Done (RESEARCH.md §63.4): the OOM crash was v0.38.0's (fixed in v0.39.x) → crashes/exits before the running version's install are a WATCH/WARN (`AdvisorTest`, `DiagnosticsTest`), a crash records its version (`AppExitsTest`); the 3 "low memory" exits → cached reclaims aren't failures (`Exit.reclaimed`, importance), and Vigilant trims off screen (`onTrimMemory` → `Scanner.trimForBackground`, `BackgroundTrimTest`); readable release stacks (R8 keeps app names + file/line) and `mapping.txt` on each Release; scan 322 s → AO3's live feed fix. All mutation-checked.
- [x] AO6 Full floor, ship, answer Tj with the findings on each point and the Release link.
  SHIPPED: v0.44.0 (code 79). CI green on d8695cbe, ship.sh gate 1,604 passed / 23 skipped, release.yml run 36966331376 green on 07ce6e1a, Release confirmed (APK + mapping.txt.gz), APK checked (aapt2: versionCode 79 / 0.44.0; apksigner: BRIEF.md's certificate; dex keeps `com/tjshea/vigilant/app/ScanService`; the new live-feed text present). Recorded in BUILDLOG.md.


## Tj, 2026-10-02 ~05:1xZ: "Why is this saying the edge is gone? It's the same odds, and they are positive ev"

His screenshot: CNO tab, Bet sheet for Under 47.5 (BYU @ TCU, NCAAF), $0.97 (¼ Kelly), red text "The edge is gone: Novig's best price is now +108, and the fair odds +103 make that +2.4% EV." Cause: `ApiBetPlanner.plan` refuses any bet under `BetLimits.minEv` (Settings › Betting › "Smallest edge a bet is still placed at", Tj's is +3%) and always words it as "the edge is gone", even at +2.4%.

- [x] AP1 Say the real reason: positive EV under the minimum names the minimum and where to change it; "the edge is gone" only when the EV at Novig's price is zero or less. The ladder note ("Only $X … at a positive edge") names the minimum too when it is above zero. Tests (mutation-checked), floor, ship, answer Tj.
  Done: `ApiBetPlanner.plan` ("+2.4% EV at Novig's best price now (+108, fair +103) is under your +3.0% minimum (Settings › Betting › Smallest edge a bet is still placed at)"), `BetLimits.minEvWhere` (auto-bet names its own setting), the ladder note. `ApiBettingTest` (+2 tests, 3 mutants killed); floor 1,606 passed / 23 skipped. v0.44.1 (code 80).


## Tj, 2026-10-02 ~05:4xZ (with the v0.44.1 diagnostics file): "When I scan with vigilant scanner, the entire app becomes laggy still. Remove the restriction of minimum bet EV on bet slips in the app, I should be able to bet on whatever I want manually. Only keep the hard restrictions on the auto bet function based on whatever settings I set. There was an error and it wouldn't let me auto bet. See if this is fixable."

- [x] AQ1 Read the v0.44.1 diagnostics file end to end: the lag evidence (cycle/scan timings, main-thread signs, memory), the auto-bet error, anything else it flags.
  Done: no scan numbers in it (scanning paused, "no scan since the app opened"); the auto-bet error is Novig answering 423 on every signed route since 01:33 (limits, catalog/markets, orders) while public routes answer: an account-level lock on Novig's side, not the app (AQ4). Every Check odds now in it says "24 Vigilant bets not updated: the Vigilant scanner is off" (AR).
- [x] AQ2 Lag during a Vigilant scan, still: find what is left (measure, don't guess), fix it, with a test or a measurement.
  Done: the scan notification was rebuilt and its found count re-screened the whole feed on the MAIN thread at every progress tick (3+/s): now built off the main thread, counted once per new result (`FoundCount`, `ScanNotifyLagTest`, 3 mutants killed). On-phone measurement for what's left: a frame meter (`FrameStats` + `FrameMeter`) in Diagnostics' Performance block, slow-frame % split by scan running vs not, Advisor finding `perf:frames:*` (5 mutants killed). Checked and not scan-driven: CNO's live Novig prices change at most once per 15 s and only while CNO's list is on screen; CNO's books twice per agreement read, one read per 4 s; the CNO badge and list recount only on those (keyed `remember`).
- [x] AQ3 Manual bet slips (the Bet sheet, betting through the API by hand) have no minimum-EV rule: Tj can bet whatever he wants manually. Auto-bet keeps every hard rule it has, from his auto-bet settings. Remove or repurpose the "Smallest edge a bet is still placed at" setting for manual bets; say on the sheet what the EV is without refusing.
  Done: `BetLimits.manual` (Bet sheet): no minimum edge, no fair-age refusal (said beside the EV instead), not held by Pause; Tj's max stake, max a day, pregame-only and what Novig sells still apply. The setting's chips are gone from Settings › Betting (auto-bet keeps its own minimum). Tests: `ApiBettingTest` "by hand, …" ×4, "a pause holds the auto-bet, never a bet placed by hand"; `ApiBettingControllerTest` "a bet placed by hand has no minimum edge and no pause…"; `ApiBettingUiTest`. 6 mutants killed.
- [x] AQ4 The auto-bet error: find it in the file, fix the cause if it is fixable.
  Done: the cause is Novig's (a 423 account lock: Tj has to ask Novig support, quoting the code); the app now names Novig's 423 code in its words (`NovigApiException.advice`), and a 423 that locks only one market/game/league/player (`betLocked`) skips that bet instead of stopping auto-bet. `ApiBettingTest` "a 423 names Novig's code…", "a locked market skips that one bet…" (mutants killed).
- [x] AQ5 Tests (mutation-checked), floor, ship, answer Tj.
  Done: v0.44.2 (code 81), released 2026-10-02 15:29Z with AR and AS; floor 1,638 passed / 23 skipped; CI green on 583f0780.

## Tj, 2026-10-02 ~05:55Z (mid-AQ, screenshot of Tracker › Bets, Open (160), Scanner: All): "Review the screenshot. When I press check odds now, it doesn't refresh vigilant odds. I want the check odds now to refresh the current odds and EV for every single open bet regardless of scanner"

His screenshot: a Vigilant bet (Under 52.5, Stanford @ Wake Forest) reads "−1.1% EV at your +115 · fair then +117 · Vigilant's fair odds · 7 books · as of 2h ago · tap Check odds now" after he tapped Check odds now; a CNO bet below it reads "now +2.2% EV … read 3m ago".

- [x] AR1 Find what Check odds now does today per scanner (CNO vs Vigilant vs ParlayAPI vs manual) and why a Vigilant bet stays "as of 2h ago".
  Found: `checkOdds` took `c.betPricer?.takeIf { settings.vigilantOn }`, so with the scanner on CNO only a bet with no CNO page (Vigilant's, ParlayAPI's, synced) got neither Vigilant's pricing nor ParlayAPI's books, and `OpenBetPricer.run` itself returned nothing while CNO only. Tj's Diagnostics: "24 Vigilant bets not updated: the Vigilant scanner is off" on every check.
- [x] AR2 Check odds now refreshes Novig's current price AND fresh fair odds/EV for every open bet, whatever scanner placed it, within the API budget (BRIEF.md: credits are a budget; free sources first).
  Done: `OpenBetPricer.run(anyScanner = true)` from Check odds now and a bet's Price now (Tj's taps), whatever the scanner choice; background passes stay asleep in CNO only. Same bets-only pass as Both (only the open bets' leagues and families). Settings › Scanner's CNO-only hint says so. Tests: `OpenBetPricerTest` "Tj's own tap prices with the scanner on CNO only too", `CheckOddsAnyScannerAppTest` (3 mutants killed).
- [x] AR3 Tests (mutation-checked), ship with AQ, answer Tj.
  Done: Shipped in v0.44.2.

## Tj, 2026-10-02 ~06:14Z (mid-AQ/AR, sent twice): "If I press check odds now, or pull to refresh, and the scanner is paused, automatically resume the scanner. If I switch from vigilant to another app then back to vigilant, do not turn off auto bet. Auto bet should only be off by default on a fresh app launch or restart, not just switching apps / On the last scan the novig scanning was going very slow, make sure it is set up correctly / In the scanners, especially cno scanner, it is counting identical odds from sister sports books (for example, multiple hard rock sports books just in different states). Investigate if this is smart to do, and if not, don't let vigilant double count odds from the same company sportsbooks / URGENT: … Automatically resume and finish this session in two hours, including all the prompts I sent since the last version."

(Picked up 2026-10-02 ~14:41Z when Tj asked to resume; the two-hour auto-resume had not fired.)

- [x] AS1 Check odds now, or pull to refresh, while the scanner is paused: resume the scanner automatically.
  Done: `MainViewModel.resumeThen` (saves paused = false, toasts "Scanning resumed", then does the tap): `checkOdds()` (default `resume = true`), `scan(resume = true)` from the +EV and Games pulls, `refreshCno(resume = true)` from CNO's pull. The plain Scan/Refresh buttons and the widget keep the pause. Settings' Pause hint says so. Started by the accidental second session (14:45Z), reviewed and finished here. Tests: `AutoResumeAppTest` (4, 4 mutants killed), `PauseScanningAppTest` still green.
- [x] AS2 Auto-bet survives switching to another app and back: off by default only on a fresh launch / process restart (not on Activity recreation, backgrounding, or returning).
  Done: `LaunchGate` (one per process, `AppContainer.launches`) decides in `MainActivity.onCreate`: a restored screen never resets; another screen in the same process (the mini window closed, then Vigilant opened again: the old rule's "fresh launch") resets only after a swipe out of Recents seen by a service's `onTaskRemoved`, and not when that removal is the mini window closing (within 10 s, or while in it); a new process resets unless Android's last exit record says it freed memory (low memory, signaled, freezer, excessive use, "other" but not an install), and always after a phone restart (record older than boot), an update, a crash or Tj's own close. The event log says which ("back from another app: auto-bet and auto-scan kept"). Tests: `LaunchGateTest` (4), `LaunchResetTest` (+1), 6 mutants killed.
- [x] AS3 "Novig scanning was going very slow" on the last scan: check the Novig price read (live feed / REST fallback, batching, rate limit, the plan handed to the feed) is set up correctly; fix what isn't, with a test or a measurement.
  Found: set up as designed; the slowness is Novig refusing his key since 01:33 (423 on every signed route, v0.44.1 Diagnostics), so book reads fall back to the public routes (4-6/s on a per-IP limit, 429s seen at 01:38) instead of the key's 14/s, with no live websocket feed (it needs the key too); the key is retried every 10 min. Fixed: a scan that started while the key was already set aside (an auto-bet order or catalog read hit the 423 first) said nothing; now every such scan says "Novig key: <Novig's code and advice> This scan read Novig's public prices instead…" (`NovigSource.keyDown`, `Scanner.PUBLIC_PRICES`). Tests: `ScanKeyDownTest`, `NovigPublicClientTest` (+1, +2 asserts), 3 mutants killed. The real fix is Novig support unlocking the account.
- [x] AS4 Sister sportsbooks (one company, several state skins, e.g. Hard Rock NJ/IN/…): find where CNO and Vigilant count books (fair price, book counts, consensus); decide whether counting them separately is right (they copy one trading desk, so no); if not, count one company once, with a test.
  Found: Vigilant's own scanner already counts each company once (RESEARCH.md §46: its books leave out LowVig and betPARX/Unibet, BetOnline's and BetRivers' twins). CNO's book check did not: each CNO column (Hard Rock IN/FL/IL/OH, Sporttrade NJ/CO/IA/AZ/VA, BetMGM + BetMGM (ON), FanDuel + YourWay) was its own vote in the consensus, the "books pricing both sides" count and the "agreeing" count, so one company's four state sites could alone pass "3 books agree" and auto-bet's books rules. Not smart (one desk's line counted four times). Fixed: `CnoBooks.company` (a state site is its company's; FDYW is FanDuel), `check` averages a company's sites into one fair and counts companies; the Tracker's `BetInsight` does the same. The book sheet still lists every column. Tests: `SisterBooksTest` (4), 5 mutants killed; CnoBooks/AutoBet/SharpConfirm suites green.
- [x] AS5 Tests (mutation-checked), floor, ship with AQ/AR as one release, answer Tj with the Release link.
  Done: v0.44.2 (code 81) released 2026-10-02 15:29Z, APK verified (versionCode 81, cert AB:22:07:A8…). CI caught a flake from v0.44.0 (BackgroundTrimTest counted the scanner's own memory-pressure trims on CI's busy JVM); reproduced and fixed with a roomy MemoryGuard probe.

## Tj, 2026-10-02 ~15:31Z: "Make sure to finish all tasks that I sent in this chat history, but ignore the two messages that begin with the word urgent"

- [x] AT1 Check every request of this chat (03:50Z on) against TASKS.md and the Releases.
  Done: all ticked with their tests: 03:50Z full tests / lag / "9 min old" / APIs / Diagnostics (AO1-AO6, v0.44.0); 05:02Z the .txt.gz question (answered: the Release's R8 mapping file, not in the repo); 05:04Z "edge is gone" (AP1, v0.44.1); 05:46Z lag, manual min EV, auto-bet error (AQ1-AQ5, v0.44.2); 05:55Z Check odds now for every bet (AR1-AR3, v0.44.2); 06:14Z auto-resume, auto-bet on app switch, slow Novig scan, sister books (AS1-AS5, v0.44.2); 14:41Z/14:50Z resume after the second session (its half-change reviewed and finished). The two URGENT parts ignored as asked. Open by nature: AQ2's lag on Tj's own phone is measured by the new frame meter in his next Diagnostics file; the 423 account lock is Novig support's to lift.

## Tj, 2026-10-02 ~16:05Z (screenshot: CNO bet sheet, Juwan Johnson Under 39.5, "4 of 4 books agree", rows BetMGM and BetMGM (ON) both 50.0%): "I had auto bet running in the notifications in the background and when I opened vigilant it again turned off auto bet. I want the app never to turn off auto bet unless I turn it off. The default is auto bet off but only when opening the app after a restart or after I already turned off auto bet manually. / Review the screenshot, notice betmgm and betmgm (on). Is the app still double counting these? / And I'm getting no volume so far on auto bet with the option for each bet to be verified positive EV by a sharp book. Is this working correctly? Is it getting sharp book pricing?"

- [x] AU1 Auto-bet is never turned off by the app: off by default only on the first open after a phone restart, or because Tj turned it off himself (it stays off then). Not after a swipe out of Recents, an update, Android freeing memory, a crash, or a new screen. Find what turned it off this time (LaunchGate's rules) and change the rule, with tests.
  Done (RESEARCH.md §64.1): v0.44.2 reset after a swipe out of Recents (a service's onTaskRemoved) and after an update/force stop/crash: auto-bet in the notification with Vigilant swiped away, or a new version installed, both reset it. Now `LaunchGate` keys on Android's boot count (SharedPreferences "launch"): the boot receiver (`AutoScanReceiver.afterBootOrUpdate`) or else the first screen after a new boot resets and the next screen says so; nothing else does; the first look (this update) keeps what was on. Tests: `LaunchGateTest` (3), `LaunchResetTest` (7), `CycleRecorderTest` pin; 4 mutants killed.
- [x] AU2 BetMGM + BetMGM (ON) on the CNO bet sheet: check whether the numbers still count them twice (header count, fair, agreeing); make the sheet show that a sister site is counted with its company, with a test.
  Done (RESEARCH.md §64.2): not double counted: the screenshot's own numbers give 4 books / +104 (48.9%) only with BetMGM once (twice: 5 books, +103). The table now shows the company's fair on its first site and "same co." on the others, and the footnote names them (`BookTableText`). `SisterRowsTest` (3, the screenshot's sheet); 2 mutants killed.
- [x] AU3 Auto-bet's "verified +EV by a sharp book" option: find why it places nothing (which sharp books it wants, where their prices come from for CNO's and Vigilant's bets, whether they arrive at all); fix what is wrong, with tests.
  Done (RESEARCH.md §64.3): working: a real PinnWire board through PinnapiClient → SharpBooks → SharpConfirm finds Pinnacle's exact line for CNO-named props and game lines and judges them (`SharpRealBoardTest`, the real 2026-09-27 PinnWire answer). Low volume = Pinnacle lacks the exact line (Tj's screenshot has no Pinnacle column), Pinnacle's own devig vetoes, and/or Novig's 423 lock. Now on screen: Settings › Betting's "Sharp check since Vigilant started: N bets asked about, X confirmed, Y with no Pinnacle price for that exact line, …" (`AutoBettor.sharpLine`, `Status.sharp`). `SharpConfirmAppTest` (+1); 2 mutants killed.
- [x] AU4 Tests (mutation-checked), floor, ship, answer Tj with the findings and the Release link.
  Done: v0.44.3 (code 82). Floor 1,643 passed / 23 skipped (ship.sh gate); CI green on 99fa19d1; release.yml run 37034870341 green; Release confirmed (APK + mapping.txt.gz); APK checked (aapt2: versionCode 82 / 0.44.3; apksigner: BRIEF.md's certificate AB:22:07:A8…; dex has the new auto-bet text). 8 mutants killed across AU1-AU3. release.yml's Release text no longer says auto-bet switches off at every reopen (from the next Release on; v0.44.3's page still has the old sentence).

## Tj, 2026-10-02 ~16:45Z: "Do research and tell me the best settings to get volume but also a good chance at beating clv. For example, if 7 of 9 books agree that it is positive EV, is this good enough or is it a red flag because 2 books say no? Is it good enough to find positive EV through multiple non sharp books or should I require a sharp book? What is the lowest percent positive EV I should look for per bet to safely beat clv? What other settings or changes should I have to get some volume but also the best chance at beating clv"

- [x] AV1 Research (RESEARCH.md first, then sources): book agreement (7 of 9: dissent as a red flag, which books dissent), consensus of soft books vs a required sharp book, the lowest EV that beats CLV (by market type), other settings that keep volume with the best CLV odds; use any CLV evidence the repo has.
- [x] AV2 Answer Tj with recommended auto-bet settings mapped onto the app's actual settings; record it in RESEARCH.md.
  Done: RESEARCH.md §65 (his own CLV by edge band and market from the 2026-10-01 Diagnostics in INBOX.md; Kaunitz et al., Buchdahl, Data Golf, the MLB props sharpness study, Unabated). Proposed, not built (his call): sharp-veto mode, auto-bet market filter, agreement recorded at bet time + CLV split by it.

## Tj, 2026-10-02 ~17:01Z: "Use the research you just found, double check and make sure it is accurate. Do more deep research on clv and best settings for finding clv. Then adjust the app settings accordingly. Maybe make a preset section in the settings that sets all the settings to ideal settings for volume but safe clv scanning. Do deep research on the best settings for profit and clv, and make this a preset in the app. Also make it so I can make my own settings presets. The auto bet feature must abide the preset rules. Include at least the following: 1) sharp veto instead of requirement. Only skip a meet if the sharpest book for that market says it is not +ev. This must separate types of bets by which books are sharpest for those bet types. 2) record all types of information on the bet as placed, such as odds, books in agreement, time before game start, percent EV, and more. The more information logged the better. Then include this information for all bets in the diagnosis feature. The diagnosis file can be as large and comprehensive as needed for Claude to properly diagnose and fine tune the app. Remember the goal is profit and positive EV and clv. Log and save as much information for the diagnosis feature as needed to fine tune the app for this goal. Also, in addition to the share with feature, make sure the diagnosis prompt file for Claude is saved to my android downloads folder"

- [x] AW1 Double-check RESEARCH.md §65 (each source and number re-read; correct anything wrong) and deeper research on CLV and the settings that find it (sharpest book per market type, EV floors, time to start, odds range, market types, stake), recorded in RESEARCH.md.
- [x] AW2 Sharp veto (replaces "sharp must confirm"): skip a bet only when the sharpest book for that kind of bet, on the bet's book page (and the Pinnacle feeds where they have it), says it is not +EV. Which books are sharpest is per bet type (player props, game lines by sport, totals, …), from AW1. Tests.
- [x] AW3 Presets: a Presets section in Settings with a built-in research preset ("volume with safe CLV") that sets every relevant setting at once; Tj can save his own presets (name, save current, apply, delete). Auto-bet abides by the applied rules (the settings a preset sets are the ones the auto-bet reads; anything the preset adds, such as a market filter, is enforced in the auto-bet). Tests.
- [x] AW4 Record everything about each bet as placed (odds, fair, EV by CNO and by the book check, books pricing both sides and agreeing, which books dissented and by how much, sharp-veto verdict, time to start, league/market/bet type, stake and Kelly, preset in force, scanner, Novig liquidity, quote ages, …), kept with the bet (not overwritten by re-checks). Tests.
- [x] AW5 Diagnostics: every bet's record as placed plus its close/CLV/result in the file (as big as needed), with breakdowns by agreement, dissent, sharp veto, time to start, EV band, market type, preset; the file also saved to the phone's Downloads folder besides the Share sheet. Tests.
- [x] AW7 (found in the post-AW sweep, 2026-10-02 ~17:40Z) `BetKind.of` reads a bet's kind through the grader, which gives up on anything it can't grade: Player Interceptions, Sacks, Field Goals, Singles, Pitcher Outs, Blocked Shots, Shots on Target came out OTHER, so the Volume preset (props, moneylines, spreads) never auto-bet them and the veto judged them by Pinnacle/Circa instead of the prop books; quarter/period lines and 1st-5 moneylines came out OTHER; tennis set spreads/set totals (whole match) came out PERIOD. Fix with a by-words fallback and whole-match sets; tests. Also: a ✓ from a notification (no page) was split as "no dissent" in Diagnostics even when books disagreed (use two-sided − agreeing when the record has no page).
  Done AW1-AW5, AW7: RESEARCH.md §66.1-66.5 (two §65 corrections; §66.5 = the presets' values vs the defaults). Tests: SharpVetoTest (kinds, ranking, veto; AW7's ungradeable props/quarters/sets), PresetsTest + PresetsUiTest (built-ins, save/apply/delete, summary), AutoBetTest/AutoBettorTest (kinds and shortest odds enforced, veto in the auto-bet), SharpConfirmAppTest (alerts veto via SharpGate.unvetoedAlerts), AtBetTest + BetLedgerTest (record as placed, JSON line, splits; AW7 page-less dissent), DiagnosticsFileTest (EVERY BET + splits), DiagnosticsShareTest (Downloads/Vigilant via MediaStore), AutoBetUiTest (no-kinds warning). Floor before AW7: 1,693 (1,670 passed, 23 skipped). Mutants killed: 5 (AW7 fallback, SETS whole-match, page-less dissent, percent rounding, plus the earlier session's).
- [x] AW6 Apply the research preset as the app's recommended settings (Tj applies it with one tap; say what changes), full floor, ship, answer Tj with the Release link.
  Done: v0.45.0 (code 83). Floor 1,695 (1,672 passed, 23 skipped; ship.sh gate); CI green on 95b9097d; release.yml run 37043831635 green; Release confirmed (APK + mapping.txt.gz); APK checked (aapt2: versionCode 83 / 0.45.0; apksigner: AB:22:07:A8…). The preset is one tap in Settings › Presets (applying it changes: edge 3%→2.5%, both sides 2→3, odds any→−200..+150, kinds all→props/ML/spreads, stake rule→¼ Kelly, alerts 3%→2.5%, CNO rows 50→100, background every 10 min→30 s; see RESEARCH.md §66.5).

## Tj, 2026-10-02 ~17:55Z: "the settings menu in this app is getting very large and confusing. organize the settings menu intuitively. make it so everything is clear and easy to find. make any advanced setting have a plain English explanation, so even a beginner can understand the setting. look for and fix or remove superfluous settings or settings no longer needed. look for settings that contradict each other and fix them. maybe make the auto bet feature its own section instead of buried in the settings. consider and implement the best intuitive organization and modifications for the settings and features of the app"

- [x] AX1 Inventory every setting (tab, section, what it does, who reads it, default) into a map; mark superfluous (nothing reads it, or superseded), contradictory (two settings that fight), and unclear (no plain-English line).
- [x] AX2 Design the new organization (how betting apps and Android's own Settings group things: a few top-level groups by task, most-used first, advanced tucked under each); write it in TASKS.md / RESEARCH.md before moving code.
  AX1 findings (2026-10-02 ~18:05Z, inventory of SettingsScreen.kt / AutoBetUi.kt / ApiBettingUi.kt / ScanSettings.kt):
  - Superfluous: `apiMinEv` (nothing reads it since v0.44.2's "no minimum edge by hand"). `cnoEnabled`/`autoScanMinutes` are migration-only (keep).
  - Contradictions: (a) bet slip "$1" opens Novig's slip at $1 but the Bet sheet at "Amount a bet starts at" ($5); (b) "Only bets the books agree on" forces
    the book reads while "Green ✓ when books agree" shows off; (c) background auto-scan "CNO + Vigilant" with the scanner on CNO only (or "CNO" with Vigilant
    only) runs nothing / half; (d) auto-bet on with background scan off silently does nothing (only a line of text says so); (e) auto-bet's longest odds /
    smallest edge and the alerts' edge can be looser than CNO's own list filters, which then decide silently; (f) "Days ahead" shorter/longer than "Games
    starting within" (one shadows the other); (g) two different sections both called "Sharp books" (fair-odds sharp books vs the auto-bet/alerts veto).
  - Scattered: alerts' edge (Scan tab) vs alerts' sharp veto (Betting tab); the auto-scan interval edited in Scan and in Auto-bet; presets (own tab) set
    auto-bet, alerts and CNO rules; auto-bet buried at the bottom of Betting after the key and wallet.
  - Jargon without a plain line: devig, worst case, complete sportsbook, sharp weight, outlier guard, Kelly, fill the scan, re-use, keep awake, veto/confirm.
  AX2 design: Auto-bet becomes a bottom tab (Novig, CNO on): status + one-tap fixes, presets, what it bets, sharp veto, how much, how often, notifications,
  activity. Settings becomes a home list (search box + categories with live one-line summaries, Android-Settings style) opening pages with a back arrow:
  Scanning · Alerts · CrazyNinjaOdds list · Widget & mini window · +EV feed & scan size · Fair odds & sources · Betting & Novig account · API usage & keys ·
  Diagnostics & about, plus an "Auto-bet & presets" row that opens the tab. Advanced settings under "Advanced" headings, each with a plain-English line.
  A settings index (title, page, keywords) powers search and a test that every indexed setting is on its page.
- [x] AX3 Auto-bet as its own section (its own screen or tab outside Settings, with its presets, rules, limits, sharp books and status in one place).
- [x] AX4 Move the settings into the new groups; every advanced setting gets a plain-English line a beginner understands.
- [x] AX5 Remove or fold superfluous settings (keeping saved values migrating safely); fix contradictions (one setting wins clearly, or the UI prevents the conflict, and says so).
  Done AX3-AX5 (tests): Auto-bet tab `AutoBetScreen` (bottom bar, Novig with CNO on; badge while running, "!" when halted): status + one-tap fixes (`AutoBetText.fixFor`: SettingsFixesTest), presets, what it bets (plain lines + Shadowed warnings), sharp-book veto (`SharpVetoSection`), how much, how often, notifications (AutoBetUiTest, ScreenshotTest.autoBetTab). Settings: home list + search (`SettingsIndex`) + 9 pages with back arrow (SettingsPagesTest: every section on exactly one page, every indexed setting on its page, search, rotation, Add money); plain-English lines on devig, EV, Kelly, sharp books, CLV, true odds, complete sportsbook, both sides. Contradictions fixed: (a) $1 sheet = $1 (BetAmountTest, SettingsFixesTest), (b) ✓/only-✓ nested (SettingsFixesTest), (c) background scan one switch (SettingsFixesTest), (d) auto-bet off-background warning + fix, (e)(f) Shadowed (SettingsFixesTest), (g) "Books that count as sharp" vs "Sharp-book veto". Removed: apiMinEv (PresetsTest reads an old file with it). Feed/CNO Settings buttons open their page. Mutants killed: 6/6 + 1 (only-✓).
- [x] AX6 Tests (UI tests for the new layout, find-every-setting test, migration test), full floor, sweep, ship, answer Tj with the Release link and what moved where.
  Done: v0.46.0 (code 84). Floor 1,710 (1,687 passed, 23 skipped; ship.sh gate); CI green on 6514a7e9; release.yml run 37048789265 green; Release confirmed (APK + mapping); APK checked (aapt2 versionCode 84 / 0.46.0; apksigner AB:22:07:A8…).

## Tj, 2026-10-02 ~18:50Z: "Research and see if it is possible to arbitrage bet my own bets in novig based on timing. For example, if I place a bet early and it significantly shifts a certain way, I could take the other side of the bet later on and guarantee a profit no matter which side of the bet wins. See if this is plausible in vigilant app, how it would efficiently scan for these opportunities, and if it is plausible, build the system. It must guarantee profit because I will put real money on it. Make sure it takes full advantage of the apis I have and it finds proper arbitrage opportunities based on the bets I already placed. If it is plausible and you build it, include an option to auto bet these bets in addition to whatever the auto bet system already does. Then make a filter option for the stats and bet tracker where I can select novig only. What this will do is find the current novig odds for each of my open bets and show the percent EV compared only from novig odds, filtering out other sports books. For example, if I placed a bet two days ago, it will find that same exact bet odds currently on novig and do the already in place stats and ev calculations that this section already does, but only for novig. Make sure it is smart and doesn't waste any api usage on other sports books if not needed when I select this filter, and also if I already just scanned without using this filter and there is still fresh novig odds for all my bets, it doesn't need to rescan. It can just filter"

- [x] AY1 Research (NOVIG_API.md first: orders, fills, fees, positions, voids/pushes; then the exchange-hedging literature): can an open Novig bet be locked in by buying the opposite side of the SAME market later, with a profit guaranteed whichever side wins (fees, partial fills, price moves between quote and fill, pushes and voids, limits)? How it compares with letting a +EV bet ride. Record in RESEARCH.md; answer plausible or not.
  Done: RESEARCH.md §67: plausible and exact on Novig (same market, FOK at a limit worked out worst-case, fees in, positions checked; FMV pays the same; push = refund; in-game locks skip pushable lines). Locking cashes in the CLV already earned at a cost of ~half the spread (+ in-game fee): certainty, not extra EV. API bets only (app bets can't be confirmed).
- [x] AY2 If plausible: the lock-in math (pure, data): from the bet's real fills (contracts, cost, fees) and Novig's order book for the other side now, the hedge size range that guarantees profit both ways, the equal-profit size, and the limit price; never anything that could lose in either outcome. Tests (property tests over prices, sizes, fees).
  Done: data/novig/trading/LockIn.kt (plan: equal-profit top-up of the short side, limit = deepest level needed ≤ the ceiling, worst case at the limit with the most fee, never a part lock; pushable lines skipped while a fee is charged; holdValue). LockInTest (10, incl. a 20,000-case property test: no plan pays under the minimum whichever side wins, at any fill ≤ the limit, or on an FMV void). Mutants killed 3/3.
- [x] AY3 Scanning for them efficiently: only Tj's open API bets (real fills), only Novig's book for the other side of each (free public reads, rate-limited; the websocket where on), on every Tracker open / Check odds now / background cycle, without other books' APIs. Tests.
- [x] AY4 Placing a lock: a "Lock in profit" action on the bet (shows both outcomes' profit) placed through the API as a limit order that can't fill at a losing price; partial fills handled so no outcome loses; the pair recorded and linked in the Tracker. Tests with fake Novig.
  Progress (not ticked: UI and app wiring still to do): data side done: TrackedBet.lockFor/isLock, BetTarget.lockFor, LockPositions (holdings from open API bets; mismatch vs Novig positions), ApiBetPlacer.placeLock (fresh book ≤15 s, LockIn on it, positions must match exactly, one FOK at the limit, logged as a lock; locks exempt from the daily limit). LockPlacerTest (5) + mutants 3/3.
- [x] AY5 Auto-lock: an option on the Auto-bet tab (off by default) to place locks automatically, with its own minimum guaranteed profit and limits, alongside the existing auto-bet. Tests.
  Done AY3-AY5: LockScanner (app: only markets the subaccount holds via the Tracker's open API bets, one Novig book each, market details cached 2 min; no other book's API), scanned when the Tracker opens, after Check odds now, after a lock, and each background cycle with auto-lock on. Lock by hand: the bet sheet's 'Lock in a profit' card (both outcomes, ride vs lock, confirm; placed only if it still pays what was confirmed). Auto-lock: Auto-bet tab (off by default; min profit % of the market's stake; in-game switch), run after auto-bet in the cycle, notification per lock, 1 min retry wait, 10 min after an unconfirmed answer. Tracker badge (🔓 Lock / 🔒 Locked); locks count in money but not record/EV/CLV (TrackerStats.locks). Tests: LockAppTest (4), LockPlacerTest (6), LockInTest (10); mutants killed 3 (LockIn) + 3 (placer) + 3 (app).
- [x] AY6 Tracker / stats filter "Novig only": each open bet's EV now from Novig's own current odds for that exact bet (devigged from Novig's two sides), stats and EV calculations as today but on that basis; reads only Novig (no other books' APIs); reuses fresh Novig odds from a recent scan instead of reading again. Tests.
  Done: TrackedBet.novigFair/novigAtMs/novigClose/novigCloseAtMs; NovigNow (mid of Novig's bid and offer; apply keeps the last pre-start read as Novig's close; stale = missing or > 2 min; read asks only Novig's books for stale bets' markets, nothing when all fresh, all on Check Novig now; view prices open bets from Novig alone, cuts books to Novig, CLV from Novig's close). A normal Check odds now stores Novig's mid from the same book read (OpenBetPricerTest), so switching right after needs no read. Tracker chip 'Novig only' (kept in settings) + 'Check Novig now'. Tests: NovigNowTest (4), TrackerNovigOnlyTest, OpenBetPricerTest; mutants 2/2. Diagnostics: settings line + EVERY BET carries lockFor/novigFair/novigClose.
- [x] AY7 Full floor, sweep, ship, answer Tj (plausible or not, how it works, the link).
  Done: v0.47.0 (code 85). Floor 1,736 (1,713 passed, 23 skipped); CI green on 1738e581; release.yml run 37054545528 green; Release confirmed; APK checked (aapt2 85 / 0.47.0, apksigner AB:22:07:A8…). Sweep fixes: lock live-fee from the earlier start time (LockPlacerTest), Novig-only chip moved out of the pinned bar (StickyHeadersTest).

## Tj, 2026-10-02 20:06Z: "Add options to remove arbitraged locked bets out of stats and bet trackers. It makes no sense for me to track a bet that is already cashed out. Maybe maybe a stat tracker for amount and percentage of bets locked in and the total profit and percentage of profit for those bets. Also many open bets are not finding the current novig odds for the same exact bet. This may be because it is not currently offered, but make sure the feature is coded properly."

- [x] AZ1 Investigate why open bets miss Novig's current odds in the Novig-only filter (and Check odds now's Novig read): bets with no Novig market/outcome id recorded (CNO ✓, by hand, other scanners), markets Novig's public catalog no longer lists, 3-way / more-than-two-outcome markets, one-sided books, batch read failures, ids that don't match the book's. Fix every real coding miss; for what's truly not offered, say why per bet instead of a blank.
  Done: the misses were real coding gaps, not just lines Novig doesn't offer. (1) A CNO/ParlayAPI ✓, an alert ✓ and an imported mark log no Novig market id (logCno never got one; one betFinder.find at log time was the only fill, never retried), and NovigNow.priceable skipped every such bet silently, so the filter said "tap Check Novig now" forever. (2) Replace's rememberOutcome kept only the side, never the market. (3) A one-sided book (bids on the bet's own side only, common on props) gave no price. (4) No reason was kept per bet. Fixes: NovigBetFinder.locate (the side on record names the market, else the strict word match; says why not: no league, game not listed, exact line not offered, Novig busy); NovigIds re-looks up every open Novig bet missing ids (Novig's catalog only, 10 min between misses, Check Novig now / Check odds now look again at once) before the Novig-only read and before Check odds now's own pass; Replace stores the market too; NovigNow.mid uses the bid alone when nothing is offered; NovigNow.read gives each unpriced bet a reason (book unread / market no longer listed / side not the market's / nothing bid or offered), kept on the bet (TrackedBet.novigWhy) and shown on its card and in the Novig-only line (priced · no Novig price now · not read yet) and the toast. Tests: NovigBetFinderTest (locate), NovigIdsTest (2), NovigNowTest (one-sided + reasons), TrackerTextTest (Novig-only line, toast).
- [x] AZ2 "Hide locked bets" option (Tracker and Stats): a market locked in (both sides held equally, the lock and the bets it locks) is cashed out: hidden from the Tracker lists and left out of the stats when on; the money still adds up honestly. Off/on kept in settings.
  Done: LockedBets (data): a market is locked when API bets hold its two outcomes with equal contracts (worked out from the bets' fills, settled markets too; a ✓ mark never counts; unequal = partly locked, still riding). ScanSettings.trackerHideLocked (ON by default: Tj said tracking a cashed-out bet makes no sense): every bet of a locked market leaves the Tracker's lists, counts, CLV card and stats; a "Hide locked bets" chip at the top of the Tracker (once anything is locked) and a switch on the Auto-bet tab's lock section. A bet's sheet stays open when it's locked from that sheet. Tests: LockedBetsTest (4), TrackerLocksTest (hide/show).
- [x] AZ3 Lock stats: how many bets (and what % of bets) were locked in, the total profit locked and its % of what was staked in those markets.
  Done: Tracker Stats "Locked in" card (above the rest, per period, whether hidden or not): bets locked (picks, locks aside) and their % of the period's picks, profit locked (graded markets: what the results paid; open: contracts × $0.01 − both sides' spend), its % of what both sides staked, how much is graded, partly locked markets, and whether the rest of the Tracker counts them. Diagnostics: the same line plus why open Novig bets have no Novig price. Tests: LockedBetsTest (stats, period, graded, void), TrackerLocksTest (card numbers); mutants 8/8 killed across AZ1-AZ3.
- [x] AZ4 Tests (data + UI), mutants, full floor, sweep, ship v0.48.0, answer Tj with the link and what the Novig-odds misses were.
  Done: v0.48.0 (code 86). Floor 1,748 (1,725 passed, 23 skipped); mutants 8/8 killed; CI green on 511ed2bf; release.yml run 37063401416 green; Release confirmed (APK + mapping, cert AB:22:07:A8…). Sweep: Tracker Stats screenshot with the Locked in card checked (4l_tracker_locked_in.png).

## Tj, 2026-10-02 ~21:15Z: "Run full tests on this app, make sure all the math is right and that the stats and closing lines are gathered correctly and reflect accurate data"

Full tests (.claude/skills/test-protocols "Full tests"), with Tj's emphasis on math, stats and closing lines.
- [ ] BA1 Floor: `bash tools/test.sh` (exit code AND output), `-Pscreenshots` and look at every Tracker/stats PNG.
- [ ] BA2 Math audit vs BRIEF.md locked decisions + NOVIG_API.md: engine Odds/Devig/FairValue/Fees/EvMath; a bet's price, cost (live taker fee), EV at bet, EV now, profit if won, FMV, Kelly; API fills (paid/fee/contracts → price, cost, stake); lock math (LockIn, LockedBets).
- [ ] BA3 Stats audit: BetTracker.stats (profit, ROI, record, expected vs actual, luck SD, open money, outliers, voids, locks), TrackerBreakdown, CheckOddsStats, LockStats, period filters, Novig-only view; each number checked by hand against a fixture.
- [ ] BA4 Closing lines audit: when a close is read (ClosingLine due/window/final), what counts as a true close, CLV math, ClvStats (periods, outliers, by source), CloseBackfill (ParlayAPI Pinnacle, ESPN, Novig trades), observe()/applyPricing/mergeReads writing closingFair, Novig's own close (NovigNow), bets placed after the start.
- [ ] BA5 Wider sweep (best effort): the rest of the test-protocols surface, stale copy, races that lose data.
- [ ] BA6 Fix everything found with a failing-first test each, full regression, ship, answer Tj with the link and what was found.
