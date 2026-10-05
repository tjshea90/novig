# CHECKPOINT 2590 — read me first, then TASKS.md

**Written:** 2026-10-05T22:21:24Z · **tests:** all 3 fast checks green
**Branch:** `claude/low-api-auto-bid-x67nzl` · **builds on:** `71625714` (this checkpoint is the commit after it)

## Just done
CQ3: tests green (LowUsageBidTest 14, LowUsageScanTest 9, LowUsageBidsTest 19, FairValue +4); 14 mutants killed (guards: minSharp, stale drop, null stamp, +130, 2.5% floor, books gate, obscure, window, pace gap, bets-only, book cap, catalog horizon, readable, tighter limit)

## Do this next
CQ4: tag bids with the mode (MakerBid), Diagnostics line + split, health check, settings index; then UI test + screenshot; then CQ5 sweep, full floor, ship v0.68.0

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  c7f201de ckpt 2589: CQ2/CQ3: maker rules (LowUsage.narrow, lowUsageBooks/skipObscure gates, Make
  eea9867f ckpt 2588: CQ2 (1/3): engine minSharp + tests; LowUsageBids (books/feeds/profile), Scan
  b5011203 ckpt 2587: CQ1: design written (RESEARCH §92: LOW_USAGE focus, sharp prop books, feeds
  600f21d5 ckpt 2586: CQ0: wrote Tj's low-API prop auto-bid request + plan CQ1-CQ5 into TASKS.md
  c4c39ddd ckpt 2585: v0.67.0 logged: BUILDLOG row (c52c2818) confirmed on main; TASKS CP4 notes C
  957367cf ckpt 2584: v0.67.0 released and recorded (CP1-CP4 done): run 37375200456 green, Release
  a61f6cc5 ckpt 2583: Tj asked to trigger the apk build: release.yml run #119 (id 37375200456) tri
  6369ba81 ckpt 2582: CP4: swept, floor green locally, v0.67.0 on main; release blocked by a GitHu
  0f9447e6 ckpt 2581: v0.67.0 shipped to main (full floor 2157 passed locally); CI run 37369765480
  337e8cb1 ckpt 2580: pre-release: v0.67.0: longest odds a bid may be posted at (Bids rules: +140 
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
