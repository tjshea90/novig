# CHECKPOINT 2736 — read me first, then TASKS.md

**Written:** 2026-10-08T04:16:07Z · **tests:** all 4 fast checks green
**Branch:** `claude/tennis-live-betting-match-o0nzub` · **builds on:** `6bea1aac` (this checkpoint is the commit after it)

## Just done
Fixed Pinnodds live not matching tennis: Pinnacle books tennis as Sets (winner) + Games (spread/total) children with units != Regular, which PinnBook dropped. Now both kept, Games suffix stripped, targets read their own child, sets-line guard, tests in LiveMatcherTest (77 pinnodds tests green)

## Do this next
Tell Tj tennis now matches; tennis socket frames carried no score in the tape so the default 'After a score' trigger will not fire for tennis (use a price-move trigger); then resume PX2/PX3 and ship via release.yml

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  d1e4537b ckpt 2735: Audit made resumable by another session: tools/research/pinnodds_audit_workf
  d6215c3e ckpt 2734: Added tools/research/pinn_pregame.py (pregame recorder+analyzer, exits on ev
  62e0fc93 ckpt 2733: Wrote Tj's real-money safeguards + pregame request into TASKS.md (PX1-PX4); 
  c20dd2df ckpt 2732: pre-release: v0.76.1: Pinnodds live runs in the background with the screen o
  2963905b ckpt 2731: v0.76.1 ready: LiveFeedService (foreground service holding the CPU while the
  b6b1c150 ckpt 2730: pre-release: v0.76.0: Pinnodds live (Pinnacle vs Novig): Pinnodds WebSocket 
  3e95a5d8 ckpt 2729: Pinnodds live ready to ship: all-sports league list, final pooled study (52 
  61095455 ckpt 2728: Pinnodds live: UI test + settings search entries, MockWebServer tests for th
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
