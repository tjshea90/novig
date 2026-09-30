# PARLAY_API — ParlayAPI (parlay-api.com), as verified with Tj's own key, 2026-09-30

**Read this before touching any ParlayAPI code.** It is the project's permanent memory of ParlayAPI: how Vigilant uses it, what its answers
really look like (they differ from its docs in places), what each call costs, and the build guide for the features Tj asked for next
(§6). A fresh session on any account should be able to work from this file, `TASKS.md` and the sample answers in
`data/src/test/resources/parlay-*.json` alone. RESEARCH.md §43–§45 hold the history and the reasoning.

**Sources, to refetch when exact wording matters** (all public, no key): the OpenAPI spec `https://parlay-api.com/openapi.json` (~350 KB,
OpenAPI 3; every path's description, parameters and costs), `https://parlay-api.com/llms.txt` (index of the docs pages),
`https://parlay-api.com/docs/best-practices`, and the machine-readable plan limits `https://parlay-api.com/v1/meta/limits`. This repo is
**public**: the docs are summarized here, not copied in.

**Keys.** Never commit a ParlayAPI key (CLAUDE.md "Credentials"). Tj shared his main key in chat on 2026-09-30 and was told to rotate it; a
new session has no key unless Tj gives one, and then keeps it only in the session's scratchpad (never a repo file, fixture, checkpoint or
commit message). Every sample under `data/src/test/resources/parlay-*.json` is public odds data with the key, key fingerprint and account
email removed. Probing with Tj's key spends his credits: say what a probe costs before spending more than a few.

---

## 0. The one-paragraph version

ParlayAPI is The Odds API's format (`/v1/sports/{sport_key}/odds`, the same sport keys) plus a lot more: a whole league's player props in
one call, Pinnacle's closing lines, prediction markets, injuries, period markets, line movement, and its own +EV tools. Auth is the
`X-API-Key` header. Tj is on the **$5 Starter plan: 20,000 credits a calendar month (UTC), no per-second cap, 7 days of history**. Vigilant
uses it for scans (game lines and props from Pinnacle, ProphetX, BetOnline, bet365, Bovada and the US books), Pinnacle's closing lines for
CLV, the key's own credit count for the meter, and (since v0.29.0) as Check odds now's backup when CrazyNinjaOdds can't price a bet.

## 1. Plans and limits (`GET /v1/meta/limits`, public, 2026-09-30)

| Tier | $/month | Credits/month | History | Streams (SSE/ws) |
| :- | -: | -: | :- | :- |
| free | 0 | 1,000 | 48 h | no |
| **starter (Tj)** | 5 | 20,000 | 7 days | no |
| pro | 20 | 100,000 | 30 days | no |
| business | 40 | 1,000,000 | 90 days | yes |

Rate limit: 60/s free, none on paid tiers. Errors: 429 with `Retry-After` (`{"error":"rate_limit"}`); **403
`{"error":"credit_limit_exceeded"}`** when spent; 403 `HISTORICAL_LIMIT` when asked past the plan's history; **503 "busy"** bodies on
heavy endpoints (`props_temporarily_busy` on /verdict, `LINE_MOVEMENT_TIMEOUT` with `retry_after_seconds` on /line-movement), and **a 503
from /line-movement is still charged** (2 credits each, seen 3 times).

## 2. Credits: where the numbers come from

- **Paid replies of /odds, /props, /closing-lines, the closes file** carry `x-requests-remaining`, `x-requests-used`, `x-requests-last`
  (the call's cost) — exact. `x-ratelimit-limit/remaining` read `unlimited` on paid plans; there is **no reset header**. `/v1/meta/limits`
  also lists `X-Credits-Remaining`, `X-Credits-Cost`, `X-Rate-Limit-*` (hyphenated): `CreditHeaders` reads every spelling.
- **/verdict and /best-bets send no credit headers**: the answer body has `"credits":{"monthly_remaining":19871,"monthly_limit":20000}`.
  Record that into the meter (`UsageMeter.recordBalance`) or the meter falls behind.
- **Free account reads:** `GET /v1/usage` (alias `/v1/account`): `credits_used`, `credits_remaining`, `credits_total` (plan +
  `credits_granted`), `tier`, `period_start`/`period_end` (**the calendar month, UTC**: a plan bought Sep 30 02:51Z had the whole 20,000 for
  Sep 1 → Oct 1), plus `email` and `api_key_fingerprint` (never log or commit those). `GET /v1/meta/api-key-check`: the same counts,
  `valid`, `reason` (`credit_exhausted`, `key_inactive`), and `subscription.period_end_iso` = **Stripe's billing date, not the credit reset**.
  `GET /v1/meta/usage?days=` (max 90): its `credits_*` fields read **0 (broken)**, but `daily_breakdown` (`day`, `credits`, `requests`) and
  `top_endpoints` (`endpoint` like `props:baseball_mlb`, `credits`, `requests`) are right. Sample: `parlay-meta-usage.json`.
- Code: `data/.../reference/ParlayAccount.kt` (reads /v1/usage, falls back to the key check, at most once a minute a key;
  `historyDays()` from the tier), `data/.../keys/Usage.kt` (`QuotaPolicy.PARLAY`, `CreditPace`: a day's share, a new plan spread over the
  days left in its month, the last 300 kept for closing lines, background auto-scans keep half a day's share), `data/.../keys/CreditHeaders.kt`.

## 3. What Vigilant calls today (all verified live 2026-09-30)

| Call | Cost | Where | Notes |
| :- | -: | :- | :- |
| `GET /v1/sports/{s}/odds?bookmakers=<10>&markets=h2h,spreads,totals&oddsFormat=decimal&commenceTimeTo=` | 3 (5 with `alternate_spreads,alternate_totals`) | `TheOddsApiClient` (`OddsFeed.PARLAY`) | markets × ⌈books/10⌉. Alternates are **Pinnacle's only**: asked only when PinnWire/pinnapi are off. Games under way are **left out unless `include_live=true`** (sent when Settings' live games are on). Three-way h2h (soccer, with a Draw) is dropped at parse. |
| `GET /v1/sports/{s}/props?markets=<Vigilant's>&bookmakers=pinnacle,draftkings,fanduel,caesars,bovada,prophetx&oddsFormat=american&limit=10000&offset=&maxAgeSec=600` | 3 | `ParlayPropsSource`, `ParlayProps.parse` | One row per book per prop: `event_id`, `home_team`, `away_team`, `commence_time` (ms), `bookmaker`, `player`, `market_key` (**each book's own name**, e.g. `player_passing_attempts`, `batter_total_bases`), `market` (label), `period` (`FULL`; others are 1Q/1H props, skipped), `line`, `over_price`, `under_price` (either may be null: one-sided markets), `last_update`, `age_seconds`, **`injury`** (see §6.1). `ParlayMarkets.statOf` maps key+label to Novig's stat. `x-result-truncated: true` means a book writes faster than one read: narrowing `markets=` is their fix (done). |
| `GET /v1/sports/{s}/closing-lines?bookmakers=pinnacle&daysFrom=&oddsFormat=american` | 5 | `ParlayCloses.parseGameLine` | **Flat rows, moneyline only in practice** (15/15 NFL, 4/4 MLB rows `h2h`, though the docs promise spreads/totals): `home_odds`, `away_odds`, `draw_odds`, `commence_time`, `last_update` (= the start). `daysFrom` never past the plan's history. |
| `GET /v1/historical/closing-lines.json?date=&sport_key=&source=pinnacle&limit=10000` | 1 per 1,000 rows | `ParlayCloses.parseProp`, `parseFileGameLine` | `{rows:[…]}`: props **and** Pinnacle's game lines (moneyline/spread rows name one team in `player_name` with its price in `over_price`; totals `player_name:"Total"` with both sides). `snapshot_time` is often **hours** before the start (MLB props 12–15 h): a price more than 2 h early isn't used as a close. |
| `GET /v1/meta/source-quality` | free | `ParlaySourceQuality` | Books ParlayAPI says are stale are left out of a scan. |
| `GET /v1/usage`, `GET /v1/meta/api-key-check` | free | `ParlayAccount` | §2. |
| `/odds` + `/props` for one bet | 5 + 3 a league, kept 2 min | `data/.../tracker/ParlayBooks.kt` | Check odds now's backup: every book's two-sided price for an open bet, shaped as CNO's game page (`CnoBooksView`) so `CnoBooks.check` judges it with CNO's exact math. |

Book keys → CNO codes (`ParlayBooks.CODES`): pinnacle PN, draftkings DK, fanduel FD, caesars CZR, betmgm MGM, betrivers BR, bet365 B365,
fanatics FN, bovada BV, betonline BO, prophetx PX, kalshi KI, novig NV, hardrock HR-IN, pointsbet PB, circasports CS, fliff FL.

## 4. Wiring (app module)

`app/.../VigilantApp.kt` builds everything: `parlayPool` (one `KeyPool` for all ParlayAPI calls, `QuotaPolicy.PARLAY`), `parlayAccount`,
`parlayQuality`, `parlayOdds` / `parlayOddsBackground` (+ their props sources), `parlayBooks`, `parlayCloses` (history from
`parlayAccount.historyDays()`), and `referenceSources(settings, background)` (what a scan calls; turns ParlayAPI's alternates off while a
Pinnacle feed is on). Settings switch: `ScanSettings.useParlay`; keys: `ApiProvider.PARLAY` via `keyStore`. Meter UI:
`app/.../ui/UsageMeters.kt` (`PARLAY_PACE`), Settings text: `app/.../ui/SettingsScreen.kt` (search "ParlayAPI"), Diagnostics:
`app/.../Diagnostics.kt` (`parlayAccounts`). Refresh of the account: scan start, Settings › API usage opened, Diagnostics, key added
(`MainViewModel.refreshBalances`).

## 5. Things learned the hard way

- The docs and the real answers disagree often enough that **every new endpoint must be probed once and its answer kept as a trimmed
  sample** before code is written against it (§3's closing-lines, props market names and snapshot ages were all found that way).
- A ParlayAPI answer can carry Novig prices that are far off Novig's own book (best-bets once listed Novig at +2122 on a +900 fair
  prop, see §6.5): anything that says "bet this at Novig" must re-read Novig's own price first.
- `edge_pct` in /best-bets and /verdict is a **probability-point** difference, not EV% (fair +900 = 10.0%, Novig +2122 = 4.5%,
  edge_pct 5.5; **confirmed 2026-09-30** on a second answer: fair +113 = 46.9%, best +102 = 49.5%, edge_pct −2.56, which its summary calls
  "−2.6% EV" though the real EV is about −5%). Show Vigilant's own EV (fair ÷ price − 1) next to it, never edge_pct as if it were EV.
- /verdict's "fair" can be **Novig's own no-vig price** (`fair.source: "novig"`) when no sharper book lists the bet, even with
  `sharpBook=pinnacle`: for a Novig bet that's circular. The second-opinion card says so (`Verdict.fairFromNovig`).
- Period-market rows carry two ages: `age_seconds` (since the price last **changed**) and `observed_age_seconds` / `last_observed_ms`
  (since ParlayAPI last **saw** it). A line unchanged for hours is still current: freshness uses the observed age. (The `/props` rows in
  `parlay-props-with-injury.json` have only `age_seconds` and `last_update`.)

## 6. Build guide: the features Tj asked for on 2026-09-30

Tj, 2026-09-30, after being told what else ParlayAPI offers: "On that session I'm going to have Claude build everything you just listed."
The list, its tasks (TASKS.md §M), the endpoint, cost, real sample and what to watch. Build the free ones first. Every paid call goes
through `parlayPool` (metered, paced, `CreditsHeldBackException` when a day's share is spent), never on a timer the user didn't ask for
unless the task says so, and only while `useParlay` is on with a key.

### 6.1 Injury tags on prop bets (M1) — free from /props, or `GET /v1/sports/{s}/injuries` (1 credit a league)
- Every `/props` row already carries `injury` (null when none): `{status, description, date, team, team_abbr, il_category, body_part,
  side, position, expected_return, updated_at}`. Statuses seen (NFL, 789 players): `Active` 624, `Questionable` 92, `Injured Reserve` 41,
  `Out` 23, `Doubtful` 9. Samples: `parlay-props-with-injury.json` (rows with the object), `parlay-injuries-nfl.json` (the /injuries
  answer: `{sport_key, count, athlete_filter, status_filter, results:[{athlete_name, team, team_abbr, position, status, il_category,
  body_part, side, detail, description, expected_return, short_comment, date_reported, updated_at}]}`, 2 rows per status).
- /injuries covers only `baseball_mlb, basketball_nba, basketball_wnba, icehockey_nhl, americanfootball_nfl` (others 400). ESPN refreshed
  every ~10 min.
- Show a tag only when status isn't `Active`: Out / Injured Reserve (red), Doubtful / Questionable (amber), with the short comment on tap.
- **Built (v0.30.0):** `data/.../reference/ParlayInjuries.kt` (`Injury`, `InjuryIndex`, `ParlayInjuries`), `InjuryTags.kt`. A player listed in
  a /props answer with `injury: null` counts as covered (no /injuries read for him). /injuries is read through the new generic
  `TheOddsApiClient.parlayGet` (metered, paced, body credits read, 503 handed back to the caller).
  Where: Vigilant's +EV prop cards and sheet, CNO's prop cards, the widget, and the Tracker's open prop bets. Match by player name
  (`PlayerNames.same`) and team when known. Keep the index from the last /props answers (free); ask /injuries (1 credit a league, cached
  10 min) only for open or listed prop bets whose player isn't in a recent /props answer.

### 6.2 Per-day credit chart (M2) — `GET /v1/meta/usage?days=30`, free
- **Built (v0.30.0):** `ParlayAccount.refreshHistory` / `History`, chart `app/.../ui/UsageChart.kt`; read only when Settings › API usage
  is on screen (not on every scan).
- Sample `parlay-meta-usage.json`. Use `daily_breakdown` and `top_endpoints` only (its `credits_*` fields read 0). A small bar chart in
  Settings › API usage under ParlayAPI's meter ("credits a day, last 30 days") plus the top endpoints ("props:baseball_mlb 15"); read when
  the tab opens, at most once a minute (with `ParlayAccount`'s refresh).

### 6.3 Biggest line moves (M3) — `GET /v1/meta/movers?sport_key=&window_minutes=&limit=&pre_game_only=true`, public, free (90 s cache)
- Sample `parlay-movers-nfl.json`: `movers:[{home_team, away_team, commence_time, source:"pinnacle", home_ml_first/last/delta,
  away_ml_first/last/delta, home_prob_delta_pp, away_prob_delta_pp, abs_max_prob_delta_pp, snapshots_seen}]`, ranked by probability
  points moved; moves under 1.0 pp are filtered as noise. Moneyline only, Pinnacle as the anchor, window 5–360 min.
- Where (suggested; Tj decides placement): a "Line moves" card on the Games tab for the picked leagues, and a small "Pinnacle moved
  toward/against" note on +EV cards, CNO cards and open Tracker bets whose game is in the list (a move toward your side is good CLV).
- **Built (v0.30.0):** `data/.../reference/ParlayMovers.kt` (`ParlayMovers` reads it, `LineMoves` matches team bets). Verified 2026-09-30
  05:45Z: answers **without a key** (no `X-API-Key` sent), `cache-control: public, max-age=90`, an MLB board at night came back with
  `movers_returned: 0`. Read every 3 min per picked league while Vigilant is on screen and ParlayAPI is on; notes only on moneyline and
  full-game spread bets (a moneyline move says nothing sure about totals or players).

### 6.4 One-bet verdict (M4) — `GET /v1/verdict`, 5 credits a bet
- Params: `sport` (sport key), `market` (`h2h` | `spreads` | `totals` | a player prop key), `side` (team name, or `over`/`under`), `home` +
  `away` (or `event`="Away @ Home", or `team`), `player` (props), `line`, `price` (American, the bet's own), `books=novig`, `bankroll`,
  `kelly` (0.5), `sharpBook` (pinnacle). The prop market key must be ParlayAPI's (`player_passing_attempts`, …): reverse
  `PropStats.parlayMarkets(sport)` (pairs of key → Novig stat).
- Samples `parlay-verdict-h2h.json` and `parlay-verdict-prop.json`: `{bet, verdict: BET|LEAN|FAIR|PASS|NO_DATA, worth_betting, summary,
  fair:{price, implied_prob, source}, best_available:{price, book, implied_prob, edge_pct}, your_bet:{price, implied_prob, edge_pct},
  books_compared, confidence, movement_pp_since_open, stake, credits:{monthly_remaining, monthly_limit}}`. A prop Novig doesn't carry at
  that line comes back `NO_DATA` with the fair still given. `parlay-verdict-busy-503.json`: retry once after 2 s, then say it's busy.
- Where: a "Second opinion (ParlayAPI, 5 credits)" button in the +EV bet sheet, the CNO bet sheet and the Tracker's bet sheet; shows the
  verdict, its fair and Vigilant's EV at the bet's price, books compared. Never automatic (5 credits each).
- **Built (v0.30.0):** `data/.../reference/ParlayVerdict.kt` (`VerdictQuery`, `VerdictQueries`, `Verdict`, `ParlayVerdicts`,
  `ParlayMarketKeys`); UI `app/.../ui/SecondOpinion.kt` (`LocalOpinions`). `side` is sent as the team's **full name** (the spec allows "Team
  name or home/away"), so a game listed the other way round can't flip it. Prop keys: the spec's canonical list (`GET /v1/meta/markets`,
  public: `player_pass_attempts`, `batter_hits`, …) is what Vigilant stores, but the one real prop verdict used the board's
  `player_passing_attempts` and answered with a fair price, so the key a `/props` answer actually used for the stat is preferred
  (`ParlayMarketKeys`, book-prefixed names like `prophetx_…` ignored), the canonical one otherwise. **Verified 2026-09-30:** the canonical
  key answers too (`player_rush_yds`, Aaron Rodgers Over 1.5 at −107: PASS, fair +113 from Novig itself, 6 books; sample
  `parlay-verdict-prop-canonical.json`).
- Also in the spec (2026-09-30): `GET /v1/try/verdict` — **free, no key**, 60/hour per IP, US books, no staking/movement. Not used (a demo
  endpoint; Tj asked for the 5-credit call). A cheaper second opinion if Tj ever wants one.

### 6.5 ParlayAPI's own +EV list at Novig (M5) — `GET /v1/sports/{s}/best-bets?books=novig&min_edge=&min_books=&limit=&markets=`, 10 credits a league
- Samples `parlay-best-bets-mlb.json` (5 plays + an edge alert) and `parlay-best-bets-empty-nfl.json`. `best_bets:[{bet:"Carson Kelly Over
  0.5 Home Runs (Chicago Cubs @ San Diego Padres)", market_key, fair_price, best_price, best_book, edge_pct, verdict, books_compared}]`,
  `edge_alerts:[{bet, book, price, apparent_edge_pct, caveat}]`. **Player props only** (game moneylines excluded: use /verdict). No event
  id, start time, player, side or line fields: parse `bet` ("<Player> Over|Under <line> <Stat label> (<Away> @ <Home>)") and match the
  game to Novig's catalog by teams. Defaults: min_edge 2.0, min_books 4 (the NFL board at 2%/4 books was empty).
- **Verify at Novig before showing**: the sample's first play priced Novig at +2122 against a +900 fair — re-read Novig's own price for
  that outcome (Vigilant's Novig client) and drop plays that aren't +EV at Novig's real price. Treat `edge_alerts` as "verify first".
- Where (suggested): a third scanner beside CNO and Vigilant (the scanner switch already has CNO only / Both / Vigilant only), refreshed
  only on tap or pull (10 credits a league), each card with Open in Novig, ✓/✕ like the others (placed.json keys `parlay:<…>`), logged
  to the Tracker like CNO's.
- **Built (v0.30.0):** `data/.../reference/ParlayBestBets.kt` (`ParlayBestBets`, `ParlayPlay`, `ParlayPick`), UI `app/.../ui/ParlayPicks.kt`:
  a "ParlayAPI's picks at Novig" section at the top of the +EV tab (only while ParlayAPI is on with a key), read only on its button
  (`min_edge=1`, `min_books=3`, `limit=50`: wide, since every play is judged again at Novig). Each play becomes a CNO-shaped row
  (`ParlayPlay.row()`), so `NovigBetFinder` finds the exact Novig outcome (and the game's start, `NovigBetFinder.event`) and
  `NovigLive.readNow` prices it from Novig's own book: shown only if +EV at Novig's price now against ParlayAPI's fair, within Tj's EV
  range, odds cap (counted when over it) and start window. "Recheck" re-reads Novig only (free). Edge alerts have no fair price: one is
  taken from their `apparent_edge_pct` as probability points over the price's own. ✓ logs to the Tracker as source `parlay`
  (`BetTracker.SOURCE_PARLAY`, its own Tracker scanner filter). Not in the widget.
- **Built (v0.32.0, TASKS.md P1/P2/P4, Tj 2026-09-30):** each pick card has the **Bet** button (Novig API betting, only when set up):
  `ApiBettingController.bet(pick)` is CNO's own path (`betRow`: `NovigBetFinder` exact outcome, then the Bet sheet and planner) with
  ParlayAPI's fair line, `fairAsOfMs` = when its board was read (so the planner refuses it past the freshness limit: "scan again"),
  logged as source `parlay` under the pick's `parlay:` key (`ApiBetTargets.of(pick, …)`). Beside the EV badge: **CNO's and Vigilant's
  EV at the same Novig price** (`data/.../reference/ParlayCompare.kt`): CNO's from its list row for the same bet (`PlacedIndex.identity`,
  same-game start window, a Novig row first; its list must be fresh), Vigilant's from the last scan's same Novig outcome (the live
  price now carries `marketId`/`outcomeId`) or a bets-only read made for the shown picks after each Scan/Recheck
  (`OpenBetPricer.fairs`: the Tracker's pass, nothing written); "—" with the reason otherwise. **Tapping a pick** opens its sheet
  (`ParlayPickDetail`): the three EVs plus "Books" (Vigilant's worst case), every book's odds for the bet and its other side with
  CNO's verdict card and book table: CNO's game page when CNO lists the same bet, else (or when that page can't be read)
  ParlayAPI's own books (`ParlayBooks.view(league, event, start, market, bet)`: its `/props` or game lines, shared 2 min).
- **Why a pick's sheet so often said "no other book", and the fix (v0.33.0, TASKS.md Q1-Q2; measured with Tj's key 2026-09-30 14:4xZ):**
  ParlayAPI's picks are mostly home-run and touchdown props, and the books list those one-sided. MLB home runs (4 games): the scans'
  request (`markets=batter_home_runs`, which ParlayAPI answers with its own `player_home_runs` rows: aliases work; books = Pinnacle, DK, FD,
  Caesars, Bovada, ProphetX; `maxAgeSec=600`) got 146 rows, 138 of them "Over" only (FanDuel, Bovada, Caesars, most of ProphetX's), and the
  scans' reader keeps two-sided lines only; the books that price both sides (bet365, Hard Rock, Fliff, betPARX) weren't asked for. DraftKings
  had no HR rows. NFL anytime TD (`player_anytime_td`): 2,512 rows, **every** book "Yes" only (bet365, FanDuel, Caesars, Fanatics, Hard
  Rock, DraftKings, Bovada). Pick'em apps (PrizePicks, Underdog, Pick6, Sleeper) send flat payouts, flagged `is_dfs_flat_payout` /
  `dfs_normalized`. Rows mix time formats for one game (`…000Z`, `…Z`, `…-04:00`, `…+00:00`) and the same book can come twice.
  `age_seconds` spread widely (a book's HR price 20-50 min old next to others seconds old). `/props` can answer 200 with
  `{"error":"props_temporarily_busy","detail":"… Retry in a couple of seconds."}` (or a 503): asked again once.
  Fix: `data/.../reference/OtherBooks.kt`, for a tapped pick's sheet only (never a fair line): ParlayAPI `/props` for that market, **every
  book** (no `bookmakers`), `maxAgeSec=3600`, one-sided kept, pick'em apps and Novig left out, shared 2 min per league+market (3 credits);
  **PropLine** for that game at the same time (its events list + one game board, 18 books, one-sided kept: 1-2 of its free 1,000 a day);
  **The Odds API** (10 US books, about 1 credit) only when neither found another book. One line per book (a current price first, then
  two-sided); prices past the freshness limit listed apart with their age, never counted; Novig's live price is the judged row. The scans'
  own readers are unchanged (they still need both sides for a fair line).
- **Scans' props book list widened (v0.34.0, TASKS.md T2; measured 2026-09-30 ~15:40Z):** `GET /v1/bookmakers` lists 36 keys: pinnacle,
  superbet, bookmaker_eu, betonline, draftkings, fanduel, betmgm, caesars, fanatics, bet365, betrivers, bovada, novig, prophetx, polymarket,
  kalshi, robinhood, prizepicks, underdog, sleeper, fliff, parlayplay, betr, hardrock, parx, pick6, unibet(_be/_nl), pmu, betrivers_ca,
  rushbet, betway(_mz), tenbet, sportsbet_au. **An unknown key in `bookmakers` refuses the whole call** (HTTP 400 `UNKNOWN_BOOKMAKER`, e.g.
  `circasports`). `ParlayProps.BOOKS` is now pinnacle, draftkings, fanduel, betmgm, caesars, fanatics, bet365, betrivers, bovada, betonline,
  prophetx, fliff, hardrock, parx: a whole MLB slate (14 markets, `maxAgeSec=600`) was 1,453 rows, one page, still 3 credits, with two-sided
  lines from bet365 257, BetMGM 222, DraftKings 122, ProphetX 122, Fanatics 121, Caesars 49 (79 two-sided home-run lines). Live Check odds
  now test (`LiveCheckOddsPropsTest`, 15 random real MLB prop bets): 6 refreshed before, 10 after (Vigilant's read + ParlayAPI's every-book
  read); the other 5 were lines no book prices (Novig's alternate lines, a FanDuel-only "Yes").

### 6.6 Line-movement chart (M6) — `GET /v1/sports/{s}/line-movement?eventId=&market=&player=&hours=`, 2 credits
- **Unreliable (2026-09-30):** props lookups answered 503 `LINE_MOVEMENT_TIMEOUT` three times (**charged 2 credits each**), a moneyline
  lookup answered empty; the endpoint "reads the live prop_snapshots table, which holds roughly the last 6.5 hours". Samples
  `parlay-line-movement-busy-503.json`, `parlay-line-movement-empty.json` (its `note` lists why a lookup is empty). `eventId` is the
  `/props` row's `event_id` or `canonical_event_id`. No successful answer has been seen: **probe once and keep the sample before
  building the chart**.
- Build with guardrails: only on tap in a bet sheet, at most one retry after `retry_after_seconds`, then "ParlayAPI's history is busy";
  window ≤ 6 h. Show the bet's price over time per book as a small line chart. Say plainly if it stays unreliable.
- **Probed with Tj's key 2026-09-30 06:3xZ, and left out (not built):** `hours=6` for Aaron Rodgers (PIT @ CLE) answered 503 again (4th
  time, charged 2); `hours=1` answered 200 in 2 s: a list of series `{event_id, home_team, away_team, matched_by, source, player,
  market_key, line, snapshots:[{timestamp_ms, time, over_price, under_price, line}], count, opening_over, current_over, over_movement,
  opening_under, current_under, hours_tracked}` (sample `parlay-line-movement-prop.json`), but **only from the pick'em apps (Underdog,
  Pick6)**, none of the sportsbooks Vigilant prices from; the same lookup filtered to `bookmaker=caesars` (which listed the prop) came
  back empty. A chart of pick'em prices says nothing about a Novig bet's value, and a wide window costs credits for a 503, so the chart
  isn't worth building. Pinnacle's moneyline moves come free from `/v1/meta/movers` (§6.3) instead.

### 6.7 Period lines from more books (M7) — `GET /v1/sports/{s}/live/period_markets?period=1H|2H|Q1..Q4|OT|all&market=&source=`, 2 credits
- Samples `parlay-period-markets-nfl-1h.json` (one game) and `parlay-period-sources-nfl.json` (`/live/period_markets/sources`, 1 credit:
  which books carry which periods; NFL: bet365, BetMGM, Caesars, DraftKings, Fanatics, FanDuel for 1H/Q1…; Pinnacle 1H spreads/totals
  with alternates). Rows: `{source, match_id, home_team, away_team, commence_time, period_key, market: h2h|spread|total, line, side:
  home|away|over|under, price (American), timestamp_ms, age_seconds}`; one row per side, each with its own number: a spread's away row
  at −0.5 pairs with the home row at +0.5 (checked in the sample); totals pair over/under at the same `line`. Pregame too, despite "live" in the path.
- Use: a `ReferenceSource` feeding `RefBookMarket(period = 1)` (1st half; baseball's first 5 innings are period 1 too) and quarters only
  if Novig lists quarter markets; periods and hockey's P1–P3 need checking against Novig's catalog. Priced like any other source by the
  scanner; one call per league per period asked (1H by default), paced.
- **Built (v0.30.0):** `data/.../reference/ParlayPeriods.kt` (`ParlayPeriodSource`, id `parlay_1h`, in `Scanner.SOURCE_ORDER` after
  `parlay` and in `ScanSettings.enabledSources`: **a source id missing from those two is fetched but never priced**). Novig lists only
  `SPREAD_1H` / `TOTAL_1H` (no 1st-half moneyline), so only spreads and totals are paired. `needsCatalog`: no call (no credits) for a
  league whose Novig board has no 1H spread/total, or with the 1st-half family off. Football and basketball ask `period=1H`.
- **Probed with Tj's key 2026-09-30** (`period=all`, 2 credits each; samples `parlay-period-markets-mlb-all.json`,
  `parlay-period-markets-nhl-all.json`): **MLB names the first 5 innings `F5`** (Pinnacle only, spreads with alternates and totals, pairing
  as football's) and is asked `period=F5`, only while no Pinnacle feed of Vigilant's own is on (`pinnacleFeedOn`: those send Pinnacle's
  lines already). **NHL answers `P1`/`P2`/`P3`** (Pinnacle only); Novig lists no hockey period markets, so it isn't asked. The free sandbox
  (`/v1/sandbox/…/period_markets`, no key) answers every sport with the same made-up rows: useless for this.
- Freshness uses `observed_age_seconds` (§5): in the NFL answer Pinnacle's price had last changed 5.4 h earlier (`age_seconds` 19542) but
  was seen 9 s earlier, so it's priced, not dropped as stale (the first build read `age_seconds` and dropped it).

### 6.8 Not buildable on Starter
Streaming odds (`/v1/sse/odds/{s}`, `/v1/odds-drop/{s}`, websocket) need the Business plan ($40). Prop-line alerts (`/v1/alerts/…`) and
webhooks need a server to receive them.

### 6.9 Tuning review, 2026-09-30 ~07:10Z (Tj: "make sure the apis are being used to their full potential, especially my paid parlayapi")
- Re-read `/docs/best-practices`, the full endpoint list (214 paths in `openapi.json`) and the free cost catalogue `GET /v1/meta/credit-costs`
  (public; "cache the response for the life of your client process"). Every cost Vigilant assumes matches it: `/odds` = markets ×
  ⌈books/10⌉ (3, or 5 with alternates), `/props` 3, `/closing-lines` 5, the closes file 1 per 1,000 rows, period markets 2, injuries 1,
  line-movement 2; `/verdict` 5 and `/best-bets` 10 per their own descriptions.
- Fixed: a 503 with `Retry-After` is now waited out as the header says (capped at 20 s) before the one retry, and a busy /verdict waits the
  time it names (header, else the body's `retry_after_seconds`), per "honor the header; don't poll faster" (`TheOddsApiClient.retryAfterMs`,
  `Reply.retryAfterMs`; `ParlayRetryAfterTest`).
- Already as the guide says: the key in `X-API-Key`, one retry on a 502/504 or a dropped connection, none on 4xx, `X-Request-ID` in errors,
  degraded-mode books left out (`/v1/meta/source-quality`), the key's own figures read for free (`/v1/usage`), no ETag polling (live odds
  endpoints don't send ETags).
- Looked at and not used, with why: `/v1/exchange/{s}/markets` (3 cr: Novig's order book, which Vigilant reads free from Novig itself),
  `/v1/prediction-markets/{s}` (1 cr: Kalshi and Polymarket, read free directly), `/ev`, `/consensus`, `/compare`, `/arbitrage`, `/middles`
  (Vigilant computes its own fair lines against Novig's taker price), `POST /v1/clv` (5+ cr: Vigilant already gets closes from ParlayAPI's
  closing lines, ESPN and Novig's trades), `/v1/sports/{s}/scores` (1–2 cr: ESPN and MLB grade for free; a possible backup if they fail),
  `/live/api/sparkline` and `/moves.json` (free line history and moves; `/v1/meta/movers` already covers Pinnacle's moves).
- Check odds now now prices every open bet from Vigilant's own sources too (ParlayAPI's game lines, props and 1st-half lines among them),
  beside CNO's pages (TASKS.md O1): the pass is paced like a scan, so it spends from the day's share.

### 6.10 More books? (2026-09-30 ~20:00Z, RESEARCH.md §46)
- `bookmaker_eu`, `superbet`, `betr`, `polymarket` answered **no MLB or NFL game lines** on `/odds` (20 books asked, 2 credits a sport). Game lines
  beyond Vigilant's 10: BetRivers, Hard Rock, Fliff, betPARX, Kalshi only; 11+ books costs ⌈books/10⌉× the credits.
- Unfiltered `/props`: only books already in `ParlayProps.BOOKS`, plus DFS (pick6, prizepicks, underdog, sleeper). BetRivers ≈ betPARX (Kambi,
  94% identical NFL moneylines); bet365 ≈ Hard Rock on 75% of 28 NFL props.

## 7. Still unverified
- Whether the credits actually reset on the 1st (UTC) for a plan bought on the 30th (the /v1/usage period says so; check on Oct 1).
- Settled 2026-09-30 with Tj's key (15 credits in all: 19,867 → 19,852): /line-movement's shape (§6.6, pick'em apps only), MLB `F5` / NHL `P1–P3`
  (§6.7), /verdict answering the canonical prop key `player_rush_yds` (§6.4), `edge_pct` as probability points (§5).
