# CHECKPOINT 2463 — read me first, then TASKS.md

**Written:** 2026-10-03T17:42:48Z · **tests:** all 3 fast checks green
**Branch:** `ccr-c4435189-1cfj54` · **builds on:** `9fc443c0` (this checkpoint is the commit after it)

## Just done
pre-release: v0.56.0: sharp veto bar (the sharpest book must give at least 1% itself; auto-bet, alerts, bids; presets Volume 1%/Strict 2%), Kelly never sized above the sharp book's fair, auto-bet places the most credible edges first, game-line bids skip lines Novig just moved; research RESEARCH.md §72 (versionCode 96, v0.56.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.56.0), then run: bash tools/record-release.sh v0.56.0 96 "v0.56.0: sharp veto bar (the sharpest book must give at least 1% itself; auto-bet, alerts, bids; presets Volume 1%/Strict 2%), Kelly never sized above the sharp book's fair, auto-bet places the most credible edges first, game-line bids skip lines Novig just moved; research RESEARCH.md §72"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  9fc443c0 ckpt 2462: BL7: Kelly cap tests updated (veto off for CNO-fair arithmetic; veto on size
  921776ca ckpt 2461: BL7: Kelly stake's fair capped at the sharpest book's own (AutoBet.stake sha
  4ac19f72 ckpt 2460: BL7: veto status wording covers the bar; diff re-read (callers complete, mak
  b3af55aa ckpt 2459: BL1-BL6 ticked: RESEARCH.md §72 written (sources, 3 studies, timing/types/s
  4688088a ckpt 2458: BL6c: veto bar UI test (SharpConfirmUiTest), Diagnostics veto bar line, test
  ea15453d ckpt 2457: BL6b: auto-bet orders by credible EV (AutoBet.credibleEv); game-line bids ge
  20a23992 ckpt 2456: BL6a: sharp veto bar (ScanSettings.sharpVetoMinEv, 1% default) in SharpVeto/
  b171632c ckpt 2455: BL1/BL2 in progress: sources read (Kaunitz, Moskowitz, Buchdahl, Data Golf, 
  904a2700 ckpt 2454: BL: Tj's deep sharp/CLV/trap research + implement request written to TASKS.m
  b951447c ckpt 2453: BK done: v0.55.0 (code 95) released + recorded (trap guard, Bids in Settings
```
