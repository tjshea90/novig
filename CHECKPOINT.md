# CHECKPOINT 2738 — read me first, then TASKS.md

**Written:** 2026-10-08T04:27:36Z · **tests:** all 4 fast checks green
**Branch:** `claude/tennis-live-betting-match-o0nzub` · **builds on:** `c65db13a` (this checkpoint is the commit after it)

## Just done
pre-release: v0.76.2: Pinnodds live matches tennis (Sets+Games children, sets-line guard); live orders wait up to 20 s for Novig's in-play delay instead of halting at 2.5 s (versionCode 136, v0.76.2)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.76.2), then run: bash tools/record-release.sh v0.76.2 136 "v0.76.2: Pinnodds live matches tennis (Sets+Games children, sets-line guard); live orders wait up to 20 s for Novig's in-play delay instead of halting at 2.5 s"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  eff874f5 ckpt 2737: Live trader no longer halts on every in-play order: order wait 2.5s -> 20s (
  4b8aead5 ckpt 2736: Fixed Pinnodds live not matching tennis: Pinnacle books tennis as Sets (winn
  d1e4537b ckpt 2735: Audit made resumable by another session: tools/research/pinnodds_audit_workf
  d6215c3e ckpt 2734: Added tools/research/pinn_pregame.py (pregame recorder+analyzer, exits on ev
  62e0fc93 ckpt 2733: Wrote Tj's real-money safeguards + pregame request into TASKS.md (PX1-PX4); 
  c20dd2df ckpt 2732: pre-release: v0.76.1: Pinnodds live runs in the background with the screen o
  2963905b ckpt 2731: v0.76.1 ready: LiveFeedService (foreground service holding the CPU while the
  b6b1c150 ckpt 2730: pre-release: v0.76.0: Pinnodds live (Pinnacle vs Novig): Pinnodds WebSocket 
  3e95a5d8 ckpt 2729: Pinnodds live ready to ship: all-sports league list, final pooled study (52 
  61095455 ckpt 2728: Pinnodds live: UI test + settings search entries, MockWebServer tests for th
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
