# Full API audit: every API's docs against the code (AF, 2026-10-22, acct-27a37e tj Gmail)

Tj, 2026-10-22: "do a full analysis of each api used. Thoroughly and fully review the docs. Look for potential llms docs ... used to its full potential, accuracy, speed, and features ... make sure usage is not wasted ... Fix and implement all changes and updates."

## Method
- **Docs, verbatim, not summaries.** New workflow `api-docs.yml` + `tools/api_docs_fetch.py` fetched every provider's `llms.txt`, `llms-full.txt`, OpenAPI and every page an `llms.txt` links to (685 pages, ~17 MB) onto the never-rewritten `api-docs` branch (`latest/`, `snap/<day>/`), plus public reference data (`data/`: Kalshi sports series, Novig market types and live types, Polymarket sports, SX leagues, SharpAPI health). It runs weekly (Mon 09:17 UTC) and writes `CHANGES.md` = what each provider added/changed/removed since the last run, so the next audit starts from the diff instead of re-reading everything. Read: `git fetch origin api-docs && git show origin/api-docs:CHANGES.md`.
- **Live usage** from the engine's `status.json` (apiCards, feedReport, apiChecks, advice) and `usage.json` on `cloud-feed` at 22:08 UTC, and the engine log.
- Every finding below was checked in the code before changing it; each change has a test.

## What the engine looked like at the start (22:08 UTC)
| API | today | fed lines / only it | finding |
|:--|--:|--:|:--|
| SharpAPI | 467k calls (flat 1,000/min) | 3,598 / 2 | stream live; fine |
| SGO | 13.8k calls | 11,470 / 190 | uses `oddID` filters as its docs advise; fine |
| PropLine (Pro + 2 free) | 13.4k of 25k | 18,556 / 1,804 | props read **one call per game** (docs: one bulk call per slate) |
| Kalshi / Polymarket / SX | 24k / 12.6k / 29k (free) | 8,304 / 2,444 / 1,310 | Polymarket priced from Gamma's cached copy |
| BoltOdds (Pro) | sockets | 1,422 / 0 | **props socket refused "Too many concurrent connections"**; fair line counted `consensus`, `dk predictions`, `fd predicts` as books |
| ParlayAPI | 99 calls | 922 / 0 | surplus (61% left, 30% of month) |
| PinnWire (13 keys x 100/day) | 2,457 | standby | 12 of 13 keys spent: asked one request per sport |
| The Odds API (21 free keys) | 8,983 calls, 0 credits | 0 | advisor said "will not last" counting free calls as credits |
| Unabated (free) | 1,704 calls | 0 | quotes p90 20.6 h old, 9 pts off Pinnacle, held the fair-odds step 75 s |
| OddsPapi Free | 458 | 0 | closes only; fine |
| Novig | 7,258 calls | | cold scan read 13,727 books in 420 s |

## Changes made (all tested; `bash tools/test.sh`)
1. **Novig: the catalog's best bids screen the scan** (biggest speed win). Since 2026-10-20 every market in `GET /v3/catalog/markets` carries each outcome's `bid {price, qty}` and `last`; one request lists up to 5,000 markets. `NovigPublicClient.keepTops` keeps the SIGNED route's bids as best-bid-only books (`NovigBook.partial`), `pushedTop` serves them for 30 s after the read where the stream holds nothing, `refreshTops` re-reads a query every 12 s while a scan prices, and the existing `Scanner.deepenEdges` reads every +EV one as a complete book before it is shown (so nothing is sized or shown from a top alone). The public route is edge-cached (age unknown): never used for this. A cold scan's ~9,600 one-by-one book reads become 1-2 catalog requests plus the deepen reads. Test: `NovigPublicClientTest`.
2. **Novig fees:** the Maker Credit fell to 25% live / 50% on futures on 2026-10-22 (changelog). Markets carry it per market (read as before); the fallback table `MarketFee.gameFor`/`futuresFor` now switches at Eastern midnight 2026-10-22. Test: `FeesTest`.
3. **PropLine bulk props:** a paid key now reads a whole slate's props with `GET /sports/{sport}/odds?markets=<prop keys>` (in parts of 6 markets), not one `/events/{id}/odds` per game (docs 2026-10-22: "a 15-game MLB night is 1 request, not 15"; PropLine sends `X-PropLine-Hint` to keys that do the latter). A failed bulk read falls back to per game. Expected: props requests cut by about the number of games per league. `PropLinePropsSource.bulkReads/bulkFailures`. Test: `PropLineClientTest`.
4. **PropLine `player_blocks_steals` -> Novig `STEALS_BLOCKS`** (Novig relisted it 2026-10-22; PropLine added the key).
5. **Injuries: PropLine's free report first.** `/v1/sports/{sport}/injuries` (any key, ESPN + college conference reports, `status_code`) is asked before ParlayAPI's 1-credit `/injuries`; ParlayAPI only for a sport PropLine did not answer. `PropLineInjuries`. Test: `ParlayInjuriesTest`.
6. **BoltOdds: no double counting.** With "every BoltOdds book" on, the fair line counted `consensus` (a line derived from the other books) and `dk predictions` / `fd predicts` (DraftKings'/FanDuel's own prediction markets, the same companies). `OpBooks.EXCLUDED` now leaves them out (CLAUDE.md's one-company rule). Test: `OddsPapiTest`.
7. **BoltOdds `/api/odds/user`** (new in its docs): the key's open sockets and `/api/snapshot` token budget. Shown in the key check (Settings > APIs and the engine's API checks: "N sockets open on this key - snapshot tokens X of Y today") and appended to a "too many concurrent connections" refusal, so a half-open socket from an earlier runner is visible. Tests: `BoltOddsTest`, `ApiProbeTest`.
8. **Clean socket close at job end:** `CloudSources.closeSockets` waits 1.5 s for OkHttp's close frames to leave before the JVM ends (the refused props socket at 21:58 came from the previous job's half-open sockets).
9. **Polymarket live prices:** Gamma's `bestBid`/`bestAsk` are a cached copy; after Gamma lists the games, the first outcome token of each market is re-priced from the CLOB's live `POST /books` (100 tokens a call; 500 calls/10 s allowed, keyless), stamped when read. A market the CLOB does not answer keeps Gamma's price. Protocol V2 markets' `positionIds` handled. Test: `ExchangeClientsTest`.
10. **PinnWire `sport_id=all`:** one request answers every sport (PinnWire's docs), cut one event at a time (`JsonSplit`) and kept only for the sports Vigilant prices; the next leagues read it from memory. A scan's ~6 sport requests become 1 of the key's 100 a day (on top of CH60b, which asks PinnWire only where SharpAPI's Pinnacle is missing). pinnapi and Pinnodds take one sport per request (their docs): unchanged. Test: `ExchangeClientsTest`.
11. **API governor rests useless APIs:** an API that answers but matches no Novig game for 6 scans in a row, while other feeds matched games, is rested 1 h / 3 h / 9 h (never switched off for good on that ground); exchanges and memory-read sockets are never rested for it. Unabated (free tier: DraftKings, FanDuel, MGM, Caesars, theScore, Fanatics, PrizePicks/Underdog, exchanges; 15 s+ delayed; no streaming) gives nothing other feeds lack and cost 75 s of every fair-odds step. Test: `ApiGovernorTest`.
12. **Advisor pace in credits:** "will not last to its reset" is judged from credits used over the period, not today's request count (free calls such as The Odds API's `/events` made it tell Tj to buy a plan for a key at 4,494 of 10,500). Test: `ApiAdvisorTest`.
13. **Docs mirror workflow** (`api-docs.yml`, weekly, free, keyless) and its `CHANGES.md`: the reason Claude "finds more each time" was that each audit re-read the docs from memory files; now the providers' changes come to the repo by themselves.

## Checked and found right (no change)
- **SharpAPI:** docs unchanged since the 10-21 build (llms-full byte-identical); stream + delta + closing + EV journal match the docs. Flat plan, under its 1,000/min.
- **SportsGameOdds:** `oddID` filtering on `/events` (its #1 speed advice), Pro limits read from `/account/usage`.
- **ParlayAPI:** costs per its table (bookmakers= priced per ten books), `include_live` is a real parameter, `maxAgeSec` sent on `/props`, 1st halves from `/live/period_markets`; in surplus, so no cut.
- **Kalshi:** token buckets read from `/account/limits` (Basic 200 read tokens/s = 20 reads/s at 10 tokens), `/portfolio/balance` now has its own bucket (Oct 15) and is not polled; V2 order routes; batch calls do not save tokens (docs), so none were added.
- **SX Bet:** limits and channels match SXBET_API.md; betting delays apply only to takers (Vigilant reads prices only).
- **The Odds API:** last in line (PropLine's fallback); its 8,983 calls were free `/events` lists and key checks, not credits.
- **OddsPapi v4 (free):** used for closes from the free historical route.

## Found, not built yet (TASKS.md AF6-AF10)
- **Kalshi `GET /live_data/batch`** (public, 100 milestones a call; changelog 10-22 says it is the live game status/score source, not `milestone.details.status`): a candidate score source for the live lane and tennis (CH56). Needs a recorded sample during live games first.
- **Novig `GET /v3/catalog/participants`** (players/teams with their market ids) to match props by id instead of by description; **`/v3/history/markets/{id}/candles`** for Novig's own closing price in CLV studies; **live NFL/NCAAF first halves** stay open live (Oct 22): the live lanes do not price 1H markets.
- **SharpAPI `/splits`** (public betting %, Pro+): could be journaled for the studies (public vs sharp money); not a price.
- **BoltOdds** now lists Circa, Bet365 and BookMaker (its llms.txt): with "every book" on, the engine subscribes to whatever `get_info` lists, so they flow in when the key carries them; read the engine's Bolt board census to confirm Circa arrives (a sharp book on a push socket).
- **PropLine `line_movement` websocket** needs Streaming Lite ($39/mo, 250k requests/day): would push every price change instead of polling; Tj's call (money).
- **Unabated:** recommend switching it off for good (Settings > APIs); the governor now rests it automatically either way.
- **pinnapi:** both keys answer 403 (refused): replace or remove them.

The report page for Tj: `research/api_audit_report_2026-10-22.html` (published as an artifact).

## How to judge it tomorrow
Engine log `scan report:` lines: "Novig prices in N s" (expect the cold scan's Novig step far under 420 s), `PinnWire accounts:` (keys should last into the evening), PropLine `plans:` used vs share (props requests per scan down), `API governor: resting Unabated ... (matched nothing)`, BoltOdds props socket live (no "too many concurrent").
