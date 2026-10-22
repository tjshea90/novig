> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Sharp-Line Trading & CLV - Limits, Delay & Closing Line Value

> Trade against sharp bookmaker lines on OddsPapi: stake limits per price, measured observation delay per book, main-line filtering, staleness handling, and CLV measurement.

Sharp books are the reference the rest of the market prices against. Trading on them well needs three things a generic odds feed usually does not give you: **size at the price**, **a delay you can measure**, and **a closing line to grade yourself against**.

***

## Price is not enough — take the size with it

Outcomes on the [`odds`](/websocket/channels/odds) channel can carry `limit`: a normalized size at that price. It is not universal — on a sample of 702,544 live quotes it was present on 27 of 203 venues (17.8% of quotes). Pinnacle, BookMaker.eu, Sharp.ag, Novig, ProphetX, Betfair Exchange, Polymarket and Kalshi carry it on effectively every row; bet365, DraftKings and BetOnline.ag carry it on none. Treat it as a per-book capability you check, not a field you can assume.

```json theme={null}
{
  "bookmaker": "pinnacle",
  "outcomeId": 141,
  "price": 1.243,
  "limit": 61728,
  "mainLine": true,
  "bookmakerChangedAt": 1792445529003,
  "changedAt": 1792445529264
}
```

### `limit` and depth answer different questions

Two size signals can arrive on the same row, and conflating them is a common way to over-size:

* **`limit`** means different things by venue type. On a traditional sharp book it is a cap: the most the book will accept at this price. On an exchange or prediction market it is **depth at the touch** — the same number as `meta.back[0].limit`, which we verified on 2,927 quotes across Polymarket, Kalshi, ProphetX and Betfair Exchange. Nothing is capping you there; that is simply how much is resting at the best price.
* **The ladder** (`meta.back` / `meta.lay`) bounds the fill — what is resting behind the touch, and therefore what it costs you to clear a given size.

On a traditional sharp book there is no ladder, and `limit` is the whole answer: one number, one order, and if you want more you wait for the book to re-offer. On an exchange the two are not independent signals — `limit` is the first rung of the ladder, so reading it as a cap makes a deep book look shallow and a thin one look safe. Size off the ladder, and use `limit` alone only where no ladder exists.

Where you have depth, **price the fill at the volume-weighted average, not at the touch.** Walk the ladder to your target size and use the resulting average; beyond the first level the top-of-book price is a number you will not get. Sizing off the touch is what turns a modelled edge into a realised loss on exactly the trades you most wanted.

Walk `limit`, not `size`. A prediction-market rung is `{ price, cents, size, limit }`: `size` counts shares at the venue and `limit` is the stake those shares represent (`limit == size × cents`, exact on all 3,044 rungs sampled). Summing `size` gives a number in the wrong unit. ProphetX omits `size` entirely, so `limit` is also the only field present everywhere. Worked example in [Prediction markets](/guides/prediction-markets.md#building-a-cross-venue-view).

Depth is also **per venue, never aggregate.** Summing ladders across books gives a total you cannot trade against, because one venue's order does not fill against another's resting liquidity. Model executable size per book, then compare at the selection level.

### Limit cuts are a signal in their own right

Limit changes arrive as ordinary odds updates on the same `oddsId`, so a book tightening size shows up in the same stream as a price move — often **earlier**, and often more informative. A sharp book that holds its price and halves its limit has told you something the price alone has not.

Log limit history alongside price history if you want to use this. The REST [historical odds](/api-reference/concepts.md#historical-odds-and-clv) endpoints carry the same rows, so the sequence is reconstructable after the fact as well as observable live.

***

## Know your delay per book

Four timestamps travel with the data, so the delay is something you verify rather than assume:

| Stamp | Field | Meaning |
| - | - | - |
| t0 | `bookmakerChangedAt` | The book's own change timestamp (when provided) |
| t1 | `changedAt` | When OddsPapi observed the change |
| t2 | `ts` (envelope) | When the gateway sent the message |
| t3 | — | Your receipt |

`changedAt − bookmakerChangedAt` is the observation delay for that update. Per-book bounds are published on [`GET /bookmakers`](/api-reference/common/get-bookmakers) (`maxDelayLiveInSec`, `maxDelayPregameInSec`, `maxDelayPregameMainInSec`), and representative rolling p50s per book are in [Latency and freshness](/api-reference/reliability.md#latency-and-freshness).

Two things worth wiring in early: log t0–t3 per fill so you can attribute a bad fill to the right hop, and [deploy near the gateway](/api-reference/concepts.md#server-location-matters) — the last hop is the only one you control.

***

## Stop trading when the book stops being current

| Signal | Channel | Meaning |
| - | - | - |
| `staleOdds` | [`bookmakers`](/websocket/channels/bookmakers) | Connection to that book degraded — freshness no longer guaranteed |
| `suspended` | `bookmakers` | The book's odds are suspended |
| `marketActive: false` | `odds` | The whole market is down |
| `active: false` | `odds` | That outcome is unavailable |

Treat `staleOdds` as a kill switch per book, not a warning — a stale sharp line is worse than no line, because it still looks tradeable.

Also watch `participantsRotated` on the `bookmakers` channel: a book whose home/away assignment differs from the OddsPapi baseline will otherwise read as an arbitrage that does not exist.

***

## Main lines and change filtering

* `mainLine` marks the book's main line for a market, so you can follow the headline price without subscribing to every alternate line.
* `GET /fixtures/odds/main` returns main lines only for snapshots.
* `since` filters (epoch **milliseconds**) on the odds endpoints let you backfill only what moved. See [Timestamps](/api-reference/concepts.md#timestamps-seconds-vs-milliseconds).

Remember that each line value is its own `marketId` — "Over 2.5" and "Over 3.5" are separate markets, not two outcomes of one. Group by `marketId` for overround and normalization.

***

## Asian lines

Asian handicaps and quarter lines are first-class: `marketType` `spreads` with the line in `handicap` (e.g. `-0.25`), and settlement returns `HALFWIN` / `HALFLOSS` for split-stake grading alongside `WIN` / `LOSE` / `PUSH`. See [Enumerations → settlementStatus](/api-reference/enumerations.md#settlementstatus).

For books quoting in crypto or non-USD fiat, the [`currencies`](/websocket/channels/currencies) channel carries fiat and crypto rates against USD for stake and limit conversion.

***

## Grade yourself against the close

Trade on the stream, measure on REST. The live channels deliver conflated state on a fixed \~10 ms window — the newest value always arrives, but superseded intermediate ticks do not, so the stream is not a tick ledger. For full movement and opening/closing prices:

| Purpose | Fixtures | Futures |
| - | - | - |
| Full price timeline | `GET /fixtures/odds/historical` | `GET /futures/odds/historical` |
| Opening vs closing line | `GET /fixtures/odds/clv` | `GET /futures/odds/clv` |

Both share the `oddsId` key (`{fixtureId}:{bookmaker}:{outcomeId}:{playerId}`) with the live stream, so fills join to their own closing line without a mapping step.

**1. Record the fill**

Store `oddsId`, your executed price, and t0–t3 at the moment you traded.

**2. Pull the close**

After the event starts, call `/fixtures/odds/clv` for those `oddsIds`.

**3. Grade**

Compare fill price to closing price for CLV, and to the recorded timeline (`/fixtures/odds/historical`) for slippage attribution.

**4. Settle**

Use `GET /fixtures/settlement` for per-outcome results rather than your own grading logic.

A desk that logs CLV per book and per latency bucket usually learns where its edge actually comes from within a week of data — and whether the delay it is paying for is the delay that matters.

***

## What you can rely on

* **Delay is published per book, and measured.** `maxDelayLiveInSec` / `maxDelayPregameInSec` / `maxDelayPregameMainInSec` come from observed-vs-bookmaker timestamps where a book publishes usable ones, and are labelled as estimates where it does not. The four-stamp chain means you can check those numbers against your own clock instead of taking them on faith.
* **Nothing is dropped.** Updates are conflated on a fixed \~10 ms window and the newest value always lands — the stream is latest-state, not a lossy sample. When you need every tick, the REST history endpoints have them.
* **IDs stay addressable across seasons.** The market taxonomy is append-only — new `marketType` values appear as coverage grows and existing ones never change meaning — and the same coordinates always resolve to the same `marketId` / `outcomeId`. A backtest written against last season's data still addresses the same selections today.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.