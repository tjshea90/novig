# Do bets and bids placed far from the start beat the close? (2026-10-14, v0.99.8 file question: why so few bids)

Source: every `research/uploads/*/*bets*.jsonl*` record (1,915 bets/bids, merged by id); only rows with an INDEPENDENT close (`closeIndependent`) count for CLV. Script: inline python in the session; rerun over the same files.

| made | lead | n | indep. CLV n | CLV | beat close | settled ROI |
| :- | :- | -: | -: | -: | -: | -: |
| bid | 0-6 h | 223 | 78 | +3.74% (t +7.1) | 86% | -2.0% |
| bid | 6-12 h | 114 | 36 | +2.72% (t +3.1) | 83% | +11.3% |
| bid | 12-21 h | 50 | 19 | +0.81% (t +0.7) | 79% | -8.6% |
| bid | 21-36 h | 8 | 5 | +3.46% | 100% | -3.5% |
| bid | 36 h + | 10 | 0 | no independent close | | |
| bet | 0-6 h | 533 | 196 | +2.66% (t +11.0) | 84% | -7.2% |
| bet | 6-12 h | 194 | 61 | +1.71% | 77% | -23.0% |
| bet | 12-21 h | 243 | 71 | +1.53% | 65% | +4.0% |
| bet | 21-36 h | 79 | 22 | +2.03% | 64% | +6.4% |
| bet | 36-72 h | 182 | 50 | +1.73% | 72% | -5.5% |
| bet | 72 h + | 198 | 63 | +1.55% | 60% | +4.7% |

Reading: against independent closes, early bets are not worse than late ones (CLV +1.5 to +2.7% at every lead time). Early BIDS past 36 h have no independent close yet (10 bids), so nothing is proven for them: the wallet-filling pass keeps its extra margin (+4 and +2 points), a sharp book required and half the stake. ROI is noisy and says nothing at these sizes.
