# PropLine /v1/books/accuracy: which books price props closest to the result (PL-OA, 2026-10-24)

Source: `propline.yml` task `accuracy` (90 days, closing two-way prop price de-vigged, Brier vs result and vs the other books' average; skill_bp > 0 = closer than the market; verdict only when the 95% range excludes zero). Raw answers: `research/propline/reports_2026-10-24/`. Spec: `research/propline/openapi_2026-10-24.json`.

| sport (props) | beats market | trails market (CI excludes 0) |
| :- | :- | :- |
| MLB (920,848) | Kalshi +3.8bp | Pinnacle -4.5, DraftKings -3.7, BetMGM -4.7, Hard Rock -2.5, Novig -1.3, Bovada, Smarkets, Polymarket US, FanDuel (small n) |
| NBA (47,429) | Novig +10.4 (CI 0.9..19.8) | Hard Rock -7.0, Kalshi -20.4, Courtside, 1xBet |
| NHL (124,256) | none | Hard Rock -8.7, BetMGM -10.5, theScore -3.7, Sportzino -2.7 |
| NFL (120,427) | none | Novig -6.3, ProphetX -12.6, Courtside |

Reading: no book is sharp on every prop market. Pinnacle's MLB props trail the field, so a "sharp book" rule (and the fair's weight) should not treat Pinnacle as better than the consensus on MLB props; Kalshi is the one that beats it there, and Novig beats it on NBA props but trails on MLB and NFL props. Use it as a LEVER for the SX star/weight study (research/star_rating_sx_2026-10-24.md): weight books per sport and market class by skill, not by name. Not applied to any setting yet (class B gates: one more independent source, e.g. our own CLV by book, before a change).
Unused endpoints still open: coverage/history, injuries/history, projections (paid), freshness (keyless; saved).

## Update 2026-10-25 (PSL7: prop-line.com/sportsbook-accuracy, 30 days, 485,939 closing prop prices, 24 books; saved in research/propline/docs_2026-10-25/sportsbook-accuracy.txt)
- No book beats the market average by a clear margin on props. Kalshi is sharpest (+2.0 bp, CI -0.7..+4.6, margin 3.8%); Fanatics, BetOnline/LowVig, Fliff are in line.
- **Pinnacle TRAILS the market on props** (-2.8 bp, CI -5.2..-0.4), as do Novig (-2.7), theScore, Hard Rock, DraftKings, Bovada, FanDuel, BetMGM, ProphetX, Polymarket US.
  By sport: MLB Kalshi/Fanatics lead; NHL Novig +1.9 leads; NFL BetOnline/Fliff lead (small n), Novig -6.6 trails; NBA Novig +4.3 leads (in line).
- Reading for the fair line: on PROPS the many-book devigged average is the better forecaster than any single "sharp" book; a rule that requires Pinnacle to agree on a prop
  (sharp rule) adds little information there. Game lines are not in this report (Pinnacle's game-line sharpness is not in question).
- Not applied (class B: the star study, research/star_rating_sx_2026-10-24.md, already found outside-books edges did not beat Novig's close). Lever: research/LEVERS.md "prop calibration".
