# Research file from the phone, v0.84.1, made 2026-10-09 20:52 ET (analysis 2026-10-10, steward session)

Source: `research/uploads/2026-10-10/b0cf754a-vigilant-research-v0.84.1-2026-10-09-2052.txt.gz` (26.7 MB raw). Window: paper bids 2026-10-09 03:48Z to 2026-10-10 00:52Z (21 h). 57,753 paper bids, 823 fills (692 pregame, 131 live), 717 closes, **0 graded**.

## Read first: three caveats
1. **Nothing is graded, anywhere.** Phone (21 h) and GitHub lab (16 h+) both have zero GRADE events. `BidLab.grade` asks Novig's public market for the outcome's settled status, and Novig's public catalog drops a game and its markets once it is over (Scores.kt header, found 2026-09-27), so the result never arrives. ROI is n/a until grading uses final scores (ESPN/MLB, like the Tracker). Open item SS1.
2. **CLV here is mostly the margin.** CLV = last fair before the start / price - 1, and the fair is Vigilant's own. A bid priced m points under the fair keeps about m minus 0.5-1.5 points of CLV pregame (m2 2.1, m3 2.8-3.0, m4 3.1-3.6, m6 5.3). It shows the fair did not move away from the bid; it does not show the fair is right. Independent closes (Pinnacle/Circa via SGO close, Novig's own close) are the real test.
3. **Small, clustered sample.** 17 games had pregame fills; one game has 208 fills, another 151. Game-level mean CLV +2.98% over 9 games with closes (se 0.22). Fills are paper: a trade through the price after the bid went up, queue ignored. "Strictly through" is the conservative fill: pregame strict CLV +2.50% (439 fills, 13 games) vs touch-only +3.57%.

## Recipes (pregame)
| recipe | posted | fills | fill % | strict | CLV (n) |
| :- | -: | -: | -: | -: | :- |
| pre-m3-t120m-guard | 2876 | 128 | 4.5% | 77 | +2.98% (105) |
| pre-m4-t120m-guard | 2926 | 84 | 2.9% | 57 | +3.62% (70) |
| pre-m2-t30m | 5489 | 100 | 1.8% | 69 | +2.06% (86) |
| pre-m3-t30m | 5634 | 73 | 1.3% | 47 | +2.84% (64) |
| pre-m4-t30m | 5749 | 48 | 0.8% | 27 | +3.14% (41) |
| pre-m6-t30m | 5956 | 18 | 0.3% | 9 | +5.26% (14) |
- Resting 120 min instead of 30 min triples the fill rate (m3: 4.5% vs 1.3%) at the same CLV (+3.0 vs +2.8). Best data-backed default so far: **m3, rest 120 min**. m4/120 is the next (fewer fills, +0.6 CLV). m6 is under the 30-fill bar.
- The cancel-when-the-fair-moves guard changes nothing pregame (m2: 100 vs 102 fills, same CLV) or live (m1-m4 identical within 1 fill). Not shown to matter.
- Live recipes fill about 20x faster per bid-hour (0.57-0.78 vs 0.01-0.04) but keep less: CLV m1 +0.1, m2 +1.1, m3 +2.0, m4 +2.8 (about margin minus 2). Only 5 games; n 15-19 per recipe (under 30). Live fair here is SGO's, not Pinnodds'.

## Slices of pre-m3-t120m-guard (128 fills)
- Kind: PROP 94 of 1869 (5.0%, CLV +2.9%); TEAM_TOTAL 31 of 740 (4.2%); SPREAD 2 of 110; TOTAL 1 of 117; MONEYLINE 0 of 24. Props and team totals are where bids fill.
- Side: Under 6.7% (80 fills) vs Over 3.0% (46), same CLV (+2.9 vs +3.0). Unders fill 2.2x as often.
- Hours to start: <1h 4.1% (0.15 fills per bid-hour, the highest rate), 1-3h 8.3%, 3-6h 6.9%, 6-12h 5.4%, 12-24h 0.9% (7 fills), 24h+ 0.5% (1 fill). CLV flat 2.5-3.4% where measured. Consistent with the 6 h trap guard; nothing beyond 12 h fills.
- League: NHL 5.7% (77 fills, 47 strict), WNBA 4.7%, NCAAF 2.8% (only 10 of 23 strict), NFL 1 of 137, NBA 0 of 18.
- Books behind the fair: 5+ books 6.3% (101 fills) vs 1.4-2.7% for 2-4. Probably liquidity, not edge.

## Paper lab (would-be bets, all ungraded)
TAIL 21 bets on 4 games (listed edge median +6.2%), ALT 645 on 5 games (+11.9%, a listed edge against an outside fair: almost certainly overstated until graded), COVER 21 on 2 games (+7.1%). No verdict possible.

## What to log / build next
1. SS1: grade the lab (paper bids, would-be bets) from final scores (ESPN/MLB), the Tracker's own grader. Without it there will never be a result.
2. Add an independent close to each fill (SGO/Pinnacle close from the lab's `sgo-close` journal) so CLV is not self-referential.
3. Report game-level (clustered) CLV, not fill-level.
4. Keep recording until about 2026-10-16 (7 days, more games per recipe); then rank again on graded results and independent closes. Do not change the live Bids defaults from this file alone.
