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
- [ ] Two live hypotheses for a 403 specifically (not 401, which would
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

- [ ] Stage 1, no key needed: add a `NovigV3PublicClient` (`data` module)
      over `/v3/public/...`. Use executable taker prices from the book
      (1 − best opposing bid, with depth) and per-market `fee`. Make it the
      default Novig leg. Retire the GraphQL/proxy path and its Settings UI.
      Fix `Fees.kt` for NFL/MLB/NCAAF futures (0.06, charged pregame).
- [ ] Stage 2, needs Tj's management key once: `NOVIG-V3` signer
      (unit-tested against Novig's 30 signing vectors), an in-app
      "Connect Novig" setup (import management PEM + key ID, then echo,
      open a "vigilant" subaccount, then create a `trading::read` key held in
      Android Keystore (P-256), then forget the management key), and a
      websocket `bbo` subscription per selected event for real-time repricing.
- [ ] Reference leg: Tj decides between staying on The Odds API free tier
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
- [x] A2 `engine`: fair-odds source = SHARP / MARKET_AVERAGE / BLEND
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
- [ ] H2 Data: books agreement: per-book +EV count, new SPLIT verdict (green ✓ = CONFIRMED = 3+
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
- [ ] P2 Research: every odds API / sportsbook / exchange with a free tier or free public data NOT
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
