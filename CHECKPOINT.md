# CHECKPOINT 3960 — read me first, then TASKS.md

**Written:** 2026-10-22T03:17:38Z · **tests:** all 9 fast checks green
**Branch:** `claude/optimistic-curie-wju7hd` · **builds on:** `cafef0ecf` (this checkpoint is the commit after it)

## Just done
pre-release: v1.0.46: red X on the bet slip for a player, team or prop you already hold; a bet the slip finds gone leaves the list at once; diversification caps (bets per game, dollars per team/player/prop, one bet per side) for bets and bids on the engine and phone, remembered across engine restarts; live tail lane limits no longer reset on a restart; SharpAPI, Novig stream and ParlayAPI fixes (versionCode 386, v1.0.46)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.46), then run: bash tools/record-release.sh v1.0.46 386 "v1.0.46: red X on the bet slip for a player, team or prop you already hold; a bet the slip finds gone leaves the list at once; diversification caps (bets per game, dollars per team/player/prop, one bet per side) for bets and bids on the engine and phone, remembered across engine restarts; live tail lane limits no longer reset on a restart; SharpAPI, Novig stream and ParlayAPI fixes"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  cafef0ecf ckpt 3959: pre-ship: v1.0.46: red X on the bet slip for a player, team or prop you alr
  84f2b1bb0 ckpt 3958: DV tasks written
  b57a02718 ckpt 3957: CHANGES.md entry
  a20110529 ckpt 3956: live tail lane: limits, 3-buys-per-game and repeat guard now count the carr
  e8b3dfb8a ckpt 3955: PBP last-end diagnostics; NS2 written
  d9708b94e ckpt 3954: PBP stream connection log
  7a9a5e4be ckpt 3953: ParlayAPI PBP stream: doubling backoff for streams that end early (5 credit
  aa6131a25 ckpt 3952: SX Bet diagnostics in the scan log
  02bfe7115 ckpt 3951: ParlayAPI pacing: a key met part-way through its period is paced on what wa
  030392088 ckpt 3950: SharpAPI: game start time taken from sibling rows (44k of 45k rows had none
```
