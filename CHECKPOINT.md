# CHECKPOINT 3294 — read me first, then TASKS.md

**Written:** 2026-10-15T00:07:38Z · **tests:** all 4 fast checks green
**Branch:** `ccr-e8041e9d-7grwre` · **builds on:** `63c786cfa` (this checkpoint is the commit after it)

## Just done
pre-release: v0.101.0: pull to refresh and Scan ask the GitHub engine to scan; the Feed shows its progress, an acknowledgement and measured price/fair ages; the engine mirrors the phone's settings (versionCode 279, v0.101.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.101.0), then run: bash tools/record-release.sh v0.101.0 279 "v0.101.0: pull to refresh and Scan ask the GitHub engine to scan; the Feed shows its progress, an acknowledgement and measured price/fair ages; the engine mirrors the phone's settings"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  63c786cfa ckpt 3293: TCL9: refresh requests, progress, measured currency stat, settings mirror (
  5a182e3ae ckpt 3292: v0.100.2 recorded; engine now has 13 feeds from the app-sent keys and publi
  306606dff ckpt 3291: engine: settings change late in a scan waits for next pass (switch-off stil
  42de5fb66 ckpt 3290: pre-release: v0.100.2: cloud engine sends heartbeats during its first long 
  9df5958eb ckpt 3289: TCL8: heartbeat during long scans, sendKeys no longer restarts the engine f
  f7d2fc39f ckpt 3288: v0.100.1 released and recorded (TCL7)
  b28e3dd5a ckpt 3287: pre-release: v0.100.1: the app sends your API keys to GitHub secrets for th
  948771d04 ckpt 3286: TCL7: app sends API keys to GitHub secrets (SealedBox, sendKeys, button), d
  05f8377e1 ckpt 3285: v0.100.0 released and recorded (TCL5 done)
  9f974b523 ckpt 3284: research doc: built + first live run; Diagnostics CLOUD section; release.ym
```
