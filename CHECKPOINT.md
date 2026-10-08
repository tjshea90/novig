# CHECKPOINT 2745 — read me first, then TASKS.md

**Written:** 2026-10-08T06:11:52Z · **tests:** all 4 fast checks green
**Branch:** `claude/tennis-live-betting-match-o0nzub` · **builds on:** `526ba73d` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.77.0: Pinnacle only removed entirely; Pinnodds live pregame steam (stale prematch orders, no fee), hold-off after a score, 40% edge cap kept, post-score study in Diagnostics

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M RESEARCH.md
     M app/build.gradle.kts

## Last ten checkpoints
```
  5c280e8b ckpt 2744: PZ1 done: 'Pinnacle only' removed entirely (setting, scanner mode, auto-bet 
  e1c9e841 ckpt 2743: Researched Novig post-score pause vs Pinnodds lag trade and burst (RESEARCH.
  df9febd3 ckpt 2742: pre-release: v0.76.3: Pinnodds live Stale orders trigger (ask still at Pinna
  828922f5 ckpt 2741: pre-ship: v0.76.3: Pinnodds live Stale orders trigger (ask still at Pinnacle
  2447984a ckpt 2740: Diagnostics: order time (median/slowest) and missed-without-move count for P
  241b337a ckpt 2739: Pinnodds live: new 'Stale orders' trigger (ask still at Pinnacle's earlier p
  77b869b1 ckpt 2738: pre-release: v0.76.2: Pinnodds live matches tennis (Sets+Games children, set
  eff874f5 ckpt 2737: Live trader no longer halts on every in-play order: order wait 2.5s -> 20s (
  4b8aead5 ckpt 2736: Fixed Pinnodds live not matching tennis: Pinnacle books tennis as Sets (winn
  d1e4537b ckpt 2735: Audit made resumable by another session: tools/research/pinnodds_audit_workf
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
