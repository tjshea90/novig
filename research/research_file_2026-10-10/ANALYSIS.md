# Research file v0.85.5, made 2026-10-10 03:37 ET (81,809 paper bids, 951 fills, 1,417 ALT / 169 TAIL / 39 COVER would-be bets) — analysis, 2026-10-10

Upload: `research/uploads/2026-10-10/24d7e4e3-vigilant-research-v0.85.5-2026-10-10-0337.zip` (the 39.8 MB text, zipped). Intervals are bootstrap 95% over GAMES. **This is a paper study: no queue position, no Novig in-play order delay (5.3 s), and "CLV" is against the app's OWN last fair (SportsGameOdds), not an independent close. Nothing here changes a rule.**

## What the file says, plainly
1. **Pregame paper bids: longer rest beats shorter, a bigger margin trades fills for edge, the guard changes nothing.**
   | recipe | fills (games) | CLV | strict fills |
   |---|---|---|---|
   | pre m2 rest 30 min | 99 (14) | +2.12% [+1.43, +2.80] | 70% |
   | pre m3 rest 30 min | 73 (11) | +2.93% [+2.07, +3.64] | 64% |
   | **pre m3 rest 2 h (guard)** | **126 (13)** | **+3.09% [+2.71, +3.64]** | 61% |
   | pre m4 rest 2 h (guard) | 85 (12) | +3.82% [+3.51, +4.39] | 66% |
   | pre m4 rest 30 min | 49 (11) | +3.39% | 55% |
   | pre m6 rest 30 min | 19 (8) | +5.57% | 47% |
   Fills × CLV: **m3 / 2 h rest** (126 × 3.1 ≈ 390) beats m4 / 2 h (85 × 3.8 ≈ 325) and every 30-minute recipe. The same margin rested 2 h instead of 30 min gets 1.7× the fills at the same CLV. "Guard" vs no guard: identical fills and CLV to the decimal (the pre guard never fires on this data). The README's bar is 30 fills; the sample is only 11-15 games, and EV at post (+3.6%) ≈ CLV (+3.1%): **the fair barely moved after the fills, which is what a stale fair also looks like** (no independent close).
2. **Where the fills are** (pre m3 / 2 h): 1-3 h to start 8.2% fill rate, 3-6 h 6.9%, under 1 h 4.2% (0.15 per bid-hour, the highest rate), 6-12 h 2.9%, **12-24 h 0.6%, 24 h+ 0.5%** (4 fills in 1,460 bids at 12-24 h). Same shape as the scan study's real bids (ANALYSIS in `research/scan_study_2026-10-10/`): **bids more than ~12 h out almost never fill.** CLV by hour is flat (+2.7..+3.6%), so the loss from long bids is wasted posts and API budget, not worse fills.
3. **Kind and league:** props 94 of 126 fills (CLV +2.9%), team totals 28 (+3.6%), moneylines/spreads/totals 1-2 each (no data). NHL 77, WNBA 28, NCAAF 20, NFL 0, MLB 0 in that recipe. "Under" fills 5.0% vs "Over" 2.3%. Books behind the fair: 5+ books 4.9% fill rate vs 1.0-2.0% for 2-4 books.
4. **Live paper recipes (SportsGameOdds alternate lines, 2-minute rests): 6 games in all** (4 NHL, 1 NBA, 1 NCAAF), so the 24-30 fills per recipe are ONE sample of ~25 fills seen through every margin. CLV rises with the margin as arithmetic says (m1 +1.7% → m4 +4.9%, m3 +4.0% [+1.15, +6.66] over 5 games), ~80% of fills strictly through the price, median fill 52 s after posting (10% inside 2 s). The guard again changes nothing (29 vs 30 fills). By kind: spreads +6.8% [+3.2, +8.6] (91 fills, 5 games), totals -0.15%, moneylines +2.9%. **Too few games to call any live recipe better; and this model has no 5.3 s delay, so it is not evidence against §123's finding that the delay costs about half the edge.**
5. **Paper lab:** TAIL (a late-game strike the game has decided): 18-0 graded, ROI +17.4%, **6 games**; ALT 1,417 would-be bets (listed edge median +14%) and COVER 39 have no result yet; ALT edges that large against Pinnacle's alternate lines are mostly stale quotes: untested.
6. Grades: 28 graded paper bids in 81,809: results are useless; CLV is the only usable number.

## What this supports (the data suggests; Tj decides)
- For real pregame bids: **~3% margin, resting about 2 hours, posted 1-6 hours before the start** (the scan study's real bids say the same on timing: CLV +3.5% under 2 h, +2.0-2.2% at 2-12 h, -0.7% at 12-24 h). Longer rests, not bigger margins, is where the extra fills are.
- **Do not pick a live recipe from this file.** The live bids shipped as v0.85.x use Pinnacle's live fair with the order delay modelled (RESEARCH.md §124) and are untested on real fills: that remains the test.

## Log next time
Per paper bid: the fair 30 s and 120 s after the fill (a stale fair shows as a fair that moves AFTER the fill) and an independent close (Pinnacle where it exists, Novig's last trades otherwise), the fill's `strict` flag with queue-ahead contracts, and live-recipe fills with the game state (score, clock) at the fill.
