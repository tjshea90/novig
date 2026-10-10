# CHECKPOINT 2869 — read me first, then TASKS.md

**Written:** 2026-10-10T15:22:46Z · **tests:** all 4 fast checks green
**Branch:** `ccr-3fba2f23-lerg26` · **builds on:** `c35f9aee` (this checkpoint is the commit after it)

## Just done
pre-release: v0.86.1: trap guard hours split (auto-bet vs bids), profit graph with ranges, pinch zoom, scroll and full screen, heap census and per-game JSON parsing for SGO and PropLine (memory), Sofascore backs off (versionCode 170, v0.86.1)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.86.1), then run: bash tools/record-release.sh v0.86.1 170 "v0.86.1: trap guard hours split (auto-bet vs bids), profit graph with ranges, pinch zoom, scroll and full screen, heap census and per-game JSON parsing for SGO and PropLine (memory), Sofascore backs off"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  c35f9aee ckpt 2868: pre-ship: v0.86.1: trap guard hours split (auto-bet vs bids), profit graph w
  2fe38e40 ckpt 2867: TK2 profit graph (ranges, pinch zoom, scroll, full screen) done; TJ4 memo wr
  08dfe7fa ckpt 2866: TK1 trap guard split done (bids vs auto-bet), TJ2 HeapCensus + JsonSplit (SG
  9f1d520a ckpt 2865: TJ written; release v0.86.0 triggered (run 38061298709)
  b5bbb409 ckpt 2864: research-record.yml schedule removed at Tj's word
  f9f95ea4 ckpt 2863: Lab stopped at Tj's word: cron removed from lab-record.yml, steward claim re
  820f62d8 ckpt 2862: pre-release: v0.86.0: cleanup. Diagnostics reset button, automatic 2-day ret
  ff036059 ckpt 2861: TI3: auto-bet/Bet sheet find the bet through CNO's own link outcome (NovigBe
  b5805899 ckpt 2860: TI: Games tab + Locked in card removed, Bids rules text fixed, edges-real re
  5674414e ckpt 2859: TI request written into TASKS.md
```
