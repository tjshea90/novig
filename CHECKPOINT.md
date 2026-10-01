# CHECKPOINT 2281 — read me first, then TASKS.md

**Written:** 2026-10-01T23:49:27Z · **tests:** all 3 fast checks green
**Branch:** `ccr-650fc40f-rtt4er` · **builds on:** `737304b0` (this checkpoint is the commit after it)

## Just done
pre-release: v0.41.0: Keep awake keeps background auto-scan and auto-bet on schedule with the screen off, locked and the phone idle: while auto-scan runs faster than every 9 minutes the service holds a partial wake lock (CPU only, never the screen) and runs the cycles from its own loop, with the alarm demoted to a safety net, a restart after a swipe-away, a switch in Settings › Background auto-scan (on by default), battery Unrestricted prompts, and a cycle record in Diagnostics (cycles with the screen off and in Doze, late ones, standby bucket, Battery Saver) (versionCode 76, v0.41.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.41.0), then run: bash tools/record-release.sh v0.41.0 76 "v0.41.0: Keep awake keeps background auto-scan and auto-bet on schedule with the screen off, locked and the phone idle: while auto-scan runs faster than every 9 minutes the service holds a partial wake lock (CPU only, never the screen) and runs the cycles from its own loop, with the alarm demoted to a safety net, a restart after a swipe-away, a switch in Settings › Background auto-scan (on by default), battery Unrestricted prompts, and a cycle record in Diagnostics (cycles with the screen off and in Doze, late ones, standby bucket, Battery Saver)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  737304b0 ckpt 2280: AK3 built and mutation-checked: KeepAwake rules, CycleLog meter, service loo
  6a8935c1 ckpt 2279: AK3 in progress: KeepAwake rules, CycleLog, AutoScanService loop + watchdog,
  f79ef723 ckpt 2278: wrote Tj's keep-alive research request into TASKS.md as AK1-AK4
  56129545 ckpt 2277: v0.40.2 released and recorded: auto-bet every-book-must-agree switch (AJ1-AJ
  8f849aa9 ckpt 2276: pre-release: v0.40.2: auto-bet can require that every book scanned agrees th
  22997e5d ckpt 2275: wrote Tj's all-books-agree request into TASKS.md as AJ1-AJ3
  395bc7eb ckpt 2274: v0.40.1 released and recorded: Check odds now gets every closing line and ho
  ebea8af1 ckpt 2273: pre-release: v0.40.1: Check odds now looks for every closing line (every sta
  f45b3717 ckpt 2272: AI2-AI3 built: forced close backfill (CloseBackfill.run(force)), FocusGate h
  727b540c ckpt 2271: wrote Tj's Check-odds-now request into TASKS.md as AI1-AI4
```
