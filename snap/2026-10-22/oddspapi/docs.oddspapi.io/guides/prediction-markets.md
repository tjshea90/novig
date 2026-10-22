> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Prediction Markets & Exchanges - Orderbook Depth & Limits

> Build market-making and arbitrage systems on OddsPapi: aggregated exchange prices with back/lay orderbook depth, native venue IDs for order routing, stake limits, and measured per-venue delay.

Market making across venues needs four things at once: prices, depth, the identifiers to send an order back, and an honest answer to "is this quote still real?". All four arrive on the same row, so adding a venue is a filter change rather than an integration project.

This guide covers the trading decisions on top of that stream. Payload schemas live on the [`odds`](/websocket/channels/odds) channel page.

***

## What arrives on every price

| Field | What it gives you |
| - | - |
| `price` | Best available price (decimal), plus `priceAmerican` / `priceFractional` |
| `meta.back` / `meta.lay` | Orderbook ladders — `{ price, cents, size, limit }` rungs, best price first, up to three deep |
| `limit` | Stake available at this price. On an exchange it mirrors top-of-book depth (`meta.back[0].limit`); on a traditional book it is the maximum accepted stake |
| `bookmakerMarketId` / `bookmakerOutcomeId` | The venue's own market and selection identifiers |
| `bookmakerChangedAt` / `changedAt` | Venue's change stamp (t0), and when we observed it (t1) |
| `active` / `marketActive` | Hard stops — do not quote against a false |

No secondary lookup, and one parsing branch rather than one per venue: `polymarket`, `polymarket.us`, `kalshi` and `prophetx` share the same ladder shape, and the first `back` entry is top of book and matches `price`. `betfair-ex` is the exception — it uses legacy `availableToBack` / `availableToLay` keys. Shape detail in [Advanced metadata](/websocket/channels/odds.md#advanced-metadata-meta).

***

## Building a cross-venue view

Group by selection — the same selection at two venues differs only in the venue segment of the key:

```
id1100064864029581 : polymarket : 112 : 0
└──── fixture ────┘   └─venue─┘   └selection┘
```

Then take the highest `back` and the lowest `lay` across the venues you are permissioned for. Carry both sides even if you only ever take one — the tightest spread shows where consensus sits, and a venue far outside it is either carrying information or carrying a stale price. The timestamps tell you which; the price does not.

```json theme={null}
"price": 1.053,
"meta": {
  "back": [
    { "price": 1.053, "limit": 246.19, "cents": 0.95, "size": 259.15 }
  ],
  "lay": [
    { "price": 12.5, "limit": 21.62, "cents": 0.08, "size": 270.2 }
  ]
}
```

Best `back` always equals `price`. Here the `lay` side sits nowhere near it — 15 units of interest at 100.0 is a one-sided book, not a spread you can quote inside.

Three things that bite:

* **A crossed book is a signal, not an opportunity.** If best `back` on one venue exceeds best `lay` on another, check `changedAt` on both sides. A genuine cross closes in milliseconds; a persistent one means a side stopped updating.
* **Depth is per venue, never aggregate.** You cannot fill one venue's order against another's queue. Model executable size per venue, then aggregate the *outcomes*, not the books.
* **`limit` is a stake in the venue's base currency; `size` is a count of shares.** Compare depth on `limit`, and convert through the [`currencies`](/websocket/channels/currencies) channel before comparing across crypto venues, or the same exposure reads as two different numbers.

***

## Sizing: `limit` and depth do different jobs

* **`limit`** on an exchange or prediction market is depth at the touch — the same value as `meta.back[0].limit`, not a cap on your order. (On a traditional book it *is* a cap; see [Limits](/websocket/channels/odds.md#limits).)
* **The ladder** bounds the fill — what rests behind the touch, and what it costs to clear a size.

Walk the ladder to your target and price the fill at the volume-weighted average, not at the touch:

```json theme={null}
"price": 3.333,
"limit": 308.25,
"meta": {
  "back": [
    { "price": 3.333, "limit": 308.25, "cents": 0.3, "size": 1027.51 },
    { "price": 3.226, "limit": 1840.71, "cents": 0.31, "size": 5937.78 },
    { "price": 3.125, "limit": 971.23, "cents": 0.32, "size": 3035.1 }
  ]
}
```

Walk `limit`, not `size`: `size` counts shares at the venue, `limit` is the stake those shares represent (`size × cents`). 3,120.19 of stake rests on this ladder, so a target of 1,228.61 fills 308.25 at 3.333 and 920.36 at 3.226 — an average of **3.253**, not the 3.333 on screen. Note the quote's own `limit` (308.25) is just the top rung: it is the depth at the touch, not a cap on your order.

Limit cuts arrive as ordinary odds updates on the same `oddsId` and are often the earlier signal. Full treatment, including traditional books where `limit` is the only size signal there is, in [sharp-line trading & CLV](/guides/sharp-line-trading.md#limit-and-depth-answer-different-questions).

***

## Gating: is this quote tradeable

Freshness is a separate question from value, and the expensive one to get wrong. Four flags can veto, each independently:

| Gate | Source | Meaning |
| - | - | - |
| `active` | `odds` | This selection is not currently available |
| `marketActive` | `odds` | The whole market is down — every selection under it is untradeable |
| `suspended` | [`bookmakers`](/websocket/channels/bookmakers) | The venue has suspended this fixture |
| `staleOdds` | [`bookmakers`](/websocket/channels/bookmakers) | **Our connection to the venue was lost.** Freshness is no longer guaranteed |

Two of them arrive on a different channel than the price, which is what makes the failure mode look like this — a quote that is immaculate and untradeable at the same time:

```json theme={null}
// odds
{ "price": 6.369, "active": true, "marketActive": true, "changedAt": 1766939876376 }

// bookmakers — same venue, same fixture
{ "bookmaker": "polymarket", "hasOdds": true, "suspended": false, "staleOdds": true }
```

`staleOdds` is the one people miss, because nothing about the last price you received looks wrong — the prices you hold may have moved at the venue with no way for us to tell you. Pull your quotes rather than re-price them, until it flips back to `false`. See [Critical fields for automated trading](/websocket/channels/bookmakers.md#critical-fields-for-automated-trading).

Add a fifth gate of your own — an age check against the bound the book publishes in `maxDelayLiveInSec` / `maxDelayPregameInSec` on `GET /bookmakers`. A quiet venue and a broken venue look identical for the first few seconds. See [Status signals](/coverage/bookmakers.md#status-signals). Evaluate all five as one predicate on every update, not on a timer.

> [!NOTE]
>
> On fixture markets, `participantsRotated` is a sixth check of a different kind: when true, that venue's home/away assignment is flipped relative to ours, reversing side-based outcomes and flipping handicap signs. It affects correctness, not freshness — and it is silent if you map outcomes by position instead of by ID.

***

## Delay budget

Your reaction time is four segments, and you control one:

| Segment | Measure it as | Who controls it |
| - | - | - |
| Venue → our observation | `changedAt − bookmakerChangedAt` | published per book |
| Conflation | fixed \~10 ms batching window; newest value always lands | bounded by design |
| Gateway → you | `your receipt − ts` | **you** — see [Server location](/api-reference/concepts.md#server-location-matters) |
| Decision + order round-trip | your own clock | you |

Every message carries the first three stamps, so the split is measurable per venue rather than something you take on trust:

```
"bookmakerChangedAt": 1776717657043   ← t0  the venue stamped it
"changedAt":          1776717657402   ← t1  we observed it      +359 ms
"ts":                 1776717657500   ← t2  the gateway sent it  +98 ms
                                        t3  your receipt — the one segment you move
```

Uniform quoting policy across venues is therefore wrong: a 0.05 s venue and a \~1 s venue justify different spreads on the same underlying, and the numbers to parameterize that are [published](/api-reference/reliability.md#latency-and-freshness) rather than inferred. If a venue re-prices faster than your loop closes, quote behind it or size smaller — do not chase its touch.

***

## Surviving gaps

The worst state is quoting from a book you believe is current and is not. So: **pull quotes first, reconcile second.**

* **Server-initiated reconnect** — we push a `reconnect` control frame before deploys and keep streaming through a grace window. Act on the frame, not on the eventual socket error.
* **Within the replay window** — reconnect with your per-channel cursor (`serverEpoch` + `entryId`) and missed updates replay with no gaps.
* **Outside it** — the gateway emits `snapshot_required`; restore with one REST snapshot. A designed path with a bounded worst case, not an error.
* **Changed `serverEpoch`** — old cursors are meaningless; re-snapshot instead of resuming.

Mechanics in [Resume & Replay](/websocket/resume-replay.md). Keep a "last known good" timestamp per venue and let it gate quoting independently of whether your socket is up — a live socket with a frozen venue behind it is the failure that actually costs money.

***

## Routing orders back

Venue-native IDs ride on the price itself, so the moment you decide is not the moment you need a lookup:

```json theme={null}
"price": 6.369,
"limit": 4.71,
"bookmakerMarketId": "1011497",
"bookmakerOutcomeId": "4937..."
```

Those last two are Polymarket's own market and selection identifiers, already in hand: the order is `bookmakerOutcomeId` at `price`, staked at no more than `limit`.

At fixture level, `GET /fixtures/mapping` resolves both directions — `fixtureIds` in for venue IDs, `bookmakerFixtureIds` in to go back. The [`bookmakers`](/websocket/channels/bookmakers) channel also carries `bookmakerFixtureId` and `fixturePath`, so a manual desk can open the venue's own page.

***

## Pricing across venues

Group by `marketId` before pricing anything: all outcomes under one `marketId` form a complete probability space, which is what overround and completeness checks need — and each distinct line value is its own `marketId`, so a market never silently mixes lines.

Normalize within each venue, then combine across them. Two weightings the data supports explicitly:

* **By size** — real depth and a high `limit` is a stronger observation than a token amount at the same price.
* **By delay** — a measured sub-100 ms venue and a multi-second one are not equally current.

For fill auditing, backtests, and closing lines, the REST [historical odds and CLV](/api-reference/concepts.md#historical-odds-and-clv) endpoints share the same `oddsId` key as the live stream, so decisions and their audit trail join without a translation step.

***

## Non-sports markets are futures

Politics, elections, finance, crypto, and weather are sports with `sportId` 69+ and exist as **futures only** — one future per outright market, on the [`futures`](/websocket/channels/futures) and [`oddsFutures`](/websocket/channels/oddsFutures) channels, with the same delivery semantics and the same orderbook `meta`. Everything above applies unchanged.

```
{futureId}:{bookmaker}:{futureOutcomeId}:{participantId}
```

**Which question** the future asks is `market.marketId` — the 5-digit `futureMarketId` also carried in the `futureId` tail, allocated per sport (`69002` = Politics Prediction). **Which selection** a price is on depends on the market's shape:

| Shape | Markets | `futureOutcomeId` | The selection is |
| - | - | - | - |
| Participant-keyed | `winner`, `topscorer`, `relegation`, `mvp` | `0` — sentinel, no outcome rows exist | the participant you back |
| Decomposed | `prediction` Yes / No, season totals Over / Under | a real id per side per line | the side, plus the participant the line is about |

One decomposed future market spans many lines, and unlike fixture markets the handicap sits on the **outcome**, not on the market. Read both from the explicit fields rather than splitting `oddsId` — see [Future odds IDs](/api-reference/concepts.md#future-odds-ids) and [How Futures Work](/api-reference/concepts.md#how-futures-work-prediction-markets).

***

## Wiring it up

**1. Stream**

Connect to `wss://v5.oddspapi.io/ws` and log in with the channels, sports, and venues you trade. Subscribe to `bookmakers` alongside `odds` — without it you have prices but no staleness signal.

**2. Snapshot**

With updates already flowing, hydrate over REST (`/fixtures/odds` or `/futures/odds`) and merge by newest `changedAt` per key. Connect-first is what makes hydration gapless — there is no REST-to-WebSocket cursor, and none is needed.

**3. Apply updates**

Key by `oddsId` and overwrite in place. Each message is state, not a tick.

**4. Gate**

Evaluate the quoting predicate on every update, per venue, before anything downstream sees the price.

**5. Recover**

Handle `reconnect` and `snapshot_required` as designed paths.

Filter at login — `sportIds`, `tournamentIds`, and `bookmakers` cut volume far more effectively than client-side filtering — and prefer `receiveType: "zstd"` on `odds` at scale ([Compression](/websocket/compression.md)). Login shapes and a worked client are on the [`odds`](/websocket/channels/odds) page and in the [Quickstart](/quickstart.md).

***

## What you can rely on

* **One orderbook shape, not one per venue.** On Polymarket, Polymarket US, Kalshi and ProphetX, `meta.back` / `meta.lay` are `{ price, cents, size, limit }` arrays with the same semantics, so you write one parser — treat `size` as optional, since ProphetX omits it. Unknown `meta` keys are additive; the `back` / `lay` core will not be repurposed.
* **Venue-native IDs ride on the price itself.** Routing an order back costs no second lookup and no reverse-mapping table of your own.
* **Size is a first-class field.** `limit` is normalized across books and carried for close to every sharp book — per rung on an exchange, so you can price a fill rather than guess at one.
* **Loss of contact is reported, not hidden.** `staleOdds` is the difference between pulling quotes and unknowingly trading against a frozen book.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.