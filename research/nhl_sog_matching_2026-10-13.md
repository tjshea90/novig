# NHL shots on goal: are the Vigilant scanner's +EV props matched to the right odds? (Tj, 2026-10-13)
Question: "flooded with nhl player shots on goal props ... they may be showing positive EV because they are incorrectly matched to a different type of bet odds."
Data: the 1,648 records (25 Sep - 13 Oct, `research/uploads/`), 165 NHL shots-on-goal (SOG) bets and bid fills; independent closes (Pinnacle via SGO/ParlayAPI) on 86.

## 1. Matching: correct by construction, now pinned by tests
A book's quote joins a Novig prop's fair line only if stat, line, period and player are all equal (`LineKey.matches`) and the book prices BOTH sides at that line (`Pricing.chosenMarkets` drops a book that lacks either side). So a different stat at the same number (goals, points, assists, saves), another line of the same player's shots, the 1st period, a team/game total, the other Tkachuk, or an alternate ladder with one side cannot reach a SOG line. `ShotsOnGoalMatchTest` (4 tests) pins each. Source tables: ParlayAPI `player_shots_on_goal`, SGO `shots_onGoal`, OddsPapi `shotsongoal`; PropLine, Unabated and BoltOdds carry no NHL SOG mapping, so they price none. Nothing here has met a live SOG payload from this container: the proof is the data below.

## 2. What the data says (no sign of a mismatch, but the fair is generous)
- A mismatch (wrong stat/line/side) would show as big gaps against the independent close. SOG: gap fair-minus-close has sd 2.4 points over 86 bets, none over 10 points; no wrong-side signature (Over and Under both gap the same direction by about +2).
- But the Vigilant scanner's SOG fair is HIGH against Pinnacle's close: mean fair - close = **+2.1 points** (t = +3.5, n = 24: 7 taker bets +3.4, 17 bid fills +1.5), against +0.5 for Vigilant's other props (n = 115) and +0.4 for CNO's SOG (n = 62). Average listed EV 4.1%, average CLV **-0.3%** (other Vigilant props +2.7%).
- It is not the bet type, it is thin early books: gap by lead time 12 h+ = +3.1 points (n = 14), 2-6 h +1.1, under 2 h +0.1; by books in the fair 3-4 books = +3.0 to +3.7, 8+ books = about 0. Worst cases: Timo Meier Under 2.5 fair 0.473 from 4 soft books vs Pinnacle close 0.378; Will Smith Over 2.5 0.512 vs 0.421.
- Placed SOG overall: win 46.9% vs fair 50.0% (z -0.8), ROI -10.2% over 162, CLV +1.9% (CNO bets carry that).
- The bid desk reaches games 12-24 h out and fills there, which is where the gap is.

## 3. Options (not built: Tj decides)
(a) A SOG/prop fair built from fewer than 5 books and a game over 12 h away takes a 2-3 point haircut, or is not bet; (b) require a real sharp price (Pinnacle/exchange) for NHL SOG, not BetOnline's alone; (c) leave it and rely on the small-prop guard (share 25%, 3 per game). The sample is 24 independent closes: say the t-statistics, re-run when more arrive (`python3` over `research/uploads/*bets*.jsonl.gz`: fields fair, closeFair, closeIndependent, leadMin, books).
