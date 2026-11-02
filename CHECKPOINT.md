# CHECKPOINT 4356 — read me first, then TASKS.md

**Written:** 2026-11-02T18:26:19Z · **tests:** all 9 fast checks green
**Branch:** `claude/optimistic-curie-htf6em` · **builds on:** `5ca3409a3` (this checkpoint is the commit after it)

## Just done
pre-release: v1.0.99: bids half as many at double stake, slower requote (Novig read limit) (versionCode 439, v1.0.99)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.99), then run: bash tools/record-release.sh v1.0.99 439 "v1.0.99: bids half as many at double stake, slower requote (Novig read limit)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  218f7bd76 ckpt 4355: HB2 diagnosed: Novig read rate limit from bid churn
  9494e6920 ckpt 4354: FR1 freshness verified accurate; root cause PropLine slow reread + ParlayAP
  77749fdb4 ckpt 4353: PL1 investigated, replied
  0650f995d ckpt 4352: CB1 answered: NCAAB already enabled
  51a6bb76a ckpt 4351: answered chat: stale PropLine ages vs Novig socket
  4b5e27071 ckpt 4350: WATCH 2026-11-02 engine healthy, no change
  67b83132b ckpt 4349: ANALYST 2026-11-02 skipped: no new digest, engine healthy
  ebd2c9af6 ckpt 4348: pre-release: LR1: a phone restart no longer switches off the GitHub engine'
  b9de00901 ckpt 4347: pre-ship: LR1: a phone restart no longer switches off the GitHub engine's a
  cf12aeb2c ckpt 4346: LR1: restart no longer resets auto-bet/bids while engine trades
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
