# Active log, 2026-10-07 (Tj's request: "keep an active log so the next Claude session can pick up where you were interrupted")

Newest entry LAST. Each line: UTC time, what was finished, what is next. A cold session: run `bash tools/install-hooks.sh`, read this file, CHECKPOINT.md, TASKS.md (CURRENT JOB: DG1-DG8, DH0-DH6), then continue from the last "NEXT".
Scheduled check-ins: 11:01Z and 16:11Z (finish anything unfinished, then stop).

- 05:50Z v0.71.2 released (OUT-player fix). 06:00Z obscure-bid fill BUILT in the tree (v0.72.0 candidate): evidence agent says 6% margin / 0.5 stake / default ON is supported (research/obscure_bid_study_2026-10-07.json); adversarial review found 6 defects, all fixed with tests (research/obscure_bid_review_2026-10-07.md).
- 06:30Z DG6 (rec 11) done: ApiSettler.wonByOtherLeg. DG1 (rec 2) done: FirstListed + TrapGuard.listedEarly. DG2 (rec 4) done: favourites need +1 point (autoBetFavouriteExtraEv), plus-money chip relabelled. DG3 (rec 5) done: lower of CNO edge and books' check edge for gate and Kelly size. DG5 (rec 10) done: unread Novig trades skip game-line auto-bet and bids. Each with tests and mutants killed. Floor 2438 green at 07:00Z.
- 07:00Z DG4 (rec 9) code done: KalshiClient fastPace test at 3 req/s (setting kalshiFastPace default ON, Diagnostics line); tests in ExchangeClientsTest and DiagnosticsTest green. Tj sent new asks (TASKS.md DH0-DH6) and two v0.71.2 files; check-ins scheduled.
- NEXT: tick DG4, floor, checkpoint; DH1 release v0.72.0 (RESEARCH 102 and 104, version, CI, ship, release, record); then DH2 feed sources test, DH3 analysis of the v0.71.2 files, DH4 full tests, DH5 API audit.
