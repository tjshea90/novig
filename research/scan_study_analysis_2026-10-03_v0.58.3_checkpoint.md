# Vigilant scan study analysis — CHECKPOINT (resume file)
Source file: vigilant-scan-study-v0_58_3-2026-10-03-2248.txt (upload again if sandbox reset). Parser: /home/claude/work/load.py (reads JSON lines after '== EVERY BET').
Task = the 8-point task in the file's READ ME FIRST. Status log below, newest last.

## Step 1 DONE — data check
- 622 bets, 0 duplicate ids. firstSeen 2026-10-03 17:56 -> 22:47 EDT (ONE evening, ~5h). So hour-of-day / multi-day splits are meaningless.
- Starts: 191 on Oct 3, 416 on Oct 4 (NFL Sunday, ungraded), 15 later. Graded 47 (25W-22L), all from Oct 3 games. 575 pending.
- Closes: 43/622. ESPN-DK 21, Tracker 20 (only bets Tj placed -> selection bias), ParlayAPI-Pinnacle 2.
- The 2 Pinnacle closes are BAD (Wash St ML -117 -> +272, CLV -50%; Arkansas St 186 -> 216, -9%): wrong side/game match. Exclude.
- 20 of 43 closes are Tj-placed bets (Tracker read) => not a random sample.

## Steps 2–8 DONE — report written
- Full report: Vigilant_scan_study_analysis_v0.58.3.md (same folder). Nothing left unfinished for this export.
- Headline: file summary distorted by 2 bad Pinnacle closes; clean CLV +2.20% (41 closes, 23 games) but yardstick has ~+1.4pt level offset vs listed EV (EV<0.5% bets show +1.7%), so only group differences matter and none are significant.
- Results on expectation (25 W vs 24.2 expected). Props sharp-book question NOT answerable: 33/365 props judged; require-sharp keeps 22 (-94%).
- Next action when resuming: re-export after Sunday NFL games (Oct 4-5) and rerun the same checks (load.py parses JSON lines; clean = drop closeVia starting 'ParlayAPI' until matcher fixed).
