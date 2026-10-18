> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Markets

> What a market is on SX Bet, and every field on a market row.

## What is a market?

A market on SX Bet represents a single, binary question about the outcome of a sporting event.

For example:

* **Will the Lakers or the Celtics win?** *(Moneyline)*
* **Will the total goals scored be over or under 2.5?** *(Total)*
* **Will Manchester City win by more than 1.5 goals?** *(Spread)*

Every market has exactly two sides you can bet on. When you place a bet, you're taking a position on one of those two outcomes. If neither outcome is valid (e.g. the game is cancelled), the market resolves as void and all bets are returned.

Many markets exist for any single fixture. A Premier League match might have a moneyline market, multiple spread markets at different lines, multiple over/under totals, and more.

## The market hash

Every market on SX Bet is identified by a unique `marketHash`.

```
"marketHash": "0x024902746edaed3ffd447aca28f695362264e045be71b3d2ba53e2097dd7667b"
```

The `marketHash` is the primary key used across the entire API. You'll use it to:

* Read the book on a market
* Post and cancel orders
* Query your bet history
* Subscribe to orderbook updates via WebSocket

## Outcomes

Every market has three outcome fields:

| Field | Description |
| - | - |
| `outcomeOneName` | The name of the first bettable outcome |
| `outcomeTwoName` | The name of the second bettable outcome |
| `outcomeVoidName` | The condition under which the market is voided |

The meaning of these fields depends on the market type:

| Market type | `outcomeOneName` | `outcomeTwoName` |
| - | - | - |
| Moneyline | Team/player one | Team/player two |
| Spread | Team A covering the spread | Team B covering the spread |
| Total | Over | Under |
| 1X2 | Team A wins | Team A does not win |

## Market types

In addition to outcome names, each market has a `type` field. This numeric identifier tells you what kind of market it is. SX Bet supports over 30 market types — see the [full list on the Market Types page](/api-reference/market-types). A few common examples:

| `type` | Name | Description |
| - | - | - |
| `52` | 12 | Who will win the game (no draw) — e.g. `"Lakers"` vs `"Celtics"` |
| `1` | 1X2 | Who will win the game, including draw — e.g. `"Man City wins"` vs `"Man City does not win"` |
| `226` | 12 Including Overtime | Who will win the game including overtime |
| `3` | Asian Handicap | Who will win with a points handicap — e.g. `"Lakers -3.5"` vs `"Celtics +3.5"` |
| `2` | Under/Over | Will the total score be over or under a line — e.g. `"Over 2.5"` vs `"Under 2.5"` |

For spread and total markets, the `line` field contains the relevant value:

```json theme={null}
{
  "type": 2,
  "outcomeOneName": "Over 2.5",
  "outcomeTwoName": "Under 2.5",
  "line": 2.5
}
```

## Main lines and alternate lines

For spread and total markets, multiple lines are often available for the same fixture. For example, a soccer match might have totals at 1.5, 2.5, and 3.5 goals.

The `mainLine` field indicates whether a market is currently the primary line for its type:

```json theme={null}
{ "type": 2, "line": 1.5, "mainLine": false }   // alternate line
{ "type": 2, "line": 2.5, "mainLine": true }    // main line
{ "type": 2, "line": 3.5, "mainLine": false }   // alternate line
```

The main line is the primary, most balanced line where both outcomes are closest to having an equal probability (50/50) — it shifts as the market moves.

A market type either carries a line or it does not — see [Market types](/api-reference/market-types), the **Has lines** column.

| | Line-bearing type (e.g. 28, Under/Over) | Line-free type (e.g. 274, Outright Winner) |
| - | - | - |
| Markets per event | one per line value | one |
| `line` | present, a number like `48.5` | **absent entirely** |
| `mainLine` | present on the ones that have been computed | **absent entirely** |

## `isQuarterLineMarket`

`isQuarterLineMarket` is present on **every** market row, as a boolean.

```json theme={null}
"isQuarterLineMarket": false
```

A quarter-line market settles as a half-win or half-loss. See [Quarter-line markets](/developers/quarter-line-markets).

## Market status

The `status` field tells you whether a market is currently open for trading.

| Status | Description |
| - | - |
| `ACTIVE` | The market is open — orders can be posted and matched |
| `INACTIVE` | The market is closed for trading |

## Live markets

Many markets support in-play (live) betting. The `liveEnabled` field indicates whether a market is available for live betting:

```json theme={null}
{ "liveEnabled": true }
```

Live markets remain active while the game is in progress. Odds and liquidity can move quickly on live markets, so be mindful of [taking liquidity](/developers/taking-liquidity) and [betting delays](/developers/betting-delays) on live markets.

## Full market object

Here's a complete market object for reference:

```json theme={null}
{
  "status": "ACTIVE",
  "marketHash": "0x1c8f12c7e05760295e95ea83666e0e199c9ba07b571631d695f8a91325bcbc83",
  "outcomeOneName": "Paris Saint Germain -2",
  "outcomeTwoName": "Chelsea +2",
  "outcomeVoidName": "NO_GAME_OR_EVEN",
  "teamOneName": "Paris Saint Germain",
  "teamTwoName": "Chelsea",
  "type": 3,
  "gameTime": 1773259200,
  "line": -2,
  "sportXeventId": "L18148217",
  "liveEnabled": true,
  "sportLabel": "Soccer",
  "sportId": 5,
  "leagueId": 30,
  "leagueLabel": "Champions League_UEFA",
  "group1": "Champions League",
  "group2": "UEFA",
  "chainVersion": "SXR",
  "participantOneId": 839,
  "participantTwoId": 4,
  "mainLine": false,
  "isQuarterLineMarket": false,
  "__type": "Market"
}
```

| Field | Description |
| - | - |
| `status` | `ACTIVE` or `INACTIVE` |
| `marketHash` | The unique identifier for the market |
| `outcomeOneName` | Outcome one for this market |
| `outcomeTwoName` | Outcome two for this market |
| `outcomeVoidName` | Outcome void for this market |
| `teamOneName` | The name of the scheduled home team/player |
| `teamTwoName` | The name of the scheduled away team/player |
| `type` | The type of the market |
| `gameTime` | The UNIX timestamp of the game |
| `line` | The line of the market. Only applicable to markets with a line |
| `sportXeventId` | The unique event ID for this market |
| `liveEnabled` | Whether or not this match is available for live betting |
| `sportLabel` | The name of the sport for this market |
| `sportId` | The ID of the sport for this market |
| `leagueId` | The league ID for this market |
| `leagueLabel` | The name of the league for this market |
| `mainLine` | Whether this market is currently the main line. Not present on markets without multiple lines |
| `isQuarterLineMarket` | Whether the exchange produced this market by splitting a line-bearing market into quarter lines |
| `group1` | Indicator to the client of how to display this market |
| `group2` | Indicator to the client of how to display this market |

A settled market additionally carries `reportedDate`, `outcome`, `teamOneScore` and `teamTwoScore` — see [Find markets](/api-reference/get-markets-find). A parlay market carries `legs` — see [Parlays](/developers/parlays).

## Related

<CardGroup cols={2}>
  <Card title="Overview" icon="sitemap" href="/developers/markets-overview">
    The sport → league → event → market hierarchy, and how to traverse it.
  </Card>

  <Card title="Market types" icon="table-list" href="/api-reference/market-types">
    Every `type` id, its bet group, and whether it carries a line.
  </Card>

  <Card title="Fetching markets" icon="list" href="/developers/fetch-markets">
    Filters, pagination, and querying by sport, league, event or hash.
  </Card>

  <Card title="Quarter-line markets" icon="ruler-combined" href="/developers/quarter-line-markets">
    What `isQuarterLineMarket` changes about settlement.
  </Card>
</CardGroup>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.