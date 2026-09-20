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

- [ ] `data` module: `Sport`/`SportsCatalog` (curated list of The Odds
      API sport keys, pure Kotlin so it's shared by `EvScanner` and the
      UI picker without an Android dependency).
- [ ] `EvScanner` takes a `List<String>` of sport keys instead of one —
      scans reference odds for every selected sport, merges the results,
      matches against Novig's board same as before. Returns immediately
      (no repository calls at all) when the list is empty. Update
      `EvScannerTest` for the new constructor shape.
- [ ] `ScannerViewModel`: replace the `init { rescan() }` auto-scan with
      an explicit `Idle` state — nothing loads on app open. Add
      multi-select sport state (`toggleSport`); `rescan()` only performs
      a scan when at least one sport is selected, and is the single path
      both the refresh button and the pull-to-refresh gesture call.
- [ ] `OpportunitiesScreen`: a sport picker (multi-select chips) above the
      opportunity list, wrap the content in Compose Material3's
      `PullToRefreshBox` for the swipe-down gesture (in addition to the
      existing FAB refresh button — both should trigger the same
      `rescan()`), and a clear "select a sport, then refresh" idle state
      instead of showing anything before the user acts.
- [ ] Verify what this container can (`:engine:test :data:test`), push,
      confirm CI green for the `app` module — the Compose
      Material3-pull-to-refresh usage is new API surface this container
      can't compile-check locally.
- [ ] Checkpoint, then trigger the release GitHub Actions workflow
      (`workflow_dispatch`) via the API, confirm it goes green, and send
      Tj the new Release link (plain tappable text, not a code block —
      CLAUDE.md's standing rule).
