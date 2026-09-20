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
      `engine`+`data` compile and their 54 tests pass for real, here. The
      `app` module cannot be locally verified (no Android SDK in this dev
      container — confirmed, recorded in BRIEF.md as a standing fact, not
      re-discovered next session) — added `.github/workflows/ci.yml`
      (installs a real Android SDK via `android-actions/setup-android`,
      runs all unit tests, then `assembleDebug`) so the `app` module gets
      real compile verification from the one place that actually has an
      SDK, matching this project's own release model. **Not yet confirmed
      green** — needs a push and a check of the Actions run; do that before
      telling Tj the app module itself compiles, don't assume it from
      review alone.
- [ ] Push, confirm CI is actually green (fix anything it finds — this
      container could not catch an `app`-module compile error, so treat a
      first CI failure there as expected-possible, not alarming).
- [ ] Tell Tj plainly what he needs to do to go from this sample-data beta
      to live data: (a) sign up for a free The Odds API key himself
      (self-serve, no card needed for the free tier) and (b) contact Novig
      directly from his existing account to ask about official API access
      (RESEARCH.md §4.1/§10 — still unconfirmed whether that's free or
      what the process is). Neither blocks trying the beta today.
- [x] Checkpointed multiple times through this (engine, then data, then
      app+CI) rather than only at the end.
