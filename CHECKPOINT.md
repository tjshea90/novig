# CHECKPOINT 2726 — read me first, then TASKS.md

**Written:** 2026-10-08T01:21:13Z · **tests:** all 4 fast checks green
**Branch:** `ccr-f7044881-1c7epp` · **builds on:** `5926ac2d` (this checkpoint is the commit after it)

## Just done
PINNODDS LIVE (TASKS.md PW*): read pinnodds docs + tested the key (trial_demo, WS add-on active until 2026-10-10 23:34Z); wrote tools/research/pinnodds_tape.py + pinn_novig_lag.py (the study recorder, running in the scratchpad; ONE pinnodds socket per account so stop it before the app uses the feed); built data/pinnodds/ (PinnBook, LiveEdge, LiveMatcher, PinnSocket, PinnKeyTest, PinnLiveTrader, PinnLiveRunner) with 56 passing tests on real captured frames; found Novig's websocket now has place/cancel verbs

## Do this next
NEXT: PW7 app wiring: ApiProvider.PINNODDS key + ScanSettings fields (pinnLive, pinnLiveBet, stake, caps, rules) + VigilantApp (runner, trader, orders port, tick) + Settings section with Test key button + Diagnostics lines + ScannerFilter.PINNODDS; then stop the study recorder, save study results to research/pinnodds_2026-10-08/, NOVIG_API.md section 21, RESEARCH.md section 116, floor green, release v0.76.0

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  a4df2b62 ckpt 2725: Made the ten-sources research resumable by any session/account: tools/resear
```

(28 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
