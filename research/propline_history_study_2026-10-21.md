# What PropLine's history says (XO4, 2026-10-21): calibration of the closes, which books are accurate, and how early Novig prices pay

Tj, 2026-10-21: "review and analyze historical odds and props from my apis which have this feature. Find patterns and profitable methods to incorporate into vigilant and the GitHub engine."
Data (Releases, no API call was spent): `propline-archive-2026-10-16..20` (the resolved-prop exports of Novig and Pinnacle for MLB, NBA, NFL, NCAAF, NHL from 2026-07-18: 789,000 graded rows after removing the overlap and the canary rows; each carries the book's opening and closing price and point, the grade and the final score), `books-accuracy-2026-10-20.json` (PropLine's own per-book accuracy over 30 days and 479,429 closing prop lines), `propline-timing` (the 2026-10-16 timing study). SGO's and OddsPapi's history endpoints give closes for the app's own bets (they feed the CLV consensus, CH57) but no results at this scale, so they were not re-read. Method: pair each Novig outcome with the Pinnacle outcome on the same event, market, player and line, devig Pinnacle's two sides multiplicatively, keep only pairs whose line never moved (the grade then belongs to the line the prices describe), and group by event so a game counts once.

## 1. Pinnacle's devigged prop close is miscalibrated in a stable, large way: Overs are overpriced, Unders underpriced

100,254 prop rows (two-sided Pinnacle closes, line unchanged), realised win rate against the devigged close probability, and the return of backing the side AT that fair price:

| side | fair prob | n | won | bias (points) | z | return at the fair price |
|:--|:--|--:|--:|--:|--:|--:|
| Over | 0.10-0.20 | 7,343 | 13.3% | -2.0 | -4.8 | -13.3% |
| Over | 0.20-0.30 | 2,990 | 21.1% | -3.2 | -4.1 | -13.1% |
| Over | 0.30-0.40 | 4,741 | 34.6% | -2.4 | -3.4 | -6.4% |
| Over | 0.40-0.50 | 14,669 | 45.2% | -0.4 | -0.9 | -0.8% |
| Over | 0.50-0.60 | 13,447 | 53.2% | -1.7 | -3.9 | -3.0% |
| Under | 0.40-0.50 | 14,988 | 47.4% | +1.7 | +4.3 | +3.8% |
| Under | 0.60-0.70 | 4,741 | 65.4% | +2.4 | +3.4 | +3.8% |
| Under | 0.70-0.80 | 2,990 | 78.9% | +3.2 | +4.1 | +4.2% |
| Under | 0.80-0.90 | 7,343 | 86.8% | +2.0 | +4.8 | +2.4% |

By sport the same shape holds in MLB (Over at 0-0.3: -2.25 points, z -5.9; 0.3-0.7: -1.2, z -3.7) and NHL (-2.9 and -1.7, z about -2.5), and is absent in NFL and NCAAF (small n, bias within 1 point, not significant). Game totals, spreads and moneylines are calibrated (all bias within 2 points, mostly inside the noise; MLB spreads at 0-0.45 are a run-line artifact). So a prop Under bought at Pinnacle's devigged price earns about +2% to +4% by itself, and a prop Over loses 3% to 13%, in baseball and hockey. This is the structure behind the engine's own finding that prop Overs lose (the prop haircut, `PropHaircut`: Over -7 points, Under -3 points, taken from Tj's CNO-based bets), and behind the engine's own prop fills being Unders (58 of 76 bid fills; the 24 prop auto-bets are all Unders, +41% ROI on 23 settled).

What it does NOT yet license: lowering the Under haircut or adding a plus to Unders. The engine's own prop Under LINES at first look in NFL lost (41.5% won against a 49.3% fair, 277 settled, z -2.6) while the Pinnacle-close calibration for NFL props shows none: an early fair is not a close, and these lines are first-seen prices days before the game with the line often moved. The test that would settle it is cheap and has no risk: log, next to each prop line's EV, the EV under a calibrated fair (Over: fair - bias(p), Under: fair + bias(p) with the table above), then compare ROI and CLV of the rows the calibrated fair would add or remove once 200 settled in each side exist. `tools/orders_clv.py` and the lines dataset already carry the fair and the price; the calibration is a column to add offline (no engine change).

## 2. Which books are accurate (PropLine, 30 days, 479,429 closing prop lines, Brier against the result vs the average of the other books)

Only **Kalshi beats the market (+4.3 basis points, interval +2.1 to +6.4; MLB +5.3 [2.4, 8.2])**. Trailing it, with intervals excluding zero: Hard Rock -3.5, Pinnacle -3.9, DraftKings -3.9, Novig -3.4 (MLB -4.9, NFL -6.2; NHL +2.1 is in line), Bovada -3.2, BetMGM -4.5, Polymarket US -4.8, Courtside -7.0, Smarkets -7.4, FanDuel -9.9, **ProphetX -10.0 [-13.0, -7.1] (the least accurate of 24)**. The others are in line. So for PROPS: the engine's sharp set (`pinnacle, polymarket, kalshi, betonlineag, lowvig, betfair_ex_eu, matchbook, sxbet`, weight 0.7 in the BLEND) holds one accurate book (Kalshi) and two that trail (Pinnacle, Polymarket); ProphetX is not in it (good) and the old CNO scan's use of it as a sharp prop book (`CnoBooks`, `SharpVeto.ranking`) is the only place left. A prop fair that leans on Kalshi alone when it prices the line is the candidate; its cost is coverage (Kalshi lists far fewer props), so test the rule on the lines dataset (`sharp_in_fair`, `n_fair_books`) before building it. Nothing changed in the engine.

## 3. Novig's opening price against Pinnacle's fair: the edge persists, and early game totals pay

56,000 Novig/Pinnacle pairs after dropping rows with a placeholder price (cost under 0.2 or over 0.8, or a stated edge over 15%): the edge at the open (`Pinnacle's devigged open / Novig's open cost - 1`) carries into the CLV against Pinnacle's close with a slope of 0.91 (0.9 points of every point of edge survive to the close), and for edges of 1.5-3% / 3-5% / 5-15% the CLV is +0.9% / +2.2% / +5.2% (t 5, 9, 22). Realised ROI of those bands is +2.0%, +4.5%, +1.2% (800-1000 bets each, t under 1): the CLV is real but dollars need far more bets than this archive holds.
- Game totals bought at Novig's open with a 1.5%+ edge, by how early the open was: more than 48 h before the start CLV +5.0% (t 12.3), ROI +13.8% (t 2.0, 459 bets); 24-48 h +3.0%, ROI +10.6%; 12-24 h +4.9%, ROI -1.6%; within 12 h CLV +0.55%, ROI -11.1% (217). The `propline-timing` study (the same question at fixed lead times, hindsight-selected on a +2% CLV) agrees for game totals: +7% to +37% ROI at every lead from 72 h to 1 h. Early edges in game totals and spreads are real; the near-start ones are not better. This is the opposite of the trap guard's premise (bets 6 h or more out lost to the close), which was measured on Tj's own CNO-fair bets (2026-10-03). The engine's own lines do not reproduce it either (CLV beyond 6 h +2.5% against +2.0% within), but its early game-total and spread bets did lose money (ROI -18.6% and -13.8%, t about -2, 400 settled): the two measures disagree, so the guard stays until the results side moves (`engine_profit_study_2026-10-21.md` section 5).
- By kind and sport (edge 1.5%+): MLB prop Unders CLV +3.7% (t 10), ROI +2.6%; MLB prop Overs CLV +2.2% but ROI -7.3% (the devig artifact of section 1: the CLV vs a devigged close flatters Overs); NHL prop Under/Over ROI +17%/+5%; NCAAF totals +9.4% / +4.6% (Over / Under), NCAAF spreads +8.6%; NFL prop Over +24% (111); MLB game totals Under -3% to +17%. None of these cells has the 200 settled bets the class B gates ask for except MLB props.

## 4. Decisions

1. No engine setting or rule changes from this study: every candidate (a calibrated prop fair, a Kalshi-weighted prop fair, a per-kind trap window, game-total timing) needs results the data does not hold yet, and each has a cheap offline test first.
2. Built, for the next sessions (no engine behaviour): `tools/orders_clv.py` (the per-order CLV at the fill price into `engine-dataset`, daily); the numbers above are the baseline for the calibrated-fair test.
3. Watch list (also in TASKS.md): (a) prop calibration column in the lines dataset and the test at 200 settled per side; (b) Kalshi-only prop fair on the lines that carry it; (c) game totals and spreads beyond 6 h at 200 settled each (now 206 and 193: the interval decides); (d) the 100k-prop calibration rerun monthly from the archive.
