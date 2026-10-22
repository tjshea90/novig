> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Bookmaker Coverage - Sharps, Exchanges & Retail Books

> What OddsPapi covers across 350+ bookmakers: sharp trading books, exchanges and prediction markets, and retail sportsbooks — with per-book capabilities, freshness bounds, and status signals.

OddsPapi aggregates **350+ bookmakers**, anchored on the books that matter for price discovery rather than optimized for a headline count. The catalog available to you is determined by your key — query [`GET /bookmakers`](/api-reference/common/get-bookmakers) for the live list.

***

## Categories

**Sharp trading books** — the core of the feed. The books that are most accurate in pricing and dominate global sports prediction (e.g. Pinnacle, Singbet, Circa Sports). These are what trading systems benchmark against, and they carry [stake limits](/websocket/channels/odds.md#limits) and the full delivery timestamp chain.

**Exchanges & prediction markets** — realtime prices with [orderbook depth](/websocket/channels/odds.md#advanced-metadata-meta): `meta.back` / `meta.lay` ladders of `{ price, cents, size, limit }` rungs, up to three deep, with the same shape across Polymarket, Kalshi and ProphetX (Betfair Exchange uses legacy `availableToBack` / `availableToLay` keys). Non-sports prediction topics are covered as futures; see [Coverage by sport](/coverage.md).

**European, US, and crypto sportsbooks** — broad retail coverage across regions (e.g. DraftKings, Stake, BC.Game).

**Asian lines are first-class**: Asian handicap market types, quarter-line settlement (`HALFWIN` / `HALFLOSS`), and fiat + crypto rates on the [`currencies`](/websocket/channels/currencies) channel for stake and limit conversion.

***

## What a book can carry

Not every capability exists at every book — what a book publishes determines what we can pass through. Check the per-book fields rather than assuming:

| Capability | Where it appears | Notes |
| - | - | - |
| Price | `price`, `priceAmerican`, `priceFractional` | Always present |
| Stake limit | `limit` on the outcome | Carried for close to every sharp book |
| Orderbook depth | `meta.back` / `meta.lay` | Exchanges and prediction markets |
| Native venue IDs | `bookmakerMarketId`, `bookmakerOutcomeId` | Per price, on the live stream — used for order routing |
| Book's own timestamp | `bookmakerChangedAt` | Where the book publishes one; enables exact delay measurement |
| Main line flag | `mainLine` | Follow headline prices without every alternate line |
| Fixture-level mapping | `bookmakerFixtureId`, `fixturePath` | On the [`bookmakers`](/websocket/channels/bookmakers) channel; also `GET /fixtures/mapping` |

***

## Per-book metadata

[`GET /bookmakers`](/api-reference/common/get-bookmakers) returns the catalog with the operational fields you need to plan an integration:

| Field | Meaning |
| - | - |
| `slug` | Stable identifier used in `bookmakers` filters and in `oddsId` |
| `bookmakerName` | Display name |
| `active` | Whether the book is currently being collected |
| `websocketPregame` / `websocketLive` | Which delivery modes this book is available in |
| `maxDelayPregameInSec` | Freshness bound, pregame |
| `maxDelayPregameMainInSec` | Freshness bound, pregame main lines |
| `maxDelayLiveInSec` | Freshness bound, in-play |

The `maxDelay*` fields state the maximum expected refresh delay per mode — measured from observed vs. bookmaker change times (currently the rolling p50) where the book publishes usable timestamps, estimated otherwise. Representative p50s per book are published in [Latency and freshness](/api-reference/reliability.md#latency-and-freshness).

***

## Status signals

Coverage is not only "is this book in the list" but "is it current right now". The [`bookmakers`](/websocket/channels/bookmakers) channel reports per book, per fixture:

* `hasOdds` — the book currently offers odds on this fixture
* `staleOdds` — connection degraded, freshness no longer guaranteed (treat as a kill switch)
* `suspended` — the book's odds are suspended
* `participantsRotated` — the book's home/away assignment differs from the OddsPapi baseline

Wire `staleOdds`, `suspended`, `marketActive`, and `active` into a single "is this price tradeable" decision. See [Reliability & Operations](/api-reference/reliability.md).

***

## Discovering your catalog

```bash theme={null}
curl 'https://v5.oddspapi.io/en/bookmakers?apiKey=YOUR_KEY'
```

Bookmaker access is granted per key, and `odds`, `bookmakers`, `oddsFutures`, and `bookmakersFutures` are bookmaker-gated channels — you receive what your key allows, intersected with any `bookmakers` filter you send at login. See [Auth & Filters](/websocket/auth.md).


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.