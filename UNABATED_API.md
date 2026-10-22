# UNABATED_API — Unabated Data API, FREE tier (data.unabated.com), read 2026-10-11

Tj, 2026-10-11: "Add two more apis to the app. Unabated api and oddspapi api. Both apis are the free tiers." Docs read in full: `https://docs.unabated.com/llms-full.txt` (435 KB), `/start/agents`,
the OpenAPI document `https://data.unabated.com/swagger/v1/swagger.json` (35 operations, 125 schemas). Raw copies: `research/unabated_oddspapi_2026-10-11/`. **Nothing here has met a live key
yet** (the parser was written from the docs' examples, like SGO's and OddsPapi v5's were); Settings › Unabated › "Test and share sample" saves the key's real answers so it can be checked
(TASKS.md UN5). **Never commit an Unabated key.**

## 1. What the FREE API offers (docs: start/access-tiers, per-operation `x-unabated-tiers`)
| | Free API |
| :- | :- |
| REST odds `GET /market/{league}/{straight\|props\|futures}/odds` | **yes, delayed at least 15 s** ("not a promise every line is exactly 15 s old"; polling faster does not make it fresher) |
| Rate | **ONE odds request every 5 s per key, shared by every odds endpoint** (a NFL call spends the interval of an NBA call); `429` + `Retry-After`; `503` + `Retry-After: 5` = delayed snapshot briefly unavailable. No monthly quota is stated. Discovery calls do not use the odds interval. |
| Books | DraftKings, FanDuel, MGM, Caesars, theScoreBet, Fanatics, ProphetX, **Novig**, Kalshi, Polymarket, PrizePicks, DraftKings Pick6, Underdog, Underdog Prediction Markets. **No Pinnacle, Circa or bet365; no Unabated consensus line.** |
| Leagues | "Supported sports with available included-book data" (ask `GET /league`; Concierge is NFL/NBA/MLB/NHL/WNBA only) |
| Discovery (free of the odds interval) | `/league`, `/bettype`, `/market-type`, `/period-type[/{league}]`, `/market/{league}/{type}/sources|teams|people`, `/event/{league}/upcoming`, `/team/{league}`, `/player/{league}`, `/season/{league}`, `/tournament/{league}` |
| Deep links `GET /deeplink`, bet-slip `POST /api/v1/bet/generate` + `GET /api/v1/bet/status/{guid}` | free + enterprise. **Not used by Vigilant** (it publishes no links). |
| SSE (`POST /subscriptions`, `GET /sse/{id}`), bonuses, promos, player news, percent-to-play, NFL in-game model, SGP, article widgets, lineups | **not in Free** (a Free key gets `403 {"message":"This endpoint is not included in Free API access."}`) |
Catch (docs, access-tiers): Free access "is provided to drive sportsbook traffic through Gambly deep links... Access may be revoked at Unabated/Gambly's discretion when the partner conditions are
not met." Vigilant is a personal app and publishes no links; Tj should know his key could be revoked (a `401` then). The app never touches `deepLink` fields.

## 2. Wire format (docs: guides/odds, response-concepts, errors-and-limits)
- Auth: header `X-Api-Key`. Base `https://data.unabated.com`. Envelope `{"data":…,"success":true,"messages":[]}`: check HTTP status, then `success`. Errors: 400 bad input, 401 key, 403 tier, 404, 429, 503.
- `data.odds` -> league route name (`nba`) -> `{leagueId, periodTypes{ "<name>" -> {periodTypeId, pregame{key -> group}, live{key -> group}} }}`.
  group = `{eventId, isLive, eventStart, eventEnd, statusId (1 pre,2 live,3 final,4 delayed,5 postponed,6 cancelled), betTypeId, betSubType, eventName ("Thunder Oklahoma City OKC @ Pacers Indiana IND"), eventTeams{"0":{teamId,sideIndex,score}…}, sides{key -> side}}`.
  side = `{sideIndex (0 away/over, 1 home/under, 2 tie), sideName, teamId, personId, marketSourceLines{"<sourceId>" -> line}}`.
  line = `{marketLineId, marketSourceId, points, price (American int), sourcePrice, sourceFormat, liquidity, statusId (1 available, 2 unavailable, 3 price unknown), disabled, modifiedOn, sequenceNumber, freshnessExpiresAt, openerPrice/Points, edge, bestAlt*, alternateLines[{points, price, statusId, modifiedOn, alternateNumber}]}`.
  `data.lastUpdated` is Unix ms of the newest line (the spec's own example shows seconds: read both). Null fields are omitted: tolerate everything missing.
- Identity is by structured fields (`eventId`, `betTypeId`, `sideIndex`, `marketSourceId`, `points`), never by dictionary key spelling. Do not hard-code IDs: `/league` (names), `/bettype` (names; quickstart says `betTypeId=1` is NFL moneyline), `/period-type/{league}`, `/market/{league}/{type}/sources` (book ids + `statusId` 1 high-frequency / 2 low / 3 offline, `isActive`, `disabledMarketTypeIds`).
- `/event/{league}/upcoming`: events with `eventTeams[{teamId,name,sideIndex,score,rotationNumber}]` and `name`; used for the team names the odds board only gives inside `eventName`.

## 3. How Vigilant uses it (built v0.89.0, `data/.../reference/Unabated*.kt`)
Settings › **Unabated** (key list, Test key / Test and share sample, one switch `ScanSettings.unabated`). Switch ON and a key saved and answering (`unabatedActive`):
- **A free, last-in-line fair-line source.** `UnabatedGamesSource` (`unabated`) and `UnabatedPropsSource` (`unabated-props`) sit LAST in `Scanner.SOURCE_ORDER`. `Pricing` keeps one quote per book per line and the EARLIER source wins ("best API first"), so
  Unabated prices a line only when no better feed sent that book's quote for it: SGO / OddsPapi v5 / ParlayAPI / PropLine / The Odds API out of credits, down, or not carrying the league/book. That is the automatic fallback Tj asked for, with no new logic in the scanner.
  Nothing it sells is bought twice: it costs no credits, so no other feed rests for it.
- **Books priced:** DraftKings, FanDuel, BetMGM, Caesars (`williamhill_us`), theScore Bet (`espnbet`, as OpBooks), Fanatics. Never the fair line: Novig (the thing priced), Kalshi / Polymarket (Vigilant reads them directly), PrizePicks / Pick6 / Underdog (not bets), ProphetX (an exchange Vigilant treats on its own terms, as SGO).
  Respects the Reference-books picker. Each quote carries the line's `modifiedOn` as its update time, so Vigilant's freshness rules (not Unabated's 15-second promise) decide whether it prices.
- **Keys rotate and each has its own interval** (the Free limit is per key, so more keys = more speed, 2026-10-11): `UnabatedClient` offers the pool the keys soonest-ready first; a key refused (401), rate limited (429, with another key to try) or spent is skipped to the next, a call waits only for the key it was given, and the boards' re-read time shrinks with the key count (down to 20 s). Per key, EVERY odds call is serialised 5.2 s apart (`Retry-After` honoured, bounded retries, `success:false` is a failure, 401 refuses the key, 403 = tier). Per scan that is a games board (`straight`) and, with props on, a props board per league.
  Boards are cached 120 s (games) / 180 s (props) and **served stale-while-revalidate**: a scan never waits behind the 5 s gate, it takes the last board and a background read refreshes it for the next scan. First read per league waits once.
- **Discovery** (`/league`, `/bettype`, `/period-type/{league}`, `/market/{league}/{type}/sources`, `/event/{league}/upcoming`) is cached a day (events 10 min) and never spends the odds interval.
- **Down / fallback:** two failures and 2 minutes of silence = `down()`; the source then gives nothing and the paid feeds are unaffected (they never rested). A `429` waits its `Retry-After`; `403` on odds = the key lost its free scope (reported in Diagnostics).
- Not built, and why: SSE (Concierge+), deep links / bet slips (Vigilant publishes none), futures (Vigilant prices singles), `edge`/`bestAltEdge` (Unabated's own consensus edge: unverified, and not a price source), grading (no results endpoint).

## 4. Not known until a live sample
Bet-type names (the example shows `betTypeId` 71/72 for NBA totals, 1 = moneyline), which leagues the free key lists, prop bet-type naming (unmapped ones are listed by Test key), period names beyond `game`, whether Novig's rows carry `sourcePrice`.

## Docs re-read 2026-10-22 (AF)
Free API: REST only, at least 15 s delayed, one odds request per 5 s per key, books DraftKings, FanDuel, MGM, Caesars, theScoreBet, PrizePicks, Pick6, Fanatics, Novig, Underdog, ProphetX, Kalshi, Underdog PM, Polymarket; SSE is Concierge/Enterprise only. Every one of those books reaches the engine through a better feed, and on 2026-10-22 its quotes were p90 20.6 h old and matched 0 games while holding the fair-odds step 75 s: the API governor now rests an API that matches nothing for 6 scans (`ApiGovernor.USELESS_LIMIT`). Recommendation: switch it off.
