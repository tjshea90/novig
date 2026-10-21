# What makes money, and what beats the close, in the engine's own record (XO3, 2026-10-21)

Tj, 2026-10-21: "run an analysis on the saved scans, bets, and bids. Find patterns and profitable bet and bid types as well as what types beat the clv. Adjust the system and settings as needed for maximum profit."
Data: `engine-dataset` Release (`lines-*.csv.gz` 14,090 lines the engine's scans saw from 2026-10-15; `bids.csv.gz` 339,555 bids), `propline-clv.csv.gz` (the same lines with CLV from PropLine's Pinnacle, SGO's Pinnacle and Circa, ESPN's DraftKings and Novig's trades, consensus and confirmation: CH57), the ledger `trades.json` (618 orders: 94 auto-bets, 524 bid fills), the 12,088 trading-pass journals, and the review digest (15:21Z). CLV = the close's probability over the price paid, minus 1, standard errors count games. Every number is from those files; no API call was spent. The per-order table is now produced by `tools/orders_clv.py` (daily, Release `engine-dataset`: `orders-clv.csv.gz`, `orders-clv.md`), so this does not need redoing by hand.

## 1. The money so far (digest, 2026-10-21 15:21Z)

Engine orders in the ledger 617 (all LIVE): 359 settled, +$19.51 on $217 at risk (+13.5% on settled), average EV when placed +3.8%. By origin: engine bids 523 rows, 270 settled, +$8.83, ROI +10.5% (z +1.6); engine auto-bets 94 rows, 89 settled, +$10.68, ROI +17.8%. The sizes are small (an auto-bet averages $0.67 and a bid fill $0.30 on a $238 wallet), so the dollars are small; the question here is which kinds earn their edge.

## 2. Bid fills beat the close: +1.70% CLV (t +3.7), 67% beat it, on 201 closes in 78 games

All numbers at the FILL price (`tools/orders_clv.py`), pregame:

| group | fills | closes | CLV | t | ROI (settled) |
|:--|--:|--:|--:|--:|--:|
| **Under** (props, totals, team totals, 1H) | 207 | 84 | **+2.60%** | **+4.2** | +27% (112) |
| Over | 146 | 55 | +1.28% | +2.7 | +1.3% (85) |
| spreads, moneylines, team sides | 171 | 62 | +0.85% | +0.7 | -12.9% (73) |
| team totals | 143 | 65 | +2.06% | +3.4 | +27.9% (97) |
| player props (Under) | 58 | 16 | +4.16% | +3.2 | +27.9% (16) |
| 1st half / inning / set lines | 71 | 40 | +1.75% | +2.8 | +6.3% (58) |
| game totals | 90 | 31 | +1.40% | +2.0 | -19.9% (44) |
| spreads | 123 | 41 | +0.92% | +0.7 | -39.3% (45) |
| a sharp book in the fair | 341 | 171 | +2.02% | +3.8 | +13.0% (212) |
| no sharp book in the fair | 88 | 30 | -0.15% | -0.2 | +2.3% (58) |
| NFL / NHL | 83 / 77 | 39 / 36 | +2.9% / +2.7% | +3.6 / +3.4 | +16% / +14% |
| NCAAF (40% of all fills) | 224 | 59 | **+0.1%** | +0.1 | +23.7% (102) |

Reading: the bid desk's edge is real (CLV, not luck), it lives in Unders and team totals, in lines a sharp book is part of, and in the NFL and NHL. Spreads and game totals fill a lot (213 fills) and earn the least on both measures. NCAAF fills are the most numerous and show a close no better than break-even, although their results are good (n small: do not trust either yet). By hours to the start the fills at 1 h or less (+3.0%, 89% beat), 3-6 h (+2.1%) and 6-12 h (+2.0%, t 2.5) are all good: the near bids that fill fastest are not the worse ones. By EV at the fill: 1.5-2.0% +1.2% (t 1.6), 2.0-2.5% +2.3% (t 3.9), 2.5-3.0% -0.2%, 3-4% +3.2% (t 4.0): the 1.5% floor still earns a positive close.

## 3. The taker bets (what the scan lists): the EV is calibrated and the 1.5% floor is right

All 14,090 lines at their first-seen price (5,400 with a close): CLV by the EV at first look: -2..-1% lines -1.5% (t -5.1; the model's sign is right), -1..0% +1.4%, 0..1.5% -0.05%, **1.5-3% +2.3% (t 2.7)**, 3-5% +1.0%, 5-10% +2.0% (t 2.0). So an EV floor of 1.5% marks where the CLV turns positive and about 60% of the shown EV shows up as CLV. By kind (EV at least 1.5%): player props +3.3%, spreads +3.2% (t 2.0), team totals +1.8% (t 2.2), totals +0.9%, moneylines -0.6%; by league NBA +2.7% (t 2.9), NFL +4.9%, NCAAF +0.9%, NHL +0.8%. Realised ROI on those lines is flat to slightly negative with an interval of plus or minus 6 points over 450 settled: CLV and results cannot be told apart yet (the CH56 audit's conclusion stands: the CLV is real, +1.5% to +2.5%; the dollars need hundreds more bets).

## 4. What the auto-bet did not take, and why (the volume answer is in `bid_volume_study_2026-10-21.md`)

94 bets in six days (about 16 a day, $0.67 each): the 6 h trap guard held back 67% of the listed-row-passes, stale other-book prices 11%, no fresh Novig price 11%, the edge floor 3%. Only 26 listed bets a day start within 6 h; the engine takes most of them. Evidence on the guard from this data: no worse CLV beyond 6 h (EV at least 1.5%: within 6 h +2.0%, beyond +2.5%), but spreads (-13.8% ROI, t -1.8, 193 settled) and game totals (-18.6%, t -2.0, 206) lose beyond 6 h while props and team totals do not; `propline-timing` (Novig price at each lead against Pinnacle's close) says game totals with a 2% edge pay at every lead from 72 h to 1 h (ROI +7% to +37%, hindsight-selected). Nothing here passes the class B bar (200 settled with an interval excluding zero in the change's direction) for loosening it, so it is unchanged and written on the watch list.

## 5. Changes made, and not made

Made (XO2): the bid order (`BidVolume.score`: hours to the start x distance under the offer), `bidPerGameDollars` 5 -> 8, the bid desk's write-behind store (`CHANGES.md`). Not made, with the number that would settle each:
- No-sharp-book bids -> a higher floor or none: CLV -0.15% on 30 closes; settle at 100 closes (about 3 weeks).
- Spread and game-total bids -> a wider margin or off: ROI -39% (45) and -20% (44), CLV +0.9% and +1.4%; settle at 200 settled fills in the group.
- NCAAF bids -> off or a higher floor: CLV +0.1% on 59 closes; settle at 120 closes.
- Per-kind trap windows (props and team totals to 24 h): CLV positive beyond 6 h; settle at 200 settled in each (now 137 and 51). The per-kind switch is not built yet.
- A margin tilt by side (Unders 0.5 point lower, Overs higher): Unders +2.6% (t 4.2) and Overs +1.3% (t 2.7) at the same margins; settle with the next 100 closes per side.
- Kelly sizing: a quarter Kelly times a confidence of 0.3-1.0 (about 0.65) on the shown edge is about a quarter Kelly on the CLV-implied edge: not an inefficiency.
- The prop haircut (Over -7, Under -3 points): see `propline_history_study_2026-10-21.md`: Pinnacle's devigged prop close leaves Overs overpriced by 2-3 points and Unders underpriced by the same, but the engine's own NFL Under prop lines lost (41.5% against a 49.3% fair, 277 settled), so it is not touched.

Watch (read in the next digest, one line each in `CHANGES.md` when it moves): bid fills a day (110/77/143/83/79 on 10-16..20; 19 so far on 10-21), fill rate per 1000 postings (0.9 on 10-20), `status.deskNote` pass length, the `orders-clv.md` table by group.
