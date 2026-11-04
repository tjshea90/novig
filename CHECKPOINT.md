# CHECKPOINT 4378 — read me first, then TASKS.md

**Written:** 2026-11-04T22:17:40Z · **tests:** all 9 fast checks green
**Branch:** `claude/optimistic-curie-5159za` · **builds on:** `9a63bb669` (this checkpoint is the commit after it)

## Just done
pre-release: A phone restart no longer turns off auto-bet, bids or auto-scan (versionCode 441, v1.0.101)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.101), then run: bash tools/record-release.sh v1.0.101 441 "A phone restart no longer turns off auto-bet, bids or auto-scan"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  9a63bb669 ckpt 4377: pre-ship: A phone restart no longer turns off auto-bet, bids or auto-scan
  4e14dc81f ckpt 4376: pre-ship: A phone restart no longer turns off auto-bet, bids or auto-scan
  56a7b1fc7 ckpt 4375: LR1 restart no longer resets auto bids; engine off per Tj
  e78f2e2cd ckpt 4374: WATCH 11-04 slot1: engine healthy, nothing shipped
  ae085049f ckpt 4373: ENGINEER 11-04 slot0: engine healthy, nothing shipped
  cf16cbb86 ckpt 4372: pre-release: Odds API keys used in order (a throttle no longer moves the en
  43d165dcd ckpt 4371: OK1+BD: tests green
  8093534d8 ckpt 4370: OK1: Odds API sticky keys now return to the first usable key
  c9736992e ckpt 4369: BoltOdds off per Tj
  27bee0475 ckpt 4368: answered socket question; logged SK2
```
