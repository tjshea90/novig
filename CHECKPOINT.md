# CHECKPOINT 4348 — read me first, then TASKS.md

**Written:** 2026-11-01T19:01:18Z · **tests:** all 9 fast checks green
**Branch:** `claude/optimistic-curie-stw5bf` · **builds on:** `b9de00901` (this checkpoint is the commit after it)

## Just done
pre-release: LR1: a phone restart no longer switches off the GitHub engine's auto-bet and bids (versionCode 438, v1.0.98)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.98), then run: bash tools/record-release.sh v1.0.98 438 "LR1: a phone restart no longer switches off the GitHub engine's auto-bet and bids"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  b9de00901 ckpt 4347: pre-ship: LR1: a phone restart no longer switches off the GitHub engine's a
  cf12aeb2c ckpt 4346: LR1: restart no longer resets auto-bet/bids while engine trades
  19af00dfd ckpt 4345: WATCH 2026-11-01 engine healthy, no change
  0891efb00 ckpt 4344: BD20 bids 20/game wallet 2000%
  3c2225390 ckpt 4343: ENGINEER 2026-11-01 watch only, engine healthy, no change
  34f52d764 ckpt 4342: 2026-11-01 chat research: bids +4.6%, taker totals/spreads leak n194, no ch
  b0a7ffff2 ckpt 4341: pre-release: Tracker: both-sides markets with an odd Novig payout grade fro
  4612a0fe8 ckpt 4340: pre-ship: Tracker: both-sides markets with an odd Novig payout grade from t
  4f3cce88f ckpt 4339: fix 2 calendar-fragile tests (month-day budget on Nov 1)
  208c124c4 ckpt 4338: GR1: both-sides graded by final score + Novig net payout, ApiSettlerTest gr
```
