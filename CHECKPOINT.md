# CHECKPOINT 4070 — read me first, then TASKS.md

**Written:** 2026-10-23T22:56:17Z · **tests:** all 9 fast checks green
**Branch:** `claude/optimistic-curie-yprd2y` · **builds on:** `454574295` (this checkpoint is the commit after it)

## Just done
pre-release: ST2: stars/soonest sort works on the merged Novig+Kalshi list and shows with Kalshi-only rows; CNO tab gets Best EV/Soonest/Stars buttons (versionCode 405, v1.0.65)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.65), then run: bash tools/record-release.sh v1.0.65 405 "ST2: stars/soonest sort works on the merged Novig+Kalshi list and shows with Kalshi-only rows; CNO tab gets Best EV/Soonest/Stars buttons"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  454574295 ckpt 4069: ST2: feed sort fix + CNO sort buttons
  08b8813b1 ckpt 4068: v1.0.64 released: single star rating; cross-check on propline-clv written
  ebfe78b7e ckpt 4067: pre-release: ST1: one combined star rating (type of bet, lead time, small-b
  1068f47a7 ckpt 4066: ST1: single star rating (type+lead points, small-book cap), study written, 
  98fa56d7f ckpt 4065: ST1: history folded into the one BetRating (typePoints), history line remov
  97a24d64a ckpt 4064: CH76 done, reported keys are not Growth
  43c0797c9 ckpt 4063: CH76 rank BoltOdds keys by plan
  70d51597a ckpt 4062: CH76 log BoltOdds plan line
  7814b1198 ckpt 4061: CH76 BoltOdds Growth one-socket mode
  6bbea3766 ckpt 4060: research index rebuilt
```
