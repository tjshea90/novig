# CHECKPOINT 2074 — read me first, then TASKS.md

**Written:** 2026-09-29T15:54:35Z · **tests:** all 2 fast checks green
**Branch:** `ccr-538c37db-finzj9` · **builds on:** `38f8bcae` (this checkpoint is the commit after it)

## Just done
W1-W8 ticked; RESEARCH.md §36.6 (MoneyLine tested) and §37 (API betting design, safety rules, unverified list, first run); hardened: fills paging, ledger window/ids, unknown fair age refused, CNO earlier start

## Do this next
W9: full test protocol (floor, screenshots, sweep of the diff for money-path bugs), skill map, ship v0.21.0

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M RESEARCH.md
     M TASKS.md
     M app/src/main/kotlin/com/tjshea/vigilant/app/ui/ApiBettingUi.kt

## Last ten checkpoints
```
  2741c198 ckpt 2073: W6-W8 app layer done: connection store + container wiring, ApiBettingControl
  445ddb62 ckpt 2072: W6/W7 data: NovigBettingSetup (use the phone's trading key or revoke+mint, f
  6c651d47 ckpt 2071: W4/W5 data layer: NovigTradingClient, ApiBetPlanner, ApiBetPlacer (IOC at th
  8c20dd5a ckpt 2070: W1/W2 done (MoneyLine tested: stale ~2 h, no Pinnacle, 3 MB for 3 events); N
  a611dc3c ckpt 2069: Recorded Tj's request (build API betting + API grading for the Tracker; Mone
  cdfbe7b5 ckpt 2068: v0.20.2 (code 45) released and recorded; V1-V8 ticked
  fa5ee5b9 ckpt 2067: pre-release: v0.20.2: Polymarket reads ~2x faster (13 s to 5.6 s live); the 
  76c46f2d ckpt 2066: floor green (839 passed, 19 live skipped, exit 0); probe limited to 10 marke
  37d09d09 ckpt 2065: V1-V7 done: RESEARCH.md §36 (Novig API answers, measured slowness, Kalshi 4
  0dfd9ca3 ckpt 2064: V1-V3 written up in NOVIG_API.md §14 (all 51 routes, subaccount-wallet find
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
