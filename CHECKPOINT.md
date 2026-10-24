# CHECKPOINT 4130 — read me first, then TASKS.md

**Written:** 2026-10-24T17:45:43Z · **tests:** all 9 fast checks green
**Branch:** `claude/optimistic-curie-hj14wp` · **builds on:** `8589e8341` (this checkpoint is the commit after it)

## Just done
pre-release: CH82+CH81: engine restart reads 'stopped or restarting' not 'switched off'; Diagnostics logs every 'nobody was selling' bet (NOFILL: list age, price, depth, fair age) (versionCode 414, v1.0.74)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.74), then run: bash tools/record-release.sh v1.0.74 414 "CH82+CH81: engine restart reads 'stopped or restarting' not 'switched off'; Diagnostics logs every 'nobody was selling' bet (NOFILL: list age, price, depth, fair age)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  8589e8341 ckpt 4129: pre-ship: CH82+CH81: engine restart reads 'stopped or restarting' not 'swit
  88138eab6 ckpt 4128: CH80b candidate cause noted (requoteListed partial books)
  70fcf7828 ckpt 4127: CH83 scoped: archive not write-behind
  92ec17a42 ckpt 4126: CH83 written; diagnostics paste triaged
  6d84a658e ckpt 4125: v1.0.73 released+recorded; CH82 wording: stopped or restarting (tests green
  a17f0b62b ckpt 4124: v1.0.73 on main, release building; CH82 noted
  93b9c38bd ckpt 4124: ANALYST 2026-10-24 skipped (under gate), watch ok
  00a016094 ckpt 4123: pre-release: CH81: dead prices stay off the +EV list (1c band, 2 h, refused
  f44754c14 ckpt 4122: pre-ship: CH81: dead prices stay off the +EV list (1c band, 2 h, refused be
  43a34d406 ckpt 4121: CH81 written; engine reset for Kalshi
```
