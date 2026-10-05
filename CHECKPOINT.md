# CHECKPOINT 2593 — read me first, then TASKS.md

**Written:** 2026-10-05T22:36:50Z · **tests:** all 3 fast checks green
**Branch:** `claude/low-api-auto-bid-x67nzl` · **builds on:** `dd8697f2` (this checkpoint is the commit after it)

## Just done
pre-release: v0.68.0: Low API usage bids (Bids › Rules › Which bids go up): props only, next 6 h, a fair from 2-3 sharp prop books (default Kalshi, ProphetX, FanDuel; both sides, fresh quotes only, at least two), bids at least 2.5% under the lowest picked book's fair and never longer than +130, the likeliest fills first, a slow scan pace (default 10 min) that skips leagues with nothing to bid on; bids tagged and split in Diagnostics and the scan study (RESEARCH §92) (versionCode 115, v0.68.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.68.0), then run: bash tools/record-release.sh v0.68.0 115 "v0.68.0: Low API usage bids (Bids › Rules › Which bids go up): props only, next 6 h, a fair from 2-3 sharp prop books (default Kalshi, ProphetX, FanDuel; both sides, fresh quotes only, at least two), bids at least 2.5% under the lowest picked book's fair and never longer than +130, the likeliest fills first, a slow scan pace (default 10 min) that skips leagues with nothing to bid on; bids tagged and split in Diagnostics and the scan study (RESEARCH §92)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  dd8697f2 ckpt 2592: CQ4/CQ5 prep: RESEARCH 92.4 written, TASKS CQ1-CQ4 ticked, version 0.68.0 co
  eab22bd9 ckpt 2591: CQ4: bids tagged (focus, fairBooks, ages), BidReport splits, Diagnostics low
  00e60be2 ckpt 2590: CQ3: tests green (LowUsageBidTest 14, LowUsageScanTest 9, LowUsageBidsTest 1
  c7f201de ckpt 2589: CQ2/CQ3: maker rules (LowUsage.narrow, lowUsageBooks/skipObscure gates, Make
  eea9867f ckpt 2588: CQ2 (1/3): engine minSharp + tests; LowUsageBids (books/feeds/profile), Scan
  b5011203 ckpt 2587: CQ1: design written (RESEARCH §92: LOW_USAGE focus, sharp prop books, feeds
  600f21d5 ckpt 2586: CQ0: wrote Tj's low-API prop auto-bid request + plan CQ1-CQ5 into TASKS.md
  c4c39ddd ckpt 2585: v0.67.0 logged: BUILDLOG row (c52c2818) confirmed on main; TASKS CP4 notes C
  957367cf ckpt 2584: v0.67.0 released and recorded (CP1-CP4 done): run 37375200456 green, Release
  a61f6cc5 ckpt 2583: Tj asked to trigger the apk build: release.yml run #119 (id 37375200456) tri
```
