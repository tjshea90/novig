# CHECKPOINT 2073 — read me first, then TASKS.md

**Written:** 2026-09-29T15:50:48Z · **tests:** all 2 fast checks green
**Branch:** `ccr-538c37db-finzj9` · **builds on:** `53856b47` (this checkpoint is the commit after it)

## Just done
W6-W8 app layer done: connection store + container wiring, ApiBettingController (setup, fund/withdraw, Bet sheet state machine), Settings section, Bet buttons on +EV and CNO cards, Tracker badge/sync, auto-sync before grading; floor green 884 passed

## Do this next
Sweep of the money path: fair age must be known, ledger window +-12h, fill ids, fills paging; then docs, full test, ship v0.21.0

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  445ddb62 ckpt 2072: W6/W7 data: NovigBettingSetup (use the phone's trading key or revoke+mint, f
  6c651d47 ckpt 2071: W4/W5 data layer: NovigTradingClient, ApiBetPlanner, ApiBetPlacer (IOC at th
  8c20dd5a ckpt 2070: W1/W2 done (MoneyLine tested: stale ~2 h, no Pinnacle, 3 MB for 3 events); N
  a611dc3c ckpt 2069: Recorded Tj's request (build API betting + API grading for the Tracker; Mone
  cdfbe7b5 ckpt 2068: v0.20.2 (code 45) released and recorded; V1-V8 ticked
  fa5ee5b9 ckpt 2067: pre-release: v0.20.2: Polymarket reads ~2x faster (13 s to 5.6 s live); the 
  76c46f2d ckpt 2066: floor green (839 passed, 19 live skipped, exit 0); probe limited to 10 marke
  37d09d09 ckpt 2065: V1-V7 done: RESEARCH.md §36 (Novig API answers, measured slowness, Kalshi 4
  0dfd9ca3 ckpt 2064: V1-V3 written up in NOVIG_API.md §14 (all 51 routes, subaccount-wallet find
  69da58b4 ckpt 2063: Recorded Tj's two research projects (Novig API in depth + 7 third-party APIs
```

(18 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
