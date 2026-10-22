> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Sports Coverage - Fixtures & Futures by Sport

> Coverage of the OddsPapi B2B odds API: 350+ bookmakers anchored on sharp trading books, plus 60+ sports and prediction-market topics with fixtures and futures availability per sportId.

OddsPapi covers **60+ sports** plus prediction-market topics, each identified by a numeric `sportId`. Every sport is available as **fixtures** (match-level markets), **futures** (outrights / season-long markets), or both.

> [!NOTE]
>
> **Rule of thumb:** `sportId` **10–68** and **82–84** are covered for **both fixtures and futures**. `sportId` **69–78** are prediction-market topics covered for **futures only**. `sportId` **79–81** are racing codes covered as **ante-post futures only**.

Discover the live list programmatically via `GET /sports`. Identifiers are language-independent; `sportName` follows the request language prefix.

> **[Bookmakers](/coverage/bookmakers.md)**
>
> Sharps, exchanges, and retail books — capabilities, freshness bounds, and status signals.

> **[Markets](/coverage/markets.md)**
>
> Market types, lines, period scoping, and player props — and how to enumerate them.

## Bookmaker coverage

The feed aggregates **350+ bookmakers**, anchored on the books that matter for price discovery:

* **Sharp trading books** — the core of the feed. The bookmakers that are most accurate in pricing and dominate global sports prediction (e.g. Pinnacle, Singbet, Circa Sports) — the books trading systems benchmark against, carried with [limits](/websocket/channels/odds.md#limits) and full delivery timestamps.
* **Betting exchanges & prediction markets** — realtime prices with [orderbook depth](/websocket/channels/odds.md#advanced-metadata-meta) (`back` / `lay` ladders), e.g. Betfair and Polymarket.
* **European, US, and crypto sportsbooks** — broad retail coverage across regions (e.g. DraftKings, Stake).

**Asian lines are first-class**: Asian handicap market types, quarter-line settlement (`HALFWIN` / `HALFLOSS` — see [Enumerations](/api-reference/enumerations.md)), and fiat + crypto exchange rates on the [currencies channel](/websocket/channels/currencies.md) for odds conversion.

Discover the catalog available to your key via [`GET /bookmakers`](/api-reference/common/get-bookmakers), and see [Bookmaker coverage](/coverage/bookmakers.md) for per-book capabilities, freshness bounds, and status signals.

## Coverage by sport

| sportId | Sport | Fixtures | Futures |
| -: | - | :-: | :-: |
| 10 | Soccer | ✓ | ✓ |
| 11 | Basketball | ✓ | ✓ |
| 12 | Tennis | ✓ | ✓ |
| 13 | Baseball | ✓ | ✓ |
| 14 | American Football | ✓ | ✓ |
| 15 | Ice Hockey | ✓ | ✓ |
| 16 | ESport Dota | ✓ | ✓ |
| 17 | ESport Counter-Strike | ✓ | ✓ |
| 18 | ESport League of Legends | ✓ | ✓ |
| 19 | Darts | ✓ | ✓ |
| 20 | MMA | ✓ | ✓ |
| 21 | Boxing | ✓ | ✓ |
| 22 | Handball | ✓ | ✓ |
| 23 | Volleyball | ✓ | ✓ |
| 24 | Snooker | ✓ | ✓ |
| 25 | Table Tennis | ✓ | ✓ |
| 26 | Rugby | ✓ | ✓ |
| 27 | Cricket | ✓ | ✓ |
| 28 | Waterpolo | ✓ | ✓ |
| 29 | Futsal | ✓ | ✓ |
| 30 | Beach Volley | ✓ | ✓ |
| 31 | Aussie Rules | ✓ | ✓ |
| 32 | Field hockey | ✓ | ✓ |
| 33 | Floorball | ✓ | ✓ |
| 34 | Squash | ✓ | ✓ |
| 35 | Basketball 3x3 | ✓ | ✓ |
| 36 | Beach Soccer | ✓ | ✓ |
| 37 | Pesapallo | ✓ | ✓ |
| 38 | Lacrosse | ✓ | ✓ |
| 39 | Curling | ✓ | ✓ |
| 40 | Padel | ✓ | ✓ |
| 41 | Bandy | ✓ | ✓ |
| 42 | Kabaddi | ✓ | ✓ |
| 43 | Rink Hockey | ✓ | ✓ |
| 44 | Soccer Specials | ✓ | ✓ |
| 45 | Gaelic Football | ✓ | ✓ |
| 46 | Netball | ✓ | ✓ |
| 47 | Beach Handball | ✓ | ✓ |
| 48 | Athletics | ✓ | ✓ |
| 49 | Badminton | ✓ | ✓ |
| 50 | Bowls | ✓ | ✓ |
| 51 | Cross-Country | ✓ | ✓ |
| 52 | Gaelic Hurling | ✓ | ✓ |
| 53 | Softball | ✓ | ✓ |
| 54 | eSoccer | ✓ | ✓ |
| 55 | eBasketball | ✓ | ✓ |
| 56 | ESport Call of Duty | ✓ | ✓ |
| 57 | ESport Overwatch | ✓ | ✓ |
| 58 | ESport Rainbow Six | ✓ | ✓ |
| 59 | ESport Rocket League | ✓ | ✓ |
| 60 | ESport StarCraft | ✓ | ✓ |
| 61 | ESport Valorant | ✓ | ✓ |
| 62 | ESport Arena of Valor | ✓ | ✓ |
| 63 | ESport King of Glory | ✓ | ✓ |
| 64 | Judo | ✓ | ✓ |
| 65 | ESport Honor of Kings | ✓ | ✓ |
| 66 | Speedway | ✓ | ✓ |
| 67 | Golf | ✓ | ✓ |
| 68 | Cycling | ✓ | ✓ |
| 69 | Politics | — | ✓ |
| 70 | Elections | — | ✓ |
| 71 | Economics | — | ✓ |
| 72 | Finance | — | ✓ |
| 73 | Technology | — | ✓ |
| 74 | Health | — | ✓ |
| 75 | Science | — | ✓ |
| 76 | Cryptocurrency | — | ✓ |
| 77 | Weather | — | ✓ |
| 78 | Culture | — | ✓ |
| 79 | Horse Racing | — | ✓ |
| 80 | Greyhound Racing | — | ✓ |
| 81 | Harness Racing | — | ✓ |
| 82 | Formula 1 | ✓ | ✓ |
| 83 | ESport Mobile Legends | ✓ | ✓ |
| 84 | Chess | ✓ | ✓ |

## Notes

* **Fixtures** are match- or event-level markets (a single game, bout, or match), streamed on the [`fixtures`](/websocket/channels/fixtures) and [`odds`](/websocket/channels/odds) channels.
* **Futures** are outright / season-long or topic-level markets (league winner, tournament, election, etc.), streamed on the [`futures`](/websocket/channels/futures) and `oddsFutures` channels.
* `sportId` **69–78** are **prediction-market topics** (politics, elections, finance, crypto, weather, …) — these exist only as futures/outright markets and feed the prediction-market and exchange use cases.
* `sportId` **79–81** are **racing codes** (horse, greyhound, harness) — covered as **ante-post futures** (one future per ante-post market), not as per-race fixtures.
* The `sportId` is the **first two digits** of downstream `marketId`, `outcomeId`, and `fixtureId` values — see [Core Concepts](/api-reference/concepts.md#id-structure).


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.