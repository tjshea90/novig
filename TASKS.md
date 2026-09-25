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
- [ ] A4 `app`: OddsJam-style UI — +EV feed cards (EV%, selection, Novig
      price vs fair, liquidity at price, Kelly stake, market width, age),
      filters (league, market, min EV), detail sheet with per-book odds,
      auto-refresh of Novig books while visible (lifecycle-aware, stops in
      background), manual refresh for reference odds (credit-limited),
      Settings: fair-odds source/blend/devig method/bankroll/Kelly.
- [ ] A5 retire GraphQL/proxy path (client, proxy settings, direct toggle).
- [ ] A6 CI green, ship v0.4.0, send link.

### Milestone B — Novig API key (opt-in, "soon")
- [ ] B1 `data`: `NOVIG-V3` signer (Ed25519 + P-256), tested against
      Novig's published signing vectors.
- [ ] B2 `app`: Settings → Novig API: paste key ID + PEM (encrypted at rest),
      "Test connection" via `POST /v3/echo` with plain-English 401/451/423
      messages.
- [ ] B3 websocket live book feed (signed `GET /v3/ws`, `book` channel per
      event) used automatically when a trading/trading::read key is set.

### Milestone C — tracker + full tests + ship
- [ ] C1 bet tracker (log a bet from a card, settle, P/L + CLV-style stats),
      stored locally.
- [ ] C2 full tests per CLAUDE.md (every module, efficiency, battery),
      extend CLAUDE.md's test protocol with the real screens.
- [ ] C3 ship final version, send link.
