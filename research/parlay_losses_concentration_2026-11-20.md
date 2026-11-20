# Why the parlay book lost, and what makes it profitable (MK56, tj Gmail, 2026-11-20 17:50 UTC)

Tj, 17:34 UTC: "The parlay system is losing money miserably and taking identical bets. Continue to study and research on how to make it profitable."
Data: the hub's `parlays.json` on `cloud-feed` (27 fills: 20 settled, 7 open), read 17:45 UTC.

## 1. What the record is (20 settled parlays, 11-18 to 11-20)
- Realized -$63.42 on $95.47 wagered ($81.53 of collateral at risk). The buyer won 19 of 20.
- If our fair had been exactly right, the same 20 fills were worth +$2.48 (price over fair, about 0.5-1 point at the old floors): the margin was never large enough to pay for one bad cluster.
- 19 buyer wins at a mean fair of .553 would be 11.1 expected; the chance of 19 or more if the 20 were independent and the fair true is 1 in 9,000 (Monte Carlo, 200,000 draws).
- They are not independent. Joining parlays that share an outcome gives **8 clusters, and 13 of the 20 parlays are ONE cluster**: Houston Texans moneyline in six parlays (11-19 06:59, 10:43, 10:45, 12:25, 12:29, 12:56) joined through Shelton/Zverev (ATP), the Spurs / Trail Blazers, Washington / Florida / High Point / California to one connected block. One favourite winning is one event, and it paid 13 buyers. Eight independent bets, all of them heavy favourites (legs .70-.92), cannot tell a fair that is a point or two low from bad luck; the favourite-longshot bias (favourites win more often than their price) says the fair of an all-favourite parlay is low, which is why the rules already decline legs of .8 and up, 3+ legs all at .5 or more, and parlays over a .6 win chance.
- Three of the losses (11-19 05:01-06:09, $14.06) carry no fair in the hub: they were filled by an older engine version before the fair was journalled; they are in the total, not in any rate above.

## 2. "Identical bets": what happened
Same legs, filled twice: Magic ML + Thunder -4.5 at 13:56:51 (.503, $9.41 collateral) and 14:03:55 (.499, $9.55); the Texans leg in six parlays; 11-19 10:40 and 11:04 both Shelton + Zverev. MK54 (`walletLegShare` .25) bound only collateral, not the count: two $9 fills passed it. A buyer repeating a parlay is one bet taken twice, and the second copy is worth no diversification at all.

## 3. What changed (MK56)
- `maxSameParlay` 1: the same set of legs, in any order, is never held or quoted twice (open parlays from Novig's ledger AND quotes still out).
- `maxParlaysPerOutcome` 2: one outcome (e.g. a team's moneyline) is in at most two open parlays or quotes.
- Revert: `python3 tools/rfq_set.py maxSameParlay=0 maxParlaysPerOutcome=0`. Tests: `RfqMakerTest`.
The cost is volume on popular legs only: the declines name themselves ("the same parlay is already held or quoted", "a leg is already in N open parlays or quotes") so the journal shows how many RFQs they cost.

## 4. How it can profit (the plan, in order of evidence)
1. **Margin cannot be the source.** At price = fair + 0.1 point a fill earns about 0.2% of the stake (a $9.5 stake: $0.04) with a standard deviation near $10. Half the auctions we enter that executed are won by someone quoting at or under our fair, so the market's best quote sits at our fair: there is no room to raise the margin across the board.
2. **The edge is in classes where the market's executed price is above our fair AND buyers do not beat our fair.** The graded study of the market's 6,885 executed parlays found these for makers at the clearing price: 2 legs priced .15-.45 (+4.0%), a longshot leg (+2.1%), in-play same-game pairs (the market pays +1.4 point over our fair). Everything else is break-even or negative. So: keep the guards that decline the negative classes, and spend collateral first on the positive ones. A class margin (price above fair) should be set from `executed price - our fair` per class in the journals, minus a winner's-curse allowance from the buyer win rate per class; wait for 2+ graded slates of OUR OWN fills per class before moving it (rfq-pnl-study after 05:00 UTC 11-21).
3. **Diversify, because the variance is the problem.** The cash-time limit (each fill locks its collateral until the game ends; $83 of $125 locked now) means few independent bets: MK56 spreads them. A next step if wanted: a cap on collateral per GAME (all legs of one game together), not only per outcome.
4. **Judge only on independent settled fills.** A cluster is one observation; judge a rule on 20+ independent settled fills (count clusters, not parlays), never on the parlay count.
5. **Wallet size:** collateral = stake x (1-P)/P; a $125 wallet cannot back parlays under about 8% win chance and covers only 5-8 open parlays at once. More cash raises the number of independent bets, which is what turns a tiny positive edge into a measurable one; that is Tj's decision.

## 5. Watch
- After the MK56 deploy: count the declines with the two new reasons per hour against quotes sent; if over 30% of quotable RFQs are declined by them, raise `maxParlaysPerOutcome` to 3.
- Today's 7 open fills (4 of them NBA/NCAAB same-game pairs at a +0.1 point margin) are the first sample under the new rules; the NBA totals pair settles about 04:00 UTC 11-21.
