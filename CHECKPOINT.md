# CHECKPOINT 2743 — read me first, then TASKS.md

**Written:** 2026-10-08T05:48:56Z · **tests:** all 4 fast checks green
**Branch:** `claude/tennis-live-betting-match-o0nzub` · **builds on:** `80c40a01` (this checkpoint is the commit after it)

## Just done
Researched Novig post-score pause vs Pinnodds lag trade and burst (RESEARCH.md §118, TASKS PY1-PY4); orders now record seconds since score + Novig status, Pinnodds Diagnostics splits sent/filled/missed by timing (PinnReportTest)

## Do this next
Release as v0.76.4 if Tj wants the pause test in his next Diagnostics; then read 'Orders by timing' (PY3); PX3 pregame mode is the pause-proof next build

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  df9febd3 ckpt 2742: pre-release: v0.76.3: Pinnodds live Stale orders trigger (ask still at Pinna
  828922f5 ckpt 2741: pre-ship: v0.76.3: Pinnodds live Stale orders trigger (ask still at Pinnacle
  2447984a ckpt 2740: Diagnostics: order time (median/slowest) and missed-without-move count for P
  241b337a ckpt 2739: Pinnodds live: new 'Stale orders' trigger (ask still at Pinnacle's earlier p
  77b869b1 ckpt 2738: pre-release: v0.76.2: Pinnodds live matches tennis (Sets+Games children, set
  eff874f5 ckpt 2737: Live trader no longer halts on every in-play order: order wait 2.5s -> 20s (
  4b8aead5 ckpt 2736: Fixed Pinnodds live not matching tennis: Pinnacle books tennis as Sets (winn
  d1e4537b ckpt 2735: Audit made resumable by another session: tools/research/pinnodds_audit_workf
  d6215c3e ckpt 2734: Added tools/research/pinn_pregame.py (pregame recorder+analyzer, exits on ev
  62e0fc93 ckpt 2733: Wrote Tj's real-money safeguards + pregame request into TASKS.md (PX1-PX4); 
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
