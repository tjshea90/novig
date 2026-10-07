# CHECKPOINT 2701 — read me first, then TASKS.md

**Written:** 2026-10-07T17:50:22Z · **tests:** all 4 fast checks green
**Branch:** `ccr-b67c8afa-h14i94` · **builds on:** `0492de95` (this checkpoint is the commit after it)

## Just done
pre-release: v0.73.0: the CrazyNinjaOdds list can be limited to leagues, kinds of bet, pregame only, dollars available, words and props per game (CNO tab chips and Settings); one league or sport goes to CNO's own dropdowns, the rest is screened in the app; the auto-bet, widget and alerts see only those games; presets keep your picks (RESEARCH 110) (versionCode 131, v0.73.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.73.0), then run: bash tools/record-release.sh v0.73.0 131 "v0.73.0: the CrazyNinjaOdds list can be limited to leagues, kinds of bet, pregame only, dollars available, words and props per game (CNO tab chips and Settings); one league or sport goes to CNO's own dropdowns, the rest is screened in the app; the auto-bet, widget and alerts see only those games; presets keep your picks (RESEARCH 110)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  0492de95 ckpt 2700: pre-release: v0.73.0: the CrazyNinjaOdds list can be limited to leagues, kin
  4f2b0a24 ckpt 2699: DI5: CnoScopeUiTest green (11): CNO tab league chips, 'reading with your new
  be3da5cd ckpt 2698: pre-release: v0.72.4: small-prop guard on the Auto-bet tab: no one kind of p
  ab16bcd3 ckpt 2697: pre-release: v0.72.4: small-prop guard (share cap with an even-split floor, 
  8efd407b ckpt 2696: DI3: small-prop guard core: PropGuard (share cap with even-split floor, per-
  f3b9fbb4 ckpt 2695: pre-release: v0.72.3: Low API usage no longer hard-sets the trap guard windo
  2d31323a ckpt 2694: pre-release: v0.72.3: Low API usage no longer hard-sets the trap guard windo
  b9b0315d ckpt 2693: DI1: Low API usage no longer hard-sets the trap guard window (follows trap h
  ffcbecc9 ckpt 2692: Tj's CNO-only scanner filter request written into TASKS.md (DI5)
  d9e9d0d4 ckpt 2691: Tj's new request written into TASKS.md (DI1-DI4): Low API usage hard-set tra
```
