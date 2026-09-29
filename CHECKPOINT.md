# CHECKPOINT 2076 — read me first, then TASKS.md

**Written:** 2026-09-29T16:02:09Z · **tests:** all 2 fast checks green
**Branch:** `ccr-538c37db-finzj9` · **builds on:** `1580eba8` (this checkpoint is the commit after it)

## Just done
W9: placement never cancelled by closing the sheet (+test that failed without the fix), shared-market grading, skill map, screenshots checked, version 0.21.0/46

## Do this next
ship.sh v0.21.0, wait for CI green, trigger release.yml, record-release, tick W9, tell Tj

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  a9194471 ckpt 2075: ApiSettler: bets sharing a market are graded together (one market payout no 
  32c7dac5 ckpt 2074: W1-W8 ticked; RESEARCH.md §36.6 (MoneyLine tested) and §37 (API betting de
  2741c198 ckpt 2073: W6-W8 app layer done: connection store + container wiring, ApiBettingControl
  445ddb62 ckpt 2072: W6/W7 data: NovigBettingSetup (use the phone's trading key or revoke+mint, f
  6c651d47 ckpt 2071: W4/W5 data layer: NovigTradingClient, ApiBetPlanner, ApiBetPlacer (IOC at th
  8c20dd5a ckpt 2070: W1/W2 done (MoneyLine tested: stale ~2 h, no Pinnacle, 3 MB for 3 events); N
  a611dc3c ckpt 2069: Recorded Tj's request (build API betting + API grading for the Tracker; Mone
  cdfbe7b5 ckpt 2068: v0.20.2 (code 45) released and recorded; V1-V8 ticked
  fa5ee5b9 ckpt 2067: pre-release: v0.20.2: Polymarket reads ~2x faster (13 s to 5.6 s live); the 
  76c46f2d ckpt 2066: floor green (839 passed, 19 live skipped, exit 0); probe limited to 10 marke
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
