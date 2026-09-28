# CHECKPOINT 545 — read me first, then TASKS.md

**Written:** 2026-09-28T03:33:52Z · **tests:** all 1 fast checks green
**Branch:** `claude/scan-perf-background-notifications-ig8u0c` · **builds on:** `91548df` (this checkpoint is the commit after it)

## Just done
P1+P2 done, P3 mostly: prices/scan to 1200, props/game 16/24, odds cap +120/+150/+200/+300 (migrate schema 6), FairMemo (pricing 160ms->3.4ms per partial at 1200 mkts), devig bisection early stop, end-of-scan re-read of early edges (BiggerScansTest 8 green)

## Do this next
tick P1/P2/P3 in TASKS.md after app tests (ScreenshotTest odds cap test needs updating: +1000 -> +300, Any gone); then P4 auto-scan service + P5 alerts

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  6c91990 ckpt 544: Recorded Tj's 03:19Z request as P1-P6 in TASKS.md
  5f220e0 ckpt 543: Released Vigilant v0.17.1 (code 33): release.yml run 36365476060 green, tag v0
  a0a82b7 ckpt 542: Removed an unrelated side job from this repo (Tj: this repo is Vigilant only):
  15087c3 ckpt 541: MP3-flasher side job closed: diagnosis + workaround steps sent to Tj; no rebui
  2bdcfe8 ckpt 540: Recorded Tj's MP3-flasher side job in TASKS.md (M1-M3); diagnosis: 32-bit-only
  b6a37e6 ckpt 539: PAUSED for Tj's model switch. H1-H4 all done and on main (v0.17.1 code 33 gate
  4043306 ckpt 538: Shipped v0.17.1 to main (3a66035); release.yml run 36353475955 CANCELLED at th
  3a66035 ckpt 537: pre-release: v0.17.1: 'Starts within' filter (Any time / 12h / 24h / 48h) on t
  f2a534d ckpt 536: H4 full tests done: 624 green, live green, release APK verified; fixes F1 (Rec
  078fd3a ckpt 535: H4 in progress: floor 623 green + live Novig/CNO/scores green; fixed F1 Rechec
```

(12 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
