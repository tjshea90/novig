# CHECKPOINT 2325 — read me first, then TASKS.md

**Written:** 2026-10-02T15:05:31Z · **tests:** all 3 fast checks green
**Branch:** `ccr-ea5bf769-s5xd3t` · **builds on:** `882a7190` (this checkpoint is the commit after it)

## Just done
AS3 code+tests: slow scan = Novig's 423 on the key -> public routes (4-6/s vs 14/s, no live feed); scans now say so even when the key was set aside before they began (NovigSource.keyDown, Scanner.PUBLIC_PRICES)

## Do this next
mutation-check AS3, tick; then AS4 sister books

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  5af19d8b ckpt 2324: AS2 done and ticked (6 mutants killed)
  361848a5 ckpt 2323: AS2 code+tests: LaunchGate (per process) decides a fresh launch: restored ne
  6c70d001 ckpt 2322: AS1 done: Check odds now and pull to refresh resume a paused scanner (the ot
  831f36c4 ckpt 2321: AQ2 ticked: notification off main + FoundCount, frame meter in Diagnostics; 
  be4d5c86 ckpt 2320: AS written to TASKS.md (Tj 06:14Z: auto-resume scanner on Check odds now/pul
  158094f6 ckpt 2319: AQ2 part 2: frame meter (FrameStats + FrameMeter) in Diagnostics' Performanc
  05349b30 ckpt 2318: AQ2 part 1: scan notification built off the main thread, its found count onc
  743a98e6 ckpt 2317: AQ1/AQ3/AQ4/AR1/AR2 ticked; app-level manual-bet test added (ApiBettingContr
  11e9ba2b ckpt 2316: AR1/AR2: Check odds now and Price now price Vigilant's bets whatever the sca
  9c48a379 ckpt 2315: AR written to TASKS.md (Check odds now must refresh every open bet, Vigilant
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
