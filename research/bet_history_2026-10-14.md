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
