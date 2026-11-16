> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Coverage - Sports, Bookmakers & Markets

> What the OddsPapi B2B odds API covers: sports and prediction-market topics with fixtures and futures per sportId, the bookmaker catalogue anchored on sharp trading books, and the full fixture-market taxonomy.

Coverage has three dimensions, and each one has its own matrix: **which sports** we carry, **which bookmakers** we collect, and **which fixture markets** exist per sport. The three pages below are generated from the live catalogue rather than hand-maintained.

| | Covered |
| - | - |
| Sports and topics | **75** total — **60** with fixtures, **72** with futures |
| Bookmakers | **200+** actively collected |
| Fixture markets | published for **60** sports, up to **200** market types each |

> [card] [Sports](https://docs.oddspapi.io/coverage/sports.md)
>
> Every `sportId` with its fixtures and futures availability and how many fixture market types it publishes.

> [card] [Bookmakers](https://docs.oddspapi.io/coverage/bookmakers.md)
>
> Every bookmaker we actively collect, with the slug you address it by.

> [card] [Markets](https://docs.oddspapi.io/coverage/markets.md)
>
> The full fixture-market `marketType` × `period` matrix per sport, and which combinations settle from a collected counter.

## The two product planes

* **Fixtures** are match- or event-level markets (a single game, bout, or match), streamed on the [`fixtures`](https://docs.oddspapi.io/websocket/channels/fixtures) and [`odds`](https://docs.oddspapi.io/websocket/channels/odds) channels.
* **Futures** are outright / season-long or topic-level markets (league winner, tournament, election, etc.), streamed on the [`futures`](https://docs.oddspapi.io/websocket/channels/futures) and `oddsFutures` channels.

A sport can carry one plane or both. `sportId` **69–78** are prediction-market topics (politics, elections, finance, crypto, weather, …) and **79–81** are racing codes (horse, greyhound, harness) — both groups exist as futures only. For the per-sport answer rather than a rule of thumb, see [Sports coverage](https://docs.oddspapi.io/coverage/sports.md).

## What the bookmaker catalogue is anchored on

* **Sharp trading books** — the core of the feed. The bookmakers that are most accurate in pricing and dominate global sports prediction (e.g. Pinnacle, Singbet, Circa Sports) — the books trading systems benchmark against, carried with [limits](https://docs.oddspapi.io/websocket/channels/odds.md#limits) and full delivery timestamps.
* **Betting exchanges & prediction markets** — realtime prices with [orderbook depth](https://docs.oddspapi.io/websocket/channels/odds.md#advanced-metadata-meta) (`back` / `lay` ladders), e.g. Betfair and Polymarket.
* **European, US, and crypto sportsbooks** — broad retail coverage across regions (e.g. DraftKings, Stake).

**Asian lines are first-class**: Asian handicap market types, quarter-line settlement (`HALFWIN` / `HALFLOSS` — see [Enumerations](https://docs.oddspapi.io/api-reference/enumerations.md)), and fiat + crypto exchange rates on the [currencies channel](https://docs.oddspapi.io/websocket/channels/currencies.md) for odds conversion.

## Discovering your own catalogue

These pages describe the whole feed. What **your key** can read is narrower, and the API is the authority on it:

| Dimension | Endpoint |
| - | - |
| Sports | [`GET /sports`](https://docs.oddspapi.io/api-reference/common/get-sports) |
| Bookmakers | [`GET /bookmakers`](https://docs.oddspapi.io/api-reference/common/get-bookmakers) |
| Markets and outcomes | [`GET /markets`](https://docs.oddspapi.io/api-reference/common/get-markets) |

Identifiers are language-independent; display names follow the request's [language prefix](https://docs.oddspapi.io/api-reference/concepts.md#language-prefix). The `sportId` is the **first two digits** of downstream `marketId`, `outcomeId`, and `fixtureId` values — see [Core Concepts](https://docs.oddspapi.io/api-reference/concepts.md#id-structure).


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.