# CHECKPOINT 2138 — read me first, then TASKS.md

**Written:** 2026-09-30T02:10:32Z · **tests:** all 2 fast checks green
**Branch:** `ccr-5babcffd-eylneu` · **builds on:** `bb8fc5c6` (this checkpoint is the commit after it)

## Just done
ParlayAPI UI + switch: meter line (today's scan share / free = closes only), Settings text rewritten (paid vs free, pacing, fallback), QuotaPolicy.PARLAY rule, ParlayCloses.enabled follows useParlay; CreditPaceTest 7 green

## Do this next
RESEARCH.md §43 (sources + Starter use + free-tier verdict + buy rec), then full floor, then G4 full test protocol (load test-protocols skill), G5 sleep check

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M data/src/test/kotlin/com/tjshea/vigilant/data/keys/CreditPaceTest.kt

## Last ten checkpoints
```
  9178cca7 ckpt 2137: G1/G2 tests green (67): CreditPaceTest 5, ParlayPropsTest 7 (bulk props pars
  90b0a29f ckpt 2136: G1/G2 code in, compiling: CreditPace (day's share, unused carries over, 300 
  db89f8d6 ckpt 2135: F2 mid-change finished: one ParlayAPI KeyPool shared by scans + closes (Vigi
  e23ad8a5 ckpt 2134: Recorded Tj's 01:42Z request as G1-G6 (use ParlayAPI Starter fully, prioriti
  21f454a8 ckpt 2133: MID-CHANGE (not yet compiled): KeyPool.execute(reserve) + OddsFeed.reserve (
  0613045f ckpt 2132: F2 tests green: ParlayClosesTest 7 (props file/game lines parse, HTTP+cache,
  89d19720 ckpt 2131: F2b ParlayCloses compiles: Pinnacle prop closes (daily file, UTC+ET date), g
  40fda8c4 ckpt 2130: v0.26.0 (code 54) released and recorded; E1-E4 ticked; F2a (ParlayAPI as a f
  5fe7de23 ckpt 2129: F2a in progress: OddsFeed (ODDS_API/PARLAY) in TheOddsApiClient + OddsApiPro
  25f31ac8 ckpt 2128: pre-release: v0.26.0: closing lines are found after the game starts, even da
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
