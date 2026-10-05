# CHECKPOINT 2588 — read me first, then TASKS.md

**Written:** 2026-10-05T22:10:10Z · **tests:** all 3 fast checks green
**Branch:** `claude/low-api-auto-bid-x67nzl` · **builds on:** `62f1f06d` (this checkpoint is the commit after it)

## Just done
CQ2 (1/3): engine minSharp + tests; LowUsageBids (books/feeds/profile), ScanSettings fields+effective, Scanner/Pricing/ParlayProps wiring, LowUsageSource, AutoScan pace, app lowUsageSources; compiles

## Do this next
CQ2 (2/3): maker side: LowUsage.narrow + MakerRules lowUsage/skipObscure + MakerLine.fairBooks + precheck; then UI chips; then tests CQ3; then tagging CQ4

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  b5011203 ckpt 2587: CQ1: design written (RESEARCH §92: LOW_USAGE focus, sharp prop books, feeds
  600f21d5 ckpt 2586: CQ0: wrote Tj's low-API prop auto-bid request + plan CQ1-CQ5 into TASKS.md
  c4c39ddd ckpt 2585: v0.67.0 logged: BUILDLOG row (c52c2818) confirmed on main; TASKS CP4 notes C
  957367cf ckpt 2584: v0.67.0 released and recorded (CP1-CP4 done): run 37375200456 green, Release
  a61f6cc5 ckpt 2583: Tj asked to trigger the apk build: release.yml run #119 (id 37375200456) tri
  6369ba81 ckpt 2582: CP4: swept, floor green locally, v0.67.0 on main; release blocked by a GitHu
  0f9447e6 ckpt 2581: v0.67.0 shipped to main (full floor 2157 passed locally); CI run 37369765480
  337e8cb1 ckpt 2580: pre-release: v0.67.0: longest odds a bid may be posted at (Bids rules: +140 
  6e77053e ckpt 2579: CP1-CP3 ticked; scratch screenshot test removed; version 0.67.0 code 114
  1f364251 ckpt 2578: CP1: RESEARCH §91 written (both sides fine; the pair = one bet 'not 3 runs'
```

(10 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
