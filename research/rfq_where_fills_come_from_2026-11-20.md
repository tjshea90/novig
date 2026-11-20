# Where can a parlay maker with a $120 wallet get fills, and which are worth taking? (MK37, 2026-11-20)

**Why.** Tj (2026-11-20 05:35 UTC): "Earlier the engine was pricing and quoting a lot, and getting fills. Something happened where now it seems like it can't price any parlays." Mandate before that: fills AND profit, parlays first.

**Method.** The engine's `rfq-*` journals (Releases `cloud-archive-*`): CREATED rows (our decision, reason, fair, legs), EXECUTED rows (the market's price, our fair), PROBE rows (the best quote of the other makers on a sampled RFQ, a real 3-second auction on Novig's keyless route), QUOTE_SENT rows. Joined by RFQ id. A quote "would win" an executed trade when our price (fair + margin) <= the executed price.

## 1. Morning against now: it can still price; it quotes the same share and fewer RFQs fit

| | 11-19 06:51-14:32 UTC (the live morning) | 11-20 04:11-04:56 UTC |
|---|---|---|
| RFQs seen | 278,403 | 72,737 |
| quoted | 2,713 (0.97%) | 411 (0.57%) |
| pace | ~350 quotes/hour | 240-550 quotes/hour |
| fills | 16 (buyers won 15, 31 of 32 legs won against 23.2 expected) | 0 (expected 2-3 at the morning's 0.6% quote-to-fill rate: not yet unusual) |
| top declines | 57% two legs in one game (unmodelled), 27% caps allow a smaller stake | 30% two legs in one game (unmodelled), 22% two player props of one game (modelled, shadow), 18% a leg too likely to win, 14% cash is short |

So the engine did not lose the ability to price. What changed: (a) the parlays that produced the morning fills were favourite-heavy and lost 15 of 16, so on the graded study (`rfq_maker_pnl_2026-11-20.md`: 3+ legs all >= 50% -20.6% of pot, a leg >= 80% -10.8%) they are now declined (leg cap, all-favourite rule); (b) the evening flow is mostly in-play, which needs the live book; (c) every engine reset cold-starts the fair for 10-20 minutes (8 resets that day).

## 2. What actually trades (EXECUTED rows with our fair, 4 jobs 03:10-05:03 UTC)

`win@0.5` = the share of those trades a quote at our fair + 0.5 point would have won; `gap` = the market's price minus our fair, points of pot.

| class | trades | win@0.5 | median gap | wager |
|---|--:|--:|--:|--:|
| pre, game lines, 4+ legs | 629 | 30% | +0.33 | $25.1k |
| LIVE, game lines (different games), 2 legs | 425 | 29% | +0.17 | $29.7k |
| LIVE, game lines, 4+ legs | 307 | 32% | +0.27 | $10.3k |
| LIVE, game lines, 3 legs | 241 | 39% | +0.25 | $9.9k |
| pre, 4+ legs with a prop | 184 | 65% | +1.12 | $5.7k |
| pre, game lines, 2 legs | 149 | 31% | +0.15 | $7.2k |
| pre, game lines, 3 legs | 119 | 28% | +0.15 | $11.2k |
| **LIVE, same game, 2 legs** | 91 | **66%** | **+1.42** | $4.6k |
| pre, same game, 2 legs | 70 | 54% | +0.73 | $4.4k |
| pre, same game, 3 legs | 64 | 23% | -0.22 | $3.0k |
| pre, same game, 4+ | 51 | 24% | +0.24 | $1.0k |
| pre, 2 legs with a prop | 49 | 57% | +1.24 | $2.8k |
| LIVE, same game, 4+ | 49 | 61% | +0.91 | $1.6k |

Reading: the market is efficient on plain game-line parlays (gap +0.15 to +0.3 points: our floors of ~0.5 lose 70% of them, and a thinner margin buys fills with no edge, which the graded study says is -2.6% of pot to makers at the clearing price). The classes where the market pays MORE than the plain product are the same-game ones (the market prices a side and the total about 3% over independent, `research/novig_parlay_maker_2026-11-19.md`; graded 11-19: makers +8.0% of pot on 651 same-game pairs, SPREAD+TOTAL +10.9%).

## 3. Probes (the other makers' best quote on sampled RFQs, 2,136 priced)

Over all classes the market's best quote is above our +0.5 point floor 54% of the time; by class it is above it in 81% of 2-leg low-chance game-line parlays (+1.24 median), 80% of 2-leg low-chance same-game ones (+2.10), 74% of 4-leg high-chance (+1.40), and 85-100% of same-game prop parlays (+1.4 to +10 over our premium-included model). Probes overstate what trades: only RFQs with a tight best quote trade (executed same-game 2-prop pairs: the market paid a median -1.55 points UNDER our premium-included fair, 35% would have been won), so a class is judged on section 2, not on the probes.

## 4. What was built from it

- **MK37:** in-play same-game pairs (moneyline or spread with the total, 1,500 RFQs in 45 minutes, 27 a minute: five times our whole quote rate) were declined only because live legs carried no kind/side/line for the copula. `LiveFair.structure` reads them; the pricer is unchanged. On the executed 91 pairs a quote at the model's price wins 66%.
- Not built (no evidence yet): prop parlays (55-65% would-win at +1 point over a fair that already carries the 20% premium per prop leg, but nothing grades props: needs a player-stat grader before `propPairsLive`), live game lines of different games (29% would-win, +0.17 gap, the buyer's 6-29 s option on a live quote makes a thin margin negative).
