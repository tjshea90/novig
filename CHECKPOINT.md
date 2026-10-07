# CHECKPOINT 2721 — read me first, then TASKS.md

**Written:** 2026-10-07T22:35:14Z · **tests:** all 4 fast checks green
**Branch:** `ccr-f8e1d0b1-qlnoqa` · **builds on:** `65d84dc6` (this checkpoint is the commit after it)

## Just done
DM4: 23 mutants killed (age rule, orientation, sharp requirement, unknown age, held fix, stop, scan-end passes, lane reads, labels, health checks), screenshots rendered (app/screenshots 4r/4s), unjudged fills keep their page read, fairNewestMs = page time; full floor green 2574; version 0.75.0 / code 133; CLAUDE.md pointer

## Do this next
push, confirm ci.yml green on this exact commit (mcp__github__actions_list), bash ship.sh, trigger release.yml, confirm Release v0.75.0, bash tools/record-release.sh v0.75.0 133 note, then DM5 answer Tj with the link

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M CLAUDE.md
     M app/build.gradle.kts

## Last ten checkpoints
```
  2b31e45d ckpt 2720: DM3c done: Bids tab (Bids priced from chip, CNO age limit chips, CNO status 
  04317943 ckpt 2719: DM3b done: CnoBidLane (data: wide read + page lane with a 3-page/step budget
  f555eb44 ckpt 2718: DM3a done: BidSource + makerSource + makerCnoMaxAgeSeconds + bidsFromCno in 
  886a28fc ckpt 2717: DM1 done: wrote RESEARCH.md §113 (verdict: CNO prices are already old when 
  75aa246a ckpt 2716: DM1 findings so far written into TASKS.md (one page-wide CNO age, missing ag
  c4b02728 ckpt 2715: Saved the first 6 of 7 scout results of research workflow wf_edc6b432-17e to
  3e800174 ckpt 2714: DM1 research workflow wf_edc6b432-17e launched (7 read-only scouts: app age 
  bd668740 ckpt 2713: Tj's yes to CNO-only auto bids written into TASKS.md as DM1-DM5 (research ho
  b9874dc4 ckpt 2712: v0.74.0 RELEASED and recorded (https://github.com/tjshea90/novig/releases/ta
  897ed404 ckpt 2711: pre-release: v0.74.0: bets and bids told apart. One rule (TrackedBet.isBid) 
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
