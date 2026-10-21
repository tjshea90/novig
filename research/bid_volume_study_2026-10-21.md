# Why the bid fills are few, and what ranks the bids that fill (XO2, 2026-10-21)

Tj, 2026-10-21: "diagnose the low volume of bid fills and auto bets. Tweak the settings as needed for more volume, but keep reasonable safeguards and a floor for positive EV."
Data: the engine's own record (`engine-dataset` Release: `bids.csv.gz` 339,555 bids 2026-10-16..21 and `propline-clv.csv.gz` with the CLV columns; `trades.json` the ledger; `tradepass-*` journals of the auto-bet's 12,088 passes).
Scripts are in the session scratchpad only; every number below is from those files (no API calls were spent).

## 1. The bids: 339,555 posted, 528 filled (0.16%), and the rate is falling as the postings rise

| UTC day | bids posted | fills | fill rate | paid |
|:--|--:|--:|--:|--:|
| 10-16 | 20,521 | 110 | 5.4 per 1000 | $19.60 |
| 10-17 | 19,475 | 77 | 4.0 | $23.11 |
| 10-18 | 95,174 | 143 | 1.5 | $36.53 |
| 10-19 | 85,049 | 83 | 1.0 | $28.81 |
| 10-20 | 86,709 | 79 | 0.9 | $35.46 |

Posting 4.5 times as many bids from 10-18 (volume mode, 1/16 Kelly bids) did not make more fills. Most of the new postings were bids that can hardly fill:
- **48% of all postings were on games more than 72 h away (0.77 fills per 1000) and 18% on games 36-72 h away (0.35); games inside the hour fill at 12.8 per 1000, 1-2 h at 7.1, 2-6 h at 4.6, 6-12 h at 3.7, 12-24 h at 2.2.**
- **Distance under Novig's offer (the ask) is the other big driver:** a bid within 1 point of the offer fills 14.5 per 1000, within 2 points 3.4, 2-3 points 1.9, 3-5 1.2, 5-15 0.84, over 15 points 0.58. A bid posted on a one-sided book (no offer at all, 18% of postings) fills 0.27 per 1000.
- A bid that improves the best bid by 0.5-1.5 points fills 4.0 per 1000, one that joins it 2.8, one behind it 1.0.
- Median bid life is 335 s (65% end by "About to expire: re-posted": a bid never outlives its fair's freshness); half the fills come within 136 s of the post and 90% within 340 s, so the re-posting loses little. The fill hazard is highest in a bid's first minutes (24 per 1000 bid-hours) and falls to 4 after ten minutes: mostly the competitive bids fill and leave the uncompetitive ones behind, not a queue-priority effect.

## 2. What ranks them: hours-to-start x distance-to-offer, kind and league add nothing

The desk's volume order (`BidVolume.score`) used hours x kind x league x "leads its side" with factors measured on 24,267 bids. Leave-one-day-out over the 511 fills (train on five days, rank the sixth; area under the capture curve, and the share of the day's fills held by the top fifth of bids):

| ranking | area | top 20% holds |
|:--|--:|--:|
| old score (BidVolume before this change) | 0.614 | 38% of fills |
| hours only | 0.713 | 53% |
| distance to offer only | 0.648 | 46% |
| **hours x distance to offer** | **0.754** | **59.5%** |
| + leads-its-side | 0.752 | 60% |
| + kind | 0.750 | 57.5% |
| + league | 0.738 | 56% |
| hours x kind x league x lead (the old shape, refit) | 0.697 | 46% |

Better than the old score on every one of the six days (0.81/0.76/0.60/0.84/0.85/0.80 against 0.68/0.58/0.50/0.64/0.73/0.71). Fitted on all days (tables in `BidVolume.hoursLift`/`gapLift`) the top fifth holds 70% of the fills against 45% for the old order. The order only decides which bids go up first when a limit binds (the most dollars on one game, $5; 250 new bids a pass): every bid still has to pass every EV check. Both limits do bind (the desk's note in the log: "the most bid dollars on one game ($5.00) is reached" for 400+ waiting bids, "a pass sends at most 250 new bids" for 1,000+).

## 3. Are the fills good? Yes: CLV +1.7% (t 3.7) at the fill price, 68% beat the close

Joined the 524 bid fills in `trades.json` to the lines dataset (521 matched; 201 have a close), CLV against the consensus close at the FILL price:
- all pregame bid fills +1.70% (se 0.46, t 3.7), ROI +10.5% on 270 settled; no sign of being picked off (EV at the fill, fair a scan later over the price: +2.7% on average).
- by kind: team totals +2.04% (t 3.4, 64 closes), player props +4.16% (t 3.2, 16), 1st-half lines +1.75% (t 2.8, 40), game totals +1.40% (t 2.0, 31), spreads +0.92% (t 0.7, 41), moneylines -0.8% (9).
- by league: NFL +2.9% (t 3.6), NHL +2.7% (t 3.4), NBA +2.0%, MLB +2.1%, **NCAAF +0.1% (t 0.1, 59 closes) although it is 40% of the fills**.
- **a sharp book in the fair: +2.02% (t 3.8, 171 closes); none: -0.15% (30 closes).** Evidence for "no sharp book = no edge", not yet enough to drop them (n 30).
- by EV at the fill: 1.5-2.0% +1.2% (t 1.6), 2.0-2.5% +2.3% (t 3.9), 2.5-3.0% -0.2%, 3-4% +3.2% (t 4.0): the 1.5% floor still earns a positive close, no support for going lower.
- by hours to the start, fills ≤1 h +3.0% (89% beat), 1-3 h +1.5%, 3-6 h +2.1%, 6-12 h +2.0% (t 2.5), 12-24 h +0.5%: the near bids that fill fastest are not the worse ones.

## 4. The auto-bet: 94 bets in six days because of what it may bet, not a fault

The auto-bet's 12,088 passes looked at 287,000 listed-row-passes and ordered 70 (+24 live): the held-back reasons, share of row-passes: **the 6 h trap guard 67%**, the other books' prices too old 11%, no Novig price read lately 11%, edge under the floor 3%; the kind filters that hid totals and team totals until 10-18 are fixed (`forTrading`).
- Supply: over the six days 634 bets were listed, 156 of them within 6 h of the start (26 a day): the engine bet 16 a day, so most of what the guard lets through is taken. More volume needs a wider window.
- Does the window earn its place? Not visibly in the engine's own data: CLV of the +EV lines is no worse beyond 6 h (EV ≥ 1.5%: ≤6 h +2.0%, >6 h +2.5%, t about 2 each); by kind, beyond 6 h: player props CLV +4.5% (t 2.2, 40 closes) ROI +4.2% (137 settled), team totals CLV +0.9% ROI +18.9% (51), **spreads ROI -13.8% (t -1.8, 193) and game totals ROI -18.6% (t -2.0, 206)**, moneylines flat. The original evidence for the guard was Tj's own 176 bets from the CNO lists (2026-10-03), a different fair; the engine's fair (sharp book anchored, outlier-proof) has not reproduced it. Under the class B gates (200+ settled, an interval excluding zero) NOTHING here is strong enough to loosen it for any kind: props and team totals beyond 6 h are the candidates when each has 200 settled (now 137 and 51). Not changed.
- Today's passes: 42% of the row-passes inside 6 h were held because the fair's oldest quote was over 10 minutes old (SGO Pro's bar) with every scan taking 7-14 minutes at about 10.5 Novig prices a second through the read key (the key's 14 a second is the ceiling). Fix direction (TASKS.md TCL16 option "parallel reader jobs"): a second `trading::read` key for a second reader would double the Novig reads; it needs Tj to mint the key.
- Sizing is a quarter Kelly times the confidence (0.3-1.0, about 0.65): about a sixth of the shown edge's Kelly stake. Average auto stake $0.67 on a $238 wallet; against a CLV-implied true edge of about 60% of the shown EV this is roughly a quarter Kelly on the true edge: not an inefficiency to remove.

## 5. What was changed (XO2)

1. `BidVolume.score` = hours-to-start x distance-to-offer (x1.5 for a bid that leads its side), the evidence above; EV-neutral (it only orders what a limit leaves waiting). Test `MakerTest` "Volume mode's score ...".
2. `vigGuards.bidPerGameDollars` 5.0 -> 6.25 (+25%, the largest step the gates allow): the per-game dollar limit is the limit that binds most of the time; every extra bid still passes the same EV floors (1.5% safe, 2.5% risky) and the wallet and picked-off guards.
Not changed (and why): the EV floors (1.5% still earns +1.2% CLV: no support for lower; the no-sharp-book group's -0.15% CLV on 30 closes is a reason to watch, not to cut), the trap guard (above), Kelly sizing (above), the wallet-use target (100% of the wallet is the stated rule; the per-game limit binds first).
Next, in order, one change a day: `bidDeployPct` 100 -> 125 once the first change's fills are in; per-kind trap windows for props and team totals at 200 settled each; no sharp book -> a higher margin at 100 closes.
