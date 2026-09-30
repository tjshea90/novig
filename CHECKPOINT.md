# CHECKPOINT 2136 — read me first, then TASKS.md

**Written:** 2026-09-30T02:05:23Z · **tests:** all 2 fast checks green
**Branch:** `ccr-5babcffd-eylneu` · **builds on:** `49cd3407` (this checkpoint is the commit after it)

## Just done
G1/G2 code in, compiling: CreditPace (day's share, unused carries over, 300 reserve, free key = closes only, unknown plan = first call tells) + CreditsHeldBackException (scanner: standing by, SourceReport.heldBack, Diagnostics); ParlayPropsSource (bulk /props 3 credits/league, pages, canonical books); alt spreads/totals on ParlayAPI; canonicalBook (caesars/betonline/hardrock); merge order parlay before propline

## Do this next
tests: CreditPace math, KeyPool held-back, alternates parse, ParlayProps parse/paging, canonical keys, scanner standby; update reserve test to CreditsHeldBackException; Settings text + QuotaPolicy.PARLAY rule; RESEARCH §43; full floor

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M data/src/main/kotlin/com/tjshea/vigilant/data/keys/Usage.kt

## Last ten checkpoints
```
  db89f8d6 ckpt 2135: F2 mid-change finished: one ParlayAPI KeyPool shared by scans + closes (Vigi
  e23ad8a5 ckpt 2134: Recorded Tj's 01:42Z request as G1-G6 (use ParlayAPI Starter fully, prioriti
  21f454a8 ckpt 2133: MID-CHANGE (not yet compiled): KeyPool.execute(reserve) + OddsFeed.reserve (
  0613045f ckpt 2132: F2 tests green: ParlayClosesTest 7 (props file/game lines parse, HTTP+cache,
  89d19720 ckpt 2131: F2b ParlayCloses compiles: Pinnacle prop closes (daily file, UTC+ET date), g
  40fda8c4 ckpt 2130: v0.26.0 (code 54) released and recorded; E1-E4 ticked; F2a (ParlayAPI as a f
  5fe7de23 ckpt 2129: F2a in progress: OddsFeed (ODDS_API/PARLAY) in TheOddsApiClient + OddsApiPro
  25f31ac8 ckpt 2128: pre-release: v0.26.0: closing lines are found after the game starts, even da
  286d721d ckpt 2127: F0 done: Wi-Fi gate removed, rule written into BRIEF.md + CLAUDE.md
  ba5aac97 ckpt 2126: Recorded Tj's mid-turn request as F0-F4 (research 4 sources, implement what 
```

(10 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
