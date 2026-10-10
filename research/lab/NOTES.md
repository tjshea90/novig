# Research lab notes (newest at the bottom). Raw data: `lab-archive-*` Releases. Rules: STEWARD.md.

## 2026-10-09 (setup, steward session 01CNZcNj)
- GitHub lab running since 2026-10-09 08:30Z: 330-minute runs every 6 h, back to back. Run 6 (18:04Z-23:34Z) was the first with SportsGameOdds ON (1 key): 45,766 paper bids posted, 315 fills, 68 scanner passes, 645 paper-lab cycles (71 alternate-line would-be bets in the last pass).
- SGO tape, same run: DraftKings prices read about 523 s old (median), refreshing about every 329 s; 1.75 M price changes written.
- Retention fixed today: the `lab-data` branch is a 7-day snapshot only, so the workflow now also uploads every run's raw journals to a `lab-archive-YYYY-MM-DD` Release (never pruned).
- Not analysed yet. READY FOR ANALYSIS target: about 2026-10-16 (7 days, 30+ fills per recipe), stop 2026-10-19.

- 2026-10-10 00:05Z: archive backfilled by the first sweep run (38007075086): release `lab-archive-2026-10-09` has 14 assets (bidlab, bidlab-event, edge, lab, sgo-close, sgo-tick; runs ending 08:51, 18:02, 23:35). Daily steward routine trig_01Ks2vjbokaZdSuTrpoz4m9i created (08:47 ET).

## 2026-10-10 (steward session): first analysis of Tj's phone file (v0.84.1, 21 h)
- Full write-up: `research/lab_analysis_2026-10-10_phone_v0.84.1.md`. Data: `research/uploads/2026-10-10/b0cf754a-...research...txt.gz`.
- 57,753 paper bids, 823 fills, 717 closes, 0 graded. **Grading is broken** (both phone and GitHub lab): Novig's catalog drops settled markets, so `BidLab.grade` never sees a result. Fix = grade from final scores (TASKS SS1).
- Best default so far: margin 3 points, rest 120 min (4.5% fill, CLV +3.0%). The guard shows no effect. Props and team totals fill; moneylines/spreads/totals barely. Unders fill 2x Overs at equal CLV. Nothing beyond 12 h before the start fills.
- CLV is mostly the margin (the fair is Vigilant's own); 17 games with fills. Not a profit claim.
