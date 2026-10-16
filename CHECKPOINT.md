# CHECKPOINT 3474 — read me first, then TASKS.md

**Written:** 2026-10-16T19:46:43Z · **tests:** all 5 fast checks green
**Branch:** `ccr-95eefe49-t2qp21` · **builds on:** `f31e0c6f0` (this checkpoint is the commit after it)

## Just done
pre-release: v0.114.9: PropLine Pro: paid key found and used first, every game's props incl. NBA and NHL, a PropLine +EV-at-Novig screen (from the +EV tab), plan shown in the key test (versionCode 305, v0.114.9)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.114.9), then run: bash tools/record-release.sh v0.114.9 305 "v0.114.9: PropLine Pro: paid key found and used first, every game's props incl. NBA and NHL, a PropLine +EV-at-Novig screen (from the +EV tab), plan shown in the key test"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  f31e0c6f0 ckpt 3473: pre-ship: v0.114.9: PropLine Pro: paid key found and used first, every game
  8f7a36e9d ckpt 3472: TCL57: SGO NFL guide checked: Vigilant already follows it, documented
  050acec3b ckpt 3471: CLAUDE.md: PropLine Pro rule
  6e2aba5de ckpt 3470: propline probe: finds a finished event from the export; adds player trends/
  7ecf772bf ckpt 3469: propline.yml: clv task + after the daily archive
  073d217fb ckpt 3468: TCL56f: propline_clv.py (CLV of every engine bet from PropLine archive) + s
  f1fa57c48 ckpt 3467: TCL56e: PropLine +EV sweep (engine) + phone screen
  919679d21 ckpt 3466: propline.py: export per sport and book (novig, pinnacle), streamed to gzip;
  6c4c69d72 ckpt 3465: propline.py selftest independent of the environment
  736816dc0 ckpt 3464: TCL56g: tools/propline.py (plans/probe/archive) + propline.yml workflow
```
