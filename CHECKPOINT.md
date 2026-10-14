# CHECKPOINT 3272 — read me first, then TASKS.md

**Written:** 2026-10-14T20:39:53Z · **tests:** all 4 fast checks green
**Branch:** `ccr-5fc41785-3m8rjr` · **builds on:** `e67ee3fb2` (this checkpoint is the commit after it)

## Just done
pre-release: v0.99.16: Volume mode scales the stakes of new bids up toward the wallet target (the count stays), so the wallet fills instead of sitting near half (versionCode 275, v0.99.16)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.99.16), then run: bash tools/record-release.sh v0.99.16 275 "v0.99.16: Volume mode scales the stakes of new bids up toward the wallet target (the count stays), so the wallet fills instead of sitting near half"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  e67ee3fb2 ckpt 3271: pre-ship: v0.99.16: Volume mode scales the stakes of new bids up toward the
  1fe47f144 ckpt 3270: v0.99.15 shipped (far books kept + 90 min far book age in Volume mode), rel
  c51eb5d2c ckpt 3269: pre-release: v0.99.15: Volume mode bids far games from a Novig book up to 9
  7cd1275e9 ckpt 3268: pre-ship: v0.99.15: Volume mode bids far games from a Novig book up to 90 m
  b23dc905c ckpt 3267: TVM4: logged 'few bids' report on v0.99.14 (93 bids / $18.30), no v0.99.14 
  a90410b1c ckpt 3266: v0.99.14 released and recorded (TVM3 done)
  26db6f743 ckpt 3265: v0.99.14 shipped (Volume mode), release.yml triggered
  da918b9dc ckpt 3264: pre-release: v0.99.14: Volume mode for bids - EV floors 1.5% safest / 2.5% 
  51040dd2b ckpt 3263: pre-ship: v0.99.14: Volume mode for bids - EV floors 1.5% safest / 2.5% ris
  4c3849b70 ckpt 3262: pre-ship: v0.99.14: Volume mode for bids - EV floors 1.5% safest / 2.5% ris
```
