# CHECKPOINT 2720 — read me first, then TASKS.md

**Written:** 2026-10-07T22:25:07Z · **tests:** all 4 fast checks green
**Branch:** `ccr-f8e1d0b1-qlnoqa` · **builds on:** `9c7aa408` (this checkpoint is the commit after it)

## Just done
DM3c done: Bids tab (Bids priced from chip, CNO age limit chips, CNO status line, stop banner, captions), Settings search entry, AtBets.bid scanner = bid source + CNO ages, BidReport source + age-band splits, study BID_FIELDS + README, Diagnostics lane line, HealthChecks; recommend mode reads Novig at the cycle's pass. Tests added in MakerUiTest, DiagnosticsTest, BidReportTest, MakerAppTest

## Do this next
DM4: mutants on the new tests (age rule, both-sides orientation, sharp requirement, unknown list age, held fix, stop, scan-end passes skipped), screenshots of the Bids tab with CNO source, full floor, bump version (BUILDLOG.md / app/build.gradle.kts versionCode above every BUILDLOG entry), ship.sh, wait CI green, trigger release.yml, confirm Release, record-release.sh, then DM5 answer Tj

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  04317943 ckpt 2719: DM3b done: CnoBidLane (data: wide read + page lane with a 3-page/step budget
  f555eb44 ckpt 2718: DM3a done: BidSource + makerSource + makerCnoMaxAgeSeconds + bidsFromCno in 
  886a28fc ckpt 2717: DM1 done: wrote RESEARCH.md §113 (verdict: CNO prices are already old when 
  75aa246a ckpt 2716: DM1 findings so far written into TASKS.md (one page-wide CNO age, missing ag
  c4b02728 ckpt 2715: Saved the first 6 of 7 scout results of research workflow wf_edc6b432-17e to
  3e800174 ckpt 2714: DM1 research workflow wf_edc6b432-17e launched (7 read-only scouts: app age 
  bd668740 ckpt 2713: Tj's yes to CNO-only auto bids written into TASKS.md as DM1-DM5 (research ho
  b9874dc4 ckpt 2712: v0.74.0 RELEASED and recorded (https://github.com/tjshea90/novig/releases/ta
  897ed404 ckpt 2711: pre-release: v0.74.0: bets and bids told apart. One rule (TrackedBet.isBid) 
  6e640fc1 ckpt 2710: DJ4 + DJ5 done in the tree (Diagnostics + scan study split bets and bids; mu
```

(12 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
