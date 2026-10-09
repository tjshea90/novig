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

## 8. Spec audit (2026-10-09, v0.83.0): every request Vigilant makes, against the docs
Source of truth: `openapi.json` (parameters), the errors / rate-limit / best-practices / data-batches / consensus-odds pages. Where two doc pages disagree the OpenAPI wins (marked ⚠).
| Call | What Vigilant sends | Spec check |
|---|---|---|
| Auth | header `x-api-key: <key>` (never the URL) | ✓ cheat-sheet/setup: header or `apiKey` query; header chosen so keys never reach a log or URL |
| Games scan | `GET /v2/events?leagueID=NFL&oddsAvailable=true&limit=100&oddID=<list>&bookmakerID=<books SGO lists>[&includeAltLines=true]` | ✓ all four are OpenAPI params; `oddID` (singular, comma list) per OpenAPI ⚠ (the response-speed page says `oddIDs`, the OpenAPI/cheat-sheet say `oddID`); ✓ filtering by `oddID` is SGO's own speed advice; ✓ `limit` 100 ≤ the 25-100 events cap and < 300; `bookmakerID` (comma list, only ids in SGO's own list, so Hard Rock can never cause a 400) keeps replies small per SGO's league pages, but its errors page lists it with `includeAltLines` as what slows a query: a 504 drops the alternates, then the book filter. ⚠ `oddID` vs `oddIDs`: the OpenAPI says `oddID`, some example pages say `oddIDs`; Vigilant sends the OpenAPI's, and "Test key" reports whether the filter was honored |
| Props scan | same with `oddID=<stat>-PLAYER_ID-game-ou-over,...-under` (+ `-yn-yes/no` for touchdowns, double-doubles, home runs), `bookmakerID`, `includeAltLines` | ✓ `PLAYER_ID` wildcard is documented (response-speed page); both sides listed explicitly so `includeOpposingOdds` is not needed; alternate prop lines are documented ("including alternate lines") |
| Paging | `cursor=<nextCursor>` unchanged, all other params repeated; stops at `nextCursor` null, at most 6 pages | ✓ data-batches page |
| Closes (CLV) | `leagueID`, `startsAfter=<unix ms>`, `startsBefore=<unix ms>`, `includeOpenCloseOdds=true`, `oddID=<list>` | ✓ date params accept ISO or unix ms (errors page); ✓ `includeOpenCloseOdds` gives `closeOdds/closeSpread/closeOverUnder` per book; ⚠ `startsAfter/Before` + many params are on SGO's slow list → on 504 the `oddID` filter is dropped and it is asked again |
| Scores | `startsAfter/startsBefore` for an Eastern day, `oddID=points-home-game-ml-home` (smallest reply); a box score by `eventID=<id>&expandResults=true` | ✓ `eventID` alone is the fastest query (best-practices); `expandResults` is an OpenAPI param; ✓ `teams.*.score` and `status.finalized` per the schema |
| Other books on a tapped prop | `leagueID`, window of ±6 h, `oddID=<stat>-PLAYER_ID-game-ou-over,..-under`, `includeAltLines=true` | ✓ as above |
| Key test | `GET /v2/account/usage`, then `GET /v2/events?leagueID=..&oddsAvailable=true&limit=2&includeAltLines=true&includeOpenCloseOdds=true` | ✓ `/account/usage` returns `rateLimits.<interval>.{maxRequestsPerInterval,maxEntitiesPerInterval,currentIntervalRequests,currentIntervalEntities,currentIntervalEndTime}` |
| Errors | 401 key refused (rotate to the next key); **403 is that call's failure only** (plan/feature: stream is All-Star); 429 key rests 60 s (docs: "wait up to a minute") or until `Retry-After`; 500/503/non-JSON/no-`success` body → ONE retry after 2-5 s, no loop; 504 → lighter query once | ✓ errors page, rate-limit page |
| Rate | calls spaced 220 ms (≤ 272/min of the Pro 300); the meter also caps 270/min per key; hourly/daily default caps (50k req, 300k objects per hour) are far above use | ✓ rate-limit page |
| Polling | game lines reused 25 s, props 45 s, the lab's board 25 s; never faster than SGO refreshes ("polling faster than 30 s wastes quota") | ✓ best-practices "Polling Too Frequently" |
| Not used | `/stream/events` (All-Star only), `fairOdds`/`bookOdds` as the fair line (Vigilant devigs each book itself), `deeplink`, `/teams /players /sports /leagues /stats /markets` (static; the schema is in this file) | — |

### What "full power" covers (feature by feature, SGO on)
| Vigilant feature | Reads SGO? | How |
|---|---|---|
| +EV scan (game lines, alternates, 1st-half/F5/first inning, team totals, props) | yes | `SgoGamesSource`, `SgoPropsSource` lead the source list |
| Extra sportsbooks | yes | Circa, SuperBook, bet365 join the fair line (`sgoExtraBooks`) |
| Live games | yes | `includeLive` honoured; ~30 s refresh; each price's own age gates it |
| Bid desk (make orders) | yes | prices from the same scan; the bid desk's fair is SGO's |
| Open-bet EV ("Check odds now", Tracker) | yes | `OpenBetPricer` uses `referenceSources` |
| CLV / closing lines, incl. bets already in the Tracker, historical | yes | `SgoCloses` first (Pinnacle, then Circa), then ParlayAPI/ESPN/Novig trades only for what SGO lacked |
| Grading win/loss | yes | `SgoScores` first (final only when `finalized`), ESPN/MLB behind; a prop's box score prefers the free feed |
| Tapped bet's "other books" | yes | `OtherBooks` asks SGO alone (every book it carries, alternates included) |
| Injury tags | yes | SGO `players.*.status` fills `InjuryIndex` free; ParlayAPI's paid injury read stands down |
| Paper lab (live alternate lines) | yes | `SgoAltQuotes` replaces dormant Pinnodds |
| GitHub lab | yes | `lab-record.yml` with the `SGO_API_KEY` secret: scan, paper bids, EDGE LOG, SGO TAPE, SGO closes |
| Low-usage bids | stands down | SGO has no per-call budget to save |
| ParlayAPI second opinion / its picks | no (taps only) | ParlayAPI-specific features, spent only when Tj taps them |
| CrazyNinjaOdds, Kalshi, Polymarket, ESPN | unchanged | free or not replaceable |

## 9. The GitHub lab (v0.83.0)
`lab/` is a plain JVM program (`./gradlew :lab:run --args="--minutes 60 --out out"`) running Vigilant's own `Scanner`, `LabRecorder` and `BidLab` on public data; `.github/workflows/lab-record.yml` runs it every 6 hours (and on demand).
Output on the `lab-data` branch: `latest.txt` (the phone's research-file format), `state/` (what the next run restores), `archive/<date>/*.gz` (every journal: `edge` = every Novig price against the outside fair on change; `sgo-tick` = every
book's main-line price change with SGO's own update time; `sgo-close` = each book's price at the start; plus `lab`, `lab-grade`, `bidlab`, `bidlab-event`). Only one secret is needed: `SGO_API_KEY`. It places no order.
What it measures that the phone cannot: how often SGO really refreshes each book and how old a price is when read (the SGO TAPE), around the clock including when the phone is off; what it cannot: your phone's network (Novig may treat a
datacenter IP differently), your wallet and your own bids' queue position.

### 8b. Re-read of the docs (2026-10-09, after v0.83.0): what changed in Vigilant
- OpenAPI re-fetched: unchanged since the first read. Parameters used are exactly its `/events` list; nothing deprecated.
- **New:** `bookmakerID` filter on game and prop scans (league pages: "Add bookmakerID=... to limit to the bookmakers you care about"), restricted to ids SGO lists (`SgoBooks.KNOWN`).
- **New:** alternate PROP lines (`includeAltLines` on the props call; the player-props page says alternates are included on every prop), with the same 504 fallback chain.
- **New:** the closes read asks `bookmakerID=pinnacle,circa` only; its 504 chain drops `oddID`, then the book filter.
- **New:** "Test key" times five ways of asking on the real key (oddID only; + bookmakerID; + alternates; alternates without the book filter; no filters) and says whether `oddID` was honored, so the order can be tuned from the sample.
- Noted, not built: Pinnacle is "Pro and above" and "refreshes within seconds" (the sample's `lastUpdatedAt` will say how true); Novig is a bookmaker on Rookie and above (`bookmakerID=novig`: a possible independent check of Novig's own price against its public API, never a fair source); an optional WebSocket exists only on All-Star.
