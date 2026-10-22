# CHECKPOINT 3966 — read me first, then TASKS.md

**Written:** 2026-10-22T04:30:35Z · **tests:** all 9 fast checks green
**Branch:** `claude/optimistic-curie-aosilm` · **builds on:** `73ed6907d` (this checkpoint is the commit after it)

## Just done
pre-release: DV4: repeat guard in the engine's bid desk and live lane also reads Novig's own open positions (survives restarts) (versionCode 387, v1.0.47)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.47), then run: bash tools/record-release.sh v1.0.47 387 "DV4: repeat guard in the engine's bid desk and live lane also reads Novig's own open positions (survives restarts)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  73ed6907d ckpt 3965: pre-ship: DV4: repeat guard in the engine's bid desk and live lane also rea
  34a7b3f48 ckpt 3964: pre-ship: DV4: repeat guard in the engine'"s
  e0a1a19e0 ckpt 3963: tennis watch removed
  a6f91f0aa ckpt 3962: stop the day-long tennis PBP watch (5 credits per empty connection)
  27cc19336 ckpt 3961: v1.0.46 shipped (DV1-3)
  27688fd85 ckpt 3960: pre-release: v1.0.46: red X on the bet slip for a player, team or prop you 
  cafef0ecf ckpt 3959: pre-ship: v1.0.46: red X on the bet slip for a player, team or prop you alr
  84f2b1bb0 ckpt 3958: DV tasks written
  b57a02718 ckpt 3957: CHANGES.md entry
  a20110529 ckpt 3956: live tail lane: limits, 3-buys-per-game and repeat guard now count the carr
```
