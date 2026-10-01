# CHECKPOINT 2225 — read me first, then TASKS.md

**Written:** 2026-10-01T01:17:52Z · **tests:** all 3 fast checks green
**Branch:** `ccr-01f0c1f9-xy73ag` · **builds on:** `4cc218c5` (this checkpoint is the commit after it)

## Just done
pre-release: v0.36.2: Kelly amount in every bet slip (in-app Bet sheet and Novig links, to the cent), Kelly math verified with worked numbers, smooth Check odds now (throttled progress and saves, work off the main thread), full tests: badges and CNO screening memoized (versionCode 66, v0.36.2)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.36.2), then run: bash tools/record-release.sh v0.36.2 66 "v0.36.2: Kelly amount in every bet slip (in-app Bet sheet and Novig links, to the cent), Kelly math verified with worked numbers, smooth Check odds now (throttled progress and saves, work off the main thread), full tests: badges and CNO screening memoized"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  1e0d0d39 ckpt 2224: Y4 full tests done: floor green 1,279 (22 live skipped), 91 screenshots OK, 
  140c86fa ckpt 2223: Y3: Check odds now progress and Tracker saves throttled into the screen (300
  6b8ef3ce ckpt 2222: Y1-Y2: Bet sheet follows the Kelly slip setting (per-bet Kelly to the cent, 
  42427dc5 ckpt 2221: Logged Tj's 2026-10-01 request as TASKS.md Y1-Y5 (Kelly in bet slips + math,
  be961a80 ckpt 2220: v0.36.1 (code 65) released and recorded: X1-X3 done (mid-scan lag/crash)
  7a8e815a ckpt 2219: pre-release: v0.36.1: no more lag and crash switching tabs mid-scan (the scr
  aa28a1c8 ckpt 2218: X1/X2: scan and usage mirrors throttled (350 ms / 1 s), feed rebuilt only on
  809c349d ckpt 2217: Logged Tj's crash report (switching tabs during a Vigilant scan: laggy then 
  45b6058b ckpt 2216: v0.36.0 (code 64) released and recorded: W1-W4 done
  af9b6d8a ckpt 2215: pre-release: v0.36.0: Diagnostics fixes from Tj's first report (no false ala
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
