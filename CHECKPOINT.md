# CHECKPOINT 2558 — read me first, then TASKS.md

**Written:** 2026-10-05T17:28:50Z · **tests:** all 3 fast checks green
**Branch:** `ccr-fef46304-swa9m4` · **builds on:** `de138c63` (this checkpoint is the commit after it)

## Just done
fixed a kill-switch race: the pause watcher now cancels bids with the kill switch's reason and STOP counts bids taken down by either path (+1 test); full floor otherwise green (2117 tests)

## Do this next
read mutants.txt, sweep diff v0.63.0..HEAD, bump 0.64.0/111, ship; then CI2 PinnacleBackup/PinnacleBettor, CI3, CO1-CO7

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  3ebe8403 ckpt 2557: wrote CO7 (does MatchWire help prop matching / save API usage) into TASKS.md
  5e20215c ckpt 2556: wrote Tj's API-audit request (CO1-CO6) into TASKS.md with the new work order
  336095bd ckpt 2555: CI2 started: pinnacleOnly settings + effective() + Scanner.refreshFair (data
  271f2d84 ckpt 2554: CK1-CK3 ticked: bid health checks, Diagnostics unlimited wording, fast-fill 
  29ec9d49 ckpt 2553: CN1 done (MatchWire is mapping-only, no prices/Pinnacle/Novig: no use); RESE
  b63b888a ckpt 2552: CK2 code: BidReport (rows, summary, fill lines), AtBet for maker fills, Diag
  1c7b3d90 ckpt 2551: CK1/CK3 code: sharp-anchored bid price + Kelly on the anchor, fill-time judg
  f3449ca6 ckpt 2550: CL1 done: kill switch (killed + derived paused, KillMarker second copy, Kill
  08cbeea6 ckpt 2549: wrote the work order and CI1 finding into TASKS.md (kill switch first, then 
  10c64bc3 ckpt 2548: CJ1 done: Tracker profit/staked/chart count every settled bet in both views 
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
