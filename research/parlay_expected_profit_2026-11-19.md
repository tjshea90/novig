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
