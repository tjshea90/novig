# Bids: sharp-book veto vs the many-book average (PX11c, 2026-10-25)

Tj: "If a sharp book disagrees that the bid would be positive ev, do not post it, or bid under the devigged sharp book--- Unless the data shows that bidding under the fair odds of the average of many books is
more profitable than a sharp book veto method."

Data: every settled engine bid fill in `trades.json` (cloud-feed, note `bid`, WON/LOST/PUSH: 779) joined by client id to its journal entry in the `cloud-archive-*` Releases (`bids-*.jsonl.gz`, 346 files): 448 joined.
`sharpFairAtPost` = the sharp book's devigged chance for the side when the bid went up; price = the fill's average price.

| group | n | staked | P/L | ROI (t) | won | blend fair | price |
| :- | -: | -: | -: | -: | -: | -: | -: |
| all settled bid fills | 448 | $223.34 | +$13.33 | +6.0% (+1.73) | 51.8% | 49.1% | 47.9% |
| no sharp book at post | 69 | $46.15 | +$3.93 | +8.5% (+0.56) | 50.7% | 48.5% | 47.1% |
| sharp agrees (sharp fair > price) | 379 | $177.19 | +$9.40 | +5.3% (+1.64) | 52.0% | 49.2% | 48.1% |
| sharp DISAGREES (sharp fair <= price) | 0 | | | | | | |
| sharp agrees by 2+ points | 102 | $49.13 | +$5.78 | +11.8% (+1.07) | 52.0% | 48.1% | 47.0% |
| sharp 0-2 points above price | 277 | $128.05 | +$3.62 | +2.8% (+1.26) | 52.0% | 49.6% | 48.5% |

Reading: the desk never filled a bid a sharp book disagreed with (the veto is already in force: `MakerQuote`'s sharp rule), so there is no evidence that the many-book average without the veto is more
profitable; the no-sharp bids' +8.5% is 69 fills and t 0.56 (noise). Keep the sharp veto. A wider sharp margin (2+ points) looks better (+11.8%) but t 1.07: watch, not a rule yet (class B needs
200+ settled and an interval excluding zero). Re-run when 200+ more fills settle.
