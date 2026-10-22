> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Market Coverage - Market Types, Lines, Periods & Player Props

> What markets OddsPapi covers: market types, period scoping, Asian and European handicaps, totals, player props, and how to discover the full market map per sport via GET /markets.

Markets are not a fixed list per sport — they are generated from a deterministic decomposition, so the same bet resolves to the same identifiers everywhere. This page covers what exists and how to enumerate it; the mechanics are in [Markets and outcomes](/api-reference/concepts.md#markets-and-outcomes).

***

## How a market is addressed

```
marketType → period → handicap (line value) → side   [+ optional player]
```

| Level | Field | Example |
| - | - | - |
| Kind of market | `marketType` | `totals` |
| Segment | `period` | `fulltime`, `p1` |
| Line value | `handicap` | `2.5`, `-0.25`, `0.0` |
| Side | `outcomeName` | `Over` / `Under` |
| Player | `playerId` | `0` for non-player markets |

Two consequences worth designing around:

* **Every distinct line value is its own `marketId`.** "Over 2.5" and "Over 3.5" are different markets, not two outcomes of one. Within a `marketId` the outcomes are only the sides of that exact line.
* **`marketId` equals the market's first `outcomeId`**, and `marketLength` is the number of sides — so the outcomes under one `marketId` form a complete probability space without any extra metadata.

***

## Market families

`marketType` is an **open taxonomy** — stable per value, growing over time. Representative types:

| `marketType` | Market |
| - | - |
| `1x2` | Three-way match result |
| `moneyline` | Two-way winner (incl. overtime where applicable) |
| `totals` | Over / under a line |
| `teamtotals-team1` | Over / under for one team |
| `spreads` | Asian handicap |
| `spreads-european` | European (three-way) handicap |
| `bothteamsscore` | Both teams to score |
| `drawnobet` | Draw no bet |
| `oddeven` | Odd / even |
| `players-…` | Player propositions (e.g. `players-shots`) |

This is not the full map — new types are appended as coverage grows, and what exists differs per sport. Enumerate the real map with `GET /markets` rather than hard-coding.

***

## Period scoping

Markets are scoped to a segment via `period`, using one shared vocabulary across all sports: `fulltime`, `result`, `overtime`, `penalties`, numbered periods `p1`…`p12`, combined segments (`p1+p2`, `fulltime+overtime`, …), and sub-period keys for games within a set (`p1g1`…`p5g13`, `currentgame`).

What a period *means* depends on the sport — `p1` is a half in soccer, a quarter in NBA basketball, a set in tennis, a map in esports. Use the fixture's `expectedPeriods` and `periodLength` to interpret it. Full list in [Enumerations → period](/api-reference/enumerations.md#period); for which sports actually offer `fulltime` as a distinct period and which only have `result`, see [Periods per sport](/guides/periods.md).

***

## Player props

Markets carry a `playerProp` flag, and prices for player markets carry a non-zero `playerId`:

```
{fixtureId}:{bookmaker}:{outcomeId}:{playerId}
```

The `outcomeId` encodes everything except the player — market type, period, line, and side — so the same prop for two different players shares an `outcomeId` and differs only in `playerId`. Discover squads via `GET /players`.

***

## Enumerating the map

```bash theme={null}
# Every market and its outcomes for a sport
curl 'https://v5.oddspapi.io/en/markets?sportId=11&apiKey=YOUR_KEY'

# Resolve specific markets or outcomes
curl 'https://v5.oddspapi.io/en/markets?marketIds=111,113&apiKey=YOUR_KEY'
curl 'https://v5.oddspapi.io/en/markets?outcomeIds=114&apiKey=YOUR_KEY'
```

```json theme={null}
{
  "marketId": 111,
  "marketLength": 2,
  "sportId": 11,
  "playerProp": false,
  "handicap": 0.0,
  "period": "result",
  "marketType": "moneyline",
  "marketName": "Winner (incl. overtime)",
  "marketNameShort": "Winner",
  "outcomes": [
    { "outcomeId": 111, "outcomeName": "1" },
    { "outcomeId": 112, "outcomeName": "2" }
  ]
}
```

`marketName` / `marketNameShort` follow the request's [language prefix](/api-reference/concepts.md#language-prefix) and are for display only — branch on `marketId`, `outcomeId`, `marketType`, and `period`.

***

## Which markets a fixture actually has

`GET /markets` describes what exists for a sport; what is **priced right now** for a given fixture comes from the odds themselves. Pull `GET /fixtures/odds` (or `/fixtures/odds/main` for main lines only) and group by `marketId`. Books differ in which markets and which line values they offer, and `mainLine` marks each book's headline line.

> [!NOTE]
>
> `marketType` is append-only: new values appear as coverage grows and never change meaning. Match on the types you support and ignore the rest rather than failing on unknown values.

***

## Settlement

Grading follows the same coordinates. Asian quarter lines settle as `HALFWIN` / `HALFLOSS` alongside `WIN` / `LOSE` / `PUSH`, and grading is done on the bookmaker-independent selection key `{fixtureId}:{outcomeId}:{playerId}`. See [Enumerations → settlementStatus](/api-reference/enumerations.md#settlementstatus) and [Powering a sportsbook](/guides/sportsbook.md).


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.