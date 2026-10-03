# CHECKPOINT 2433 — read me first, then TASKS.md

**Written:** 2026-10-03T05:48:30Z · **tests:** all 3 fast checks green
**Branch:** `ccr-9491e046-7f6pnb` · **builds on:** `be8b82f4` (this checkpoint is the commit after it)

## Just done
pre-release: v0.53.0: bids fully automatic (posted while each scan runs), Bids tab always shown with Off/Recommend/Automatic that turns on what it needs, wallet balance above the tabs, less lag during Vigilant scans (scan at background priority), background cycles no longer wait for Vigilant's scan, Novig/ParlayAPI pacing and retries, WTA delayed matches priced (versionCode 93, v0.53.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.53.0), then run: bash tools/record-release.sh v0.53.0 93 "v0.53.0: bids fully automatic (posted while each scan runs), Bids tab always shown with Off/Recommend/Automatic that turns on what it needs, wallet balance above the tabs, less lag during Vigilant scans (scan at background priority), background cycles no longer wait for Vigilant's scan, Novig/ParlayAPI pacing and retries, WTA delayed matches priced"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  be8b82f4 ckpt 2432: BI1-BI8 done; v0.53.0 (code 93) bumped; tab labels one line
  d478c67b ckpt 2431: test fixes: WalletStripTest own sandbox (like every UI test), CycleRecorderT
  06a89b15 ckpt 2430: BI1/BI3/BI6: background cycle no longer waits for Vigilant's scan (CNO + aut
  0a945718 ckpt 2429: BI1/BI3 work: offline failures not counted + DoH skipped offline; key retrie
  c4671489 ckpt 2428: BI3 in progress: scan on background-priority threads (ScanThreads), CPU spli
  3ec2f412 ckpt 2427: BI2 done: wallet strip above the tab bar (WalletBalance.flow, 30 s refresh o
  7f0b773f ckpt 2426: BI4/BI5/BI8 done: Bids tab always shown with Off/Recommend/Automatic, turnin
  17e09f11 ckpt 2425: BI5 core: maker passes judge running scans (partial: unjudged bids stay), li
  784bead3 ckpt 2424: BI8 added (Tj: Bids tab always shown; turning bids/auto-make on turns on Vig
  58d0876a ckpt 2423: BI: Tj's 6 optimizations (diagnostics, wallet always visible, scan lag, Bids
```
