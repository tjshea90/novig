# Bet history read, 2026-10-14 (1,915 bets and bid fills, 2026-09-25 to 10-14; Tj's `vigilant-bets-2026-10-14-all-*` files)

Files: `research/uploads/2026-10-14/{97daf98f..all-bets-p1, 31908c68..all-bids-posted-p1, 80bbf9c8..README}`. `python3 tools/profit-profile.py` was re-run on them (profit_profile.json asOf 2026-10-14, 1,783 records); the profile only acts while Settings › Profit-proven only is on (default off).

## Headline (settled taker bets, outliers left out; my own recomputation from the jsonl)
- 1,228 settled bets: ROI -6.4%, stated EV when placed +2.9%. **The gap is -9.7 points, t = -3.2.** The CLV is positive (+1.0%, t 4.2; independent-close CLV +1.9% on 447, beat close 74%): the market closes where the bets said, and the bets still lose more than their stated edge.
- **It is concentrated, not general:**
  - Underdogs (+110 or longer): n 685, ROI -13.7% vs EV +3.2%, **gap -15.6, t = -3.8**. Even money (-110..+110): n 414, ROI +1.8% vs +2.6%, gap +0.1 (the edge is REAL there). Favourites: n 129, ROI +3.6%.
  - Player props: n 852, ROI -9.8%, gap -13.3, t = -3.8. Game totals (+6.3%, n 141), spreads (+4.4%, n 141) match their stated EV.
  - 4.5 stars: n 198, ROI -17.7%, gap -25.6, t = -3.5; 4 stars +1.6% (n 322). Stars do not rank profit at the top end; the 5-star sample (26) is too small to say.
  - Sharp book "said no": 3-13 on 16 (too few for a t, but it agrees with the veto).
- Bids (452 fills): ROI +1.0%, EV +3.6%, gap about -2.6 (not significant), CLV +2.3% (t 9.0, independent +3.1% on 138). 22,234 bids posted -> 245 filled (1%): quality is fine, volume is the problem (matches the wallet finding).
- Time to the start: the CLV is large under 2 h (+2.8..3.4%, t 12-13) and nil beyond 24 h (-0.2%); ROI does not follow (noise at these sizes).

## Reading
The stated EV is overstated for LONGSHOT sides and player props by about 13-16 points, on samples large enough (685, 852; t about -3.8 each) that luck is an unlikely explanation. A close that agrees with the bet while results do not is the favourite-longshot bias (RESEARCH.md §8.1, §16.2, §69): books and the close both price longshots too generously, so a "fair" built from their no-vig prices is too high for dogs. `ThinEarlyHaircut` (-2.5 points) fixes one narrow case (NHL shots-on-goal); this is the general case.

## Proposed (NOT built; per CLAUDE.md the rule changes are Tj's call)
1. A longshot haircut on the fair for sides at +110 or longer (start about 4 points of probability at +150, scaled by price), applied in `Pricing.price` like `ThinEarlyHaircut`, switch default on, and re-measured on the next history file (the gap should close to about 0).
2. Or the blunt version: no auto-bet / bids on sides longer than +110 (`BetSlider.LONGEST_ODDS`, now +150) until the haircut is measured.
3. Turn Settings › Profit-proven only on (the regenerated profile blocks the types that lost).

## Addendum (Tj, 2026-10-14: "Have the losing bet types been consistent since the start" / "consistently beating CLV yet losing: is the CLV wrong or the EV?")

### 1. Consistency (settled taker bets, by week placed)
| week | prop OVERS (fair / won / gap / ROI) | prop UNDERS | non-props |
| :- | :- | :- | :- |
| 09-21 | 18: .487 / .444 / -4.2 / -5.7% | - | 18: -23.8% |
| 09-28 | 174: .491 / .414 / -7.7 / -13.0% | 167: -2.9 / -2.4% | 156: +13.5% |
| 10-05 | 198: .497 / .384 / -11.3 / -18.3% | 115: -5.6 / -8.3% | 86: +18.6% |
| 10-12 | 91: .494 / .385 / -10.9 / -15.9% | 25: -5.4 / -11.8% | 93: -28.7% |
Prop OVERS lost in all four weeks and the gap grew; prop UNDERS lost every week (smaller); non-props swing both ways (+13.5, +18.6, -28.7: noise, total +$13.57 on 353, t about 0). Bids: ~0 gap every week. **The whole loss is prop taker bets: props -$114.12 (Overs -$97.79 on 481, Unders -$16.33 on 317); everything else is +$13.57 taker and +$3.00 bids.** Tennis taker bets: -28% on 97 (non-prop tennis -40% on 38, t -4.1). Bids on moneylines -43.7% (31, t -2.0); team-total bids +26.1% (94, t +2.1: the only positive group with a t above 2, and it is the tier BidTier already ranks "proven").

### 2. Is the CLV wrong or the EV wrong? Neither is arithmetically wrong; the reference they both use is biased for props.
CLV = closeFair/cost - 1 and EV = fair/cost - 1 are both measured against a no-vig price built from books (the close: Pinnacle's price at the start; the fair: the consensus at bet time). If that no-vig price were an unbiased probability, win rate would equal it. Settled bets with a close, win rate vs the close's own fair:
- all 1,398: close .482, won .454 (z -2.1);
- **prop OVERS 542: close .486, won .410 (-7.6 pts, z -3.6)**; with Pinnacle's own close (independent of the app): 272, close .488, won .393 (-9.5, z -3.1); novig-trades close .476, won .370 (92);
- prop UNDERS 439: close .489, won .467 (-2.2, z -0.9); non-props 417: close .468, won .499 (+3.0, z +1.2: calibrated).
So the market's own closing price, de-vigged, is ~8 points too high for prop Overs: the books put their margin on the Over side (the public's side), and a de-vig that splits the margin evenly (every method the app has: multiplicative, additive, power, Shin) gives the Over back that margin. Buying an Over at 47c when the books' de-vigged Over is 49c "beats the close" (CLV +1.7%) and still loses, because the true chance is about 41%. **CLV measures agreement with the market; it cannot see a bias the market's own price carries.** Grading is not the cause (prop Overs graded by Novig: won .398 on 254; by the app's scores: .394 on 226).
Adverse selection adds to it: the bigger the stated edge on a prop the worse it did (stated EV 3.5-5%: -10.6 pts; 5%+: -15.1 pts, ROI -38% on 80): the largest gaps between Novig's price and the books' are mostly the books being wrong about the Over.

### 3. What was done / proposed
- Built (v0.99.4): `PropHaircut` (Over -7 pts, Under -3 pts) and Profit-proven only on.
- Built (this release): tennis auto-bets off (evidence above), Diagnostics "FAIR CALIBRATION" (win rate vs fair and vs close by kind and side, with z) so each history file shows whether the haircut closed the gap.
- Not changed: team-total bids, game-total and spread takers (positive but t < 1).
