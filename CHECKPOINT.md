# CHECKPOINT 2143 — read me first, then TASKS.md

**Written:** 2026-09-30T02:26:11Z · **tests:** all 2 fast checks green
**Branch:** `ccr-5babcffd-eylneu` · **builds on:** `c5b40f47` (this checkpoint is the commit after it)

## Just done
pre-release: v0.27.0: ParlayAPI fully used on its $5 Starter plan: Pinnacle's closing lines for CLV (back 7 days, checked first), a whole league's player props from Pinnacle, DraftKings, FanDuel, Caesars, Bovada and ProphetX in one 3-credit call, alternate spreads and totals, paced to a day's share (auto-scan half of it; 300 kept for closes; a free key only for closes), falling back to the other feeds when off, spent or gone (versionCode 55, v0.27.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.27.0), then run: bash tools/record-release.sh v0.27.0 55 "v0.27.0: ParlayAPI fully used on its $5 Starter plan: Pinnacle's closing lines for CLV (back 7 days, checked first), a whole league's player props from Pinnacle, DraftKings, FanDuel, Caesars, Bovada and ProphetX in one 3-credit call, alternate spreads and totals, paced to a day's share (auto-scan half of it; 300 kept for closes; a free key only for closes), falling back to the other feeds when off, spent or gone"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  c5b40f47 ckpt 2142: pre-ship: v0.27.0: ParlayAPI fully used on its $5 Starter plan: Pinnacle's c
  2e0a6863 ckpt 2141: Full test findings #2-#3 fixed: CLV card copy now names Pinnacle's ParlayAPI
  ebc4a263 ckpt 2140: Full test finding #1 fixed: background auto-scans could spend ParlayAPI's wh
  fda54162 ckpt 2139: RESEARCH.md §43 written (5 sources, ParlayAPI tested endpoints/costs, what 
  bac95a85 ckpt 2138: ParlayAPI UI + switch: meter line (today's scan share / free = closes only),
  9178cca7 ckpt 2137: G1/G2 tests green (67): CreditPaceTest 5, ParlayPropsTest 7 (bulk props pars
  90b0a29f ckpt 2136: G1/G2 code in, compiling: CreditPace (day's share, unused carries over, 300 
  db89f8d6 ckpt 2135: F2 mid-change finished: one ParlayAPI KeyPool shared by scans + closes (Vigi
  e23ad8a5 ckpt 2134: Recorded Tj's 01:42Z request as G1-G6 (use ParlayAPI Starter fully, prioriti
  21f454a8 ckpt 2133: MID-CHANGE (not yet compiled): KeyPool.execute(reserve) + OddsFeed.reserve (
```
