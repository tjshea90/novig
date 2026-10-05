# CHECKPOINT 2542 — read me first, then TASKS.md

**Written:** 2026-10-05T15:40:19Z · **tests:** all 3 fast checks green
**Branch:** `ccr-44a73259-01ykiu` · **builds on:** `afa63093` (this checkpoint is the commit after it)

## Just done
pre-release: v0.62.0: a market held on both sides is never graded lost from Novig's silence; a locked market with every leg lost is taken back (Ollie Gordon lock) (versionCode 109, v0.62.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.62.0), then run: bash tools/record-release.sh v0.62.0 109 "v0.62.0: a market held on both sides is never graded lost from Novig's silence; a locked market with every leg lost is taken back (Ollie Gordon lock)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  afa63093 ckpt 2541: pre-ship: v0.62.0: a market held on both sides is never graded lost from Nov
  eac3888a ckpt 2540: CG1-CG2 done: the -$2.29 was Ollie Gordon's lock leg (Over 29.5, won with 10
  2ed52077 ckpt 2539: wrote Tj's 2026-10-05 'locked in negative profit' report into TASKS.md (CG1-
  1c2ed131 ckpt 2538: CF1 done: RESEARCH §86.5 profile of the Pinnacle-prop bets (NFL player prop
  643c4d47 ckpt 2537: CE1-CE2 done: RESEARCH §86 (cheaper than Pinnacle on a prop: dead zone 2.5 
  5b9f0547 ckpt 2536: RESEARCH §85: exact football count (1,096)
  c87ba995 ckpt 2535: CD1 done: CrazyNinjaOdds has no tennis (Sport list, League list, 0 Tennis ro
  80c5e8f0 ckpt 2534: RESEARCH §84.1 fixed: exact win-probability swing per burst, 9th burst = st
  706e8562 ckpt 2533: CC1-CC2 done: RESEARCH §84 (how the 9 bursts happened: big plays, neighbour
  a93c21e0 ckpt 2532: CB1 live-arbitrage research done: RESEARCH §83, NOVIG_API §18, tools/resea
```
