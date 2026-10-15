# Are the auto-bet filters too strict? First look (Tj, 2026-10-15: "you have full reign to change filters and settings ... if they prove too strict or not necessary for profit")

Data: `review-digest` branch `latest.txt` of 2026-10-15 15:07Z (1,511 logged +EV bets, game days Oct 13-21, graded and closed by the app's own code). CLV = how far the price beat Novig's closing line; it needs far fewer bets than results.

## 6-hour trap guard (`trapEarlyHours`): KEEP, the data supports it
Lead time at first listing (the "lead" split, n>=15): `<1h` CLV +2.9% (183 closes) · `1-3h` +2.3% (137) · `3-6h` +2.0% (159) · `6-13h` -0.6% (184) · `>13h` -0.1% (447). The "Time to the start" table says the same (24 h+: -0.68% on 16 closes; 6-24 h: 0.00% ±2.24 on 90; 2-6 h: +0.30%; 30 min-2 h: +0.30%; under 30 min: +1.00% ±0.64, beat the close 75%). Bets taken more than 6 hours out sit at about zero CLV; inside 6 hours it is +2 to +3 points. Loosening the guard would add volume at ~0 edge. Do not loosen. (If volume stays too low, widen from 6 to 8 or 9 h only if a later digest shows 6-9 h bets at CLV above +1% with 100+ closes.)

## Three books agreeing (`AutoBet` books-agree) and the bet kinds: NO PROOF either way yet
Books agreeing: the splits are noisy (ROI swings -40% to +64% on 10-20 bets per cell, intervals of +-60 points); nothing here shows 3 is too strict. Kinds: player props CLV +1.01% ±0.43 (193 closes) is real; team totals +1.82% (10 closes, too few); game totals +0.46%; spreads -1.01% ±2.51; the app's kind filter is the user's own list, so a kind may be added when its CLV has 100+ closes and an interval above zero.
Verdict: nothing proven too strict yet, so nothing changed on this data. The volume problem on 2026-10-15 was plumbing, not rules: 27 of 31 listed bets were refused for a Novig price older than 3 minutes (fixed: re-read before judging, v0.109.x engine). Re-judge with the engine's `tradepass-<day>.jsonl` (held-back reasons per pass) after a day of live passes.
