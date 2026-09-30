# CHECKPOINT 2141 — read me first, then TASKS.md

**Written:** 2026-09-30T02:23:55Z · **tests:** all 2 fast checks green
**Branch:** `ccr-5babcffd-eylneu` · **builds on:** `97ef7ec3` (this checkpoint is the commit after it)

## Just done
Full test findings #2-#3 fixed: CLV card copy now names Pinnacle's ParlayAPI closes first; sheet label 'Pinnacle via ParlayAPI' (was nested parens); sample state has a Starter ParlayAPI key so meters/screenshot show 'Scans can spend N more today'; ClosingLineAppTest +2, CreditEstimateTest ParlayAPI naming; version 0.27.0 code 55; F1-F3,G1-G3,G5 ticked; floor 1106 green before these

## Do this next
wait for local assembleRelease (R8) result; final floor; ship.sh; CI on HEAD; release.yml; confirm; record-release; tick F4/G4/G6; answer Tj

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  ebc4a263 ckpt 2140: Full test finding #1 fixed: background auto-scans could spend ParlayAPI's wh
  fda54162 ckpt 2139: RESEARCH.md §43 written (5 sources, ParlayAPI tested endpoints/costs, what 
  bac95a85 ckpt 2138: ParlayAPI UI + switch: meter line (today's scan share / free = closes only),
  9178cca7 ckpt 2137: G1/G2 tests green (67): CreditPaceTest 5, ParlayPropsTest 7 (bulk props pars
  90b0a29f ckpt 2136: G1/G2 code in, compiling: CreditPace (day's share, unused carries over, 300 
  db89f8d6 ckpt 2135: F2 mid-change finished: one ParlayAPI KeyPool shared by scans + closes (Vigi
  e23ad8a5 ckpt 2134: Recorded Tj's 01:42Z request as G1-G6 (use ParlayAPI Starter fully, prioriti
  21f454a8 ckpt 2133: MID-CHANGE (not yet compiled): KeyPool.execute(reserve) + OddsFeed.reserve (
  0613045f ckpt 2132: F2 tests green: ParlayClosesTest 7 (props file/game lines parse, HTTP+cache,
  89d19720 ckpt 2131: F2b ParlayCloses compiles: Pinnacle prop closes (daily file, UTC+ET date), g
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
