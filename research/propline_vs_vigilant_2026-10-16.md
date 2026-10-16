# Why Vigilant did not list the +EV lines PropLine's /ev found (TCL65b; Tj 2026-10-16: "concerning ... important")

**Answer in one line: for player props the lines are not +EV. PropLine's `/ev` compares Novig's price with a devigged sharp/market fair; that fair runs high on prop Overs, and Vigilant's `PropHaircut` (-7 pts on an Over, -3 on an Under) removes exactly that. A 25,909-bet backtest on PropLine's own resolved archive shows the Overs PropLine calls +EV LOSE money.**

## 1. What the engine's own journal shows (propline-ev.json.gz, 2026-10-16 ~20:44Z, 511 rows)
- 427 rows were matched to a Vigilant-priced line (84 not). On those 427: median PropLine EV +1.9%, median Vigilant EV -0.05%, only 65 had Vigilant EV > 0, none >= 1%.
- Price is NOT the difference: Novig's price, PropLine's read vs Vigilant's live read, differs 0.05 pts (props). Game lines: Novig had moved 0.6-3 pts against the old PropLine price (moneyline -3.2, totals -0.6, spreads -1.0): PropLine's Novig read is ~1 minute old (props 15-45 min per its docs), Vigilant's is live.
- The FAIR is the difference. Props: PropLine fair 8.0 pts higher than Vigilant's (Pinnacle-sourced props: 0.355 vs 0.277; Kalshi: 0.165 vs 0.089). Game lines: 0.3-1.4 pts (Vigilant devigs WORST_CASE and blends more books).
- The 84 unmatched: 23 NHL "player goals Over 1.5" at +3025 / first-goal at +5456..+7592 with a lone Kalshi fair (thin-source longshots, not real edges; Vigilant's longshot caps and sharp rules rightly ignore them), 47 NCAAF alt spreads/totals on games 18-22 h out whose alternate lines the scan did not carry.

## 2. Backtest of the claim itself (tools/research/propline_prop_backtest.py on the `propline-archive-2026-10-16` Release: MLB + NHL, Novig and Pinnacle, 2026-07-18..10-16)
Bet Novig's CLOSING price on every prop with a two-sided Pinnacle close; "stated edge" = Pinnacle's devigged close x Novig decimal - 1; ROI measured on the real results.
| side | stated edge vs Pinnacle | n | actual ROI | won | Pinnacle fair |
|:--|:--|--:|--:|--:|--:|
| Over | +2..4% | 2,076 | **-12.1%** | 27.5% | 31.0% |
| Over | +4..7% | 1,753 | **-9.4%** | 22.8% | 25.5% |
| Over | +7..12% | 1,447 | **-13.6%** | 15.8% | 19.7% |
| Over | +12%+ | 1,117 | **-14.3%** | 11.7% | 16.4% |
| Under | +2..4% | 398 | +1.9% | 51.0% | 51.6% |
| Under | +4..7% | 210 | +9.9% | 49.5% | 48.2% |
Overs whose stated edge is positive win 2.7-4.7 points LESS than Pinnacle's fair; no PropLine +EV Over is a real edge. (Pinnacle's own devigged prop Over runs 1.5-2 pts high overall: MLB 32,818 pairs fair 40.5% vs won 39.0%, NHL 5,855: 41.4% vs 39.3%; and more where Novig is cheap, because Novig is cheap for a reason.) Unders are roughly calibrated; the +EV Under sample (608) is too small to call (t about 1.2).
**Verdict: PropHaircut is right in direction. Measured deficit is 3-5 pts against the sharp fair, 7 pts is cautious (Tj's own history measured -9.7 on his blended fairs). Not changed: no class B evidence yet that 7 is too much; revisit with the engine's graded prop Overs (n now 183 results, 36 with CLV).**
Game lines: archive totals/moneyline pairs are too noisy (alt-line point mismatches) to say anything; the live comparison above shows no hidden edge (Vigilant's EV ~0 at the live Novig price).

## 3. Fix
- The PropLine +EV screen now shows a CALIBRATED edge (fair minus Vigilant's own prop cut, same numbers) beside PropLine's, ranks and filters on it, and says why; a PropLine prop Over that is +7% raw is typically -1..-3% calibrated and no longer looks like a bet.
- The engine matches PropLine rows against every priced line, not only the shown list (more Bet buttons).
- Open: lines Vigilant never priced (build the bet from Novig's market + PropLine's fair), alt spreads/totals for far games.
