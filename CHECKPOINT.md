# CHECKPOINT 2592 — read me first, then TASKS.md

**Written:** 2026-10-05T22:34:39Z · **tests:** all 3 fast checks green
**Branch:** `claude/low-api-auto-bid-x67nzl` · **builds on:** `8e0b1622` (this checkpoint is the commit after it)

## Just done
CQ4/CQ5 prep: RESEARCH 92.4 written, TASKS CQ1-CQ4 ticked, version 0.68.0 code 115, notes wording fixes, setup banner in the mode

## Do this next
CQ5: run the whole floor (bash tools/test.sh), fix anything red, then bash ship.sh, confirm CI green on the commit, trigger release.yml, record-release.sh v0.68.0 115, send Tj the Release link

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M RESEARCH.md
     M TASKS.md
     M app/build.gradle.kts

## Last ten checkpoints
```
  eab22bd9 ckpt 2591: CQ4: bids tagged (focus, fairBooks, ages), BidReport splits, Diagnostics low
  00e60be2 ckpt 2590: CQ3: tests green (LowUsageBidTest 14, LowUsageScanTest 9, LowUsageBidsTest 1
  c7f201de ckpt 2589: CQ2/CQ3: maker rules (LowUsage.narrow, lowUsageBooks/skipObscure gates, Make
  eea9867f ckpt 2588: CQ2 (1/3): engine minSharp + tests; LowUsageBids (books/feeds/profile), Scan
  b5011203 ckpt 2587: CQ1: design written (RESEARCH §92: LOW_USAGE focus, sharp prop books, feeds
  600f21d5 ckpt 2586: CQ0: wrote Tj's low-API prop auto-bid request + plan CQ1-CQ5 into TASKS.md
  c4c39ddd ckpt 2585: v0.67.0 logged: BUILDLOG row (c52c2818) confirmed on main; TASKS CP4 notes C
  957367cf ckpt 2584: v0.67.0 released and recorded (CP1-CP4 done): run 37375200456 green, Release
  a61f6cc5 ckpt 2583: Tj asked to trigger the apk build: release.yml run #119 (id 37375200456) tri
  6369ba81 ckpt 2582: CP4: swept, floor green locally, v0.67.0 on main; release blocked by a GitHu
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
