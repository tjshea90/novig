# Novig's OPENING price against Pinnacle's devigged CLOSE, with the real results (PropLine resolved archive; TCL66b; 2026-10-16)

Method (tools/research/propline_open_vs_close.py, run on the `propline-archive-2026-10-16` Release: MLB, NBA, WNBA, NHL, NFL, NCAAF, Novig and Pinnacle, 2026-07-18..10-16): for every graded Novig Over/Under outcome with a two-sided Pinnacle close at the SAME line, CLV = Pinnacle's devigged close x Novig's opening decimal - 1 (what an early taker would have had), ROI = the real result at that opening price. Opening = the first snapshot in the 14 days before the start (often early and wide), so the all-bets mean is negative; the question is the SELECTED bets with CLV >= +2%.

| bets with CLV >= +2% at Novig's open | n | win | ROI at the open |
|:--|--:|--:|--:|
| Game totals Over | 528 | 50.0% | +9.4% |
| Game totals Under | 943 | 49.7% | +9.7% |
| Prop Under (all sports) | 731 | 54.0% | +7.2% (NHL +23.9% n=102, MLB +5.8% n=510) |
| Prop Over (all sports) | 3,918 | 21.0% | **-5.9%** (MLB -7.1% n=3,426; NHL -2.0%; NFL +9.2% n=194) |
| NFL game totals (both sides) | 154 | ~60% | +31..38% (small) |
| NCAAF game totals | 788 | 49% | +4..6% |

Read: (1) **game totals bought early at a price Pinnacle's close beats by 2%+ really pay** (t about 3.7 on 1,471 bets): the engine already leans there (ledger: totals and spreads lead). (2) **prop Overs flagged +2% lose**: confirms PropHaircut (second independent test; the first was at the close: research/propline_vs_vigilant_2026-10-16.md). (3) prop Unders flagged +2% earn +7% (t about 1.9, n=731): suggestive that the 3-point Under cut is a little heavy, NOT enough for a class B change (interval touches zero; same games as the first test). Revisit when the engine's own graded Unders reach 200.
No rule changed. Metric to watch: engine orders on game totals/spreads vs props per day and their CLV in the lines dataset; revert rule n/a.
