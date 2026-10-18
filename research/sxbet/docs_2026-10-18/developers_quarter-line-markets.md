> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Quarter-line markets

> How quarter-line markets work on SX Bet.

Soccer markets on SX Bet support quarter lines — Asian handicap and Asian totals in `.25` increments (for example `-1.25`, `+0.75`, `Over 2.75`).

<Accordion title="Example quarter-line market">
  ```json theme={null}
  {
    "status": "success",
    "data": [
      {
        "status": "ACTIVE",
        "marketHash": "0x59b0e45f79d4b6eedaed199011b8bf72a8b4342e3b410457cc42abcf05d3592d",
        "outcomeOneName": "Brighton and Hove Albion -1.25",
        "outcomeTwoName": "Wolverhampton Wanderers +1.25",
        "outcomeVoidName": "NO_GAME_OR_EVEN",
        "teamOneName": "Brighton and Hove Albion",
        "teamTwoName": "Wolverhampton Wanderers",
        "type": 3,
        "gameTime": 1778335200,
        "line": -1.25,
        "sportXeventId": "L18724593",
        "liveEnabled": false,
        "sportLabel": "Soccer",
        "sportId": 5,
        "leagueId": 29,
        "leagueLabel": "English Premier League",
        "group1": "English Premier League",
        "chainVersion": "SXR",
        "participantOneId": 1002,
        "participantTwoId": 998,
        "mainLine": false,
        "sxTeamOneId": 100,
        "sxTeamTwoId": 97,
        "__type": "Market"
      }
    ]
  }
  ```
</Accordion>

## How quarter lines work

The exchange splits a bet on a quarter-line market into two trades, on the surrounding whole and half lines. Each trade takes half the stake, at the same odds. A 50 USDC bet on `Brighton -1.25 @ 1.9` is recorded as:

* 25 USDC on `Brighton -1.0 @ 1.9`
* 25 USDC on `Brighton -1.5 @ 1.9`

The two child trades settle independently against the same final score. That reproduces the quarter-line outcomes a single binary market cannot express:

| Result | `Brighton -1.0` | `Brighton -1.5` | Net payout on 50 USDC |
| - | - | - | - |
| Brighton wins by 2+ | Win | Win | 47.50 USDC (full win) |
| Brighton wins by 1 | Push | Loss | 25 USDC (half loss) |
| Brighton draws or loses | Loss | Loss | 0 USDC (full loss) |

Orderbooks, posting orders, and taking liquidity on whole and half lines work as usual. The split happens only when you place a bet against a quarter-line `marketHash`.

## How quarter-line bets appear in the API

One bet on a quarter-line market produces **one bet row** and **two fill rows** (one per leg). The hashes
point at different markets:

| Grain | `marketHash` | Extra field |
| - | - | - |
| [Bet](/developers/which-grain#bets) | The **parent** — the `.25` / `.75` market you ordered against | none |
| [Fill](/developers/which-grain#fills) | A **leg** — one of the surrounding whole/half lines | `quarterlineMarketHash` = the parent |

### Settlement

Each leg settles independently against the same final score, and the parent's `outcome` is derived
from the pair:

| Leg one | Leg two | Parent `outcome` |
| - | - | - |
| Won | Won | `1` |
| Lost | Lost | `2` |
| Void | Void | `0` |
| Won | Void *(or void + won)* | **`1`** |
| Lost | Void *(or void + lost)* | **`2`** |


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.