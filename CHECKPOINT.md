# CHECKPOINT 2718 — read me first, then TASKS.md

**Written:** 2026-10-07T21:58:17Z · **tests:** all 4 fast checks green
**Branch:** `ccr-f8e1d0b1-qlnoqa` · **builds on:** `72e7722a` (this checkpoint is the commit after it)

## Just done
DM3a done: BidSource + makerSource + makerCnoMaxAgeSeconds + bidsFromCno in ScanSettings; MakerLine.fairMaxAgeMs/listAtMs/pageAtMs; MakerBid.listAgeSec/pageAgeSec; MakerRules.of narrows for the CNO source; CnoMakerLines (pure builder: both sides of a page, age = older of list and page, unknown list age = stale, page age unknown = 45 s) + CnoBidCandidates (picker) + NovigLive.targetsNow; CnoMakerLinesTest 13 green

## Do this next
DM3b app: a CnoBidLane in the app (wide read + page lane with a time budget + NovigLive.targetsNow), MakerRunner source branch (run/preview/post: lines from the lane, no 'result == null' early return, stop on CNO pause/stuck/late), AutoScanner.cycle calls the lane step when settings.bidsFromCno, MakerSetup for the CNO source (do not force scanner BOTH / autoScan BOTH; pinnacleOnly must be off), held overlap fix; allow the lane file in ScanStudyAppTest's wide-read pin

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  886a28fc ckpt 2717: DM1 done: wrote RESEARCH.md §113 (verdict: CNO prices are already old when 
  75aa246a ckpt 2716: DM1 findings so far written into TASKS.md (one page-wide CNO age, missing ag
  c4b02728 ckpt 2715: Saved the first 6 of 7 scout results of research workflow wf_edc6b432-17e to
  3e800174 ckpt 2714: DM1 research workflow wf_edc6b432-17e launched (7 read-only scouts: app age 
  bd668740 ckpt 2713: Tj's yes to CNO-only auto bids written into TASKS.md as DM1-DM5 (research ho
  b9874dc4 ckpt 2712: v0.74.0 RELEASED and recorded (https://github.com/tjshea90/novig/releases/ta
  897ed404 ckpt 2711: pre-release: v0.74.0: bets and bids told apart. One rule (TrackedBet.isBid) 
  6e640fc1 ckpt 2710: DJ4 + DJ5 done in the tree (Diagnostics + scan study split bets and bids; mu
  435bc978 ckpt 2709: DJ2 + DJ3 done in the tree: bid/bet rule + tagBids wiring, Tracker Bets/Bids
  7b4b02b7 ckpt 2708: DK1/DK2 done: researched CNO-only auto bids (RESEARCH §112; §111 stub for 
```

(8 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
