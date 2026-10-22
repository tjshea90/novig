> ## Documentation Index
> Fetch the complete documentation index at: https://docs.novig.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Futures Markets

> Integrating Novig futures markets into your platform

## Overview

Novig offers futures markets for NBA, MLB, NHL, WNBA, Tennis, and special events like the Olympics.
A futures market settles on a season or a tournament, not a single game.

You read futures from the same REST catalog as game markets.
[Odds Screens](/affiliates/odds-screens.md) shows how to get a key, sign a request, and turn an order book into prices.

## Tell futures apart from game markets

The catalog lists futures and game markets together.
A market's `marketType` says which one it is.

| `marketType` | Kind |
| - | - |
| `MONEY` `SPREAD` `TOTAL` | Game line |
| `CHAMPIONSHIP_WINNER` `SERIES_WINNER` `FINALS_MVP` | Futures |
| `SUPER_BOWL_WINNER` `WORLD_SERIES_WINNER` `STANLEY_CUP_WINNER` | Futures |

Read the full list of market types from `GET /v3/types/markets`.

> [!WARNING]
>
> Filter on `marketType` when you list a league's markets, so futures don't show up next to your game lines.

## List a league's futures

Pass the futures market types you support in `marketType`, comma-separated.

**Request**

```bash theme={"dark"}
curl -s "$NOVIG_HOST/v3/catalog/markets?league=MLB&marketType=WORLD_SERIES_WINNER,AL_MVP" \
  -H "Novig-Key-Id: $TRADING_KEY_ID" \
  -H "Novig-Timestamp: $TS" \
  -H "Novig-Signature: $SIG"
```

**200**

```json theme={"dark"}
{
"items": [
{
  "marketId": "9b2e41c7-…",
  "eventId": "c4d81f20-…",
  "marketType": "WORLD_SERIES_WINNER",
  "status": "OPEN",
  "description": "Los Angeles Dodgers WORLD_SERIES_WINNER",
  "startsTs": 1781136000000,
  "fee": {
    "coefficient": "0.06",
    "makerCredit": "0.5",
    "charged": "ALWAYS"
  },
  "outcomes": [
    {
      "outcomeId": "e7a03b55-…",
      "name": "Yes",
      "status": "TBD"
    },
    {
      "outcomeId": "18f6c2d9-…",
      "name": "No",
      "status": "TBD"
    }
  ]
}
],
"next": "eyJvIjoxNzgxMTM2MDAwMDAwfQ"
}
```

To list game lines only, pass the game market types instead, like `marketType=MONEY,SPREAD,TOTAL`.

## How futures differ from game markets

* **A futures event never goes live.** It never reaches `OPEN_INGAME`, so you can show its prices until the market closes.
* **A futures market can carry a fee on every fill.** On an eligible league, `fee.charged` is `ALWAYS`, with a coefficient of `0.06` and a maker credit of `0.5`. See [Trading fees](/api/concepts/fees.md#which-schedule-a-market-is-on).
* **Some futures events have no markets yet.** These are placeholders, so skip an event with no markets.

## Futures market shapes

> **Team futures**
>
> One market per team, like "Dodgers to win the World Series", with Yes and No outcomes.

> **Player futures**
>
> One market per player, like "Nikola Jokic Finals MVP", with Yes and No outcomes.

> **Series futures**
>
> One market per series, with one outcome for each team.

> **Placeholder events**
>
> A futures event with no markets yet. Skip it until markets appear.

The market's `description` names the team or player, and each outcome's `name` names the answer.
Build on `marketId` and `outcomeId`, not on `description`, because the text can change.

## Read a futures order book

A futures order book works like a game market's.
A bid on one outcome shows as available liquidity on the opposite outcome.

| Scenario | Interpretation |
| - | - |
| BID on NO at 0.36, qty 45000 | Someone risks \$162 to win \$288 |
| Available liquidity | Shows on YES at 0.64, \$288 to win \$162 |

[Odds Screens](/affiliates/odds-screens.md) shows how to turn the book into displayable prices.

## Special events

Special events like the Olympics use their own leagues, such as `Olympics Hockey Men`.
Read the current league names from `GET /v3/types/leagues`.

An Olympics league has game markets for each matchup, like `MONEY`, `SPREAD` and `TOTAL`, and a `CHAMPIONSHIP_WINNER` futures market for each country.

## Best practices

**1. Filter explicitly**

Always pass `marketType`, so futures and game lines stay separate.

**2. Handle empty events**

Skip a futures event that has no markets yet.

**3. Cache IDs**

Cache each market's `marketId` and `outcomeId`s, and match on them, not on `description`.

**4. Watch for new leagues**

Re-read `GET /v3/types/leagues` to pick up new leagues and special events.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.