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
- 2026-10-11 (TAC): the six identical "agreeing" books (Bally Bet, betPARX, Casumo, Grosvenor, TABtouch, Unibet) all run Kambi's sportsbook: one desk, now one company in BookCompany (research/book_company_kambi_2026-10-11.md).
- 2026-10-12: rapid NBA live betting is not +EV with Novig's ~5.3 s in-play order delay (RESEARCH §120.2 item 4, §125); alternatives = tail bettor + guarded live bids: research/live_rapid_nba_plausibility_2026-10-12.md
- 2026-10-12 NOVIG v0.91.0 first evidence (Tj's diagnostics): bbo works, scans 115 s -> ~25 s, pushed orders work; stream allowance still shows the documented 512 / 4 a second (source unknown) and the canary timed out inconclusive. research/novig_v0.91.0_first_evidence_2026-10-12.md
- 2026-10-12 v0.92.0 evidence: order socket 60 ms ack, compression 3.4x; reconnect regression fixed in v0.92.1 (research/novig_v0.92.0_evidence_2026-10-12.md)
- 2026-10-12 TPF: profit by type from the 1,331-bet history (research/profit_types_2026-10-12.md): NFL player props (esp. Overs, t=-2.6) and 4-6% EV bets lose; team-total bids win (+34%, t=2.3); profile + 'Profit-proven only' switch built (v0.93.0)
- 2026-10-12 v0.93.3: Entain family (bwin, Party Sports, Sports Interaction, Ladbrokes, Coral, Neds, ...) and BetMGM are one company in every book count (research/book_company_2026-10-12.md).
- 2026-10-12 Tj's v0.94.1 diagnostics: BoltOdds has no sharp book (live fair stays Pinnodds), key is on Trial plan; streaming engine updates were starved by full scans (fixed v0.94.2). research/streaming_v0.94.1_first_evidence_2026-10-12.md
- 2026-10-12 v0.95.0: BoltOdds live (is_live parsed), soft-book average live fair (BoltLiveConsensus/BoltLiveFeed, PinnFeedChoice.BOLT, paper-only until boltLiveReal); streaming engine now push (addGameListener). BOLTODDS_API.md §10.
- 2026-10-12 v0.95.1: live tennis on the BoltOdds live feed, feed explains why it has no game, BoltOdds conversion cached per entry (refresh was 7-18 s), stream update timing breakdown. research/boltodds_live_v0.95.0_first_evidence_2026-10-12.md
- 2026-10-12 v0.95.2: BoltOdds live feed note in Diagnostics, tennis subscription fixed (info sport 'Tennis'), refresh hand-off only +EV rows, conversion cache keyed on last-heard. research/boltodds_live_v0.95.1_evidence_2026-10-12.md
- 2026-10-12 v0.95.2 diagnostics read (research/boltodds_live_v0.95.2_evidence_2026-10-12.md): BoltOdds live feed has 0 games: 9 'in play' flags, none with a fresh quorum; NBA flags likely preseason not-live; per-game detail added to the note; re-subscription rate limit seen.
- 2026-10-12 PropLine Streaming Lite evaluated from vendor docs (research/propline_streaming_lite_2026-10-12.md): Pinnacle is polled not pushed; only DK/FD/Fanatics/Kalshi/Polymarket push; not a Pinnodds replacement; useful for suspend events and a 250k REST ceiling. Not built.
- 2026-10-12 v0.95.3 diagnostics read (research/boltodds_live_v0.95.3_evidence_2026-10-12.md): BoltOdds live feed builds 1 live game with quorum; engine not run (feed off); NBA flags likely stale.
- 2026-10-12 PropLine vs ParlayAPI pregame comparison (research/propline_vs_parlayapi_pregame_2026-10-12.md): PropLine far cheaper per scan, ParlayAPI one-call props + measured ages; keep both.
- 2026-10-12 second v0.95.3 file: BoltOdds off; refresh 'boards read' 7-14 s traced to conversion cache keyed on aliveAt; fixed (v0.95.4). PropLine only 72 calls on 3 free keys, no info on quality.
- 2026-10-12 subscription advice (research/subscription_advice_2026-10-12.md).
- 2026-10-12 follow-up advice: free keys beat PropLine Pro for scanning; SharpAPI not worth it (research/subscription_advice_2026-10-12.md).
- 2026-10-12 TFR: keyless freshness measurements (research/api_freshness_2026-10-12.md), shopping list (research/api_shopping_list_2026-10-12.md); built 'Freshest quote wins' + Diagnostics Source race.
- 2026-10-12 first API study (research/api_study_v0.95.5_2026-10-12.md): SGO median quote age 229 s; ParlayAPI slowest read; PropLine unmeasured -> API study mode in v0.95.6.
- 2026-10-13 second study file read: PropLine median 0 s p90 70 s (real stamps), SGO 334 s, Unabated tail hours; chain is sequential so a scan waits for every better feed; SourceAgreement (vs Pinnacle) added.
- 2026-10-13 TAQ built: FairProvenance (bet says which API/age per book), SourceScore + ApiChain measured ranking + audition, SourceAgreement vs Pinnacle; research/api_logistics_2026-10-13.md.
- 2026-10-13 budget plan (research/api_budget_plan_2026-10-13.md): buy ParlayAPI $5 only; farm Pinnwire/pinnapi/PropLine free keys.
- 2026-10-13 copy-the-sharps research (research/copy_sharps_2026-10-13.md): individuals anonymized; liquidity-following -1% EV; whale takers n.s.; sharp book prices already the core.
- 2026-10-13 true profit on money put in: MoneyIn replay (least deposit with winnings re-bet) + optional Settings figure; Tracker 'Your money, all time' card; Diagnostics line; chart's % relabelled 'Per $ staked'.
- 2026-10-13 odds-api.net researched (research/odds_api_net_2026-10-13.md): Pinnacle polled 60-120 s, pre-match only, no free tier, sharp books only Pinnacle+Betfair confirmed; not worth $50.
- 2026-10-13 profitable-types study (research/profitable_types_2026-10-13.md): team totals the one type with profit+CLV+n>100; player props lose (-$122/989); bids not proven by ROI; time <6h beats close, 24h+ gives it away.
- 2026-10-13 all daily bet files received (1648 unique): refresh in research/profitable_types_2026-10-13.md; team totals bids +29.8% n=73 (NHL best), bets -9%.
- 2026-10-13: NHL shots-on-goal matching audited (`research/nhl_sog_matching_2026-10-13.md`): matching is exact (stat+line+period+player, both sides) and now tested (`ShotsOnGoalMatchTest`); Vigilant's SOG fair runs +2.1 pts above Pinnacle's close (t +3.5, n 24), concentrated at 12 h+ and 3-4 books; options (a)-(c) wait for Tj.
- 2026-10-14 TWF: v0.97.3 file read (research/diagnostics_v0.97.3_2026-10-14.md): sockets were off; lab = 67% of Novig reads; public 429s = lookups that did not slow the gate; bids keep the wallet working (v0.98.0).
- 2026-10-14 bet history (1,915): stated EV overstated for underdogs (+110 or longer, n 685, gap -15.6 pts, t -3.8) and player props (n 852, gap -13.3, t -3.8); even-money bets match their EV. Proposals in research/bet_history_2026-10-14.md; profit_profile.json regenerated.
- 2026-10-14 CLV vs losses: the whole loss is prop taker bets (-$114 of -$100); the de-vigged close is ~8 pts too high for prop Overs (won .410 vs close .486, n 542, z -3.6), so CLV and EV both overstate; addendum in research/bet_history_2026-10-14.md.
- 2026-10-15 first GitHub review digest (`review-digest` branch, research/review/2026-10-15.md): 1,440 scanned bets, 315 closes (44%), CLV +0.51% ±0.73 (not separable from zero), ROI noise (±10.5 pts, 49 games); supported on CLV: player props +1.12 ±0.37 (190 closes), starts under 30 min +1.16 ±0.58; Tj's placed props still pool to ROI -10.5%, t -2.98 (the 2026-10-14 finding, fixed in v0.99.4); digest now splits placed bets before/after v0.99.4 (108 after: too few to judge).

- 2026-10-20 ANALYST: engine live ROI +20.8% n=281; NBA/WNBA/game totals weak but n<200; see research/review/2026-10-20_analyst.md
