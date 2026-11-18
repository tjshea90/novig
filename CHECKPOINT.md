# CHECKPOINT 4650 — read me first, then TASKS.md

**Written:** 2026-11-18T05:55:42Z · **tests:** all 10 fast checks green
**Branch:** `ccr-f4518318-aidjgt` · **builds on:** `bdc9dda5a` (this checkpoint is the commit after it)

## Just done
MK7: corrected the quotable-market analysis (+0.93 pts median, 6% return wins 2%: section 12), evidence counters count only quotable trades, restored Tj's own app settings (return 2%, 35/50, 4 legs) after a session overwrote the return for 4 minutes; v1.0.138 released

## Do this next
watch the live maker (first win/confirm/fill, first settlement); next engine reset loads the counter fix; MK5 grader; MK6 same-game via SGP correlation; tell Tj the corrected expectation

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  ade928021 ckpt 4649: pre-release: MKV: parlay maker results in the app (Settled parlays tile, 24
  92d776b01 ckpt 4648: pre-ship: MKV: parlay maker results in the app (Settled parlays tile, 24 h 
  7b408180b ckpt 4647: MK7: parlay maker LIVE since 04:50 UTC 2026-11-18 (Tj: 'Live, small limits'
  b8e884a27 ckpt 4646: engine reset for PropLine EV sweep; DG3 live lag not yet diagnosed
  4c681795a ckpt 4645: DG2 tests green; DG3 logged
  4f9d09d60 ckpt 4644: DG2: PropLine EV sweep on, phone bets kept 8 days
  363ba23cc ckpt 4643: DG2 logged
  a23a545ca ckpt 4642: chat: triaged diagnostics report DG1
  3891f24bb ckpt 4641: MKV3: LIVE prerequisites: 24 h realized-loss stop from Novig's executions (
  09268ea2c ckpt 4640: MKV: v1.0.137 released (recorded); MKV2 game-lines-only maker + 6% return d
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
