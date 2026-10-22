# CHECKPOINT 4006 — read me first, then TASKS.md

**Written:** 2026-10-22T22:40:39Z · **tests:** all 9 fast checks green
**Branch:** `claude/optimistic-curie-lpr7ge` · **builds on:** `fed103748` (this checkpoint is the commit after it)

## Just done
pre-release: CH62a Arb note shows the engine's full check; CH63a Kalshi bets from the engine list keep their ticker; CH64a the phone waits for the engine's Kalshi list instead of scanning Kalshi itself (versionCode 389, v1.0.49)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.49), then run: bash tools/record-release.sh v1.0.49 389 "CH62a Arb note shows the engine's full check; CH63a Kalshi bets from the engine list keep their ticker; CH64a the phone waits for the engine's Kalshi list instead of scanning Kalshi itself"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  fed103748 ckpt 4005: bump 1.0.49 (389)
  62dba7012 ckpt 4004: CH63a: CloudCodec keeps Kalshi ticker (bookRef)
  47176632c ckpt 4003: CH62a: Arb note shows the engine's full check
  2df747e57 ckpt 4002: AF1: api-docs mirror workflow; research index rebuilt
  b569e2b18 ckpt 4001: AF1: api-docs mirror workflow + tool
  cc5f7c524 ckpt 4000: CH60b: PinnWire/pinnapi wait for SharpAPI Pinnacle (ApiChain.pinnacleNeeded
  670802ba9 ckpt 3999: CH60a answered
  f4ad84602 ckpt 3998: CH59a closed (Novig lists 1 live tennis game, matched); CH59b audit
  a622da7a6 ckpt 3997: CH58k closed: lane judges tennis, no lag edge
  ae436ce1d ckpt 3996: CH58k: found live lane idle because Tj's Live master switch is OFF (phone, 
```
