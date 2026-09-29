# CHECKPOINT 2071 — read me first, then TASKS.md

**Written:** 2026-09-29T15:27:38Z · **tests:** all 2 fast checks green
**Branch:** `ccr-538c37db-finzj9` · **builds on:** `f2e7852e` (this checkpoint is the commit after it)

## Just done
W4/W5 data layer: NovigTradingClient, ApiBetPlanner, ApiBetPlacer (IOC at the confirmed ceiling, fills -> tracker via logApi, lost answer looked up by clientId), TrackedBet orderId/contracts/paid/fee; 14 tests

## Do this next
W6: enable betting (revoke+mint trading key via management key), fund/withdraw, connection store, app wiring

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  8c20dd5a ckpt 2070: W1/W2 done (MoneyLine tested: stale ~2 h, no Pinnacle, 3 MB for 3 events); N
  a611dc3c ckpt 2069: Recorded Tj's request (build API betting + API grading for the Tracker; Mone
  cdfbe7b5 ckpt 2068: v0.20.2 (code 45) released and recorded; V1-V8 ticked
  fa5ee5b9 ckpt 2067: pre-release: v0.20.2: Polymarket reads ~2x faster (13 s to 5.6 s live); the 
  76c46f2d ckpt 2066: floor green (839 passed, 19 live skipped, exit 0); probe limited to 10 marke
  37d09d09 ckpt 2065: V1-V7 done: RESEARCH.md §36 (Novig API answers, measured slowness, Kalshi 4
  0dfd9ca3 ckpt 2064: V1-V3 written up in NOVIG_API.md §14 (all 51 routes, subaccount-wallet find
  69da58b4 ckpt 2063: Recorded Tj's two research projects (Novig API in depth + 7 third-party APIs
  92fa8cfd ckpt 2062: U6 ticked: v0.20.1 released
  330c2463 ckpt 2061: v0.20.1 (code 44) released and recorded: CI green (run 36535348703), release
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
