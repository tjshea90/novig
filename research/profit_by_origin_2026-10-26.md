# Profit by who placed the bet (2026-10-26, CH87b/CH87c)

**Question (Tj, 2026-10-24):** "my wins today are profit yet my total profit is going more negative", then: the all-time figure from the Vigilant subaccount's own ledger.

## Novig's own ledger of the Vigilant subaccount (the engine's first read, 2026-10-26 02:56 UTC)
FILL -3,668.03 (4,581 rows), FEE -1.65 (138), MAKER_CREDIT +0.49 (95), SETTLEMENT +3,506.56 (1,960); open bets $105.33 at cost.
**All-time betting profit: -$57.29.** The wallet figure (wallet now less money put in) said -$61.15 on 10-24: they agree.

## Why the Tracker said +$11
The Tracker also counts bets imported from the Novig app (`origin: imported`): 1,172 bets, **+$82.07 on $474.66**. Those sit in the cash wallet, which no API route reads (NOVIG_API.md: a key reaches one subaccount), so the subaccount ledger never contains them. Nothing is missing; the two figures cover different wallets.

## By origin (phone-data `phone/bets/bets-*.jsonl`, newest state of each bet; engine `trades.json`)
| who placed it | bets | settled | staked | profit |
|:--|--:|--:|--:|--:|
| GitHub engine (auto-bets + bid fills) | 1,334 | 1,225 | $566.15 | **+$40.85** (+7.2%) |
| bet sheet, by hand | 1,363 | 1,290 | $1,189.76 | -$36.61 (-3.1%) |
| phone auto-bet | 516 | 516 | $659.89 | -$17.81 (-2.7%) |
| phone bid desk | 518 | 518 | $549.62 | -$1.49 (-0.3%) |
| imported from the Novig app (cash wallet) | 1,172 | 1,058 | $474.66 | +$82.07 (+17.3%) |
| older rows without an origin (hand / auto / bid) | 409 | 409 | $354 | -$14.34 |

Results only (CLV is the better judge, ROUTINE.md); small samples, wide intervals. What it says: the engine's own orders are the part that makes money; the losses come from bets placed on the phone, by hand and by the phone's old auto-bet (which now stands down while the engine trades). The 2026-10-17 study said the same of by-hand props.
