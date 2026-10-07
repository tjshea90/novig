# CHECKPOINT 2719 — read me first, then TASKS.md

**Written:** 2026-10-07T22:15:25Z · **tests:** all 4 fast checks green
**Branch:** `ccr-f8e1d0b1-qlnoqa` · **builds on:** `9c7126ba` (this checkpoint is the commit after it)

## Just done
DM3b done: CnoBidLane (data: wide read + page lane with a 3-page/step budget + stand-ins for bids whose row left the list + stop reasons), MakerRunner branches on makerSource (run/preview/post; never reads Vigilant's scan for CNO bids), AutoScanner.cycle calls cnoBids.step and runs the maker pass every cycle for CNO, container watchers skip scan-end passes for CNO and cancel auto bids when the source changes, MakerSetup.forCno, MakerDesk.held fix (a bet by hand holds a side with a bid resting), RESEARCH §113 + §114. Floor: all green except the wide-read pin, updated

## Do this next
DM3c surfaces: Settings (Bids priced from + age limit) + SettingsIndex entry, Bids tab (MakerScreen: source chips/explainer/stop banner/CNO ages, make NEEDS_VIGILANT etc. source-aware keeping default texts byte-identical), AtBets.bid scanner = bid source, BidReport source split + CNO age buckets, StudyExport BID_FIELDS + README, Diagnostics lane status line + HealthChecks, then DM4: mutants, screenshots, floor, CI, ship.sh, release.yml, record-release

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  f555eb44 ckpt 2718: DM3a done: BidSource + makerSource + makerCnoMaxAgeSeconds + bidsFromCno in 
  886a28fc ckpt 2717: DM1 done: wrote RESEARCH.md §113 (verdict: CNO prices are already old when 
  75aa246a ckpt 2716: DM1 findings so far written into TASKS.md (one page-wide CNO age, missing ag
  c4b02728 ckpt 2715: Saved the first 6 of 7 scout results of research workflow wf_edc6b432-17e to
  3e800174 ckpt 2714: DM1 research workflow wf_edc6b432-17e launched (7 read-only scouts: app age 
  bd668740 ckpt 2713: Tj's yes to CNO-only auto bids written into TASKS.md as DM1-DM5 (research ho
  b9874dc4 ckpt 2712: v0.74.0 RELEASED and recorded (https://github.com/tjshea90/novig/releases/ta
  897ed404 ckpt 2711: pre-release: v0.74.0: bets and bids told apart. One rule (TrackedBet.isBid) 
  6e640fc1 ckpt 2710: DJ4 + DJ5 done in the tree (Diagnostics + scan study split bets and bids; mu
  435bc978 ckpt 2709: DJ2 + DJ3 done in the tree: bid/bet rule + tagBids wiring, Tracker Bets/Bids
```

(19 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
