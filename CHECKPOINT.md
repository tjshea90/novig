# CHECKPOINT 2734 — read me first, then TASKS.md

**Written:** 2026-10-08T02:53:38Z · **tests:** all 4 fast checks green
**Branch:** `ccr-f7044881-1c7epp` · **builds on:** `9e94c2ff` (this checkpoint is the commit after it)

## Just done
Added tools/research/pinn_pregame.py (pregame recorder+analyzer, exits on eviction, refuses if /health connected_clients>0) and simulate --dump; pregame tape recording in scratchpad (pid file pinn/pre.pid, 50 min from 02:47Z); studied the 52-min pool: big EV (>15%) and 'standing' edges are stale Pinnacle lines on finished games or spikes

## Do this next
When audit workflow wf_e596e2b2-378 finishes: save its findings to research/pinnodds_audit_2026-10-08/, then implement guards (EV window 5-15%, line re-confirmation freshness, old-fair alignment, book spread, decision-age cap, STANDING off for real bets) + pregame trigger; analyse the pregame tape with pinn_pregame.py analyze

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  62e0fc93 ckpt 2733: Wrote Tj's real-money safeguards + pregame request into TASKS.md (PX1-PX4); 
  c20dd2df ckpt 2732: pre-release: v0.76.1: Pinnodds live runs in the background with the screen o
  2963905b ckpt 2731: v0.76.1 ready: LiveFeedService (foreground service holding the CPU while the
  b6b1c150 ckpt 2730: pre-release: v0.76.0: Pinnodds live (Pinnacle vs Novig): Pinnodds WebSocket 
  3e95a5d8 ckpt 2729: Pinnodds live ready to ship: all-sports league list, final pooled study (52 
  61095455 ckpt 2728: Pinnodds live: UI test + settings search entries, MockWebServer tests for th
  9a4d414e ckpt 2727: Pinnodds live: app wiring done (ApiProvider.PINNODDS, ScanSettings pinnLive*
  6136ef32 ckpt 2726: PINNODDS LIVE (TASKS.md PW*): read pinnodds docs + tested the key (trial_dem
  a4df2b62 ckpt 2725: Made the ten-sources research resumable by any session/account: tools/resear
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
