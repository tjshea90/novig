> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Core Concepts - IDs, Data Models & Best Practices

> Understand OddsPapi data models: fixture IDs, market IDs, odds identifiers, entity relationships, timestamps, and implementation best practices for arbitrage detection.

This page explains the core concepts used throughout the Odds API: how IDs are constructed, how entities relate to each other, how timestamps are used, and how to design reliable client-side storage.

***

## Language prefix

All endpoints are prefixed with a language code:

* `/en/...`
* `/de/...`
* `/fr/...`

Translated fields (for example names) follow the prefix language when available.
Identifiers (`sportId`, `fixtureId`, etc.) are language-independent.

***

## Sports, tournaments, seasons

### sportId

* Integer.
* The **first two digits** embedded into several downstream IDs.
* Discover via `/sports`.

### tournamentId

* Integer.
* Belongs to exactly one sport.
* Discover via `/tournaments`.

### seasonId

* Integer.
* Belongs to exactly one tournament.
* Discover via `/seasons`.

> See the full [Sports Coverage](/coverage.md) table for every `sportId` and whether it's available as fixtures, futures, or both.

***

## Entity hierarchy

Everything in the API hangs off one tree:

```
Sport
  └── Tournament (recurring competition / topic)
        └── Season (time-bound instance)
              └── Fixture (single match) ──or── Future (outright / prediction market)
                    ├── Participants (teams / competitors)
                    ├── Scores (per period — fixtures only)
                    └── Players (squad members — sports only)
```

A **tournament** is the recurring thing ("Premier League", "US Presidential Election"), a **season** is one time-bound instance of it ("Premier League 25/26", "US Presidential Election 2028"). Fixtures and futures both live inside a season.

### How Fixtures Work (Sports)

A **fixture** is a single match between two participants (teams or players):

```
Sport:       Soccer                  (sportId=10)
Tournament:  Premier League          (tournamentId=17)
Season:      Premier League 25/26    (seasonId=130281)
Fixture:     Arsenal vs Chelsea      (fixtureId=id1000001770366972)
  ├── participant1Id → Arsenal
  ├── participant2Id → Chelsea
  ├── Scores:  result 2-1, p1 1-0, p2 1-1   (per-period rows)
  └── Players: squads per participant        (player prop markets)
```

* The fixture object carries the schedule and state: `startTime` (epoch seconds), `statusId`, `participant1Id` / `participant2Id`, `seasonId`, venue — see the [`fixtures` channel](/websocket/channels/fixtures) and REST reference for full schemas.
* **Scores are per period**, keyed `result`, `p1`, `p2`, … — the `result` row is the authoritative final/current score; period rows let you settle period markets. See the [`scores` channel](/websocket/channels/scores).
* Odds attach to fixtures via the [odds identifiers](#odds-identifiers) below — one price per `{fixtureId}:{bookmaker}:{outcomeId}:{playerId}`.

### How Futures Work (Prediction Markets)

A **future** is an outright market on a whole season or event rather than a single match — "Who wins the Premier League?" — and the same model covers non-sports prediction markets ("Who wins the 2028 US election?", "Will Bitcoin hit \$100k?").

For sports, a future hangs off the **exact same season** its fixtures do — only the leaf differs:

```
Sport:       Soccer                  (sportId=10)             ← same as the fixture example
Tournament:  Premier League          (tournamentId=17)        ← same tournament
Season:      Premier League 25/26    (seasonId=130281)        ← same season
Future:      Winner market           (futureId=id1013028110001)
  └── Participants: the entities you can back (Arsenal, Chelsea, …)
```

Compare with the fixture above: `Arsenal vs Chelsea` is **one match inside** season `130281`; the Winner future is a **market on that season as a whole**. Same tree, same `seasonId` — a fixture resolves in 90 minutes, a future resolves when the season does.

Non-sports prediction markets reuse the identical model — the recurring topic is the tournament, the edition is the season:

```
Sport:       Politics                        (sportId=69)
Tournament:  US Presidential Election        (tournamentId=800042)   ← recurring topic
Season:      US Presidential Election 2028   (seasonId=8847293156)   ← this edition
Future:      Winner market                   (futureId=pm69884729315669001)
  └── Participants: the entities you can back (candidates, parties)
```

* One future = one outright market within a season. The trailing `futureMarketId` in the `futureId` says which kind. It is allocated **per sport**, so the same question is a distinct market in each sport:

| `futureMarketId` | marketType | Question |
| - | - | - |
| `{sportId}001` | `winner` | Who wins the Premier League? Who is the next president? |
| `{sportId}002` | `topscorer` | Golden Boot, top goalscorer — sports only |
| `{sportId}002` | `prediction` | Will X happen by date Y? — non-sports and racing specials only |
| `{sportId}003` | `relegation` | Which teams go down |
| `{sportId}004` | `mvp` | Season MVP |

So soccer's Winner market is `10001` and the Politics Prediction market is `69002`. Position `002` is `topscorer` for sports and `prediction` for the non-sports bands — the two never collide, because no sport carries both.

* Futures odds are keyed `{futureId}:{bookmaker}:{futureOutcomeId}:{participantId}` — see [Future odds IDs](#future-odds-ids).
* Non-sports topics (politics, elections, finance, crypto, weather, …) are modeled as sports with `sportId` 69+ and exist as **futures only**; racing codes (79–81) are ante-post futures. See [Sports Coverage](/coverage.md) for the exact bands.

***

## Markets and outcomes

> [!NOTE]
>
> **TL;DR** — Every selection follows a fixed hierarchy: `marketType → period → handicap → side`, all baked into the `outcomeId` — so the same bet always resolves to the same `marketId` / `outcomeId`, addressed by coordinates rather than bookmaker name strings. The one part *not* in `outcomeId` is the player, carried by `playerId`. All outcomes of a market share its `marketId`, which equals the market's first `outcomeId`.

For which market types, lines, periods, and player props are actually carried, see [market coverage](/coverage/markets.md).

### How markets decompose (the deterministic hierarchy)

Every `outcomeId` comes from a single, deterministic decomposition — OddsPapi splits each market into a fixed hierarchy:

```
marketType → period → handicap (line value / specifier) → side (outcome)   [+ optional player]
```

So "total goals over 2.5, full time" always lands on the same `marketId` / `outcomeId` across bookmakers and fixtures.

| Level | Field | Meaning |
| - | - | - |
| 1 | `marketType` | The kind of market (`1x2`, `totals`, `spreads`, `spreads-european`, `bothteamsscore`, `drawnobet`, `oddeven`, …). |
| 2 | `period` | The segment the market applies to (`fulltime`, `p1`, `p2`). |
| 3 | `handicap` | The line value / specifier (e.g. `2.5`, `-0.25`). **Each distinct line value is its own `marketId`.** `0.0` for lineless markets like `1x2`. |
| 4 | `outcomeName` | The **side** of the market (`Over` / `Under`, `1` / `X` / `2`, `Yes` / `No`, …). |

> **Key rule:** because every line value gets its **own** `marketId`, "Over 2.5" and "Over 3.5" are *different* markets — not two outcomes of one market. Within a single `marketId`, the outcomes are only the **sides** of that exact line (e.g. `Over 2.5` + `Under 2.5`).

> **Worked example — soccer (sportId 10)**
>
> A representative slice of the real soccer market map. Each `marketId` equals its first `outcomeId`, and each line value is its own market:
>
> | marketId | marketName | marketType | period | handicap | outcomeId | outcomeName |
> | -: | - | - | - | -: | -: | - |
> | 101 | Full Time Result | `1x2` | fulltime | 0.0 | 101 | 1 |
> | 101 | Full Time Result | `1x2` | fulltime | 0.0 | 102 | X |
> | 101 | Full Time Result | `1x2` | fulltime | 0.0 | 103 | 2 |
> | 104 | Both Teams To Score | `bothteamsscore` | fulltime | 0.0 | 104 | Yes |
> | 104 | Both Teams To Score | `bothteamsscore` | fulltime | 0.0 | 105 | No |
> | 106 | Over Under Full Time | `totals` | fulltime | 0.5 | 106 | Over |
> | 106 | Over Under Full Time | `totals` | fulltime | 0.5 | 107 | Under |
> | 1010 | Over Under Full Time | `totals` | fulltime | 2.5 | 1010 | Over |
> | 1010 | Over Under Full Time | `totals` | fulltime | 2.5 | 1011 | Under |
> | 1068 | Asian Handicap | `spreads` | fulltime | -0.5 | 1068 | 1 |
> | 1068 | Asian Handicap | `spreads` | fulltime | -0.5 | 1069 | 2 |
> | 10122 | European Handicap | `spreads-european` | fulltime | 6.0 | 10122 | 1 |
> | 10122 | European Handicap | `spreads-european` | fulltime | 6.0 | 10123 | X |
> | 10122 | European Handicap | `spreads-european` | fulltime | 6.0 | 10124 | 2 |
> | 10208 | First Half Result | `1x2` | p1 | 0.0 | 10208 | 1 |
> | 10208 | First Half Result | `1x2` | p1 | 0.0 | 10209 | X |
> | 10208 | First Half Result | `1x2` | p1 | 0.0 | 10210 | 2 |
> | 10214 | Draw No Bet | `drawnobet` | fulltime | 0.0 | 10214 | 1 |
> | 10214 | Draw No Bet | `drawnobet` | fulltime | 0.0 | 10215 | 2 |
> | 10222 | Odd Even Full Time | `oddeven` | fulltime | 0.0 | 10222 | Odd |
> | 10222 | Odd Even Full Time | `oddeven` | fulltime | 0.0 | 10223 | Even |
> | 10224 | Over Under Team 1 | `teamtotals-team1` | fulltime | 0.5 | 10224 | Over |
> | 10224 | Over Under Team 1 | `teamtotals-team1` | fulltime | 0.5 | 10225 | Under |
>
> Reading the first row: `marketType=1x2`, `period=fulltime`, `handicap=0.0`, `outcomeName=1` — the home side of the full-time match-result market.

### Relationship between `marketId` and `outcomeId`

Markets and outcomes are tightly coupled by design:

* A **market** represents a complete betting market (for example moneyline, 1x2, totals).
* A **market contains multiple outcomes**.
* **All outcomes that belong to the same market share the same `marketId`.**
* **The first `outcomeId` of a market is always equal to the `marketId`.**

This makes it possible to determine which outcomes belong to which market **without additional metadata**.

### marketLength

`marketLength` is the **number of outcomes under a `marketId`** — the count of sides that can be bet on, which together form the complete probability space (their implied probabilities sum to ≈ 1 before margin removal). Examples: `moneyline` → 2, `1x2` → 3, `winningmargin` → the number of margin bands offered.

### ID structure

Both `marketId` and `outcomeId` are integers constructed as:

```
{sportId (2 digits)} + {incrementing number}
```

Examples:

* `11xxxx` → basketball market or outcome
* `14xxxx` → American football market or outcome

This design provides:

* Unlimited markets and outcomes per sport
* Fast grouping by sport
* Immediate visibility of market relationships

### Why `marketId` matters (arbitrage & modeling)

All outcomes under a single `marketId` together represent a **complete probability space**.

This makes `marketId` especially useful for:

* Arbitrage detection
* Overround / margin calculations
* Probability normalization
* Market completeness checks

**Recommendation:**
If you perform arbitrage or pricing logic, always group odds by `marketId`.

***

## Participants and players

### participantId

* Integer.
* Represents teams or competitors in fixtures.
* Discover via `/participants`.

### playerId

* Integer.
* Used for player proposition markets.
* `playerId = 0` typically represents a non-player market.
* Discover via `/players`.

***

## Fixture IDs

### fixtureId structure

`fixtureId` is a **string** that encodes multiple pieces of information:

```
{prefix}{sportId}{tournamentId}{nativeId}
```

Conceptual example:

```
id1100013262926199
```

Where:

* `id` → internal namespace prefix — treat as opaque
* `11` → sportId (2 digits)
* `000132` → tournamentId (6 digits)
* `62926199` → native fixture ID within that namespace

From the `fixtureId` alone you can infer the sport and tournament — handy for logging and cross-system correlation.

To correlate with a fixture ID from another system, use the `externalProviders` block on the [fixtures channel](/websocket/channels/fixtures.md) or the mapping endpoints — see [running OddsPapi as a second feed](/guides/second-feed.md).

***

## Future IDs

### futureId structure

`futureId` follows the same principle as `fixtureId`:

```
{prefix}{sportId}{seasonId}{futureMarketId}
```

Where:

* `prefix` → internal namespace prefix (e.g. `id`, `pm`, `ks`) — treat as opaque
* `sportId` → 2 digits
* `seasonId` → the season / event instance
* `futureMarketId` → the outright market this future asks (5 digits — see below)

This encodes sport, season, and the market question — one future per (season, question).

Tournament is a **field on the future**, not a segment of the ID. Read it from `tournament.tournamentId` on the [`futures`](/websocket/channels/futures) payload; keeping it out of the ID is what lets the `futureId` stay stable when a provider re-parents a season under a different tournament.

### Future markets and outcomes

A future market is a **question kind** — "Who wins the Premier League?", "Who is top scorer?", "Will X happen by Y?" — not a stat template. One market is reused by every future that asks it, and is identified by `market.marketId` on the [`futures`](/websocket/channels/futures) payload: the same 5-digit `futureMarketId` carried in the `futureId` tail.

Markets come in two shapes:

| Shape | Examples | The selection is | Outcome rows |
| - | - | - | - |
| **Participant-keyed** | `winner`, `topscorer`, `relegation`, `mvp` | the participant itself | none — odds carry the sentinel `futureOutcomeId` `0` |
| **Decomposed** | `prediction` (Yes / No), season totals (Over / Under) | a side the market owns | a complete side set per line, each with its own `futureOutcomeId` |

Two deliberate differences from [fixture markets](#markets-and-outcomes):

* **`handicap` sits on the outcome, not the market.** For fixtures each line is its own `marketId`. A future is minted per (season, question) before anyone knows which ladder a book will offer, so one future market carries many lines.
* **There is no `period`.** A future resolves when its season or question does.

***

## Odds identifiers

> [!NOTE]
>
> **TL;DR** — A single price is uniquely keyed by `{fixtureId}:{bookmaker}:{outcomeId}:{playerId}` (fixtures) or `{futureId}:{bookmaker}:{futureOutcomeId}:{participantId}` (futures). Use these `oddsId` strings as **primary keys** in your storage for clean dedup, updates, and reconciliation.

### Fixture odds keys

For fixtures, a single price is uniquely identified by:

```
{fixtureId}:{bookmaker}:{outcomeId}:{playerId}
```

Example:

```
id1400003160574217:bet365:141:0
```

This combination uniquely defines **one price**.

Drop the `bookmaker` and you have the **selection** itself — `{fixtureId}:{outcomeId}:{playerId}` — the bookmaker-independent key for grading / settlement, since a selection wins or loses the same way everywhere. No `marketId` is needed; the `outcomeId` already encodes the market.

Selection keys are what make cross-book and cross-feed comparison cheap — see [running OddsPapi as a second feed](/guides/second-feed.md) for joining to an existing feed on them.

### Future odds IDs

Future odds are identified by four segments, mirroring fixture odds keys:

```
{futureId}:{bookmaker}:{futureOutcomeId}:{participantId}
```

The last two segments split one job between them, and which of them carries the selection depends on the market's shape:

| Market shape | `futureOutcomeId` | `participantId` |
| - | - | - |
| Participant-keyed (`winner`, `topscorer`, `relegation`, `mvp`) | `0` — sentinel, the market owns no outcome rows | the entity you back |
| Decomposed (`prediction` Yes / No, season totals Over / Under) | the side and line within the market | the entity the line is about, where one applies |

`0` is a sentinel exactly as `playerId` is `0` for non-player fixture odds — a real, stable key value, not a null. The two ID spaces never overlap by construction: `futureMarketId` is at most 5 digits, `futureOutcomeId` is always 6 (≥ 100001), so a value can never be read as the wrong kind of ID.

**Treat `oddsId` as an opaque string.** Key your storage on it, but read `bookmaker`, `futureOutcomeId`, `participantId`, and the rest from the explicit fields on the odds row rather than splitting the segments.

***

## Timestamps (seconds vs milliseconds)

This API intentionally uses **both epoch seconds and epoch milliseconds**, depending on context. The rule is consistent across fixtures **and** futures:

### Epoch seconds (UTC)

All **start times** are integer epoch seconds:

* `startTime` / `endTime`
* `startTimeFrom`
* `startTimeTo`

### Epoch milliseconds (UTC)

All **odds-update times** are integer epoch milliseconds — for fixtures and futures alike:

* `changedAt` / `bookmakerChangedAt`
* Odds `since` filters (`/fixtures/odds`, `/fixtures/odds/main`, `/futures/odds`)
* WebSocket envelope `ts` and the timestamp part of `entryId`

### Recommendation

* Store timestamps as integers.
* Rule of thumb: **schedule times are seconds, odds-update times are milliseconds.**
* Normalize internally only if needed.

***

## Operating tips

### Server Location Matters

To achieve the lowest latency for realtime updates:

* The **fastest delivery regions** are Central Europe (recommended) and US East.
* For best performance, deploy your backend **in the same datacenters we use**, such as:
  * [Netcup](https://www.netcup.com/en/?ref=294017)
  * [Hetzner](https://www.hetzner.com/cloud)
* If you're building **prediction markets or models sensitive to latency**, consider:
  * Deploying in **Austria (AT)** via Netcup, or
  * Using **AWS eu-west-1** (Ireland)

> ⚡ Servers colocated near our streaming infrastructure receive updates faster and with fewer hops.

### Snapshot + realtime pattern

A reliable integration pattern:

**1. Subscribe to WebSocket**

Connect, log in with your filters, and start applying updates keyed per row as soon as `login_ok` arrives.

**2. Fetch HTTP snapshot**

Pull the current state (fixtures, odds, or futures) over REST and merge it in — per key, the newer `changedAt` wins.

**3. Re-snapshot on signal**

If the WebSocket signals `snapshot_required`, re-fetch the HTTP snapshot with the same merge rule.

Connecting before you snapshot is what makes the handover gapless: changes during the snapshot fetch are already on the socket, and no REST-to-WebSocket cursor exists or is needed — updates are full latest-state per row.

### Efficient backfills

* Use `since` parameters where available
* Avoid full historical fetches unless required
* Store odds keyed by their odds identifiers for deduplication

### Rate limits

See [Rate Limits](/api-reference/rate-limits.md) for headers, per-endpoint limits, and 429 backoff handling.

***

## Historical odds and CLV

The realtime WebSocket `odds` and `oddsFutures` channels deliver **latest state** on a fixed \~10 ms batching window — ticks superseded within a window are collapsed to the newest value (no state is lost), so the stream is not a tick ledger. See [delivery semantics](/websocket/channels/odds.md#delivery-semantics).

> Use the live stream to **trade**, and the REST history endpoints to **measure** — CLV models, fill auditing, and backtests.

When you need the **full price movement** of an outcome, or its **opening vs closing line**, use the REST history endpoints. They share the same `oddsId` format (`{fixtureId}:{bookmaker}:{outcomeId}:{playerId}`) as the live `odds` channel, so you can join realtime fills directly to their historical and closing-line records.

| Purpose | Fixtures | Futures |
| - | - | - |
| Full price timeline | `GET /fixtures/odds/historical` | `GET /futures/odds/historical` |
| Opening vs closing line (OLV/CLV) | `GET /fixtures/odds/clv` | `GET /futures/odds/clv` |

**Trading use cases:**

* **CLV modelling** — compare your fill price against the closing line to measure edge.
* **Fill auditing** — reconcile traded prices with the recorded timeline to detect slippage.
* **Backtesting** — replay the full historical timeline to test strategies against real line movement.

See the **API → Reference** section for full request parameters, response schemas, and an interactive playground for each of these endpoints. For the trading workflow around these — limits, per-book delay, main-line filtering, and grading — see [sharp-line trading & CLV](/guides/sharp-line-trading.md).

***

## Glossary

Quick definitions of the core terms used throughout the API.

**Identifiers & data model**

* **sportId** — Numeric sport identifier; the first two digits of downstream `marketId`, `outcomeId`, and `fixtureId`.
* **fixtureId** — String key for a single match/event, encoding sport, tournament, and a native fixture ID.
* **futureId** — String key for a futures / outright market, encoding sport, tournament, season, and market.
* **marketType** — The kind of market (`1x2`, `totals`, `spreads`, `bothteamsscore`, …).
* **period** — The segment a market applies to (`fulltime`, `p1`, `p2`).
* **handicap** — The line value / specifier of a market; each distinct line value is its own `marketId`.
* **marketId** — Integer grouping all outcomes of one market; equals the market's first `outcomeId`.
* **marketLength** — Number of outcomes under a `marketId`: the sides that can be bet on, together summing to a probability of ≈ 1.
* **outcomeId** — Integer identifying one fully-decomposed selection: `marketType` + `period` + `handicap` (line) + side. It encodes everything except the player — that's what `playerId` adds.
* **playerId** — Ties an outcome to a player for player markets; `playerId = 0` is a non-player market.
* **participantId** — Integer identifying a team or competitor in a fixture.
* **oddsId** — Composite price key. Fixtures: `{fixtureId}:{bookmaker}:{outcomeId}:{playerId}`. Futures: `{futureId}:{bookmaker}:{futureOutcomeId}:{participantId}`.

**Prices & trading**

* **OLV** — Opening line value: the outcome's first recorded price.
* **CLV** — Closing line value: the outcome's last price before settlement; the reference for grading execution.
* **staleOdds** — Flag indicating a bookmaker's connectivity is degraded, so its odds may not be current.
* **settlement** — Post-event endpoints returning per-outcome results (won / lost), final scores, and margins.

**Streaming & delivery**

* **serverEpoch + entryId** — The per-channel cursor; a change in `serverEpoch` means the client must re-snapshot.
* **resume / replay** — Reconnect and replay missed messages within `resumeWindowMs` (default `60000`).
* **snapshot\_required** — Signal that the cursor is outside the resume window and a fresh REST snapshot is needed.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.