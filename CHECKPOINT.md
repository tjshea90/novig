# CHECKPOINT 2678 — read me first, then TASKS.md

**Written:** 2026-10-07T07:12:38Z · **tests:** all 4 fast checks green
**Branch:** `ccr-b67c8afa-h14i94` · **builds on:** `92453213` (this checkpoint is the commit after it)

## Just done
pre-release: v0.72.0: small-market bid fill behind the popular bids (Quick & likely, strict safeguards, six review defects fixed), the five approved rule changes (first-listed trap guard, favourites need +1 point, gate and Kelly size on the lower of CNO's and the books' edge, unread Novig trades skip game lines, Kalshi 3 requests a second test), imported lock legs graded from the other leg's loss (versionCode 126, v0.72.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.72.0), then run: bash tools/record-release.sh v0.72.0 126 "v0.72.0: small-market bid fill behind the popular bids (Quick & likely, strict safeguards, six review defects fixed), the five approved rule changes (first-listed trap guard, favourites need +1 point, gate and Kelly size on the lower of CNO's and the books' edge, unread Novig trades skip game lines, Kalshi 3 requests a second test), imported lock legs graded from the other leg's loss"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  92453213 ckpt 2677: v0.72.0 candidate complete: obscure fill + rec 11 + recs 2/4/5/9/10, RESEARC
  a16d36aa ckpt 2676: DG4 code done (Kalshi 3 req/s test, setting, Diagnostics line, tests); TASKS
  c381f06c ckpt 2675: DG5 done (unread Novig trades skip game-line auto-bet CNO+Pinnacle and game-
  83d81cee ckpt 2674: DG3 done: gate and Kelly size on the lower of CNO's edge and the books' own 
  1e148a6f ckpt 2673: DG2 done: favourites need 1 point more edge (setting, chips, typed box, judg
  9dbb27cc ckpt 2672: DG1 done: FirstListed store (files/first_listed.json), TrapGuard.listedEarly
  3b8c0873 ckpt 2671: DG6 done (ApiSettler grades the other leg of a half-point two-way held marke
  939ffa7a ckpt 2670: v0.71.2 RELEASED + recorded (OUT-player fix); obscure-bid fill (DE3+DE4) BUI
  cfc58c12 ckpt 2669: pre-release: v0.71.2: no bid, auto-bet or alert on a player the injury repor
  82e1dde6 ckpt 2668: pre-release: v0.71.2: no bid, auto-bet or alert on a player the injury repor
```
