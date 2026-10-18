> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Market types

> Reference of market types on SX Bet.

## The table

| ID | Name | Has lines | Description | Bet group | Quarter-line eligible |
| - | - | - | - | - | - |
| 1 | 1X2 | false | Who will win the game (1X2) | `1X2` | false |
| 2 | Under/Over | true | Will the score be under/over a specific line | `game-lines` | true |
| 3 | Asian Handicap | true | Who will win the game with handicap (no draw) | `game-lines` | true |
| 28 | Under/Over Including Overtime | true | Will the score including overtime be over/under a specific line | `game-lines` | true |
| 29 | Under/Over Rounds | true | Will the number of rounds in the match be under/over a specific line | `game-lines` | true |
| 52 | 12 | false | Who will win the game | `game-lines` | false |
| 166 | Under/Over Games | true | Number of games will be under/over a specific line | `game-lines` | true |
| 201 | Asian Handicap Games | true | Who will win more games with handicap (no draw) | `game-lines` | true |
| 226 | 12 Including Overtime | false | Who will win the game including overtime (no draw) | `game-lines` | false |
| 342 | Asian Handicap Including Overtime | true | Who will win the game with handicap (no draw) including Overtime | `game-lines` | true |
| 835 | Asian Under/Over | true | Will the score be under/over specific asian line | `game-lines` | true |
| 1536 | Under/Over Maps | true | Will the number of maps be under/over a specific line | `game-lines` | true |
| 88 | To Qualify | false | Which team will qualify | `to-qualify` | false |
| 274 | Outright Winner | false | Winner of a tournament, not a single match | `outright-winner` | false |
| 165 | Set Total | true | Number of sets will be under/over a specific line | `set-betting` | false |
| 866 | Set Spread | true | Which team/player will win more sets with handicap | `set-betting` | false |
| 53 | Asian Handicap Halftime | true | Who will win the 1st half with handicap (no draw) | `first-half-lines` | false |
| 63 | 12 Halftime | false | Who will win the 1st half (no draw) | `first-half-lines` | false |
| 77 | Under/Over Halftime | true | Will the score in the 1st half be under/over a specific line | `first-half-lines` | false |
| 21 | Under/Over First Period | true | Will the score in the 1st period be under/over a specific line | `first-period-lines` | false |
| 64 | Asian Handicap First Period | true | Who will win the 1st period with handicap (no draw) | `first-period-lines` | false |
| 202 | First Period Winner Home/Away | false | Who will win the 1st Period Home/Away | `first-period-lines` | false |
| 45 | Under/Over Second Period | true | Will the score in the 2nd period be under/over a specific line | `second-period-lines` | false |
| 65 | Asian Handicap Second Period | true | Who will win the 2nd period with handicap (no draw) | `second-period-lines` | false |
| 203 | Second Period Winner Home/Away | false | Who will win the 2nd Period Home/Away | `second-period-lines` | false |
| 46 | Under/Over Third Period | true | Will the score in the 3rd period be under/over a specific line | `third-period-lines` | false |
| 66 | Asian Handicap Third Period | true | Who will win the 3rd period with handicap (no draw) | `third-period-lines` | false |
| 204 | Third Period Winner Home/Away | false | Who will win the 3rd Period Home/Away | `third-period-lines` | false |
| 205 | Fourth Period Winner Home/Away | false | Who will win the 4th Period Home/Away | `fourth-period-lines` | false |
| 236 | 1st 5 Innings Under/Over | true | Will the score in the 1st five innings be under/over a specific line | `first-five-innings` | false |
| 281 | 1st 5 Innings Asian Handicap | true | Who will win the 1st five innings with handicap (no draw) | `first-five-innings` | false |
| 1618 | 1st 5 Innings Winner-12 | false | Who will win in the 1st five innings | `first-five-innings` | false |
| 995 | Anytime Touchdown Scorer | false | Will the player score a touchdown at any point in the game (Yes/No) | `player-props` | false |
| 17 | Both Teams To Score | false | Will both teams score | — | false |
| 41 | 1st Period Winner | false | Who will win the 1st period | — | false |
| 42 | 2nd Period Winner | false | Who will win the 2nd period | — | false |

**Quarter-line eligible** marks the types that can be split into quarter lines: 2, 3, 28. See
[Quarter-line markets](/developers/quarter-line-markets).

**Player props** (e.g. type 995) have one market per player. On the
[market object](/developers/markets-and-events#full-market-object), the player is named in
`outcomeOneName` / `outcomeTwoName` (`"Kalif Raymond - Yes"` / `"Kalif Raymond - No"`) and in
`marketMeta`, a JSON-encoded string whose `player` object holds the player's `name`, `id` and
`participantId`. `participantId` matches the market's `participantOneId` or `participantTwoId`,
identifying the player's team.

## Bet groups

A **bet group** is a set of market types presented together. It is the scope that matters for two
things: the `betGroup` filter on
[`GET /markets/active`](/api-reference/get-markets-active), and the market grouping used by
[capital efficiency](/developers/capital-efficiency) — positions offset within a group, not across
groups.

| Bet group | Label | Market types |
| - | - | - |
| `game-lines` | Game Lines | 2, 3, 28, 29, 52, 166, 201, 226, 342, 835, 1536 |
| `outright-winner` | Outright Winner | 274 |
| `set-betting` | Set Betting | 165, 866 |
| `first-half-lines` | First Half Lines | 53, 63, 77 |
| `first-period-lines` | First Period Lines | 21, 64, 202 |
| `second-period-lines` | Second Period Lines | 45, 65, 203 |
| `third-period-lines` | Third Period Lines | 46, 66, 204 |
| `fourth-period-lines` | Fourth Period Lines | 205 |
| `first-five-innings` | First Five Innings | 236, 281, 1618 |
| `to-qualify` | To Qualify | 88 |
| `1X2` | 1X2 | 1 |
| `player-props` | Player Props | 995 |


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.