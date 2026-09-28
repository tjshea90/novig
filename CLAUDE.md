# novig — working agreement

Tj's Android app, targeting Android 16 (API 36) and optimized for a Moto G
2026, built to profit using the Novig sportsbook. Worked on across Claude
Code sessions and accounts — a new session may pick this up on a different
device, a different account, or simply after a previous session's usage ran
out mid-task. Everything below exists to make that handoff lossless.

The handoff mechanism (raw-inbox capture, autosave hook, deliberate
checkpoint, secret scan, milestone ship gate) is ported from and adapted for
this project from the same Claude-Code-native pattern already proven on
this account's other two repos, **fantasy-football** and **Portfolio** —
each of which learned pieces of it the hard way, on real incidents, before
this project ever existed. Where a rule below cites one of them, that
incident is real and already happened *there*; it is adopted here
preemptively, before it has a chance to happen on this project too, not
because it already has.

**The project's own standing rules** (what's actually decided about the
platform, target hardware, and purpose; what's still open) are written in
full in `BRIEF.md` and printed at session start by `bootstrap.sh`. As of
this writing (2026-09-25) the app is **Vigilant v0.4+**: Kotlin + Compose,
modules `engine` / `data` / `app`, built and released by GitHub Actions. Do not
treat `BRIEF.md`'s remaining TBD sections as settled just because they're
written down; they're marked TBD on purpose.

## Vigilant MGM is dormant — every request is for Vigilant (Novig) unless Tj names MGM

**Standing instruction from Tj (2026-09-27, verbatim):** "from now on, everything in this repo and anything I
ask you to do will always be for the regular vigilant app for novig, unless I explicitly request something for
novig mgm. Novig mgm should be dormant and no changes made at all unless I ask for it. All future work and
versions and GitHub releases will be for regular vigilant for novig only unless I say otherwise."

What that means in practice:
- **Every task, version and Release is Vigilant's** (`app`, `com.tjshea.vigilant`). Never read a request as
  covering Vigilant MGM unless Tj names it.
- **`mgm/` is frozen at v0.17.0** (its last APK is on that Release). Don't edit anything under `mgm/`. The
  module isn't in the build at all unless you pass `-Pmgm` (`settings.gradle.kts`), so `./gradlew test`,
  CI, `ship.sh` and `release.yml` build, test and publish Vigilant alone.
- **The shared code stays shared.** `app`'s sources still carry `AppBook` switches and `MgmAppTest` still runs
  in `app`'s suite: they cost nothing and keep a revival cheap. Don't add MGM work to keep them current;
  a new Vigilant feature doesn't need an MGM variant.
- **To revive it** (only when Tj asks): build with `-Pmgm`, add `mgm` back to `release.yml`'s build and
  signature check, and re-read RESEARCH.md §25.

## FIRST ACTION OF EVERY SESSION — install the hooks, don't assume they exist

This account runs several repos side by side under one Claude Code
container (`novig`, `fantasy-football`, `Portfolio`). Confirmed on this
account's other repos: Claude Code on the web does **not** load hooks from a
repo's own `.claude/settings.json` when the repo sits in a subdirectory of
the session root — `CLAUDE_PROJECT_DIR` is unset and the harness resolves
one project root for the whole container. A repo's own settings file is
kept as a fallback (for the case this repo is ever opened standalone), but
it is not what protects a session running inside this container.

**So, before anything else this session, run this once:**
```bash
bash tools/install-hooks.sh
```
It finds the session root itself, MERGES the hooks into whatever settings
file is already there (preserving any other project's permissions, env, and
hooks), and says what it did. Idempotent — safe to run any time.

**Every hook this installs is an absolute path into THIS repo's own `tools/`
directory, never a glob over sibling directories.** Portfolio's own version
of this pattern has each hook scan every repo under the session root and run
that repo's `resume.sh`/`autosave.sh` in turn, aggregating them into one
combined briefing — a reasonable design there, since Portfolio owns all of
those repos equally. It is the wrong shape here: `fantasy-football` and
`Portfolio` are **read only** for this project's work, and `autosave.sh`'s
whole job is `git add -A && git commit && git push`. Running it inside
either of those repos' real checkouts — even as an unintended side effect of
infrastructure built for this repo — would be exactly the kind of change to
another repo this project must never make. So this repo's hooks touch
nothing but itself, full stop; see the header of `tools/install-hooks.sh`
for the reasoning in full.

**If you forget, two things catch it for you** — `tools/ckpt.sh` and
`tools/resume.sh` both repair the hooks before they do anything else. So a
session that never reads this section still gets the safety net back the
moment it checkpoints or resumes. That is a backstop, not a reason to skip
this step: until something calls one of them, nothing is being saved.

Verification is not instant — firing can lag a tool call or two behind the
edit rather than commit synchronously with it. Check over a few tool calls,
not just the next one:
```bash
# after 2-3 real edits, in a LATER tool call:
git log --oneline -3   # expect fresh "auto-checkpoint:" commit(s) in there
```
If several edits go by with none appearing, the hooks aren't firing at all —
fall back to running `bash tools/ckpt.sh "did" "next"` after every step BY
HAND for the rest of the session, and say so plainly; that becomes the only
safety net.

**Whether it is currently installed, without changing anything:**
```bash
bash tools/install-hooks.sh --check && echo installed || echo MISSING
```

## Starting a session

A `SessionStart` hook has already run `tools/resume.sh`, which pulled the
latest from GitHub and printed `CHECKPOINT.md`, `TASKS.md`, the tail of
`INBOX.md`, and the rules into your context. **Do not re-run bootstrap, do
not re-plan, do not re-read finished work.** Continue from **Do this next**
in `CHECKPOINT.md`, or the first unticked `[ ]` in `TASKS.md`.

**Check the `INBOX.md` tail against `TASKS.md` before assuming you know the
whole job.** `INBOX.md` is a raw, guaranteed-captured log of every message Tj
sends (see "When Tj asks for something new" below) — if it names something
`TASKS.md` does not yet cover, that is a request a previous session never
got around to writing down, not a stale duplicate.

**If you did not see that briefing, run `bash tools/resume.sh` before doing
anything else, and tell Tj it did not fire** — it means the hook is not
running, and the automatic saving below almost certainly is not either, so
this session is working without a safety net.

Read the two warnings it can raise:

- **"INTERRUPTED MID-CHANGE"** — the last session was killed by a usage cap
  part-way through a step. The tree is clean, but only because a hook
  committed a half-written change. `CHECKPOINT.md` describes the state
  *before* that, so it is stale. Run the `git diff` it names, finish that
  change, checkpoint it — then start anything new.
- **"UNCOMMITTED WORK IS PRESENT"** — the same thing, one step worse: not
  even the hook got to it. `git diff` is what was in flight.

## Branches — `main` is the only source of truth

Claude Code on the web puts each session on its own auto-generated branch
rather than reusing one — a platform decision this repo cannot bind from the
inside. On this account's other two repos, that exact behavior has already
caused real damage: on fantasy-football, four sessions in one day forked
from the same point on `main`, each thinking it was the sole continuation of
that project's working agreement, and shipped incompatible versions with
`main` never moving — untangling it cost most of a day. On Portfolio, 565
commits sat on a stranded feature branch while `main` still showed the
original README, for the length of a whole migration, before anyone
noticed. Treat that risk as equally real here from day one, not as something
to start worrying about only after it happens on this project too:

- **Before starting real work, check whether `main` has moved past your
  starting point** (`git log origin/main` vs your branch's merge-base). If
  it has, someone else's finished work is sitting there uninherited — pull
  it in before adding more on top, the same way you would if
  `CHECKPOINT.md` had described an interrupted session.
- **When you finish something worth keeping, get it onto `main`.** `push.sh`
  fast-forwards `origin/main` to match on every successful push,
  automatically, regardless of which branch is checked out — that's the
  actual fix, and it needs no action from you. `tools/resume.sh` reports
  `origin/main`'s sync status on every session start as a sanity check on
  that automation.
- **If you discover another branch with real, uninherited work on it** (not
  just an old abandoned experiment), tell Tj plainly before merging it in
  blind — a design decision on one branch may contradict one just made on
  another. Reconciling divergent work is a judgment call each time, not
  something to automate away.
- The most reliable way to avoid a new branch appearing at all: Tj resuming
  the *same* Claude Code session/conversation rather than starting a new one
  from claude.ai/code. That is a habit on his end, not something this file
  can enforce.

## When Tj asks for something new

**Write the request into `TASKS.md` in his own words, as unticked `[ ]`
boxes, and checkpoint it before writing any code.** Until it is written into
`TASKS.md` as real steps, nobody has actually planned the work — a message
sitting in a chat window is not a task list.

**You do not have to race a usage cap to get the raw request itself onto
disk.** A `UserPromptSubmit` hook (`tools/capture_inbox.sh`) already writes
every message Tj sends to `INBOX.md`, verbatim, and commits+pushes it the
instant it arrives — before you have read a single file. This closes a real
gap found on fantasy-football (2026-09-15): a session spent its whole budget
reading the codebase for a new feature, was cut off before ever writing the
request to `TASKS.md`, and the `PostToolUse` autosave hook (which only fires
on `Edit|Write|NotebookEdit|Bash`) never fired either, because a pure
research stretch trips none of those. Nothing reached disk, anywhere, and
the next session opened cold with no way to know the request had ever been
made.

This does not lower the bar on writing `TASKS.md` promptly — a raw inbox
entry is not a plan, and a long research stretch before turning it into one
is still worth avoiding. It means a forgotten or interrupted `TASKS.md` write
is a recoverable gap instead of a total loss: the exact words are always on
`INBOX.md`, and `resume.sh` prints its tail every session specifically so
this is never missed.

Tick a `TASKS.md` box only when it is written, tested and committed, and
name the test that proves it. The next session will not re-verify a ticked
box.

## Saving work — three levels, plus a backstop underneath the first one

**0. The raw message itself (hooks — happens without you, before you do
anything).** `tools/capture_inbox.sh` (a `UserPromptSubmit` hook) appends
every message Tj sends to `INBOX.md`, verbatim, and commits+pushes it the
moment it arrives — before any tool call, before any judgment about whether
it is "worth" saving yet. This is not a substitute for level 1 below or for
writing `TASKS.md`; it exists only so the exact words are never lost even if
nothing else gets written down before a usage cap hits. You do not call it,
and you should not need it if you write `TASKS.md` promptly — treat it as
the net under the net.

**1. Automatic (hooks — happens without you).** `tools/autosave.sh` commits
and pushes after every file edit and every bash command, and again on Stop.
It has no gate and runs no tests: a broken half-edit that is committed is
recoverable, the same edit uncommitted dies with the session. This is what
survives a usage cap landing mid-change. You do not call it.

**2. Deliberate — `bash tools/ckpt.sh "what I just did" "what comes next"`.**
**Run this after every completed step, not at the end of the session.** The
autosave hook can preserve your *files* but it cannot know your *intent* —
"what comes next" is the one thing no diff can reconstruct and the one thing
the next session most needs. It runs whatever fast checks exist (discovered,
not hard-coded — see the comment in `tools/ckpt.sh`), records green or red
honestly without gating, rewrites `CHECKPOINT.md`, commits and pushes.
Skipping it is how a handoff loses a day even though every file was saved.

**3. Milestone — `bash ship.sh "note"`.** The release gate: fast checks, the
full Gradle test suite (everything CI runs when a local SDK exists, per
BRIEF.md build trap 6), versionCode strictly above every BUILDLOG.md entry,
then push. Then trigger `release.yml`, confirm the Release, and run
`tools/record-release.sh` (see "Releasing").

## Before your usage runs out

You will usually get no warning, which is why level 2 is per-step rather
than per-session. If you *do* notice you are running low, spend the
remaining budget on `tools/ckpt.sh` with an honest, specific "what comes
next" — not on one more edit.

## Large sessions

A `PreCompact` hook (`tools/toobig.sh`) fires when the conversation has grown
enough to auto-compact. Compaction rewrites earlier messages rather than
shrinking what a warm cache already covers, so the cheaper move is usually:
checkpoint, then start a fresh session — `tools/resume.sh` rebuilds
everything a session needs from GitHub in well under a hundred lines.

## Test protocols — "light tests" and "full tests"

**Standing instruction, not a `TASKS.md` job — this section IS the
explanation, so no clarification is needed when Tj asks.** Whenever Tj says
"light tests"/"light test"/"light testing", or "full tests"/"full
test"/"full testing"/"comprehensive tests" (any close wording), run the
matching protocol below immediately, on any account, from any cold start,
with zero further explanation required. Neither protocol is a `TASKS.md`
step to tick off; both end with `tools/ckpt.sh` recording what was found and
fixed (a full test that finds nothing worth withholding ends with
`ship.sh` per "Releasing" below — a light test does not ship on its own
unless Tj asks).

**The app's real surface (v0.4.0+), so "sweep the whole app" is concrete:**

- **Tabs:** +EV feed (`FeedScreen` + `OpportunitySheet` detail), CNO (`CnoScreen`), Games
  (`GamesScreen`: board + per-game line table), Tracker (`TrackerScreen`: P/L,
  ROI, CLV), Settings (`SettingsScreen`).
- **Subsystems:** fair-odds math (`engine`: `FairValue`, `Devig`, `Fees`,
  `EvMath`); Novig data (`data/novig`: `NovigPublicClient`, `NovigText`);
  reference odds (`data/reference/`: `PinnapiClient` (Pinnacle: PinnWire keys first with
  `include_specials` player props → `PinnacleProps`, then pinnapi's), `PolymarketClient`, `KalshiClient`,
  `PropLineClient` + `PropLinePropsSource` (30 books, 1,000/day free), `TheOddsApiClient`,
  `OddsApiPropsSource`; RESEARCH.md §22; where two carry the same books the second is only a fallback:
  `ReferenceSource.fallbackFor`/`needed`, `ScanContext.covered`, `Scanner.covering`, RESEARCH.md §23; PropLine's relayed Novig
  prices order Novig reads, `RefSnapshot.novig`/`Scanner.preview`, §23.6; **no book quote over 5 minutes old ever prices** (10 on a game more than 3 hours off since v0.19.3,
  `Freshness.maxAgeMs`, RESEARCH.md §30.2):
  `data/scanner/Freshness`, `Opportunity.fairAsOfMs`/`fairIsOld`, `UiState.feedAt`/`cnoTooOld`/`booksAt`, RESEARCH.md §24); matching and pricing (`data/match/TeamMatcher`,
  `data/match/PlayerNames`, `data/scanner/PropStats`,
  `data/scanner/Planner` + `Pricing`); manual scans and pacing (`data/scanner/Scanner`,
  `data/novig/RateGate`, `MainViewModel.scan`); keys, quotas and meters (`data/keys/`:
  `FileApiKeyStore`, `QuotaPolicy`/`UsageMeter`/`KeyPool`, `UsageViews`; `ui/UsageMeters`);
  persistence (`data/store/JsonFileStore`, `data/tracker/BetTracker`, `api_keys.json`,
  `usage.json`, backup rules in `res/xml`); background scans (`data/scanner/ScanRunner`,
  `app/ScanService` foreground service + notifications, `app/ScanText`) and streaming results
  (`Scanner`'s book pump, `ScanResult.freshSinceMs`); quick rechecks and bet-sheet extras
  (`Scanner.recheck`, `MainViewModel.recheck`, `Opportunity.makerBid`/`priceIsOld`,
  `data/scanner/CrossCheck` link to CrazyNinjaOdds' devigger; RESEARCH.md §16); the mini window
  over Novig (`app/MiniWindow` picture-in-picture params and buttons, `ui/MiniFeed`,
  `MainActivity` PiP wiring; RESEARCH.md §17); CrazyNinjaOdds' list (`data/cno/`: `CnoView`,
  `CnoPage`, `CnoClient` (filters posted in CNO's form), `CnoFeed` (real time/5 s/15 s, ≥3 s
  apart, only while on screen), `CnoChecks` (the app's own screen), `CnoBooks` (a bet's books +
  Vigilant's worst-case verdict); `ScanSettings.scanner` (Both / Vigilant only / CNO only, the
  rest asleep); `ui/CnoScreen` tab + `CnoDetail`, CNO rows and Books in the mini window;
  RESEARCH.md §18–19,
  `VIGILANT_LIVE=1 ... --tests '*LiveCnoSmokeTest'`); the CNO widget and when CNO is read
  (RESEARCH.md §20: `app/FloatingWidget` overlay window over Novig + `ui/FloatingFeed` (Up/Down
  bar, tap = Novig bet slip, ✓ placed + Undo, hold = books, drag/resize/bubble), `data/cno/CnoWatch`
  + `MainViewModel.watchCno` (tab / pip / overlay), `CnoFeed.keepBooksFresh` (green ✓ lane,
  `CnoBooks.agrees`), `data/teams/PlayerTeams` (ESPN rosters, `teams.json`),
  `data/tracker/PlacedBets` (`placed.json`, ✓ placed and ✕ removed = `hidden`), `data/tracker/PlacedIndex` (one "already placed" rule for the whole app: list key, Novig outcome id, or game + `BetGrader` pick within 12 h; `UiState.placedIndex`/`feedOf`/`indexed`, the widget, the mini window, `ScanService`'s counts), `data/match/Picks`;
  PiP fallback's Up/Down); reading CNO reliably and taps that always open Novig (RESEARCH.md §20.2:
  `data/cno/CnoNetwork` (`RememberingDns` + `DnsOverHttps` fallback, 20 s keep-alive, retry once,
  `CnoPace`), `CnoFeed.keepLinksFresh` (`cno_links.json`), `data/cno/TapLink` + `NovigBetFinder`
  (Novig's public catalog when CNO can't answer), `ScanSettings.cnoOnlyAgreed` ("Only bets the books
  agree on": `UiState.cnoCandidates`/`cnoShown`/`cnoBeingChecked`), stale "scan done" notification
  cancelled (`ScanService.cancelDone`)); bet slips without CNO and CNO under load (RESEARCH.md §20.3:
  `NovigBetFinder` catalog-first links (`CnoFeed.catalog`, `LiveNovigBetFinderTest`), `TapLink` race,
  `data/cno/NovigLive` (Novig's price now for CNO bets, `ScanSettings.cnoLivePrices`,
  `UiState.livePick`, read for `UiState.livePriceRows` (candidates, never filtered by those prices)), widget switch CNO only / Both / Vigilant only (`MainViewModel.setScanner`, `FloatingFeed.nextScanner`; the widget works in every mode, and Vigilant's bet sheet opens the exact bet slip, `OpportunitySheet.betSlipLink`), one row per
  bet both scanners list (`MiniWindow.merge`, `PlacedBet.aliases`), `ScanSettings.widgetRescanMinutes`
  (`WidgetRescan`), `LiveCnoBurstTest` (VIGILANT_BURST=1)); every bet tracked, settled and rechecked
  (TASKS.md N1–N5: every ✓ logs a $1 `TrackedBet` (`BetTracker.logCno/track/untrack/importPlaced`,
  one-time import flag `tracker_imported`), `data/tracker/BetSettler` (final scores: `tracker/Scores.kt`
  `FreeScores` = ESPN scoreboard/box score + MLB Stats API, graded by `tracker/BetGrader`; Novig's catalog
  forgets finished games, NOVIG_API.md; live check `VIGILANT_LIVE=1 ... --tests '*LiveScoresTest'`) run on app open, on the Tracker tab and by
  `app/SettleWorker` (WorkManager, every 3 h), `data/tracker/BetRecheck` ("Check odds now": CNO game
  page → `nowEv`), `ui/TrackerScreen` Stats | Bets (periods, running profit, by scanner, stake dialog; bets over ±6% EV when bet are outliers, left out of every stat: `BetTracker.OUTLIER_EV`, `TrackedBet.isOutlier`)).
- **Background auto-scan and +EV alerts (v0.18.0+, Vigilant only; RESEARCH.md §26):** `ScanSettings.autoScan`
  (Off / CNO / CNO + Vigilant) every `autoScanMinutes` (5-40) with Vigilant closed: `app/AutoScanService` (specialUse
  foreground service, ongoing note with Scan now/Stop), `AutoScanAlarm` (exact while idle), `AutoScanReceiver` (alarm,
  boot, update), `app/AutoScan.kt` (`AutoScanner.cycle`: CNO list + best bets' books + `NovigLive.readNow`, then
  `AppContainer.startVigilantScan`; `AlertPicks`), alerts `ScanSettings.alertMinEv` (Off/2/3/4%) via `app/EvAlerts`
  (tap = `novigapp://events/<outcome>`), `data/alerts/AlertLog` (`alerts.json`, one alert per bet),
  `data/scanner/Agreement` (3+ books agree, worst case). Also after a Scan left running off screen (`ScanService`).
  Faster scans: `data/scanner/FairMemo` (fair lines once per plan), end-of-scan re-read of early edges
  (`Scanner.REREAD_AFTER_MS`), up to 1,200 prices a scan; Vigilant's odds cap +120/+150/+200/+300.
- **Novig key's websocket, full budget, tennis (v0.19.0+; RESEARCH.md §27):** keyed scans hand the whole plan to
  `data/novig/stream/NovigStream` (`PushedBooks`; `NovigPublicClient.stream`/`watch`/`pushed`; `Scanner.BookPump`):
  one market subscribe once the `stream` bucket is full (~8 s), pushed books taken with no request, REST meanwhile,
  idle close 2 min, `ScanReport.booksViaPush` (Settings › Novig API); `ScanSettings.fillBudget` (`Planner.fill`,
  `PlannedMarket.spare`: every other quoted line up to the budget, read after the picks); tennis leagues ATP/WTA
  (`League.tennis`/`oddsApiListed`, Kalshi `…MATCH` series, Pinnacle sport 2 incl. 1st set, `NovigText` round suffixes,
  `FIRST_SET_MONEYLINE`/`PLAYER_GAMES_WON`), `VIGILANT_LIVE=1 ... --tests '*LiveTennisTest'`. v0.19.1 (NOVIG_API.md §13):
  `daysAhead` 7 by default (schema 8), every event read so `Plan.laterGames`/`ScanStats.laterGames` tell the feed what
  starts past the window, `DELAYED` games, `NovigMarket.strike` guard (`Planner.strikeAgrees`), `GET /v3/limits`
  (`NovigPublicClient.limits`, `PushedBooks.tune`), the board through the key's signed catalog (`catalog()`, public fallback).
  v0.19.3 (RESEARCH.md §29): `data/HttpSupport.vigilantHttpClient()` (16 requests a host; OkHttp's default 5 minus the
  open websocket left the key 4), key reads 10 in flight in `NovigSource.batchSize` 30s, one refused wave slows once
  (`RateGate.SAME_BURST_MS`), Kalshi game lines before props (`ReferenceSource.linesFirst`/`lines`, `Scanner.linesFirst`),
  where scan time goes (`data/scanner/ScanTiming`, `BookBatch.refused`, Settings › Novig API). Novig's 451 codes read
  right (`ANONYMIZED_NETWORK` = its verdict on an address, not the phone): Test key names the connection, checks for a
  real VPN and tries the other connection (`data/novig/signing/NovigKeyTest`, `app/PhoneNetworks`; NOVIG_API.md §11,
  RESEARCH.md §30.1). CNO's fewest books 1-4 (`CNO_MIN_BOOKS_CHOICES`, schema 9). v0.19.4 (RESEARCH.md §31): budget up
  to 2,000 (no "No limit"), long scans leave lines whose odds would be too old (`BookPump.canStillShow`,
  `ScanReport.booksTooLate`), lines/props per game up to 10/48, props credits up to 192 (`creditWorstCase`); a stake in
  Novig's bet slip (`data/novig/NovigLinks`, `SlipStake`, `ScanSettings.slipStake`/`slipStakeFor`, `EvAlert.stake`,
  `MiniWindow.Item.kelly`); one-tap Open in Novig on +EV cards (`ui/OpportunitySheet.OpenBetButton`); any PinnWire failure
  falls to pinnapi (`PinnapiClient.boardFor`).
- **Start-time window (v0.17.1+, Vigilant only):** `ScanSettings.startsWithinHours` (Any / 12 / 24 / 48 h,
  `startsInWindow`) applied at `now` in `UiState.feedAt`, `cnoCandidates` and `gamesAt`, so the +EV feed, CNO tab,
  Games board, badges, mini window, widget and `ScanService`'s counts all obey it; picked on the +EV and CNO tabs
  (`StartsWithinRow`), the floating widget's top bar (`FloatingFeed` `StartsWithinSwitch`, taps cycle
  Any time → 12h → 24h → 48h) or Settings › Scanner; what it hides is counted (`laterText`, `laterCount`);
  display only, scans unchanged (`StartsWithinTest`).
- **Vigilant MGM (v0.17.0 only; DORMANT since 2026-09-27, see "Vigilant MGM is dormant" above; RESEARCH.md §25):** the second app, module `mgm` (`com.tjshea.vigilant.betmgm`),
  compiles `app`'s own sources with `BuildConfig.BOOK = "betmgm"`; `app/AppBook` is the one switch (names, links,
  Novig-only parts off). Data side: `data/book/` (`Sportsbook`, `BookBoard`, `SportsbookScanner` behind
  `scanner/OddsScanner`, `BetMgmLinks`), `PropLineClient(relayNovig, bookIds)`. Its screens are tested in `app`
  with the book switched (`MgmAppTest`, `SampleMgm`); `mgm/src/test` checks the real build (`MgmBuildTest`).
  A sweep covers Vigilant only while MGM is dormant.
- **Automated floor:** `./gradlew :engine:test :data:test :app:testDebugUnitTest`
  (needs BRIEF.md build trap 6 locally: `bash tools/setup-android.sh`). Add `-Pscreenshots` and look at every PNG
  in `app/screenshots/`: this is the "Chromium check" for a Compose app.
  `VIGILANT_LIVE=1 ... --tests '*LiveNovigSmokeTest'` re-verifies matching against
  Novig's real catalog.

### Light tests — low usage, run after the session's own work is done

Purpose: catch obvious bugs/UI issues and anything the CURRENT session's own
changes broke elsewhere in the app. Not a general audit.

1. Run the same automated floor `tools/ckpt.sh` runs: whatever
   `tools/test_*.{js,sh,py}` exist, plus `npm test` if `package.json`
   declares one. If none exist yet, say so plainly rather than reporting a
   false "all green".
2. Re-read only the files this session actually touched, plus (via `grep`)
   whatever else calls into them, looking for obvious bugs and issues: stale
   copy, missing guards, a render or response that no longer matches
   behavior.
3. Check whether anything ELSE in the app could have broken from this
   session's changes — the "who else calls this" check that has caught real
   bugs on this account's other projects (a shared helper's new behavior
   silently feeding a different consumer, a duplicated normalizer drifting
   from the one it was copied from).
4. If the change is visually checkable once there is a UI, the pre-installed
   Chromium is fair game for markup/CSS/layout checks — this account's other
   projects have used exactly that to catch UI bugs live rather than only in
   code. Anything that needs the actual Android runtime, a native bridge, or
   real network calls does not work from a bare browser; say so rather than
   implying a real device check happened. There is no real device or
   emulator in this environment.
5. Fix anything found. If any fix was non-trivial, re-run step 1 (and step 4
   if it touched anything visual) before calling it done — confirm the fix
   didn't break something else.
6. `tools/ckpt.sh "light test: <what was found/fixed>" "<what's next>"`.

Budget discipline: this is deliberately narrow. Don't re-read files the
session didn't touch, don't chase pre-existing issues unrelated to this
session's changes — note them for a future full test instead.

### Full tests — no usage/time ceiling, best effort

Purpose: a comprehensive pass over the ENTIRE app, not just recent changes.

1. Run the automated suite (step 1 of light tests) as the floor, not the
   ceiling.
2. Sweep the whole app — once there are tabs/screens/subsystems to name,
   list them here and go through each in turn, the way Portfolio's
   `CLAUDE.md` does. Until then: read every source file that exists,
   cross-check anything one module assumes about another, verify load
   order, look for stale copy vs. actual behavior.
3. Specifically look for, and fix:
   - **Code/UI improvements** — dead branches, inconsistent formatting,
     stale or misleading copy, accessibility gaps, missing dark/light
     handling.
   - **Network efficiency** — duplicate requests against the Novig API (or
     whatever data source(s) this ends up using) or redundant
     re-computation of anything derived from a response already fetched.
   - **Caching and data retention** — nothing the app has fetched, computed,
     or the user has entered should be silently lost, overwritten, or
     mis-filed by a race.
   - **Engine/logic correctness** — anything related to the actual
     profit/edge-finding strategy checked against whatever this project ends
     up designating as its ground truth (the equivalent of
     fantasy-football's `RULES_2026.md` or Portfolio's scoring/accounting
     rules) — that ground-truth document does not exist yet; note in
     `BRIEF.md` once it does.
   - **Bugs or corruption from recent changes** — diff against the last few
     ships if that's the fastest way to spot what moved.
   - **Resource/battery waste** — anything polling, syncing, or holding a
     wake lock with no reason to while the app isn't in active use, given
     this is meant to run acceptably on a Moto G 2026; the app should sleep
     properly when backgrounded.
4. Fix everything found. Per this account's standing testing convention,
   give each fix a named test confirmed to FAIL against the pre-fix code
   where a test can prove it; where it can't (e.g. a pure UI render with no
   test harness), a source-text pin is the established fallback.
5. Full regression, verified by BOTH exit code AND output content, not a
   bare `grep FAIL` — Portfolio caught itself missing a silent crash that way
   once already (zero "FAIL" lines printed is not the same as green).
6. When clean: `tools/ckpt.sh` recording the full findings/fixes, then
   `ship.sh` and "Releasing" below to publish the Release and send Tj the
   link — a full test is exactly ship-worthy work, so don't leave it
   uncommitted-to-a-release unless Tj says not to ship yet.

## Credentials — never commit one

`tools/secretscan.sh` blocks the autosave hook from committing anything
shaped like a live credential (API keys, GitHub tokens, private keys,
`sk-ant-...` keys). If it trips, remove the credential — do not bypass it. A
key that reaches a commit has to be rotated, not deleted: GitHub keeps
commit objects reachable by SHA even after history is rewritten. Keep real
secrets in a local, gitignored `.env` — see `.gitignore`.

## Releasing — the plan, ported from Portfolio

Tj's instruction for this project: Claude writes the code, GitHub Actions
builds and signs the APK, Claude triggers that build and confirms it went
green, and Tj gets a link — not the raw APK bytes relayed through chat, and
not a build that happened inside this container. This is exactly Portfolio's
model (as opposed to fantasy-football's, where a local `build.sh` still
produces the APK and a separate workflow only republishes it) — follow
Portfolio's `CLAUDE.md` "Releasing" section and `ship.sh`/`.github/workflows/android.yml`
as the reference implementation once there is an actual Android project to
build.

**This is built and working** (since v0.1.0): `app/build.gradle.kts` signs with
the committed keystore, `.github/workflows/release.yml` builds, verifies the
certificate, tags server-side and publishes the Release, and `ship.sh` gates it.
The order per release:

1. `bash ship.sh "note"` after CI (`ci.yml`) is green on the commit.
2. Trigger `release.yml` on `main` (`mcp__github__actions_run_trigger`).
3. Confirm with `mcp__github__get_release_by_tag`.
4. `bash tools/record-release.sh vX.Y.Z <code> "note"`.
5. Send Tj the Release page link — plain tappable text on its own line,
   **never inside a fenced code block**. A code block reads as copyable text
   in some clients but is not a clickable, long-press-copyable link the way
   a bare URL is on a phone; this exact mistake was made and corrected on
   fantasy-football (2026-09-14) — don't repeat it here.

**If the repo is private**, a Release link can 404 for Tj the same way it
did on Portfolio — that's GitHub returning 404 (not 403) to anyone viewing a
private repo without access, not a broken link. Check the repo's visibility
before assuming either way; the fix, if it happens, is confirming the
Release exists (`get_release_by_tag`) and telling Tj to check he's logged
into the right account in that browser, not regenerating the link.

## Building the APK

CI builds the real release (see "Releasing"). A local build is possible once
BRIEF.md build trap 6 is set up (`bash tools/setup-android.sh`, one command): `ANDROID_HOME=/opt/android-sdk ./gradlew
:app:assembleRelease` (R8-minified, ~4.6MB). Use it to check a change compiles
and to render screenshots, not to hand Tj an APK.

## This repo's visibility

**Public** (confirmed 2026-09-25: the GitHub API answers anonymously). A leaked
credential is exposed immediately and permanently, and Release links work for
anyone. Test fixtures that must contain key-shaped text carry the scanner's
`FAKE` marker on the same line (see `tools/secretscan.sh`); never commit a real
key.

## Novig API — permanent research memory

Novig's official API (v3, beta access since 2026-09-25) is documented for
this project in **`NOVIG_API.md`**. It covers hosts, public vs. signed routes,
the `NOVIG-V3` signing scheme, the websocket, book semantics ("taker price =
1 − best opposing bid"), fees, location checks, and what's still unverified.
Read it before writing or changing any Novig data code, and update it
whenever something is verified or turns out wrong. It is the one place a
fresh session learns this, so don't re-research what's already recorded there.
`RESEARCH.md` holds the wider EV/market research. This repo is **public**:
never commit a Novig key, PEM, or key ID paired with a private key.

## Project rules

See `BRIEF.md` for the full list of what's actually decided about this
project versus what's still open, and for the rules (the eventual signing
keystore, toolchain pins, build traps, locked architecture decisions) that
will apply the moment there's something for them to govern. `bootstrap.sh`
prints the short version at every session start.
