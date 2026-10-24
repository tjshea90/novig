# Is the star rating sound and based on good data? (FX3, Tj 2026-10-23, acct-27a37e tj Gmail)

Tj: "Review the stars rating system for rating bets and make sure it is sound and based on good data."

## What the rating is (v1.0.64, ST1)
`BetRating.rate`: 0-100 points = coverage 20 (companies pricing both sides) + agreement 20 (share of them saying +EV) + edge 25 (full at 6-12%, falling above 12%) + sharp book 25 (agrees full, none priced it 30%, against 0) + consensus 10 (books' fair spread) + type of bet ±15 (Tj's history, `ProfitProfile.typePoints`, shrunk n/(n+100)) + lead +3 / 0 / -4. Stars = 1 + 4 × (points/100)^1.5, then caps: 1 company 1½, 2 companies 3 (2 with no sharp book), 3 companies 4, sharp against 2½, under +2% EV 3, no edge 1.

## The check (new, runs every day on GitHub)
`cloud/StarCheckMain.kt` in the daily digest re-rates every graded engine line (`engine-dataset` `lines-*.csv.gz`) with the rating the app ships, and splits CLV and ROI by stars and by each input, with 95% intervals over GAMES. First run: 1,968 +EV lines with the inputs recorded, game days 10-15 to 11-01, 507 with a close, 1,304 settled (912 older lines logged before the inputs were recorded are left out: rating them would rate missing data).

| stars now | lines | CLV (bets / games) | ROI (bets / games) |
|:--|--:|:--|:--|
| 1-1½ | 135 | -0.24% (16 / 16) | -24.7% ±23.7 (84 / 63) |
| 2-2½ | 688 | -0.75% ±2.2 (91 / 62) | -8.2% ±14.9 (467 / 142) |
| 3-3½ | 899 | +0.15% ±0.9 (315 / 150) | -7.3% ±11.4 (596 / 196) |
| 4-5 | 246 | +1.11% ±2.1 (85 / 62) | -0.8% ±19.9 (157 / 101) |

Slope: +0.50 CLV points and +5.0 ROI points per star (t 1.0 and 1.2, bets as the unit).

## Verdict
1. **The direction is right**: more stars, more CLV and better results, on lines the rating never saw while it was fitted. The ordering is clean in ROI and nearly clean in CLV.
2. **The evidence is thin**: every interval overlaps its neighbour; t ≈ 1. Two weeks of engine lines is not yet enough to call the rating proven. Nothing here is broken, and nothing here supports a confident re-weighting either.
3. **Two parts are not confirmed by the engine's data:**
   - **Type of bet (±15, from Tj's history).** Lines the history scores +0.5 to +5 did worst out of sample (CLV -1.01% ±1.1, ROI -21.8% over 143 games); lines it scores -5 or less did better (CLV +1.52%). The biggest disagreement is game totals: Tj's history +21.6% on overs (89 bets, t 1.1), the engine's lines -21.5% (389 settled). Both samples are small and they point opposite ways, so the history's weight is not earned yet.
   - **Sharp book agrees (25 points, the largest single input with the edge).** Lines a sharp book agreed with: CLV +0.30% (389 bets), ROI -13.0%; lines no sharp book priced: CLV +0.65%, ROI -1.1%. Part of this is circular (the fair is 70% the sharp book, and the close is Pinnacle's own price), and part is the kind of bet (the sharp book prices game lines, which lag props here: props CLV +2.5%, spreads -0.5%, game totals -0.2%).
   - What IS confirmed: the edge term's shape (CLV rises to 6-12%, lines over 12% lose: -28% ROI), small-book caps (1 company -26% ROI, 2 companies -13%, 3-4 companies +4%), agreement (100% agree: CLV +1.1%, ≤50%: ROI -17%).
4. **No weight is changed today.** ROUTINE.md's class B gates (two digests, 200+ settled, an interval that excludes zero, 25% steps, one change a day) are not met by any single input, and ST1 changed the rating today.

## The rule for the next change (LEVERS.md: star rating)
The digest prints the check every day. Change a weight only when two digests agree and the interval excludes zero:
- the type-points "+0.5..5" group with CLV below zero → cut `ProfitProfile.TYPE_POINTS` 15 → 11.25 (one 25% step), or better, refit `profit_profile.json` from Tj's bets AND the engine's graded lines together (`tools/profit-profile.py`, a far larger sample);
- "sharp agrees" with CLV not above "none priced it" over 300+ closed lines each → sharp points 25 → 18.75 and coverage/agreement up by the same total.
Revert either by putting the constant back.

## Outside sources (unchanged from ST1)
Closing-line value as the yardstick against an efficient close (Krieger 2015, Financial Services Review); the favourite-longshot bias (Snowberg & Wolfers 2010, J. Political Economy) behind the longshot caps.
