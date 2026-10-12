# CHECKPOINT 3043 — read me first, then TASKS.md

**Written:** 2026-10-12T04:26:23Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `e299c8137` (this checkpoint is the commit after it)

## Just done
pre-release: v0.89.35: Over-prop edge bar, lead-time Kelly scale, bid desk skips unfillable bids (24 h horizon), APIs & keys settings hub, diagnostics fixes (stale key FAIL, OddsPapi Free budget, ParlayAPI usage reads, time-boxed bid step), Novig reads counted by caller (versionCode 220, v0.89.35)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.89.35), then run: bash tools/record-release.sh v0.89.35 220 "v0.89.35: Over-prop edge bar, lead-time Kelly scale, bid desk skips unfillable bids (24 h horizon), APIs & keys settings hub, diagnostics fixes (stale key FAIL, OddsPapi Free budget, ParlayAPI usage reads, time-boxed bid step), Novig reads counted by caller"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  e299c8137 ckpt 3042: TAN: NET event line carries the start of a 400's body; follow-ups (e)(f) wr
  8a91641a5 ckpt 3041: version 0.89.35; TASKS TAN2-TAN6 ticked with evidence; skill doc lists the 
  b1b4d24d0 ckpt 3040: RESEARCH §135 written (the TAN analysis + what was acted on and what was n
  f8f04ecbd ckpt 3039: TAN6: Kelly stake scaled by lead time (EvMath.leadScale: 1.0 up to 9h, 0.6 
  93fe0376d ckpt 3038: TAN4: Settings home 20 -> 14 rows: APIs & keys hub with child pages (SGO, O
  e132c821e ckpt 3037: TAN4 settings audit written (research/tan_settings_audit_2026-10-12.md): AP
  b003ff695 ckpt 3036: TAN3: auto-bet asks player-prop Overs for 3 more points of edge (default, A
  2b015ed08 ckpt 3035: TAN3 rules: bid desk skips bids >1c behind the best bid; bid slider horizon
  8e11a6ba3 ckpt 3034: TAN2: bid-desk step in the background cycle time-boxed to the cycle interva
  37d7ae536 ckpt 3033: TAN2: health check ignores removed keys/switched-off APIs; OddsPapi Free fa
```
