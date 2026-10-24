# CHECKPOINT 4123 — read me first, then TASKS.md

**Written:** 2026-10-24T17:19:10Z · **tests:** all 9 fast checks green
**Branch:** `claude/optimistic-curie-hj14wp` · **builds on:** `f44754c14` (this checkpoint is the commit after it)

## Just done
pre-release: CH81: dead prices stay off the +EV list (1c band, 2 h, refused bets too); engine scan note counts exchange lines and flags books far from the others (match check) (versionCode 413, v1.0.73)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.73), then run: bash tools/record-release.sh v1.0.73 413 "CH81: dead prices stay off the +EV list (1c band, 2 h, refused bets too); engine scan note counts exchange lines and flags books far from the others (match check)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  f44754c14 ckpt 4122: pre-ship: CH81: dead prices stay off the +EV list (1c band, 2 h, refused be
  43a34d406 ckpt 4121: CH81 written; engine reset for Kalshi
  fe3e6557f ckpt 4120: pre-release: CH80: why a listed bet hides is logged (FEEDHIDE); CNO dead-pr
  c8f7f557c ckpt 4119: pre-ship: CH80: why a listed bet hides is logged (FEEDHIDE); CNO dead-price
  b81d895b6 ckpt 4118: CH80 written (flicker in +EV list); replied to Tj
  ca7d0f37b ckpt 4117: CH79: Kalshi engine lane scans+lists when switch OFF (orders still need Pap
  e9b40b39e ckpt 4116: TR1-TR2: stats/trends study (research/trends_2026-10-24.md): recent form ~1
  e415600a2 ckpt 4115: TR: Tj's stats/trends request written into TASKS.md
  ecceb13df ckpt 4114: WATCH 2026-10-24: engine healthy, no action
  03a62e532 ckpt 4113: CH78: app suite green on this code
```
