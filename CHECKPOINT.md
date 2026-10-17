# CHECKPOINT 3594 — read me first, then TASKS.md

**Written:** 2026-10-17T20:22:00Z · **tests:** all 5 fast checks green
**Branch:** `claude/wizardly-meitner-h2g4pr` · **builds on:** `080a7c8c9` (this checkpoint is the commit after it)

## Just done
pre-release: v0.114.31: the Live engine-trading button lights up whenever the trading key is on GitHub; an order error now says what Novig last returned for it, and any non-live order status counts as finished (versionCode 327, v0.114.31)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.114.31), then run: bash tools/record-release.sh v0.114.31 327 "v0.114.31: the Live engine-trading button lights up whenever the trading key is on GitHub; an order error now says what Novig last returned for it, and any non-live order status counts as finished"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  080a7c8c9 ckpt 3593: pre-ship: v0.114.31: the Live engine-trading button lights up whenever the 
  52852c20a ckpt 3592: pre-release: v0.114.30: the Live engine-trading button lights up whenever t
  f86c8350a ckpt 3591: pre-ship: v0.114.30: the Live engine-trading button lights up whenever the 
  dea99df3a ckpt 3590: pre-release: v0.114.29: a manual bet whose order Novig leaves pending is ca
  83796c166 ckpt 3589: pre-ship: v0.114.29: a manual bet whose order Novig leaves pending is cance
  8790d2ae2 ckpt 3588: pre-release: v0.114.28: an order Novig leaves pending is cancelled by its i
  1b62190c6 ckpt 3587: pre-ship: v0.114.28: an order Novig leaves pending is cancelled by its id a
  89fcdca7e ckpt 3586: Engine: pending IOC is cancelled by id and re-read before the 'unconfirmed'
  e4bb873f6 ckpt 3585: v0.114.27 released; BoltOdds key on GitHub, switch request sent
  70572032a ckpt 3584: pre-release: v0.114.27: Live light logs every decision (scans with score an
```
