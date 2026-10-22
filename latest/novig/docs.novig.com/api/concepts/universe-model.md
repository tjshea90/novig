> ## Documentation Index
> Fetch the complete documentation index at: https://docs.novig.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Universe model

> Sports, leagues, events, markets, and outcomes, and how they nest.

Everything you can trade sits in one hierarchy.
A sport and a league classify an event.
An event holds markets, and a market holds outcomes.
An order names one outcome.

SportFootball

LeagueNFL

EventChiefs at Bills, Sun 20:20

Moneyline
Market

OutcomeChiefsOutcomeBills

Total points
Market

OutcomeOver 48.5OutcomeUnder 48.5

A dashed line classifies an event. A solid line shows what it holds.

| Part | What it is | Holds | An order names it |
| - | - | - | - |
| Sport | A category, like football | Leagues | No |
| League | A group within one sport | Events | No |
| Event | One game | A start time and status | No |
| Market | One question about the event | Outcomes and a status | No |
| Outcome | One answer | Its own order book | Yes |

Sports and leagues are fixed lists.
They say what an event is, never when or whether it happens.
Read them from `GET /v3/types/sports` and `GET /v3/types/leagues`.

## Event

An event is one game, and its markets settle on it.

| Field | Meaning |
| - | - |
| `sport` `league` | Names from the two fixed lists |
| `status` | Where the event is in its [lifecycle](/api/concepts/event-lifecycle.md) |
| `startsTs` | Scheduled start, in Unix milliseconds |
| `description` | Display text. Don't parse it |

## Market

A market is one question about its event, and its outcomes are the answers.
`marketType` names the question. Read the full list from `GET /v3/types/markets`.

| `marketType` | Outcomes |
| - | - |
| `MONEY` | Home, Away |
| `SPREAD` | Home, Away |
| `TOTAL` | Over, Under |
| `TEAM_TOTAL` | Over, Under |
| `MONEYLINE_3_WAY_WIN` | Yes, No |
| `MONEYLINE_3_WAY_DRAW` | Yes, No |

A player prop has Over and Under, or Yes and No.

### Three-way moneylines

A game that can end in a draw, like most soccer games, has three possible results.
It gets three separate Yes / No markets, not one market with three outcomes.

| `marketType` | Question |
| - | - |
| `MONEYLINE_3_WAY_WIN`, home team | Does the home team win? |
| `MONEYLINE_3_WAY_WIN`, away team | Does the away team win? |
| `MONEYLINE_3_WAY_DRAW` | Is the game a draw? |

> [!WARNING]
>
> **`No` isn't the other team.** It means any other result: the other team wins, or the game is a draw. Use each result's own market.

## Outcome

An outcome is one answer to its market's question.
An order names an outcome, never a market.
Each outcome has its own order book.

```json theme={"dark"}
{
  "outcomes": [
    {
      "outcomeId": "3f2504e0-…",
      "name": "Kansas City Chiefs",
      "status": "TBD"
    },
    {
      "outcomeId": "5a1c9e73-…",
      "name": "Buffalo Bills",
      "status": "TBD"
    }
  ]
}
```

| Field | Meaning |
| - | - |
| `outcomeId` | What an order names. Fixed for the life of the market |
| `name` | Display text. Don't parse it |
| `status` | `TBD`, `WIN`, `LOSS`, `PUSH`, or a price |

A market that settles at fair market value sets each outcome's `status` to a decimal price.

> [!WARNING]
>
> **The `outcomes` array has no fixed order.** Don't read a side from its position. Match each outcome by its `outcomeId`.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.