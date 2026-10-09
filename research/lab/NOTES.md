# Research lab notes (newest at the bottom). Raw data: `lab-archive-*` Releases. Rules: STEWARD.md.

## 2026-10-09 (setup, steward session 01CNZcNj)
- GitHub lab running since 2026-10-09 08:30Z: 330-minute runs every 6 h, back to back. Run 6 (18:04Z-23:34Z) was the first with SportsGameOdds ON (1 key): 45,766 paper bids posted, 315 fills, 68 scanner passes, 645 paper-lab cycles (71 alternate-line would-be bets in the last pass).
- SGO tape, same run: DraftKings prices read about 523 s old (median), refreshing about every 329 s; 1.75 M price changes written.
- Retention fixed today: the `lab-data` branch is a 7-day snapshot only, so the workflow now also uploads every run's raw journals to a `lab-archive-YYYY-MM-DD` Release (never pruned).
- Not analysed yet. READY FOR ANALYSIS target: about 2026-10-16 (7 days, 30+ fills per recipe), stop 2026-10-19.
