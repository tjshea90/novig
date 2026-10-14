# CHECKPOINT 3226 — read me first, then TASKS.md

**Written:** 2026-10-14T06:29:57Z · **tests:** all 4 fast checks green
**Branch:** `ccr-147419fb-llvuq5` · **builds on:** `df83b480a` (this checkpoint is the commit after it)

## Just done
pre-release: v0.99.5: one Live features master switch (every live lane and socket) on the Settings home and a new Live light page (not under Pinnodds), Compose no longer deep-compares the app state on the main thread, tennis auto-bets off, Diagnostics FAIR CALIBRATION (versionCode 264, v0.99.5)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.99.5), then run: bash tools/record-release.sh v0.99.5 264 "v0.99.5: one Live features master switch (every live lane and socket) on the Settings home and a new Live light page (not under Pinnodds), Compose no longer deep-compares the app state on the main thread, tennis auto-bets off, Diagnostics FAIR CALIBRATION"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  df83b480a ckpt 3225: pre-ship: v0.99.5: one Live features master switch (every live lane and soc
  e1618641f ckpt 3224: pre-release: v0.99.4: player-prop fair prices cut (Overs 7 points, Unders 3
  3826fa353 ckpt 3223: pre-ship: v0.99.4: player-prop fair prices cut (Overs 7 points, Unders 3: t
  0b3aacb2a ckpt 3222: bet history 2026-10-14 read: longshot/prop EV overstated; profit_profile.js
  c323738a3 ckpt 3221: pre-release: v0.99.3: Live light loosened (model fair 90%, bids 7% under th
  debd9aea9 ckpt 3220: pre-ship: v0.99.3: Live light loosened (model fair 90%, bids 7% under the f
  ff90d7ec2 ckpt 3219: v0.99.2 released and recorded
  65f736872 ckpt 3218: pre-release: v0.99.2: Live light shows on the Bids tab (status, one line pe
  0b7132df8 ckpt 3217: pre-ship: v0.99.2: Live light shows on the Bids tab (status, one line per w
  da7b68cbe ckpt 3216: v0.99.1 released and recorded
```
