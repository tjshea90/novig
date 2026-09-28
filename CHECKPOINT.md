# CHECKPOINT 602 — read me first, then TASKS.md

**Written:** 2026-09-28T20:23:25Z · **tests:** all 1 fast checks green
**Branch:** `ccr-ed1962c6-kshrww` · **builds on:** `87fa0ef` (this checkpoint is the commit after it)

## Just done
pre-release: v0.19.6: No limit on every scan cap (Novig prices per scan, props credits, PropLine props games, lines/props per game, props hours); a scan reads only the selected time period (Days ahead, or Starts within when shorter), each line once, then stops; lines a long scan couldn't reach in time are read first next scan (versionCode 41, v0.19.6)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.19.6), then run: bash tools/record-release.sh v0.19.6 41 "v0.19.6: No limit on every scan cap (Novig prices per scan, props credits, PropLine props games, lines/props per game, props hours); a scan reads only the selected time period (Days ahead, or Starts within when shorter), each line once, then stops; lines a long scan couldn't reach in time are read first next scan"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  87fa0ef ckpt 601: V1-V5 built (No limit/All on every scan cap, PropLine games setting, scan wind
  189fc62 ckpt 600: V2-V5 data side: ScanSettings.NO_LIMIT on Novig prices/credits/lines/props/pro
  8a2d4bb ckpt 599: Released Vigilant v0.19.5 (code 40): release.yml green on 22e5276, APK on the 
  b65e2db ckpt 598: Recorded Tj's request (unlimited options for every scan cap, scan must stop wh
  22e5276 ckpt 597: pre-release: v0.19.5: pause all scanning (Settings switch, pause/resume on the
  2d7374f ckpt 596: U1 done + full floor 752/0 failures; v0.19.5 (40) ready
  bf7f8d1 ckpt 595: U1 built: pause all scanning (ScanSettings.paused, CnoWatch.hold, ScanRunner.s
  25f6d90 ckpt 594: U2: Open in Novig button on every CNO tab card (same path/stake as the widget 
  a021c70 ckpt 593: Recorded Tj's request (pause all scanning; Open in Novig buttons on CNO tab ca
  090d751 ckpt 592: Released Vigilant v0.19.4 (code 39): release.yml green on 23c4b10, vigilant-v0
```
