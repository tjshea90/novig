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

## 2026-10-10 — grading fixed (v0.84.2)
- Cause: Novig drops a settled market, so the status BidLab/LabRecorder waited for never came (0 GRADE events). Now graded from final scores (`LabGrader`: SGO, OddsPapi, ESPN, MLB via `BetGrader`); live YES/NO ladder sides, pregame bids, TAIL/ALT records (covers are not graded).
- Props: stat type kept in the FILL event text from v0.84.2; older prop fills stay ungraded. Period markets are not graded (name does not say which period).
- Old fills in the journals are graded on the next pass once the phone/lab runs v0.84.2 (the GitHub lab restores them from its journals).

- 2026-10-10 (scan study v0.85.3, analysed): `research/scan_study_2026-10-10/ANALYSIS.md`. Against sharp closes the lists find real edge (CLV +1.18%, EV>=2% & start<=6h: +5.2% on 62 bets/46 games, both halves); against Novig's own closes (NFL/MLB props) CLV is -1.2%. Bids: CLV +2.1% (126), 1% picked off, but 12-24 h bids burn 62% of bid-hours for 15% of fills (CLV -0.7%). Decisions for Tj listed in section 6; none applied.

- 2026-10-10 (research file v0.85.5, analysed): `research/research_file_2026-10-10/ANALYSIS.md`. Pregame paper bids: margin 3% resting 2 h = most fills x CLV (126 fills, +3.1%, 13 games); longer rest = 1.7x fills at the same CLV; guard changes nothing; bids 12 h+ out almost never fill (0.6%). Live paper recipes rest on 6 games: nothing to rank. CLV is against the app's own fair: EV at post ~ CLV, so not an independent test.

- 2026-10-10 13:35Z (steward check-in, interactive session): lab-record run 9 in progress (started 12:24Z, schedule), run 8 succeeded; `lab-archive-2026-10-10` has assets through the 10:42Z run (bidlab, events, edge, lab, sgo-tick, sgo-close, report). SGO on. Nothing wrong. **The 08:47 ET routine (trig_01Ks2vj...) fired at 12:47Z but its fresh session had no repo access and no GitHub tools, so it could not push**: routines cannot do the check-in as built. Until that is fixed, any interactive session with GitHub tools does it (resume.sh shows the LAB staleness line).

- 2026-10-10 (Tj): **LAB STOPPED.** "Stop the lab and remove the routine." The cron is gone from lab-record.yml (manual `workflow_dispatch` still works) and the daily check-in routine is deleted. Run 9 (12:24Z) was left to finish so its journals are archived; after it ends, check that `lab-archive-2026-10-10` has its assets. Nine runs in all: findings in the two ANALYSIS.md files above. Not done: `research-record.yml` (the older daily Novig-vs-Kalshi recorder, 23:00 UTC) is a separate workflow and still scheduled.
- The routine could not be deleted from this account (it lives on the steward's account): STEWARD.json's claimId was changed to `stopped-20261010`, so the routine's session stops at step 1. Tj can delete it in Routines on that account.
- 2026-10-10 (Tj): `research-record.yml` stopped too: its daily 23:00 UTC cron is removed (manual runs still work). Both GitHub recorders are now manual-only.

- 2026-10-10 (Tj's 15 odds-source links, TM): read all; see `research/odds_sources_2026-10-10.md` and RESEARCH.md §126. Headline: Pinnacle's guest website API (free, whole-sport live markets incl. alternates, same shapes as the Pinnodds frames) is the candidate replacement for the Pinnodds socket after 23:34Z tonight; freshness unmeasured. Soft-book per-event JSON endpoints (Bovada works from the container). OddsCorp / OddsShopper trials worth a request.
