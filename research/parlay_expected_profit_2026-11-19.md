# Parlay maker: what it should earn when money goes in (MK14, Tj 2026-11-19)

Tj (verbatim): "Make sure it can price as much as possible. Make sure it is within the api limits and docs. Tune it for volume and profitability. Log it and tell me how well it is running and expected
profitability for when i add money to it." The maker is in SHADOW (paper); 19 live quotes were filled on 11-18/19 before it went back to paper. Everything below is measured on the engine's own journals
(`rfq-<day>` in the `cloud-archive-*` Releases) unless it says otherwise; nothing here is a promise. Tools: `tools/rfq_summary.py`, `tools/rfq_market.py`, `tools/rfq_outcomes.py` (new), `rfq-market.yml`.

## 1. The size of the market (what is there to win)

* Novig parlay RFQs: about 68,000 an hour at midday; 4,800 trades an hour at the 21-22 UTC peak, 500-900 overnight: about **43,000 trades and $1.1 million a day** (11-18: 43,790 trades in 26 h).
* The engine can price (has a fair for every leg, rules pass) **28% of RFQs and 30% of trades** after a warm-up of about an hour (a fresh job starts near 13%: avoid resets); on the 17:10-18:31 window 758 of 4,842
  trades were quoteable by the rule and our rule's price would have won **123 (16%)**.
* The market pays parlays: users hit **21.8%** of the 2,025 graded parlays and paid an average price of **24.3%** (the market's best quote), so the makers as a group keep about 2.5 points of every dollar of pot.

## 2. Is our fair right? (graded from ESPN final scores, `tools/rfq_outcomes.py`)

On 2,025 parlays we had a fair for and whose legs are moneylines, spreads and totals of NFL/NBA/NHL/NCAAB/NCAAF (the rest are props, or games ESPN had not finished):

| | hit rate | mean fair | mean price traded |
|--|--:|--:|--:|
| all parlays | 21.8% (SE 0.9, understated: parlays share legs) | 23.7% | 24.3% |
| 579 distinct legs | 58.2% (SE 2.0) | 59.2% | |

Leg-level calibration by bucket is within noise everywhere (45-55%: 48.8 vs 50.5; 55-70%: 57.0 vs 62.0; 70-85%: 82.0 vs 76.3). Our fair is **not biased high** on game lines; at parlay level the hit rate sits
1.9 points UNDER the fair, which favours the maker (it earns price minus the TRUE chance). Not yet tested, because the games are not final: the trades our price would have WON (adverse selection: a maker that
only wins the trades the market over-prices). `rfq-market.yml` now runs the grader every 6 hours and writes `OUTCOMES.md` to the `rfq-market` Release; the legs of every quote are journaled from the next engine job
(MK14), so no catalog is needed. Read the "trades our rule's price would have WON" line when it has 300+ graded trades.

## 3. The capital is the scarce thing, so the rule asks a return per day (`liveMinReturn`)

The quoteable trades the rule would win needed **about $1,100 of new collateral an hour held 18 hours on average (median 11)**, against $80 of cash: demand exceeds the wallet 20 times over. A trade that
ties capital up for little is a trade given away. Simulated on the 123 trades the rule would have won in the 1.35 h window (EV counted if the fair is true; stake capped at the quote's $10):

| hurdle (return on collateral a day) | wins | expected profit kept | collateral needed | return on capital per day |
|--:|--:|--:|--:|--:|
| 0.4% (the SHADOW rule) | 123 | 100% | 100% | 2.1% |
| 1% | 118 | 98% | 94% | 2.5% |
| 2% | 86 | 88% | 57% | 3.9% |
| **3% (new LIVE default)** | 59 | 77% | 37% | 4.7% |
| 5% | 26 | 52% | 16% | 9.5% |
| 8% | 7 | 30% | 6% | 36% (a handful of trades: mostly our fair being wrong) |

Chasing the top rate is adverse selection by construction (the trades the market pays most over our fair are where our fair is most likely wrong), so the default is 3%, not 8%. `RfqConfig.liveMinReturn`
(default 0.03; `rfq_set.py liveMinReturn=`): LIVE quotes at max(`minReturn`, this) in the rule and in the market model's cost of collateral; SHADOW keeps quoting at `minReturn` (so the paper evidence keeps its
volume) and journals `livePrice` / `liveWouldWin` (status `liveHurdleWouldWin`) so what LIVE would do is measured. Test: `RfqMakerTest.liveAsksTheCapitalHurdleAndShadowOnlyRecordsIt`.

## 4. What it should earn (expected value, then the honest range)

Steady state, per $100 of capital in use, **if our fair is true**: the 3% hurdle earns about **$4.70 a day** (2% hurdle $3.90, accept-everything $2.10), with no fee on it: Novig's docs say makers pay
none and earn no Maker Credit (api/fees; verify against the first live fills' `executions` rows). The opportunity at the 3% hurdle is about $160 of expected profit a day on roughly $3,600 of capital, so **returns per dollar
hold up to a few thousand dollars** and then fall as the hurdle has to come down.

| capital | fair true | half the edge real | no edge |
|--:|--:|--:|--:|
| $85 (the wallet) | +$4.0/day | +$2.0/day | $0 |
| $200 | +$9.4/day | +$4.7/day | $0 |
| $500 | +$23/day | +$12/day | $0 |
| $1,000 | +$47/day | +$24/day | $0 |

**The noise is much larger than the edge on a small bankroll.** One fill is a $9 stake at a 40% price: a standard deviation of about $11 against an expected +$0.2. With $85 about 9 fills settle a day:
expected +$2 to +$4, standard deviation about $30 a day. A month is +$60 to +$120 with a standard deviation near $165; about 100 settled fills (a few weeks) are needed before the result says anything.
So far: 19 fills (about $91 of user stake), expected +$2.4 if the fair is true, **3 settled and all 3 hit for the user (-$7.09)**, 16 still open: three of three user wins is a 17% event at these prices, i.e. luck
and not yet evidence. The ledger (`status.rfq.settled/makerWon/makerLost/realizedPnl`, and `EXEC_RESULT` rows in the journal from the next job) is the real test.

## 5. What could make the estimate wrong (watch these)

1. **Adverse selection** (won trades are the ones other makers priced higher): the OUTCOMES.md "would have WON" line is the test. Props are the worst case (our prop fair runs high: prop Overs lose at Novig),
   which is why parlays with a prop leg are quoted only at +20% a prop leg and are not in the LIVE rule's volume.
2. **Correlated losses**: the open parlays lean on the same games (6 of 13 live fills rode one game): `maxLegCollateral` ($30 per outcome) caps it in LIVE.
3. **Confirm rate**: Novig locks a maker under 99% for 5 minutes; a quote won is always confirmed (MK13), standing read 6 of 6 today.
4. **Fee/limits not yet seen live**: first live quotes must be checked against `GET /rfq/v3/maker` and `/executions` (MK13).

## 6. What to do when money goes in

1. Prefer a **dedicated maker subaccount** (docs: separate wallet and throttles; the management key opens and funds it: Tj's step) and send `NOVIG_MAKER_KEY_ID/PEM`. 2. Start with the config as it is
(`maxStake` 12, `maxOpenCollateral` 80, per-outcome cap 30, hurdle 3%) and `rfq_set.py mode=LIVE`. 3. After about 100 settled fills read `OUTCOMES.md` and the ledger; scale the caps only when the
realized/expected line is at or above half. 4. The day-loss stop (`dayLossStop` $20) stays.

## 7. Too easy to win: a cap on the parlay and on any one leg (MK16, Tj 2026-11-19 ~21:00 UTC)

Tj: "do not allow parlays that are too easy to win. For example, some parlays are over 90% chance of winning. Set a reasonable cap where parlays are not accepted by the system if they are too likely to win."
`maxFair` was 0.85 (no parlay the market traded ever reached it) and there was no limit on a single leg. Paper journals of 11-19 (769 quoteable trades, 132 our rule would have won), by how likely the parlay is to win by OUR fair:

| our fair for the parlay | quoteable trades | our price would have won | paper EV | collateral | mean edge |
|--:|--:|--:|--:|--:|--:|
| under 20% | 162 | 4% | $2.67 | $229 | 1.07 pts |
| 20-40% | 476 | 9% | $10.66 | $734 | 0.98 |
| 40-55% | 100 | 59% | $9.21 | $474 | 1.01 |
| 55-70% | 24 | 71% | $2.17 | $80 | 1.02 |
| 70-85% | 7 | **100%** | $0.73 | $12 | 1.34 |

The more likely the parlay, the more often our price beats the market's best quote (above 60%: 18 of 19, against 17% overall) and the smaller the edge is against our own error. A maker that wins almost every trade in a class is mis-pricing it (the winner's curse), and the leg-level grading agrees: our 70-85% legs won 82% of the time against a 76% fair (111 legs), the favourite-longshot bias again. Trades with a leg over 90% were won by our price 42% of the time (14 of 33), also against 17%.
Decision (class B in the sense of ROUTINE.md: two independent pieces of evidence, no money rule loosened, paper only): **`maxFair` 0.85 -> 0.60** and a new **`maxLegFair` 0.90**, both applied in SHADOW and LIVE with their own decline reasons ("the parlay is too likely to win (64% against the 60% cap)", "a leg is too likely to win (95% against the 90% cap)"), both steppers on the Maker page. Cost on paper: 2.5% of the quoteable trades and 8% of the expected profit for the parlay cap, 5% of the trades and about 14% of the expected profit for both together (the profit that goes is the part most likely to be our fair being low). Revert: `rfq_set.py maxFair=0.85 maxLegFair=1.0`. Test: `RfqMakerTest.aParlayTooLikelyToWinIsNeverQuotedAndNeitherIsOneWithANearCertainLeg`. `maxFair` takes effect in the running job at once (config); `maxLegFair` with the next job (the 6 h handover).

## 8. Tonight's options and the positive-EV pass (MK17, Tj 2026-11-19 ~21:35 UTC: "I'm about to put money into the wallet ... filter ... finish today ... longshot ... 25% or less ... within the amount in my wallet ... another pass to ensure that the accepted parlays are truly positive ev")

**Options built** (all `RfqConfig` fields, Maker & controls page, `rfq_set.py`): `onlyToday` (the last leg starts before today's slate ends: 5 am to 5 am Eastern, the Tracker's day); `longshotsOnly` + `longshotMaxChance` .25 (+ `longshotPremium` .03); `walletCollateral` (with `walletShare` .30, `walletLegShare` .50, `dayLossStopPct` .20): one parlay locks at most 30% of the wallet (cash + the collateral held), one outcome 50%, the open total is the cash that is there (Novig drops a quote it cannot back, so a quote is sized to the balance), the loss stop is the larger of $20 and 20% of the wallet. A same-day parlay is charged the 3%/day capital hurdle for its real hold (a quarter of a day at least), not a full day (`holdFloorDays`). The "Tonight" button sets the three at once.

**Positive-EV audit.** What a quote is made of, and what was wrong or thin:
1. price >= fair + max(0.7 pt per extra leg, 1.5% relative, the capital hurdle) rounded UP to Novig's thousandth: positive on our fair. **Thin:** the 1.5% relative margin is below one standard error of the fair for any parlay of two or more legs (2% x sqrt(2) = 2.8%), and the trades we win are the ones where our fair is lowest against the market's. **Fix:** a LIVE quote is never under fair x (1 + 2% x sqrt(legs)) (`safe`); SHADOW records it (`livePrice`).
2. Fair accuracy: legs graded from final scores hit 58.2% against a 59.2% fair (579 legs). **Thin for longshots:** the parlays we gave 0-10% hit 5.2% against 4.6% and the 10-20% ones 15.1% against 13.8% (not significant, clustered, but as large as our edge). **Fix:** `longshotPremium` 3% on the fair in longshot mode (7 -> 3 paper fills in the 5.9-hour sample; 5% would be 1).
3. Parlays too likely to win are refused (MK16). Same-game legs go through the copula plus a 2% premium; prop legs carry +20% each (max two).
4. No fee: Novig charges makers none (docs, fees). The fill is at OUR price (the echo is rounded to a thousandth and we always confirm our own quote), so the edge is what we quoted.
5. Not quoted: a leg in play, a leg's fair older than 10 min, fewer than 3 companies behind a leg, a stake above $12 (we cover exactly the stake asked).
6. Risks that stay: the fair can be wrong in a way no sample here shows yet (props, tennis); one game's parlays lose together (`walletLegShare`); the day-loss stop.

**What tonight's setup would have done** (journal of 11-19, 5.9 hours of mostly daytime, 307 quoteable parlays at 25% or less finishing the same day, our rule's price against the price the market traded at; EV if our fair is true, counted against the premium fair):
| longshot premium | 3%/day hurdle | 1.5%/day hurdle |
|--:|--:|--:|
| 0% | 7 fills, $3.79 EV, $259 collateral | 9 fills, $3.91, $317 |
| 2% | 4 fills, $2.28, $177 | 5 fills, $2.30, $216 |
| **3% (default)** | 3 fills, $1.64, $110 | 5 fills, $2.23, $214 |
| 5% | 1 fill, $0.64, $35 | 2 fills, $0.93, $65 |
Collateral is about $35 a parlay (longshots tie up 3-4 times the stake). The evening has 2-3 times the daytime trade volume, so expect a handful of fills tonight (single digits to low teens), not dozens; every one is positive EV against the premium fair, at about 1.5% of its collateral. Raising `longshotMaxChance` to 30-35% roughly triples the fills (8-10 on the same sample) at a little more risk per parlay. The honest trade is fills against safety: both are steppers in the app.


## Update 2026-11-20 14:00 UTC (MK14 follow-up 2, tj Gmail; supersedes the longshot-premium table above, which described the paper rules of 11-19)
The rfq-market Release (OUTCOMES.md) is stale (graded parlays 0, last written 00:55 UTC; the grading the follow-up asked for is done by `tools/rfq_pnl_study.py` on the market's own executed parlays instead: 6,885 graded over 3 slates, `research/rfq_maker_pnl_2026-11-20.md`, refreshed by Release `rfq-pnl-study`). What the live maker is now (LIVE, whole wallet, MK37-MK48): price = our fair + 0.05..0.2 point (margin .001, fairErrorRel .001, minPoints .0005), prop legs x1.2 each, extrapolated alternate lines x1.03, in-play +0.8% / +0.15 pt a leg; every segment the graded study found negative is declined (3+ legs all >= 0.5, a leg >= 0.8, win chance over 0.6, in play over 0.5). Today (11-20): 1,278 quotes in 3h50m in the quiet hours, 6 fills (07:36, 09:24, 09:29, 09:31, 10:49, 13:56) of 4,335 trades seen, all two-leg game-line parlays at price = fair + 0.1 point; we win 4 of 8 auctions we enter that executed. Expected profit: each fill earns (price - fair) x pot = about $0.01-0.05 if our fair is true (a $9.5 stake at .503 vs .501 is $0.04): six fills are worth about $0.1 in expectation against a standard deviation of about $10 a fill, so NOTHING can be concluded from results until ~20+ settle; the settled book before today (18 parlays: the buyer won 17, -$59.92, favourite-heavy at fair + 0.5..1 pt) is the evidence for the rules above, not for these. Realized/expected cannot be judged yet (0 of today's fills settled; the NBA totals settle ~04:00 UTC 11-21). The profit potential is in the segments the study found positive for makers at the clearing price (2 legs priced .15-.45: +4.0%, a longshot leg +2.1%, in-play same-game pairs) with the prices those would earn only where the market pays over our fair (+1.4 pt on in-play pairs): fills at fair + 0.1 point keep the engine in the market and buy evidence; the profitable volume needs the US evening in-play flow and a bigger wallet (a $120 wallet cannot back parlays under ~8% win chance).
