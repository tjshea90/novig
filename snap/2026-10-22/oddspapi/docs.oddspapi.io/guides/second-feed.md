> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Running OddsPapi as a Second Feed - Mapping & Cross-Validation

> Add OddsPapi alongside an existing odds feed: join fixtures via external provider IDs and the mapping endpoints, compare prices on stable selection keys, and measure which feed is faster.

Most desks that adopt OddsPapi already have a feed. Running two sources in parallel is normal practice — one for cross-validation, one for the coverage the other lacks — and the cost of doing it is almost entirely in **entity mapping**: deciding that your existing provider's match is the same match as ours.

OddsPapi publishes those mappings directly, so joining is a lookup rather than a fuzzy-matching project.

***

## Join on IDs, not on names

The [`fixtures`](/websocket/channels/fixtures) channel carries an `externalProviders` object on every fixture:

| Field | Provider |
| - | - |
| `betradarId` | Betradar |
| `flashscoreId` | Flashscore |
| `pinnacleId` | Pinnacle |
| `sofascoreId` | Sofascore |
| `oddinId` | Oddin |
| `mollybetId` | Mollybet |
| `opticoddsId` | OpticOdds |
| `lsportsId` | LSports |
| `txoddsId` | TXOdds |

If your current feed is one of these, the join is a single field. Store it once per fixture and your two systems share a key from then on.

> [!NOTE]
>
> Mappings are populated where a match exists — treat any field as nullable and fall back to start time plus participants for the remainder.

***

## Bookmaker-side mapping

For joins against a **bookmaker's** own identifiers rather than a data provider's, use the mapping endpoints:

```bash theme={null}
# OddsPapi fixture -> bookmaker fixture IDs
curl 'https://v5.oddspapi.io/en/fixtures/mapping?fixtureIds=id1100013270505136&apiKey=YOUR_KEY'

# Bookmaker fixture ID -> OddsPapi fixture
curl 'https://v5.oddspapi.io/en/fixtures/mapping?bookmaker=pinnacle&bookmakerFixtureIds=1628488896&apiKey=YOUR_KEY'
```

`GET /futures/mapping` does the same for outright markets. Per price, the [`odds`](/websocket/channels/odds) channel also carries `bookmakerMarketId` and `bookmakerOutcomeId`, and the [`bookmakers`](/websocket/channels/bookmakers) channel carries `bookmakerFixtureId` and `fixturePath`.

***

## Compare prices on a stable key

Within OddsPapi a price is keyed by:

```
{fixtureId}:{bookmaker}:{outcomeId}:{playerId}
```

Drop the bookmaker and you have the **selection** — `{fixtureId}:{outcomeId}:{playerId}` — which is what you want when comparing across feeds, because a selection wins or loses identically regardless of who quoted it.

To line up a selection against another feed's market model, decompose it into coordinates rather than matching market name strings:

```
marketType → period → handicap → side   [+ playerId]
```

`marketType` (`1x2`, `totals`, `spreads`, …), `period` (`fulltime`, `p1`, …), and `handicap` (the line value) are all explicit on [`GET /markets`](/api-reference/common/get-markets). Every distinct line value is its own `marketId`, so "Over 2.5" and "Over 3.5" never collapse into one market. See [Markets and outcomes](/api-reference/concepts.md#markets-and-outcomes).

**1. Map the fixture**

Join on `externalProviders`, or via `/fixtures/mapping` for bookmaker-side IDs.

**2. Map the market**

Translate your existing model into `marketType` + `period` + `handicap` and resolve the `marketId` from `/markets`.

**3. Map the side**

Pick the `outcomeId` for the side; add `playerId` for player markets (`0` otherwise).

**4. Store the pair**

Persist your key ↔ `{fixtureId}:{outcomeId}:{playerId}` so the mapping is done once, not per update.

***

## Measure which feed is faster

Cross-validation is only worth the second integration if you can tell which source wins. Two measurements, both available without instrumentation on our side:

* **Observation delay** — `changedAt − bookmakerChangedAt` per update, against the equivalent timestamp on your other feed. Per-book bounds are published on [`GET /bookmakers`](/api-reference/common/get-bookmakers) as `maxDelayLiveInSec` / `maxDelayPregameInSec` / `maxDelayPregameMainInSec`, with representative p50s in [Latency and freshness](/api-reference/reliability.md#latency-and-freshness).
* **Delivery hop** — envelope `ts` against your receipt time. This one depends on where you deploy; see [Server location](/api-reference/concepts.md#server-location-matters).

For an outcome-level verdict rather than a stopwatch, compare fills against the closing line using `GET /fixtures/odds/clv`. Same `oddsId` format as the live stream, so the join is free.

***

## What tends to be complementary

Coverage overlap is rarely total, which is the point of running two. In practice OddsPapi adds sharp trading books, Asian lines and quarter-line settlement, exchange and prediction-market depth, and stake limits on close to every sharp book — alongside broad US and European retail coverage. See [Bookmaker coverage](/coverage/bookmakers.md) for the catalog shape and [`GET /bookmakers`](/api-reference/common/get-bookmakers) for what your key can see.

***

## Cutover checklist

* [ ] Fixtures joined via `externalProviders` or `/fixtures/mapping`, with a fallback for nulls
* [ ] Market model translated to `marketType` / `period` / `handicap` coordinates
* [ ] Storage keyed by `oddsId`, with selection keys for cross-feed comparison
* [ ] `staleOdds` and `suspended` wired into the same kill switch as your existing feed
* [ ] `snapshot_required` handling and resume implemented ([Resume & Replay](/websocket/resume-replay.md))
* [ ] Delay comparison running on both sources before you shift any weight

***

## What you can rely on

* **IDs do not move under you.** Registries are append-only and published identifiers are frozen, so a mapping table you build during evaluation is still correct a year later. New enum values are appended rather than repurposed — ignore what you do not recognize instead of failing.
* **Recovery is bounded and deterministic.** Per-channel cursors (`serverEpoch` + `entryId`) replay inside `resumeWindowMs`; past that, the worst case is one REST snapshot, not a silent gap you discover in reconciliation.
* **Mapping resolves both directions.** `externalProviders` gets you in from a provider ID you already hold, and `GET /fixtures/mapping` goes either way — so a second feed is a lookup, not a matching project.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.