# CHECKPOINT 3110 — read me first, then TASKS.md

**Written:** 2026-10-12T19:47:47Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `ebecc3e79` (this checkpoint is the commit after it)

## Just done
Recorded TBL1-5 (BoltOdds soft-book live engine). v0.94.2 release pending (trigger trig_014A4VpVafYv85dMg4fNGxvU).

## Do this next
Build TBL1 (is_live parse), TBL2 consensus (pure+tests), TBL3 BoltLiveFeed + PinnFeedChoice.BOLT wiring in VigilantApp.pinnTick/openFeed + Settings, TBL4 push callback instead of 250ms poll.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  49c395073 ckpt 3109: pre-release: v0.94.2: streaming updates no longer wait for a scan or for th
  52ad0af9a ckpt 3108: pre-ship: v0.94.2: streaming updates no longer wait for a scan or for the b
  95ee15844 ckpt 3107: pre-release: v0.94.1: streaming engine matches per line: a Novig push re-pr
  5ab341b8e ckpt 3106: pre-ship: v0.94.1: streaming engine matches per line: a Novig push re-price
  ba5ec75ab ckpt 3105: pre-release: v0.94.0: streaming engine (Novig and BoltOdds pushes start a s
  3c50031f2 ckpt 3104: pre-ship: v0.94.0: streaming engine (Novig and BoltOdds pushes start a scan
  8a2711b77 ckpt 3103: pre-ship: v0.94.0: streaming engine (Novig and BoltOdds pushes start a scan
  9b66c0e71 ckpt 3102: pre-release: v0.93.4: repeat and hedge guard (a side is bet or bid at most 
  21712abeb ckpt 3101: pre-ship: v0.93.4: repeat and hedge guard (a side is bet or bid at most twi
  9c78c9d5f ckpt 3100: TST2 done: RepeatGuard (pure, RepeatGuardTest) wired into ApiBetPlacer (aut
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
