# CHECKPOINT 2140 — read me first, then TASKS.md

**Written:** 2026-09-30T02:16:19Z · **tests:** all 2 fast checks green
**Branch:** `ccr-5babcffd-eylneu` · **builds on:** `f2958759` (this checkpoint is the commit after it)

## Just done
Full test finding #1 fixed: background auto-scans could spend ParlayAPI's whole day's share by mid-morning; now CreditPace(keepOfDay=0.5) for auto-scan cycles (separate background clients, startVigilantScan(background)), test in CreditPaceTest; floor green 1105; screenshots rendered; G5 sleep audit: nothing runs without reason (SettleWorker 3h + ClosingAlarm are Tj's features, bounded by open bets)

## Do this next
look at PNGs (settings fair odds, usage meters, tracker CLV); continue sweep: ParlayCloses/ParlayProps edge cases, OpenBetPricer+pace, UI copy; then version 0.27.0 code 55, ship

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M RESEARCH.md
     M app/src/main/kotlin/com/tjshea/vigilant/app/ui/SettingsScreen.kt

## Last ten checkpoints
```
  fda54162 ckpt 2139: RESEARCH.md §43 written (5 sources, ParlayAPI tested endpoints/costs, what 
  bac95a85 ckpt 2138: ParlayAPI UI + switch: meter line (today's scan share / free = closes only),
  9178cca7 ckpt 2137: G1/G2 tests green (67): CreditPaceTest 5, ParlayPropsTest 7 (bulk props pars
  90b0a29f ckpt 2136: G1/G2 code in, compiling: CreditPace (day's share, unused carries over, 300 
  db89f8d6 ckpt 2135: F2 mid-change finished: one ParlayAPI KeyPool shared by scans + closes (Vigi
  e23ad8a5 ckpt 2134: Recorded Tj's 01:42Z request as G1-G6 (use ParlayAPI Starter fully, prioriti
  21f454a8 ckpt 2133: MID-CHANGE (not yet compiled): KeyPool.execute(reserve) + OddsFeed.reserve (
  0613045f ckpt 2132: F2 tests green: ParlayClosesTest 7 (props file/game lines parse, HTTP+cache,
  89d19720 ckpt 2131: F2b ParlayCloses compiles: Pinnacle prop closes (daily file, UTC+ET date), g
  40fda8c4 ckpt 2130: v0.26.0 (code 54) released and recorded; E1-E4 ticked; F2a (ParlayAPI as a f
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
