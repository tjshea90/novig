# CHECKPOINT 3299 — read me first, then TASKS.md

**Written:** 2026-10-15T01:08:00Z · **tests:** all 4 fast checks green
**Branch:** `ccr-e8041e9d-7grwre` · **builds on:** `35b8fbe3e` (this checkpoint is the commit after it)

## Just done
pre-release: v0.102.0: cloud review: bids keep the phone scanning (the cloud list lacks every priced line), the Feed's recheck works in cloud mode, pause stops the engine, every setting classified (fetch/pricing/filter/phone), freshness chip in the Feed title, settings-sync line, engine log on cloud-feed (versionCode 280, v0.102.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.102.0), then run: bash tools/record-release.sh v0.102.0 280 "v0.102.0: cloud review: bids keep the phone scanning (the cloud list lacks every priced line), the Feed's recheck works in cloud mode, pause stops the engine, every setting classified (fetch/pricing/filter/phone), freshness chip in the Feed title, settings-sync line, engine log on cloud-feed"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  35b8fbe3e ckpt 3298: TCL10: efficiency (poll cadence, 5 s clock, requote throttle), push/keys mu
  2a846efcc ckpt 3297: TCL10: cloud review fixes (bids pause, recheck, pause/kill, token removal, 
  d022bf6e3 ckpt 3296: TCL10 written (review/fix/simplify/mirror/refresh/freshness)
  e4cb95841 ckpt 3295: v0.101.0 released and recorded; engine restarted on the new engine code
  669e24f8f ckpt 3294: pre-release: v0.101.0: pull to refresh and Scan ask the GitHub engine to sc
  63c786cfa ckpt 3293: TCL9: refresh requests, progress, measured currency stat, settings mirror (
  5a182e3ae ckpt 3292: v0.100.2 recorded; engine now has 13 feeds from the app-sent keys and publi
  306606dff ckpt 3291: engine: settings change late in a scan waits for next pass (switch-off stil
  42de5fb66 ckpt 3290: pre-release: v0.100.2: cloud engine sends heartbeats during its first long 
  9df5958eb ckpt 3289: TCL8: heartbeat during long scans, sendKeys no longer restarts the engine f
```
