# ODDSPAPI_API — OddsPapi v5 (oddspapi.io, 55-tech), as read from its docs 2026-10-09

Tj, 2026-10-09: "I will get a free trial of the next best api to test. Figure out which is the best api to test for a free trial that I don't already have." This file is the permanent memory of why OddsPapi
was chosen, what its docs say (nothing here has been run against a live key yet: the parser was written from the OpenAPI examples, like SGO's was; Settings › OddsPapi › "Test and share sample" saves
the key's real answers for checking), how Vigilant uses it, and what to do when Tj sends the sample. **Never commit an OddsPapi key.**

Docs read in full: https://docs.oddspapi.io/llms-full.txt (plain text, ~230 KB), https://docs.oddspapi.io/api-reference/openapi.json (OpenAPI 3.1, `servers: https://v5.oddspapi.io/en`), and per-page `.md`
(append `.md` to any docs URL). Changelog read to 2026-10-09.

## 1. Why this one (and the ones skipped)
Candidates not already in the app, checked against what Vigilant needs (fresh pregame prices from many US books incl. Pinnacle/Circa, props, live, closing lines for old bets, historical, grading, a trial):
| API | Trial | Verdict |
| :- | :- | :- |
| **OddsPapi v5** | "Trial access is arranged through contact@55-tech.com" (docs, guides/evaluation); a free key also exists (250 requests a month on the older v4 consumer API) | **Chosen.** Sharp books first-class (Pinnacle, Singbet, Circa), `limit` (max stake) per price, per-book freshness bounds, a dedicated CLV endpoint (opening AND closing price per oddsId, any bet already placed), historical timelines, per-outcome settlement grades, a WebSocket (5 connections a key group, resume/replay), scores and clocks, mappings to Pinnacle/OpticOdds ids. Documents its own trial protocol. |
| SharpAPI | self-serve 3-day trial of Hobby/Pro/Sharp ($79/$229/$399) | Good +EV/arb tools, closing lines on Pro+, but no historical odds, no settlement endpoint, SSE streaming is a $99 add-on. Second choice. |
| TheRundown | free tier only (20k data points a day, 3 books, 5-minute delay, no props) | WebSocket only on Ultra ($399/mo), no trial of it. Skip. |
| Odds-API.io | "New free API keys are paused indefinitely"; WebSocket doubles the plan price | No trial now. Skip. |
| OpticOdds | site blocked the docs fetch | Enterprise. Skip. |
Already in the app: The Odds API, PropLine, ParlayAPI, PinnWire/pinnapi, Pinnodds, SportsGameOdds.

## 2. Two products share the name: Vigilant uses v5 only
- **v5** (this file): `https://v5.oddspapi.io/en/...`, WebSocket `wss://v5.oddspapi.io/ws`, key as header `X-API-Key` (preferred) or `?apiKey=`. B2B product, trial by email.
- **v4**: `https://api.oddspapi.io/v4/...`, key only as `?apiKey=`, a self-serve plan builder, requests metered per month (free: 250/month), `bookmakers` max 3 per odds request. A scanner would use its month in minutes.
  **Vigilant does not speak v4.** Settings › OddsPapi › Test key says so when a key is v4's ("ask contact@55-tech.com for a v5 trial").
- What to ask for (the email to contact@55-tech.com): a v5 trial with **WebSocket, US books (Pinnacle, Circa, DraftKings, FanDuel, BetMGM, Caesars, Hard Rock, ESPN BET, Fanatics, BetOnline), NFL/NCAAF/NBA/NCAAB/WNBA/MLB/NHL
  (sportIds 14, 14, 11, 11, 11, 13, 15), player props, REST history/CLV/settlement**.

## 3. Rates and limits (docs: api-reference/rate-limits, websocket/overview)
- Counted per `apiKey` and by endpoint: **odds endpoints 10 requests a second** (`GET /fixtures/odds`, `/fixtures/odds/main`, `/fixtures/odds/parlay`, `/fixtures/odds/sgp`, `/futures/odds`); **everything else 100 requests a minute**
  (metadata, fixtures list, mapping, settlements, historical, CLV, media).
- Headers on every authenticated answer: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`; a 429 is `{"error":429,"code":"rate_limited","retryAfterSec":1}` with `Retry-After`.
- Errors: 401 `missing_api_key` / `invalid_api_key` (unknown, expired or disabled; sending the header AND a different query key is also 401); 403 `channel_not_allowed` (valid key, not entitled to that endpoint);
  rejected-before-limiter 401s carry no rate headers. The trial's own quota (requests a month/day) is NOT documented: Vigilant keeps to the two limits above and rests a key on a 429.
- WebSocket: max 5 concurrent connections per key group (`4003`), backpressure close `4002`; `bookmakers` filter intersected with what the key allows.
- Freshness: every price has `changedAt` (when OddsPapi saw it change at the book, epoch ms) and, for many books, `bookmakerChangedAt`; per-book bounds on `GET /bookmakers`: `maxDelayPregameInSec`,
  `maxDelayPregameMainInSec`, `maxDelayLiveInSec` (Pinnacle p50 0.38 s). `bookmakers.<slug>.staleOdds` per fixture = the book's connection dropped: a kill switch (Vigilant drops that book's prices).
- Timestamps: schedule times (`startTime`, `startTimeFrom/To`) are epoch SECONDS; odds times (`changedAt`, `since`) are epoch MILLISECONDS.

## 4. Endpoints Vigilant uses (all `GET`, language prefix `/en`)
| Endpoint | Params | Used for |
| :- | :- | :- |
| `/bookmakers` | `bookmakers`, `playerProps` | the key's own book catalog (slugs, names, delay bounds, `websocketPregame/Live`), once a day; Test key |
| `/sports` | `sportIds` | check sportIds |
| `/tournaments` | `sportId` | tournamentId of NFL/NCAAF/NBA/NCAAB/WNBA/MLB/NHL (names, `tournamentSlug`, `categoryName`) |
| `/markets` | `sportId` / `marketIds` / `outcomeIds` | the market catalogue: per market `marketId, marketType, period, handicap, playerProp, outcomes[{outcomeId,outcomeName}]`, cached a day per sport |
| `/players` | `playerIds` | player names for props ("Jokic, Nikola") |
| `/fixtures` | `sportId`, `tournamentId`, `bookmakers`, `startTimeFrom`, `startTimeTo`, `fixtureIds` | the games in the scan window (+ per-book `hasOdds/staleOdds/suspended/participantsRotated`), final scores for grading |
| `/fixtures/live` | `sportId`, `tournamentId`, `bookmakers` | live games (live betting) |
| `/fixtures/odds` | **`fixtureId` (one)**, `bookmakers`, `since` (ms), `marketActive`, `mainLine` | every line of one game: main + alternates + team totals + periods + props, every book |
| `/fixtures/odds/main` | `tournamentId` or `fixtureIds`, `bookmakers`, `since` | main lines of a whole tournament in ONE request |
| `/fixtures/odds/clv` | **`fixtureId`**, `bookmakers`, `oddsIds` | **opening (`olv`) and closing (`clv`) price per oddsId**: CLV for any bet, graded or not |
| `/fixtures/odds/historical` | **`fixtureId`**, `bookmaker` (exactly one unless `oddsIds`), `oddsIds` | a price's full timeline (`{changedAt: row}`) |
| `/fixtures/settlement` | `fixtureId`, `outcomeId`, `playerId` | per-outcome grade `WIN/LOSE/PUSH/HALFWIN/HALFLOSS/CANCELLED/UNDECIDED` (+ `reason`) with the scores used; call once `statusId == 2` |
| `/fixtures/mapping` | `bookmaker`, `fixtureIds` | bookmaker-side fixture ids (also `externalProviders.pinnacleId` on every fixture) |
Not used (and why): parlays/SGP (Vigilant prices singles), futures, media, currencies, the WebSocket (§7).

## 5. Shapes (from the OpenAPI examples)
- **Fixture**: `fixtureId` (string `id` + sportId 2 digits + tournamentId 6 + native), `reissuedFixtureId`, `status{live,statusId 0 pregame|1 live|2 finished|3 cancelled}`, `sport`, `tournament{tournamentId,tournamentName,categoryName}`,
  `startTime` (s), `participants{participant1Id,participant1Name,participant1ShortName,participant1Abbr,participant1RotNr, participant2…}`, `scores{<period>:{participant1Score,participant2Score}}` (`result` = headline score),
  `clock`, `expectedPeriods`, `periodLength`, `externalProviders{pinnacleId,opticoddsId,betradarId,…}`, `bookmakers{<slug>:{hasOdds,staleOdds,suspended,participantsRotated,fixturePath,bookmakerFixtureId}}`.
  **participant1 is NOT always the home team** (NBA example: participant1 = home Spurs; NFL example: participant1 = away Vikings): Vigilant sets `RefEvent.home = participant1` and lets `Planner.matchEvents` detect the swap by team names, as it does for every feed.
- **Odds** (`/fixtures/odds`): fixture fields plus `odds{<bookmaker>:{<oddsId>:{bookmaker,outcomeId,playerId,price(decimal),priceAmerican,active,marketActive,mainLine,marketId,limit,bookmakerChangedAt,changedAt,meta}}}`.
  `oddsId = {fixtureId}:{bookmaker}:{outcomeId}:{playerId}` (opaque; read the fields). `/fixtures/odds/main` is an array of the same.
- **CLV**: `odds{<book>:{<oddsId>:{clv:{price,priceAmerican,changedAt,…}, olv:{…}}}}`. **Historical**: `odds{<book>:{<oddsId>:{"<changedAt>":{price,active,changedAt,marketActive}}}}`.
- **Settlement**: `scores.result.{participant1Score,participant2Score}` and `settlements[{marketId,marketType,outcomeId,playerId,status,team1Score,team2Score,periods[],margin}]`.
- **Markets**: `[{marketId,marketLength,sportId,playerProp,handicap,period,marketType,marketName,marketNameShort,outcomes[{outcomeId,outcomeName}]}]`. Every distinct line value is its own marketId; the outcomes of one market are only the sides of that line.
  The **first outcomeId of a market equals its marketId**. Sides are "1"/"2" (participants), "Over"/"Under", "Yes"/"No", "X" (draw). Spreads: `handicap` is participant 1's number (the "1" side gets it, the "2" side gets its negative).
- **Periods**: `result` (everything incl. overtime: the Novig full-game market), `fulltime` (regulation only: NOT Novig's), `p1…p12` (quarter in basketball/football, period in hockey, inning in baseball), combined `p1+p2` (first half of a quarter sport),
  `p1+p2+p3+p4+p5` (first 5 innings). From 2026-10-05 no market has a blank period. **Do not hard-code `marketType`/`period` strings**: the catalogue is append-only and renames happen (2026-10-05 renamed 24 hockey/soccer types); key on `marketId`.
- American-football 2-way winner is marketType `1x2` with 2 outcomes (marketIds 141/142 in the docs example); basketball is `moneyline` "Winner (incl. overtime)" (111/112). Vigilant therefore classifies by `marketType` AND `marketLength`, not by name.

## 6. How Vigilant uses it (code: `data/.../reference/Op*.kt`, `tracker/OpCloses.kt`, `tracker/OpScores.kt`, `novig/lab` via `SgoAltQuotes`; wiring in `AppContainer`)
Settings › **OddsPapi** (key list rotated like every provider's, Test key, the one switch `ScanSettings.oddsPapi`, extra books, alternate lines). Switch ON and a key saved and answering (`opActive`):
1. **Scan** (`OpGamesSource`, `OpPropsSource`): `/fixtures` for the window, then `/fixtures/odds/main` per tournament for the main lines of every game in one request each, and `/fixtures/odds` per game for alternates, team totals, halves and props
   (10 requests a second keeps NFL's 14 games to ~2 s). Books: the reference books picked in Settings plus extras (Circa, bet365, BetOnline…), minus exchanges Vigilant reads itself (Kalshi, Polymarket, Novig).
2. **Closes / CLV** (`OpCloses`): any bet, graded or not, however old: `/fixtures` (window around the bet) to find the game, `/fixtures/odds/clv` for Pinnacle then Circa; devigged across the bet's two sides, at the exact number the bet was on.
3. **Grading** (`OpScores`): final scores (`scores.result`, per-period) from `/fixtures`, chained in front of the free feeds like SGO's.
4. **Live**: `includeLive` reads `/fixtures/live` and live games' odds; the paper lab gets OddsPapi's alternate lines (`SgoAltQuotes`).
5. **Redundancy**: while it answers, the feeds that sell the same thing rest for the leagues it carries (`OutsideOp`: PinnWire/pinnapi unless Pinnodds is on, PropLine, The Odds API, ParlayAPI); free feeds (Kalshi, Polymarket, CrazyNinjaOdds, ESPN) and Pinnodds stay.
   If OddsPapi stops answering (two failures, two minutes) they come back by themselves. Switch OFF = the app as it was (`OpWiringTest`).
6. Not used on the phone: the WebSocket. A REST read at scan time is as fresh as a pushed price (Pinnacle p50 0.38 s), and a phone-side book of every alternate line and prop would cost memory the app has had trouble with
   (heap 487 of 512 MB, v0.83.4 file). Live betting stays on Pinnodds' socket. Revisit after the trial if the numbers say OddsPapi's socket is faster than Pinnodds' for live lines (docs/guides/evaluation has the protocol).

## 7. WebSocket (read, not built)
`wss://v5.oddspapi.io/ws`; first message within 10 s: `{"type":"login","apiKey":…,"channels":["fixtures","scores","odds","bookmakers","clocks"],"sportIds":[…],"tournamentIds":[…],"bookmakers":[…],"receiveType":"json|binary|zstd|zstd-dict","serverEpoch":…,"lastSeenId":{…}}`;
`login_ok` returns `access{live,pregame}` and `resume{serverEpoch,resumeWindowMs 60000,serverEntryIds}`; data frames `{channel,type:"UPDATE",payload,ts,entryId}`; odds are conflated latest-state on a ~10 ms window; `snapshot_required` means re-fetch REST; a `{"type":"reconnect"}` frame means reconnect now;
connect first, snapshot second, merge by newer `changedAt`. permessage-deflate is negotiated by the client. Bookmaker-gated channels: `odds`, `bookmakers`.

## 8. What is NOT known (fill in from Tj's sample / trial)
- The trial's quota (requests a day/month), which books and sports the key sees (`GET /bookmakers` is authoritative), whether it has the WebSocket and player props.
- The exact slugs of the US books (docs show `pinnacle`, `circa`?, `draftkings`, `fanduel`, `bet365`, `betmgm`, `betrivers`, `betonline.ag`, `borgata`, `novig.us`, `prophetx`; Vigilant maps by slug AND by catalog name) and the `/markets` strings for NFL/NBA/MLB/NHL props.
- Whether `/fixtures/odds` returns props for books that carry them in the same call (assumed: yes, `playerId != 0`).
- Whether `staleOdds` fixtures still return their last prices (Vigilant drops that book's lines either way).

## 9. What was built (v0.84.0) and the choices made where the docs are silent
- **Parameters verified** against `openapi.json` (2026-10-09): `/fixtures/odds` `fixtureId` (required, single), `bookmakers` (comma or space separated), `since`, `marketActive`, `mainLine`; `/fixtures/odds/main` `tournamentId` | `fixtureIds`, `bookmakers`, `since` (with `since` it also returns inactive odds: not used);
  `/fixtures/odds/clv` `fixtureId`, `bookmakers`, `oddsIds`; `/fixtures/odds/historical` `fixtureId`, `bookmaker` (exactly ONE unless `oddsIds`), `oddsIds`; `/fixtures` `sportId`, `tournamentId`, `bookmakers`, `startTimeFrom/To` (SECONDS); `/players` `playerIds`; `/tournaments` `sportId`; `/markets` `sportId`.
  `/fixtures/odds/main` answers include `bookmakers.<slug>` meta (`staleOdds`, `suspended`, `participantsRotated`), which the freshness rule needs.
- **Freshness**: `changedAt` is when a price last MOVED, so a steady Pinnacle total is hours old and current. A MAIN line (`mainLine: true` on both sides) of a book whose meta says it is connected is stamped with the time of the read; any other line keeps its own `changedAt` (an alternate the book stopped
  offering would otherwise look current). If the real sample shows alternates get their `active` flag cleared when a book withdraws them, the second rule can be loosened.
- **Orientation**: `participant1` is NOT always home (the NFL example lists the visitor first). `RefEvent.home = participant1` and the planner's swap detection pairs it with Novig's game; closes and scores look a game up in either order. Books with `participantsRotated` are skipped (the price orientation is undocumented).
- **Periods**: only `result` (overtime included) is Novig's game; `fulltime` is regulation and is NOT used, except an American-football 2-way winner on `fulltime` (the docs' NFL example) when the book has no `result` one. First half = `p1+p2` (NFL/NCAAF/NBA/WNBA), `p1` (NCAAB: halves), first five innings `p1+p2+p3+p4+p5`, first inning `p1` (MLB, totals only).
- **Props**: `marketType` `players-<stat>`; stat tails in `OpProps` (hockey from the docs, the rest guessed); names from `/players` ("Last, First" -> "First Last"), kept for good. A tail not in the table is left unpriced and listed by Test key ("Unmapped prop types").
- **Request budget** (the trial's quota is unknown): a scan of a league is 1 request (`/main`) + 1 per game with alternates or props (cap 60) + `/players` for new names; two sources share it (20 s keep). Scan cadence is unchanged (4 minutes; SGO mode's 2-minute cadence is SGO's alone). If a key answers 429 with a quota-looking code it is rested until its Retry-After and the other keys/feeds carry on.
- **Not built**: the WebSocket (§7), settlement-based prop grading, a GitHub-lab recorder, `/fixtures/live` (live betting stays on Pinnodds' socket).
