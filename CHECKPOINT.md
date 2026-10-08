# CHECKPOINT 2744 — read me first, then TASKS.md

**Written:** 2026-10-08T06:09:12Z · **tests:** all 4 fast checks green
**Branch:** `claude/tennis-live-betting-match-o0nzub` · **builds on:** `53ce0178` (this checkpoint is the commit after it)

## Just done
PZ1 done: 'Pinnacle only' removed entirely (setting, scanner mode, auto-bet pass, refresh/forget plumbing, UI, diagnostics, health check, tests); floor green before PZ2-PZ4 edits. In flight: pregame steam judge, hold-off, ReopenStudy probes (not yet wired)

## Do this next
Wire ReopenStudy into PinnLiveRunner (probes on SCORE) + journal in VigilantApp + Diagnostics; compile; tests; then release v0.77.0

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  e1c9e841 ckpt 2743: Researched Novig post-score pause vs Pinnodds lag trade and burst (RESEARCH.
  df9febd3 ckpt 2742: pre-release: v0.76.3: Pinnodds live Stale orders trigger (ask still at Pinna
  828922f5 ckpt 2741: pre-ship: v0.76.3: Pinnodds live Stale orders trigger (ask still at Pinnacle
  2447984a ckpt 2740: Diagnostics: order time (median/slowest) and missed-without-move count for P
  241b337a ckpt 2739: Pinnodds live: new 'Stale orders' trigger (ask still at Pinnacle's earlier p
  77b869b1 ckpt 2738: pre-release: v0.76.2: Pinnodds live matches tennis (Sets+Games children, set
  eff874f5 ckpt 2737: Live trader no longer halts on every in-play order: order wait 2.5s -> 20s (
  4b8aead5 ckpt 2736: Fixed Pinnodds live not matching tennis: Pinnacle books tennis as Sets (winn
  d1e4537b ckpt 2735: Audit made resumable by another session: tools/research/pinnodds_audit_workf
  d6215c3e ckpt 2734: Added tools/research/pinn_pregame.py (pregame recorder+analyzer, exits on ev
```

(27 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
