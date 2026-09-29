---
name: test-protocols
description: Tj's "light tests" and "full tests" protocols for Vigilant, with the map of the app's tabs and subsystems a sweep goes through. Use whenever Tj says light tests, full tests, comprehensive tests, or close wording.
---

# Test protocols — "light tests" and "full tests"

**Standing instruction, not a `TASKS.md` job — this section IS the
explanation, so no clarification is needed when Tj asks.** Whenever Tj says
"light tests"/"light test"/"light testing", or "full tests"/"full
test"/"full testing"/"comprehensive tests" (any close wording), run the
matching protocol below immediately, on any account, from any cold start,
with zero further explanation required. Neither protocol is a `TASKS.md`
step to tick off; both end with `tools/ckpt.sh` recording what was found and
fixed (a full test that finds nothing worth withholding ends with
`ship.sh` per CLAUDE.md's "Releasing" — a light test does not ship on its own
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
- **Pause all scanning and CNO's one-tap buttons (v0.19.5+):** `ScanSettings.paused` (saved; Settings › Scanner switch,
  the +EV/CNO tabs' `PauseButton` + `PausedBanner`, the widget's header ⏸/▶): a running scan stops (`ScanRunner.stop`),
  CNO's list and lanes are held (`CnoWatch.hold`, also until settings load), auto-scan off (`activeAutoScan`; service,
  alarm, boot receiver, `AutoScanner.cycle`), Scan/Recheck/Refresh toast `PAUSED_TOAST`, reading buttons greyed; bets
  still open and settle (`PauseScanningTest`, `PauseScanningAppTest`, ScreenshotTest pause tests). Every CNO tab card has
  the +EV card's `OpenInBookButton` (`MainActivity.openInNovig`, stake `cnoSlipStake`).
- **No limit on every scan cap, scans bounded by the time window (v0.19.6+; RESEARCH.md §32):** `ScanSettings.NO_LIMIT`
  ("No limit" / "All") on Novig prices per scan, The Odds API props credits, PropLine props games per scan
  (`propLineGamesPerScan`, was a fixed 12), lines/props per game, sportsbook-props hours (`bookPropWindowHours`); the scan
  reads only `scanWindowHours` (Days ahead, or Starts within when shorter: `Planner.horizon`), each line once, never past
  its odds' freshness (`canStillShow`), and lines left too late are read first next scan (`Scanner.leftLastScan`); feed
  banner when Starts within is wider than the last scan (`ScanStatus.scannedWindowHours`). Tests: BiggerScansTest no-limit
  tests, OddsApiPropsTest, PropLineClientTest, ScreenshotTest no-limit tests.
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

## Light tests — low usage, run after the session's own work is done

Purpose: catch obvious bugs/UI issues and anything the CURRENT session's own
changes broke elsewhere in the app. Not a general audit.

1. Run `tools/test_*.sh` (what `tools/ckpt.sh` runs) and the Gradle tests of
   every module this session changed (see "Automated floor" above). Say what
   ran; never report "all green" for tests that didn't run.
2. Re-read only the files this session actually touched, plus (via `grep`)
   whatever else calls into them, looking for obvious bugs and issues: stale
   copy, missing guards, a render or response that no longer matches
   behavior. Review each touched file with the skill for its kind (CLAUDE.md
   "Skills for this app"): Compose screens with `compose-state-and-effects`
   (and `compose-performance` for anything drawn per frame or per scroll),
   coroutine/Flow code with `kotlin-concurrency-and-flow`, UI tests with
   `compose-ui-testing-patterns`.
3. Check whether anything ELSE in the app could have broken from this
   session's changes — the "who else calls this" check that has caught real
   bugs on this account's other projects (a shared helper's new behavior
   silently feeding a different consumer, a duplicated normalizer drifting
   from the one it was copied from).
4. If the change is visible, render it: `-Pscreenshots` writes every screen
   to `app/screenshots/`; look at the PNGs the change affects. There is no
   device or emulator here, so say what a screenshot can't show (real
   network, the overlay over Novig, notifications) rather than implying a
   device check happened.
5. Fix anything found. If any fix was non-trivial, re-run step 1 (and step 4
   if it touched anything visual) before calling it done — confirm the fix
   didn't break something else.
6. `tools/ckpt.sh "light test: <what was found/fixed>" "<what's next>"`.

Budget discipline: this is deliberately narrow. Don't re-read files the
session didn't touch, don't chase pre-existing issues unrelated to this
session's changes — note them for a future full test instead.

## Full tests — no usage/time ceiling, best effort

Purpose: a comprehensive pass over the ENTIRE app, not just recent changes.

1. Run the whole automated floor above (all three modules, `-Pscreenshots`,
   every PNG looked at) as the floor, not the ceiling.
2. Sweep the whole app: go through each tab and subsystem listed above in
   turn, cross-check anything one module assumes about another, verify load
   order, look for stale copy vs. actual behavior.
3. Specifically look for, and fix:
   - **Code/UI improvements** — dead branches, inconsistent formatting,
     stale or misleading copy, accessibility gaps, missing dark/light
     handling. Every Compose screen gets a `compose-state-and-effects`
     review (state owners, effect keys, previewable content) and a
     `compose-performance` pass on the feed, CNO list and widget, which
     redraw most on a 120 Hz screen.
   - **Network efficiency** — duplicate requests against Novig, CNO or the
     reference-odds providers, or redundant re-computation of anything
     derived from a response already fetched.
   - **Caching and data retention** — nothing the app has fetched, computed,
     or the user has entered should be silently lost, overwritten, or
     mis-filed by a race.
   - **Engine/logic correctness** — anything related to the actual
     profit/edge-finding strategy, checked against BRIEF.md's "Locked
     architecture decisions" (fair odds, taker price, fees, odds freshness)
     and NOVIG_API.md: the closest thing to a ground truth this project has
     (the equivalent of fantasy-football's `RULES_2026.md`).
   - **Bugs or corruption from recent changes** — diff against the last few
     ships if that's the fastest way to spot what moved.
   - **Resource/battery waste** — anything polling, syncing, or holding a
     wake lock with no reason to while the app isn't in active use, given
     this is meant to run acceptably on a Moto G 2026 (4 GB RAM; BRIEF.md
     "Target hardware"); the app should sleep properly when backgrounded.
     Review every long-lived coroutine owner (`ScanRunner`, the scan services,
     `CnoFeed`, `NovigStream`, `WidgetRescan`) with
     `kotlin-concurrency-and-flow`: who starts it, who cancels it, and what
     stops it when the app leaves the screen.
4. Fix everything found. Per this account's standing testing convention,
   give each fix a named test confirmed to FAIL against the pre-fix code
   where a test can prove it (UI tests per `compose-ui-testing-patterns`); where it can't (e.g. a pure UI render with no
   test harness), a source-text pin is the established fallback.
5. Full regression, verified by BOTH exit code AND output content, not a
   bare `grep FAIL` — Portfolio caught itself missing a silent crash that way
   once already (zero "FAIL" lines printed is not the same as green).
6. When clean: `tools/ckpt.sh` recording the full findings/fixes, then
   `ship.sh` and CLAUDE.md's "Releasing" to publish the Release and send Tj the
   link — a full test is exactly ship-worthy work, so don't leave it
   uncommitted-to-a-release unless Tj says not to ship yet.
