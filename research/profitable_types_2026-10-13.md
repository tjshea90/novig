# Most profitable types of bids and bets (Tj, 2026-10-13)
Data: README of the 10-13 all-bets export (1,648 records: 1,162 bets + 373 bid fills, 25 Sep - 13 Oct) for the totals below, and the 10-11 record-level files (research/uploads, 1,422 records: 1,122 bets, 300 bid fills) re-cut by type with /tmp study (scratchpad an/study.py logic: ROI t = mean per-bet return / SE; CLV t as in the README). Rule used: CLV needs ~100+ closed bets to mean much, ROI 1,000+; every claim says n and t. Outliers (EV beyond +-6%) left out as the app does.

## BIDS (fills; 10-13 README: 373 fills, +1.5% ROI, CLV +2.0% on 291, t=6.6, independent +2.8% on 112)
Record-level (10-11): 266 settled, ROI +3.1% (t=0.0: nothing), CLV +2.0% on 249 (t=+6.6).
| Type | n settled | ROI (t) | CLV (n, t, independent) | Verdict |
| :- | -: | :- | :- | :- |
| Team totals (both sides) | 65 | +33.9% (t=+2.3) | +2.4% (65, t=4.4, ind +3.6% on 21) | BEST: positive ROI AND beats the close; ROI sample still small (n=65) |
| Team totals, unders | 46 | +27.0% (t=+1.8) | +2.5% (46, t=3.8) | same, smaller |
| Player props, unders | 87 | -2.2% | +2.2% (94, t=4.1, ind +3.1% on 37) | beats the close, break-even results |
| Player props, overs | 79 | -10.0% | +1.5% (74, t=2.9) | beats the close, loses money |
| NFL player-prop unders | 37 | -32.2% | +2.7% (44, t=3.2) | closes beat, results bad: small n, likely noise or a fair that is too high |
| NHL (any) | 61 | +24.4% (t=+1.4) | +2.1% (57, t=3.3) | good, n small |
| NFL (any) | 88 | -16.5% | +2.3% (93, t=4.6) | beats the close, loses: watch |
| Favorites (-110 or shorter) | 38 | +20.6% | +2.8% (34, t=4.9) | n too small |
| Posted 30 min - 2 h before | 38 | +3.7% | +3.2% (38, t=7.1) | best CLV bucket |
| Posted 6-24 h before | 112 | +8.8% | +1.5% (106, t=2.6) | best by volume, positive ROI |
| Posted <30 min | 32 | -23.8% | +2.9% | n small |
README, bids posted: 22,234 posted, 245 filled (1%); fills that were picked off 1%; mean fill EV +3.4%; by kind: team totals 8,589 posted/65 filled (0.8%), player props 11,924/174 (1.5%), game totals 416/1, spreads 278/0 (so game-line bids basically never fill), 1st-half 961/4.
Conclusion on bids: nothing is proven profitable by ROI yet (every ROI interval includes 0). The only type that is both positive in results and beats the close with a decent sample is TEAM TOTALS (about 65 fills, t=2.3 on ROI, t=4.4 on CLV); NHL is next; props bids beat the close but do not pay, so keep them but do not scale them.

## BETS (takers). 10-13 README: 1,162 bets, ROI -4.5% (profit -$64.80), CLV +1.0% on 953 (t=4.3), independent +2.0% on 377
| Type | n | ROI | CLV (n, t) | Verdict |
| :- | -: | :- | :- | :- |
| Team totals (README row pools bets AND bid fills: 108 in all) | 108 | +19.4%, +$29.27 | +2.6% (101, t=5.9, ind +2.8%) | CORRECTION 10-13 evening: the money is in the BID fills; taker bets alone are 29 settled, -7.9% (see below) |
| 1st half / inning / set | 24 | +24.9% | +2.8% (23, t=2.4) | too few |
| Spreads | 162 | +14.8% | -1.0% (109, t=-1.1) | profit not backed by the close: luck risk |
| Game totals | 166 | +11.7% | +0.3% (106, t=0.3) | same: results ahead of the close |
| College football | 199 | +23.3% | +0.4% (156, t=0.7) | same |
| Player props (all) | 989 | -8.8%, -$122 | +1.5% (873, t=7.3) | beats the close but LOSES; the whole loss of the account |
| Player props OVER (10-11 data) | 401 | -15.0% (t=-3.0) | +1.8% | clear loser: the profit-proven filter already blocks it |
| Player props UNDER | 317 | -2.9% | +0.9% | break-even |
| Underdog +110..+200 | 846 | -8.6% | +1.5% (692, t=5.1) | beats the close, loses money (favorite-longshot bias) |
| Even money | 513 | +0.7% | +1.0% (428, t=3.3) | break-even |
| Favorite -200..-110 | 158 | +12.3% (t=+0.2 on 95) | +1.2% (120, t=2.2) | best price band, n small |
| Placed <30 min before | 125 | -0.5% | +3.5% (121, t=13.0) | best CLV by far |
| Placed 30 min-2 h | 148 | -7.1% | +2.8% (138, t=10.6) | great CLV, small sample |
| Placed 24 h+ | 380 | -5.1% | -0.3% (302, t=-0.5) | gives the close away: avoid |
| Sharp book agreed | 795 | -3.4% | +1.9% (690, t=8.2) | best CLV by trust signal |
| Sharp book said no | 16 | -65.5% | +0.6% | n=16, but 3-13: veto works |
| Auto | 781 | -1.4% | +1.9% (628, t=8.5) | better than Manual (-6.2%, CLV +0.6%) |
| Stars | | 4★ +6.5% (350), 4½★ -14.6% (186), 5★ -74% (16) | CLV rises with stars | stars predict beating the close, not profit |
Reading: the money loss sits in PLAYER PROPS (-$122 on 989) and underdogs; the account is positive everywhere else (game totals +$17, spreads +$23, team totals +$29, college football +$46) but those results are ahead of their closing lines (CLV about 0 or negative), so they are not yet trusted as an edge. Only TEAM TOTALS has all three: positive results, positive CLV (+2.6%, t=5.9), n>100.

## What I would do (the app's own rule: nothing recommended on a handful of bets)
1. Lean on team totals for bids AND bets (the one type with n>100, CLV t>5 and positive profit); raise their share/limits gradually (small-prop guard does not cover team totals).
2. Keep blocking player-prop overs; do not add more prop volume until ROI catches up with the CLV (props beat the close by +1.5% yet lose 9%: either the closes are flattering props or the fair is too high; test it with independent closes = +2.1%, which says the closes are not the problem, so it is more likely variance + the longshot bias + 3 books).
3. Time: bets placed 24 h+ out give the close away (CLV -0.3%, t -0.5); <6 h beats it by +2.4% to +3.5% with t 8-13: restrict the auto-bet window to the last 6 h (the app's trap guard already refuses beyond 6 h for lines) and let bids stay long.
4. Price: favorites and even money are break-even or better; underdogs +110..+200 lose 8.6% on 846 bets while beating the close: tighten the dog band (BetSlider LONGEST_ODDS +150 already).
5. Too small to act on: NHL bids (+24%), NCAAF spreads (+34%), favorites bids (+21%): watch, do not change settings.
Next data to ask: record-level files after 10-13 (the README alone cannot split bids by type) so team totals can reach n=150+.

## Team totals in detail (record-level, 10-11 files: 94 settled = 65 bid fills + 29 taker bets; ROI +22.6%, t=1.8; CLV +2.5% on 91, t=5.3, independent +2.9% on 29)
CORRECTION to my first answer: the README's "team totals 108" pools bets and bid fills, so I wrongly called it "best for bets". Split: BID fills 65 (+33.9%, t=+2.3, CLV +2.4% t=4.4); TAKER bets 29 (-7.9%, t=-0.2, CLV +2.9% on 26).
- Side: over 33 (+31.7%, CLV +2.7% t=3.4) vs under 61 (+18.3%, CLV +2.4% t=4.0): both beat the close equally; results favor over but n=33. By instrument: bid over 19 +52% / bid under 46 +27%; bet over 14 -2.5% / bet under 15 -12.2%.
- Sport: NHL 26 (+60.3%, t=3.4, CLV +3.6% t=4.0, independent +5.2% on 8) BEST; NCAAF 35 (+4.4%, CLV +3.0% t=3.2); NFL 23 (+1.7%, CLV +0.9% t=1.4, independent -0.3%: no edge); MLB 8 (-4%). NHL over 10: +85% (t=4.2); NHL under 16: +48%. NFL unders 16: -20%; NCAAF overs 15: -29% (CLV +3.5%).
- Lead time: 6-24 h 39 (+40.1%, t=2.5), 2-6 h 26 (+22.4%), 30 min-2 h 12 (-16.5%). Odds: even money 30 (+34.2%), dogs +110..+200 56 (+18.3%). Auto 71 (+29.5%, t=2.0, CLV +2.6% t=5.1) vs manual 22 (-6.9%).
- Every ROI above rests on 8-65 bets: too small to set rules; the closes (CLV) are the evidence: team totals beat the close in NHL and NCAAF, not in NFL.
