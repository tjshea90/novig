# CHECKPOINT 4011 — read me first, then TASKS.md

**Written:** 2026-10-22T22:50:27Z · **tests:** all 9 fast checks green
**Branch:** `ccr-950a7aea-8abdo8` · **builds on:** `3f99971c8` (this checkpoint is the commit after it)

## Just done
pre-ship: API audit (AF): Novig catalog best-bid screening, PropLine bulk props + free injuries, BoltOdds double-count fix and socket visibility, Polymarket live CLOB prices, PinnWire one request for all sports, governor rests useless APIs, advisor credit pace, weekly API docs mirror

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/build.gradle.kts

## Last ten checkpoints
```
  d3d3139c0 ckpt 4010: handoff: API audit to acct-27a37e; releasing 1.0.49
  3783adc03 ckpt 4006: pre-release: CH62a Arb note shows the engine's full check; CH63a Kalshi bet
  064b848cd ckpt 4009: AF: report research/api_full_audit_2026-10-22.md, API memory docs, CHANGES,
  4faa781e3 ckpt 4008: AF: PinnWire sport_id=all (one request for every sport, ~6x fewer of the 10
  fed103748 ckpt 4005: bump 1.0.49 (389)
  7082c59b1 ckpt 4007: AF: Polymarket prices re-read live from CLOB /books (Gamma's are a cached c
  6b4bdc0fd ckpt 4006: AF: BoltOdds consensus + DK/FD prediction markets out of fair lines (double
  da26ad4f5 ckpt 4005: AF: PropLine bulk slate props (1 call per sport/part vs per game), STEALS_B
  7330825f1 ckpt 4004: AF: Novig catalog best bids screen the scan (2 requests instead of ~9,600 b
  62dba7012 ckpt 4004: CH63a: CloudCodec keeps Kalshi ticker (bookRef)
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
