> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Odds Channel - Realtime Betting Odds Stream

> Low-latency realtime betting odds via WebSocket. Stream live odds aggregated from 350+ bookmakers. Decimal, fractional, American formats with orderbook depth.

## What it streams

Realtime odds updates for outcomes within markets, scoped to a specific `fixtureId`.

Odds are grouped by bookmaker and keyed by a unique **oddsId** per outcome.
Each entry represents a single **oddsId** (one price for one outcome).

***

## Routing

* Entity key: `payload.fixtureId`
* Filters: `sportIds`, `tournamentIds`, `fixtureIds`, `bookmakers`
* Access: determined by your `apiKey`
* Bookmaker-gated: ✅ Yes

***

## Delivery semantics

The odds channel delivers **conflated state** on a fixed **\~10 ms batching window**: if multiple updates for the same `oddsId` occur within one window, only the newest is sent.

* **No state is lost** — the final value of every price move is always delivered
* **Bounded staleness** — a delivered price is never more than one batching window behind our observation of the bookmaker
* The window is a fixed performance parameter, not a load-shedding mechanism

What is *not* delivered: intermediate ticks superseded within the same window. Treat every message as a **state update**, not a ledger.

> Need every price move or the closing line? For full tick-level movement use [Historical Odds](/api-reference/concepts.md#historical-odds-and-clv) and for opening/closing prices use [CLV](/api-reference/concepts.md#historical-odds-and-clv).

***

## Payload structure

oddsId:

```
<fixtureId>:<bookmaker>:<outcomeId>:<playerId>
```

***

## outcome object (full schema)

Each odds entry is an **outcome**, with the following fields:

| Field | Type | Description | |
| - | - | - | - |
| `bookmaker` | `string` | Bookmaker slug (e.g. `"stake"`, `"pinnacle"`, `"polymarket"`) | |
| `outcomeId` | `integer` | Outcome identifier | |
| `playerId` | `integer` | Player ID (`0` for non-player markets) | |
| `price` | `number` | Decimal odds | |
| `active` | `boolean` | Whether this outcome is currently available | |
| `marketActive` | `boolean \| null` | Whether the entire market is active | |
| `mainLine` | `boolean \| null` | Whether this is the bookmaker’s main line | |
| `marketId` | `integer` | Market identifier | |
| `bookmakerMarketId` | `string \| null` | Native bookmaker market ID | |
| `bookmakerOutcomeId` | `string \| null` | Native bookmaker outcome ID | |
| `bookmakerChangedAt` | `number \| null` | Bookmaker-provided change timestamp (epoch ms) | |
| `priceFractional` | \`string | \` | Fractional odds (e.g. `"5/2"`) |
| `priceAmerican` | \`integer | \` | American odds (e.g. `-110`, `+250`) |
| `limit` | `number \| null` | Maximum accepted stake at this price — carried for close to every sharp book | |
| `betslip` | `string \| null` | Optional bookmaker betslip or deeplink token | |
| `meta` | `object \| null` | Bookmaker-specific metadata (orderbooks, ladders, etc.) | |
| `changedAt` | `number` | When OddsPapi observed this change at the bookmaker (epoch ms, UTC) — always present | |

***

## Notes on timestamps

Each update carries the full delivery chain:

| Stamp | Field | Meaning |
| - | - | - |
| t0 | `bookmakerChangedAt` | The bookmaker's own change timestamp (when provided) |
| t1 | `changedAt` | When OddsPapi observed the change — always present |
| t2 | `ts` (message envelope) | When the gateway sent the message |
| t3 | — | Client receipt — measure it on your side |

`changedAt − bookmakerChangedAt` is the observation delay for that update. Per-bookmaker freshness bounds are published on [`GET /bookmakers`](/api-reference/common/get-bookmakers) — see [Latency and freshness](/api-reference/reliability.md#latency-and-freshness).

***

## Limits

`limit` is the maximum accepted stake at the current price — for a trading system, size-at-price matters as much as the price itself. OddsPapi normalizes limits across books and carries them for close to every sharp book (see `"limit": 19354` on Pinnacle below). Limit changes are delivered as regular odds updates.

`limit` and the `meta` ladders bound different things: `limit` bounds a single order, the ladder bounds the fill. On a traditional book `limit` is the only size signal there is; on an exchange you have both and they constrain you independently.

See [sharp-line trading & CLV](/guides/sharp-line-trading.md) for using limits together with per-book delay, main-line filtering, and closing line value.

***

## Advanced metadata (`meta`)

Exchanges and prediction markets carry their orderbook on the quote itself, under `meta`.

```json theme={null}
{
  "meta": {
    "lay": [
      { "price": 12.5, "limit": 21.62, "cents": 0.08, "size": 270.2 },
      { "price": 11.111, "limit": 243.39, "cents": 0.09, "size": 2704.37 },
      { "price": 10.0, "limit": 208.8, "cents": 0.1, "size": 2088.0 }
    ],
    "back": [
      { "price": 1.053, "limit": 246.19, "cents": 0.95, "size": 259.15 },
      { "price": 1.042, "limit": 886.44, "cents": 0.96, "size": 923.37 },
      { "price": 1.031, "limit": 3049.68, "cents": 0.97, "size": 3144.0 }
    ],
    "bookmakerLayOutcomeId": "13082425263396665739224071723652658671047649451832896297859072241957942307529"
  }
}
```

### Ladder entries

`back` and `lay` are arrays of rungs, best price first, up to three deep. Each rung:

| Field | Meaning |
| - | - |
| `price` | Decimal odds for that rung. The first `back` rung is top of book and equals the quote's own `price`. |
| `cents` | The venue's native share price, `0`–`1`. The reciprocal of `price`, so `cents ≈ 1 / price`. |
| `size` | Shares resting at that rung. Not published by every venue — `prophetx` omits it. |
| `limit` | Stake-equivalent currency at that rung. Always `size × cents`, so it is the number to size an order against. |

Either side can be `[]`. A one-sided book is normal on a thin market, not an error, and
best `back` touching best `lay` is a crossed book — read `changedAt` before trading it.

### Additional keys

On `polymarket`, `polymarket.us`, `kalshi` and `prophetx`, `meta` also carries
`bookmakerLayOutcomeId` — the venue's own id for the *opposite* side of the market. You need
it to route a lay order, because `bookmakerOutcomeId` on the quote addresses the back side
only. Those four share the same `back` / `lay` semantics, so one parser covers all of them.
Treat `meta` as additive and preserve keys you do not recognise.

> [!NOTE]
>
> `betfair-ex` uses a venue-specific shape instead: `availableToBack` and `availableToLay`
> (rungs of `{ price, size }` only — no `cents` or `limit`), plus `tradedVolume`, `betDelay`
> and, on handicap markets, `bookmakerHandicap`.

See [building on prediction markets & exchanges](/guides/prediction-markets.md) for reading the ladders, deriving top-of-book, and routing orders back to the venue with `bookmakerMarketId` / `bookmakerOutcomeId`.

***

## Example: traditional bookmaker odds

```json theme={null}
{
  "channel": "odds",
  "type": "UPDATE",
  "payload": {
    "fixtureId": "id1100013270505136",
    "odds": {
      "pinnacle": {
        "id1100013270505136:pinnacle:111:0": {
          "bookmaker": "pinnacle",
          "outcomeId": 111,
          "playerId": 0,
          "active": true,
          "price": 1.155,
          "marketActive": true,
          "mainLine": true,
          "bookmakerMarketId": "line/4/487/1628488896/3565645414/0/moneyline",
          "bookmakerOutcomeId": "home",
          "bookmakerChangedAt": 1776717657043,
          "limit": 19354,
          "priceAmerican": -645,
          "priceFractional": "11/71",
          "marketId": 111,
          "changedAt": 1776717657402
        },
        "id1100013270505136:pinnacle:112:0": {
          "bookmaker": "pinnacle",
          "outcomeId": 112,
          "playerId": 0,
          "active": true,
          "price": 5.77,
          "marketActive": true,
          "mainLine": true,
          "bookmakerMarketId": "line/4/487/1628488896/3565645414/0/moneyline",
          "bookmakerOutcomeId": "away",
          "bookmakerChangedAt": 1776717657043,
          "limit": 3000,
          "priceAmerican": 477,
          "priceFractional": "477/100",
          "marketId": 111,
          "changedAt": 1776717657402
        }
      }
    }
  },
  "ts": 1776717657500,
  "entryId": "1776717657500-84805"
}
```

***

## Example: prediction market odds (extended fields)

```json theme={null}
{
  "channel": "odds",
  "type": "UPDATE",
  "payload": {
    "fixtureId": "id1400003171515926",
    "odds": {
      "polymarket": {
        "id1400003171515926:polymarket:148:0": {
          "outcomeId": 148,
          "playerId": 0,
          "price": 1.053,
          "active": true,
          "meta": {
            "lay": [
              { "price": 12.5, "limit": 21.62, "cents": 0.08, "size": 270.2 },
              { "price": 11.111, "limit": 243.39, "cents": 0.09, "size": 2704.37 },
              { "price": 10.0, "limit": 208.8, "cents": 0.1, "size": 2088.0 }
            ],
            "back": [
              { "price": 1.053, "limit": 246.19, "cents": 0.95, "size": 259.15 },
              { "price": 1.042, "limit": 886.44, "cents": 0.96, "size": 923.37 },
              { "price": 1.031, "limit": 3049.68, "cents": 0.97, "size": 3144.0 }
            ],
            "bookmakerLayOutcomeId": "13082425263396665739224071723652658671047649451832896297859072241957942307529"
          },
          "bookmaker": "polymarket",
          "changedAt": 1792509182922,
          "limit": 246.19,
          "marketId": 148,
          "marketActive": true,
          "mainLine": false,
          "bookmakerMarketId": "5377515",
          "bookmakerOutcomeId": "69214126592072910433928267408837065345885666858422547701782486967866009036368",
          "bookmakerChangedAt": 1792509182578,
          "priceFractional": "5/94",
          "priceAmerican": -1887
        }
      }
    }
  },
  "ts": 1792509182957,
  "entryId": "1792509182957-253028250"
}
```

***

## Implementation guidance

* Always key storage by
  ```
  {fixtureId}:{bookmaker}:{outcomeId}:{playerId}
  ```
* Group outcomes by `marketId` (see **Concepts**) for:
  * arbitrage detection
  * overround calculations
  * probability normalization
* Treat `active=false` or `marketActive=false` as **hard stops**
* Preserve unknown fields in `meta` to remain forward-compatible

End-to-end patterns built on this channel: [prediction markets & exchanges](/guides/prediction-markets.md), [sharp-line trading & CLV](/guides/sharp-line-trading.md), [running a second feed](/guides/second-feed.md), and [powering a sportsbook](/guides/sportsbook.md).

***


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.