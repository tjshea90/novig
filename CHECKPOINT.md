# CHECKPOINT 2742 — read me first, then TASKS.md

**Written:** 2026-10-08T05:02:36Z · **tests:** all 4 fast checks green
**Branch:** `claude/tennis-live-betting-match-o0nzub` · **builds on:** `828922f5` (this checkpoint is the commit after it)

## Just done
pre-release: v0.76.3: Pinnodds live Stale orders trigger (ask still at Pinnacle's earlier price, no score needed), 40% edge cap refusing probable mismatches, order-time diagnostics (versionCode 137, v0.76.3)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.76.3), then run: bash tools/record-release.sh v0.76.3 137 "v0.76.3: Pinnodds live Stale orders trigger (ask still at Pinnacle's earlier price, no score needed), 40% edge cap refusing probable mismatches, order-time diagnostics"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  828922f5 ckpt 2741: pre-ship: v0.76.3: Pinnodds live Stale orders trigger (ask still at Pinnacle
  2447984a ckpt 2740: Diagnostics: order time (median/slowest) and missed-without-move count for P
  241b337a ckpt 2739: Pinnodds live: new 'Stale orders' trigger (ask still at Pinnacle's earlier p
  77b869b1 ckpt 2738: pre-release: v0.76.2: Pinnodds live matches tennis (Sets+Games children, set
  eff874f5 ckpt 2737: Live trader no longer halts on every in-play order: order wait 2.5s -> 20s (
  4b8aead5 ckpt 2736: Fixed Pinnodds live not matching tennis: Pinnacle books tennis as Sets (winn
  d1e4537b ckpt 2735: Audit made resumable by another session: tools/research/pinnodds_audit_workf
  d6215c3e ckpt 2734: Added tools/research/pinn_pregame.py (pregame recorder+analyzer, exits on ev
  62e0fc93 ckpt 2733: Wrote Tj's real-money safeguards + pregame request into TASKS.md (PX1-PX4); 
  c20dd2df ckpt 2732: pre-release: v0.76.1: Pinnodds live runs in the background with the screen o
```
