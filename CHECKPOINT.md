# CHECKPOINT 4012 — read me first, then TASKS.md

**Written:** 2026-10-22T22:53:12Z · **tests:** all 9 fast checks green
**Branch:** `ccr-950a7aea-8abdo8` · **builds on:** `097bf2e90` (this checkpoint is the commit after it)

## Just done
pre-release: API audit (AF): Novig catalog best-bid screening, PropLine bulk props + free injuries, BoltOdds double-count fix and socket visibility, Polymarket live CLOB prices, PinnWire one request for all sports, governor rests useless APIs, advisor credit pace, weekly API docs mirror (versionCode 390, v1.0.50)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.50), then run: bash tools/record-release.sh v1.0.50 390 "API audit (AF): Novig catalog best-bid screening, PropLine bulk props + free injuries, BoltOdds double-count fix and socket visibility, Polymarket live CLOB prices, PinnWire one request for all sports, governor rests useless APIs, advisor credit pace, weekly API docs mirror"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  097bf2e90 ckpt 4011: pre-ship: API audit (AF): Novig catalog best-bid screening, PropLine bulk p
  d3d3139c0 ckpt 4010: handoff: API audit to acct-27a37e; releasing 1.0.49
  3783adc03 ckpt 4006: pre-release: CH62a Arb note shows the engine's full check; CH63a Kalshi bet
  064b848cd ckpt 4009: AF: report research/api_full_audit_2026-10-22.md, API memory docs, CHANGES,
  4faa781e3 ckpt 4008: AF: PinnWire sport_id=all (one request for every sport, ~6x fewer of the 10
  fed103748 ckpt 4005: bump 1.0.49 (389)
  7082c59b1 ckpt 4007: AF: Polymarket prices re-read live from CLOB /books (Gamma's are a cached c
  6b4bdc0fd ckpt 4006: AF: BoltOdds consensus + DK/FD prediction markets out of fair lines (double
  da26ad4f5 ckpt 4005: AF: PropLine bulk slate props (1 call per sport/part vs per game), STEALS_B
  7330825f1 ckpt 4004: AF: Novig catalog best bids screen the scan (2 requests instead of ~9,600 b
```
