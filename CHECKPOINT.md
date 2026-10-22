# CHECKPOINT 4016 — read me first, then TASKS.md

**Written:** 2026-10-22T23:07:29Z · **tests:** all 9 fast checks green
**Branch:** `claude/optimistic-curie-lpr7ge` · **builds on:** `993f900cc` (this checkpoint is the commit after it)

## Just done
pre-release: CH65c the phone never scans Kalshi itself while the GitHub engine drives: it only reads the engine's Kalshi list (fixes the out-of-memory crashes) (versionCode 391, v1.0.51)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.51), then run: bash tools/record-release.sh v1.0.51 391 "CH65c the phone never scans Kalshi itself while the GitHub engine drives: it only reads the engine's Kalshi list (fixes the out-of-memory crashes)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  d5efe45d5 ckpt 4015: bump 1.0.51 (391)
  1a6678b9b ckpt 4014: AF done: v1.0.50 released and recorded; report artifact published
  12df96f3e ckpt 4014: CH65c: phone never scans Kalshi while cloud drives
  cac87f30c ckpt 4013: AF: v1.0.50 gated and pushed, release.yml triggered; report artifact https:
  8d01b69fc ckpt 4011: CH65: lag/crash likely local Kalshi scan during engine restarts; 1.0.49 wai
  16d814aa7 ckpt 4012: pre-release: API audit (AF): Novig catalog best-bid screening, PropLine bul
  097bf2e90 ckpt 4011: pre-ship: API audit (AF): Novig catalog best-bid screening, PropLine bulk p
  d3d3139c0 ckpt 4010: handoff: API audit to acct-27a37e; releasing 1.0.49
  3783adc03 ckpt 4006: pre-release: CH62a Arb note shows the engine's full check; CH63a Kalshi bet
  064b848cd ckpt 4009: AF: report research/api_full_audit_2026-10-22.md, API memory docs, CHANGES,
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
