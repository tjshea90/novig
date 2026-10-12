# CHECKPOINT 3111 — read me first, then TASKS.md

**Written:** 2026-10-12T20:00:44Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `61ab5be5c` (this checkpoint is the commit after it)

## Just done
pre-release: v0.95.0: live engine on BoltOdds' soft-book average (BoltOdds live games parsed, consensus of companies that pushed within seconds and agree, spoken as Pinnodds frames so the live lane, fee, 5 s rule, trader, live bids and repeat guard run on it; paper only until real orders are allowed), streaming engine now push (versionCode 236, v0.95.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.95.0), then run: bash tools/record-release.sh v0.95.0 236 "v0.95.0: live engine on BoltOdds' soft-book average (BoltOdds live games parsed, consensus of companies that pushed within seconds and agree, spoken as Pinnodds frames so the live lane, fee, 5 s rule, trader, live bids and repeat guard run on it; paper only until real orders are allowed), streaming engine now push"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  ac3fabfd4 ckpt 3110: Recorded TBL1-5 (BoltOdds soft-book live engine). v0.94.2 release pending (
  49c395073 ckpt 3109: pre-release: v0.94.2: streaming updates no longer wait for a scan or for th
  52ad0af9a ckpt 3108: pre-ship: v0.94.2: streaming updates no longer wait for a scan or for the b
  95ee15844 ckpt 3107: pre-release: v0.94.1: streaming engine matches per line: a Novig push re-pr
  5ab341b8e ckpt 3106: pre-ship: v0.94.1: streaming engine matches per line: a Novig push re-price
  ba5ec75ab ckpt 3105: pre-release: v0.94.0: streaming engine (Novig and BoltOdds pushes start a s
  3c50031f2 ckpt 3104: pre-ship: v0.94.0: streaming engine (Novig and BoltOdds pushes start a scan
  8a2711b77 ckpt 3103: pre-ship: v0.94.0: streaming engine (Novig and BoltOdds pushes start a scan
  9b66c0e71 ckpt 3102: pre-release: v0.93.4: repeat and hedge guard (a side is bet or bid at most 
  21712abeb ckpt 3101: pre-ship: v0.93.4: repeat and hedge guard (a side is bet or bid at most twi
```

(16 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
