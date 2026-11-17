# CHECKPOINT 4587 — read me first, then TASKS.md

**Written:** 2026-11-17T05:37:37Z · **tests:** all 10 fast checks green
**Branch:** `claude/optimistic-curie-dx8z2c` · **builds on:** `41d219b29` (this checkpoint is the commit after it)

## Just done
pre-release: PP1: only types proven profitable at Novig (2+ stars) are listed (versionCode 473, v1.0.133)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.133), then run: bash tools/record-release.sh v1.0.133 473 "PP1: only types proven profitable at Novig (2+ stars) are listed"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  41d219b29 ckpt 4586: pre-ship: PP1: only types proven profitable at Novig (2+ stars) are listed
  937f0ebd7 ckpt 4585: pre-ship: PP1: only types proven profitable at Novig (2+ stars) are listed
  560ccaec5 ckpt 4584: LV2: relaxed live Pinnacle rules + bid margin (paper)
  3e596fbce ckpt 4583: PP1 coded: proven-only (2+ stars at Novig) hides unproven types from Feed a
  46482ad4b ckpt 4582: PP1 logged (only proven bets shown)
  002df7a04 ckpt 4581: LV2 logged: live taker lane 0 offers of 29,690 judged, all skips Pinnacle-b
  96a2500ec ckpt 4580: v1.0.131 sharp veto unlisted; v1.0.132 live Off/Practice/Real, engine in Pr
  9a52b7ae1 ckpt 4579: pre-release: LV1: one Live switch Off/Practice/Real (versionCode 472, v1.0.
  b35280ef2 ckpt 4578: pre-ship: LV1: one Live switch Off/Practice/Real
  b2ec23479 ckpt 4577: pre-release: SV1: bets a sharp book says are not +EV are no longer listed (
```
