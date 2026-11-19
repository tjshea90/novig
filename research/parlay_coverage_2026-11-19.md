# Why the maker cannot price more: fair-odds coverage (MK18, Tj 2026-11-19 ~22:45 UTC: "I think it isn't finding enough fair odds")

Measured on the engine's journals of 11-19 (8,588 parlay trades, $231,060 staked; the 20:56-22:22 UTC jobs, which carry each leg's class) and a fresh public catalog.

**53% of the traded stake ($123,569) is on parlays the engine cannot price** (it has no fair line for at least one leg). Stake-weighted, the blocking legs (league / market, whether the scan holds the outcome at all):

| blocking leg | legs | stake behind them |
|--|--:|--:|
| ATP moneyline, not in the scan | 822 | $17,719 |
| NBA player points, not in the scan / scanned without a fair | 2,014 / 426 | $7,809 / $1,734 |
| NFL rushing yards (scanned / not / "protected") | 91 / 202 / 388 | $4,562 / $3,921 / $1,241 |
| NFL receptions, scanned without a fair | 328 | $4,173 |
| NCAAB spread / moneyline / total, not in the scan | 205 / 219 / 116 | $3,520 / $3,297 / $3,199 |
| NBA rebounds, three-pointers, PRA, assists, NHL shots (scanned, no fair) | ~2,000 | ~$12,600 |
| NFL touchdowns, receiving yards (protected and plain), longest rush, passing yards | ~2,900 | ~$10,000 |

Parlays held back by exactly ONE leg (fix that kind of leg and they price): NFL rushing yards 71 parlays $7,339; NBA points 81 $2,388; NCAAB moneyline 81 $2,091; ATP moneyline 37 $2,060; NCAAB total 32 $2,019; NCAAB spread 72 $1,996.

**Why the supply of fair odds is thin right now** (the engine's own API checks, 22:40 UTC): PropLine (Streaming-lite) and the three exchange feeds (Kalshi, SX Bet, Polymarket) carry the scan. SharpAPI has dropped to its free tier ("12 requests a minute, up to 2 books, no Pinnacle on this tier": 0 games matched), ParlayAPI's check on the screen shows the first of its ten keys (a depleted free one, 0 of 1,000); the Business key (1,000,000 a month, 656,000 left) was held back by a usage-meter bug fixed in MK19 (see CHANGES.md), OddsPapi free has used 254 of 250 requests, The Odds API has 196 of 500 credits left, and SportsGameOdds and BoltOdds are switched off (Tj switched BoltOdds off on 2026-10-24; the SGO Pro trial was never renewed). Tennis outside PropLine's list, low-major college basketball and most player-prop markets have no other source.

**What would add coverage** (each is a switch or a plan, not a rule): (1) SGO Pro back on (tennis, props, college basketball) if its plan is live: Settings > APIs > SportsGameOdds > Test key; (2) BoltOdds (props sharp consensus) likewise; (3) SharpAPI's Sharp plan (Pinnacle, Circa, 25 books); (4) ParlayAPI credits. In code: Novig's own mid as a fair for liquid markets with a premium (a new source, SHADOW first), and MK11's ladder for props. None of these changes a rule that moves money.
