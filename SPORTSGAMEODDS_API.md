# SportsGameOdds (SGO) — permanent research memory

Researched 2026-10-09 from `sportsgameodds.com/llms.txt`, `/llms-full.txt`, `/docs/llms-full.txt`, `/docs/info/ai-vibe-coding`, `/openapi.json` (the authority: exact schemas), the rate-limit, error, consensus-odds,
best-practices, data-batches and streaming pages. **Nothing here was run against the live API yet** (Tj's Pro trial key was not in hand): every shape below is from the docs/OpenAPI. Verify the first real answer
(Settings › SportsGameOdds › Test key saves a sample to Downloads/Vigilant) and fix this file where reality differs. Never commit a key.

## 1. Plans — the docs DISAGREE, check the account page
| | Amateur | Rookie | Pro | All-Star |
|---|---|---|---|---|
| Price (pricing page, fetched 2026-10-09) | free | $99/mo | $299/mo | custom |
| Price (llms.txt, "Last updated 2026-06") | free | $149 | $499 | custom |
| Objects / month | 2,500 | 100,000 | **unlimited** | unlimited |
| Requests / minute | 10 | 50 | **300** | unlimited |
| Update frequency | 10 min | 3 min | **sub-minute** | max + WebSocket |
| Leagues / books | 8 / 9 | 17 / 77 | **53 / 82** (incl. Pinnacle, Circa, Bet365, Novig, Kalshi, Polymarket) | 53+ / 82+ |
| Historical data | — | — | yes | expanded |
All plans: 50k requests/h, 250-300k objects/h, 500k requests/day, 3M objects/day ("default limits"). Monthly object cap on a **rolling 30 days from first use**. A key's own limits can differ from the docs
(`GET /account/usage` is the truth). **WebSocket streaming (`/stream/events`, Pusher protocol, beta) is All-Star only: a Pro key gets 403.** So Pro = REST polling; the docs say to poll live every 30-60 s
and that polling faster "wastes quota" (the data itself refreshes about that often).

## 2. Basics
- Base `https://api.sportsgameodds.com/v2`. Key in header `x-api-key` (or `?apiKey=`; the app uses the header, never a URL). JSON: `{success, data, error?, nextCursor?, notice?}`.
- **Billing is per event object returned** (min 1 per response), not per market/book. One game with 200 markets x 20 books = 1 object. Polling the same game twice = 2 objects. Pro is unlimited objects, so the budget is requests/minute.
- Errors: 400 bad params, 401 key, 403 plan/feature (e.g. stream), 404, 429 rate (wait up to a minute), 500/503/504 retry ONCE after 2-5 s. 504 = query too heavy: drop `includeAltLines`, `startsAfter/Before`, `playerID`, `bookmakerID`, `teamID`.
- `notice` appears when the key's plan filtered data out.
- Pagination: `limit` (default 10; **events max 25-100 depending on the query**; >300 errors) + `cursor`=`nextCursor` unchanged. `eventID`/`eventIDs` alone is the fastest query (other filters ignored; `oddID`, `bookmakerID`, `playerID` still shape the response).

## 3. `GET /events` (the one that matters)
Params: `eventID(s)`, `sportID`, `leagueID` (comma lists), `type`, `oddsAvailable`, `oddsPresent`, `oddID` (list; `PLAYER_ID` wildcard for the player part), `includeOpposingOdds`, `includeAltLines`,
`expandResults`, **`includeOpenCloseOdds`**, `bookmakerID`, `teamID`, `playerID`, `finalized`, **`live`**, `started`, `ended`, `cancelled`, `startsAfter`, `startsBefore` (ISO or unix ms), `limit`, `cursor`.
Keep to 1-3 params besides limit/cursor for speed. Default response carries ALL odds and ALL books of each event: always pass `oddID` lists to shrink it.

Event: `eventID, sportID, leagueID, type (match|tournament|prop), status{startsAt, started, ended, live, finalized, completed, cancelled, delayed, currentPeriodID, previousPeriodID, displayShort,
displayLong, oddsPresent, oddsAvailable, periods{started[],ended[]}}, teams{home|away{teamID, names{short,medium,long}, score, statEntityID}}, results{<periodID>{<statEntityID>{<statID>: n}}}, odds{<oddID>: Odd}, players{}`.
No clock seconds: only `currentPeriodID`/`displayShort` (so a time-left fraction still needs ESPN, `LabClock`).

`oddID` = `{statID}-{statEntityID}-{periodID}-{betTypeID}-{sideID}` (no bookmaker). Examples: `points-home-game-ml-home`, `points-away-game-sp-away`, `points-all-game-ou-over`, `points-home-game-ou-under`
(team total: statEntityID home/away), `passing_yards-PATRICK_MAHOMES_1_NFL-game-ou-over` (prop: statEntityID = playerID). betTypeID `ml sp ou eo yn ml3way prop`; sideID `home away over under even odd yes no draw ...`.
periodID: `game` (includes OT), `reg`, `1h 2h`, `1q-4q`, `1p-3p` (hockey), `1i-9i`, `1ix5` (deprecated: baseball first 5 = `1h` now), `ot`, `so`.
statIDs (from the Markets page): `points` (runs/goals/winner-deciding), `assists rebounds steals blocks turnovers threePointersMade`, `points+rebounds+assists points+assists points+rebounds rebounds+assists`,
`passing_yards passing_attempts passing_completions passing_touchdowns passing_interceptions rushing_yards rushing_attempts rushing_touchdowns receiving_yards receiving_receptions receiving_touchdowns
rushing+receiving_yards passing+rushing_yards touchdowns`, `batting_hits batting_homeRuns batting_RBI batting_totalBases batting_strikeouts batting_basesOnBalls batting_stolenBases batting_hits+runs+rbi
pitching_strikeouts pitching_outs pitching_hits pitching_earnedRuns pitching_basesOnBalls`, `goalie_saves shots_onGoal goals+assists` (hockey points), `defense_sacks defense_interceptions defense_combinedTackles`.

### Odd
`oddID, opposingOddID, marketName, statID, statEntityID, periodID, betTypeID, sideID, playerID, started, ended, cancelled, bookOddsAvailable, fairOddsAvailable, fairOdds, bookOdds, fairOverUnder, bookOverUnder,
fairSpread, bookSpread, score, scoringSupported, byBookmaker{<bookmakerID>: {odds, overUnder, spread, available, isMainLine, lastUpdatedAt, deeplink?, altLines[], openOdds, closeOdds, openSpread, closeSpread,
openOverUnder, closeOverUnder}}`. **All values are STRINGS** (American odds "-110", lines "224.5"). `altLines[]` (with `includeAltLines=true`) = `{odds, available, spread, overUnder, lastUpdatedAt}`.
`closeOdds`/`closeSpread`/`closeOverUnder` = the book's line **at event start** and `openOdds…` when first offered (only with `includeOpenCloseOdds=true`) — a per-book closing line for CLV straight from the API.
`score` on a finalized odd + `closeOverUnder` is how the docs grade over/unders; `results` gives the stat values.
`lastUpdatedAt` per book/market is the freshness stamp the app's rule needs (compare with the 5 min / 10 min limits in `Freshness`).
`fairOdds`/`bookOdds` are SGO's own consensus (median of books after a regression on the line, juice removed). **Vigilant does not use them as the fair line** (its per-book both-sided devig and outlier guard are
stricter); they are logged for comparison only.

## 4. Other endpoints
`/sports /leagues /teams /players /stats /markets` (metadata; cache), `GET /account/usage` (per-second/minute/hour/day/month `maxRequestsPerInterval`, `currentIntervalRequests`, `maxEntitiesPerInterval`,
`currentIntervalEntities`, `currentIntervalEndTime`), `GET /stream/events` (All-Star only).

## 5. bookmakerIDs (82+; from the docs): the ones that matter here
`pinnacle circa bet365 draftkings fanduel betmgm caesars espnbet fanatics betrivers bovada betonline williamhill unibet betway superbook lowvig matchbook sporttrade prophetexchange novig kalshi polymarket`
(+ DFS apps `prizepicks underdog sleeper parlayplay` whose prices are not bets, never a fair line). Vigilant's reference-book keys are The Odds API's (`williamhill_us`, `betonlineag`, `hardrockbet`,
`espnbet`); `SgoBooks` maps them.

## 6. How Vigilant uses it (Settings › SportsGameOdds Pro)
- Provider `ApiProvider.SPORTSGAMEODDS`, keys entered like the others (several allowed; `KeyPool` rotation, rest on 429, invalid on 401/403).
- Toggle `ScanSettings.sgoPro` (default off). On: the scan, the bid desk, open-bet pricing and the closing lines read SGO (`SgoGamesSource`, `SgoPropsSource`) and the paid feeds that sell the same
  thing (PinnWire/pinnapi Pinnacle, PropLine, The Odds API, ParlayAPI odds/props/halves) are not called. Kept: Kalshi and Polymarket (free, add exchange prices), ESPN (free, scores/clocks).
- Pacing: 300 requests/minute is 5 a second; the client spaces calls 220 ms apart (270/min) and never runs two scans of one league at once.
- Live: `live=true` per league, polled at most every ~30 s per league (SGO refreshes about that often).
- A reply's `lastUpdatedAt` becomes the quote's age; a quote with none is treated as unknown age (the freshness rule then refuses it for bets, as for every other feed).

## 7. Unverified / watch list
1. Exact casing/format of `lastUpdatedAt` (ISO string assumed; the parser accepts ISO or epoch ms).
2. Whether `byBookmaker.<id>.odds` for the opposite side sits on the `opposingOddID` (assumed: yes, each side is its own Odd with its own book prices).
3. Whether Novig-as-a-book is on the Pro plan (the books list says yes) and how stale it is: useful as a cross-check, never a fair source.
4. Pro-plan object/request headroom in practice, and `notice` text when something is filtered.
5. The price: $299 or $499 for Pro (the pages disagree).
