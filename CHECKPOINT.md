# CHECKPOINT 3364 — read me first, then TASKS.md

**Written:** 2026-10-15T20:03:45Z · **tests:** all 5 fast checks green
**Branch:** `ccr-09f8e1bf-qtmu63` · **builds on:** `6c2b70cfa` (this checkpoint is the commit after it)

## Just done
pre-release: v0.109.0: Engine dashboard inside the app (Settings > Engine dashboard, tap the Feed's GitHub bar): engine state, tiles, per-API health, usage meters, trend graphs; wallet shows not-read instead of 0; rotation failover covers whole duties (versionCode 288, v0.109.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.109.0), then run: bash tools/record-release.sh v0.109.0 288 "v0.109.0: Engine dashboard inside the app (Settings > Engine dashboard, tap the Feed's GitHub bar): engine state, tiles, per-API health, usage meters, trend graphs; wallet shows not-read instead of 0; rotation failover covers whole duties"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  6c2b70cfa ckpt 3363: TCL23a: routine setup rewritten (UI-created routines with the repo attached
  6ab6ec346 ckpt 3362: pre-ship: v0.109.0: Engine dashboard inside the app (Settings > Engine dash
  6dd1373c0 ckpt 3361: TCL24a-c: in-app Engine dashboard (Settings > Engine dashboard, tap the Fee
  b908f24a7 ckpt 3360: TCL24 written (in-app dashboard)
  d04b47104 ckpt 3359: TCL23c: Settings > Cloud scan shows which APIs work on GitHub + link to DAS
  69e1c2513 ckpt 3358: TCL23a: rotation failover (cover up to 2, whole duty first, INTERRUPTED sta
  fe049f26d ckpt 3357: TCL22a wallet retry in 60 s (CloudEngine, test), TCL22d CloudDashboard + Fe
  0fe1a14fe ckpt 3356: TCL22 written (wallet, conflicts, address, dashboard)
  62dd1cb15 ckpt 3355: v0.108.0 released and recorded; TCL21 a-e,h done
  f804cb535 ckpt 3354: pre-release: the phone shows what the GitHub engine did (wallet, orders, wi
```
