# CHECKPOINT 2739 — read me first, then TASKS.md

**Written:** 2026-10-08T04:47:47Z · **tests:** all 4 fast checks green
**Branch:** `claude/tennis-live-betting-match-o0nzub` · **builds on:** `8b620cb5` (this checkpoint is the commit after it)

## Just done
Pinnodds live: new 'Stale orders' trigger (ask still at Pinnacle's earlier price, ~2 min lookback, no score needed, sweeps like Any edge) and a maxEv 40% cap refusing probable mismatches on every trigger; 79 pinnodds tests green

## Do this next
Ask Tj to send Diagnostics Pinnodds block (skip reasons) to see why default finds nothing; release as v0.76.3 if he wants Stale orders

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  77b869b1 ckpt 2738: pre-release: v0.76.2: Pinnodds live matches tennis (Sets+Games children, set
  eff874f5 ckpt 2737: Live trader no longer halts on every in-play order: order wait 2.5s -> 20s (
  4b8aead5 ckpt 2736: Fixed Pinnodds live not matching tennis: Pinnacle books tennis as Sets (winn
  d1e4537b ckpt 2735: Audit made resumable by another session: tools/research/pinnodds_audit_workf
  d6215c3e ckpt 2734: Added tools/research/pinn_pregame.py (pregame recorder+analyzer, exits on ev
  62e0fc93 ckpt 2733: Wrote Tj's real-money safeguards + pregame request into TASKS.md (PX1-PX4); 
  c20dd2df ckpt 2732: pre-release: v0.76.1: Pinnodds live runs in the background with the screen o
  2963905b ckpt 2731: v0.76.1 ready: LiveFeedService (foreground service holding the CPU while the
  b6b1c150 ckpt 2730: pre-release: v0.76.0: Pinnodds live (Pinnacle vs Novig): Pinnodds WebSocket 
  3e95a5d8 ckpt 2729: Pinnodds live ready to ship: all-sports league list, final pooled study (52 
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
