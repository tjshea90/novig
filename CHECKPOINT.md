# CHECKPOINT 412 — read me first, then TASKS.md

**Written:** 2026-09-26T17:22:52Z · **tests:** all 1 fast checks green
**Branch:** `claude/odds-display-integration-8qnsaq` · **builds on:** `b900f72` (this checkpoint is the commit after it)

## Just done
pre-release: v0.13.0: CrazyNinjaOdds' +EV list (your Shared View link) in a CNO tab and the mini window, mixed with Vigilant's bets or alone; refreshes itself only while on screen, at most every 30 s; Refresh button in the mini window; EV rounded. 308 tests (versionCode 17, v0.13.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.13.0), then run: bash tools/record-release.sh v0.13.0 17 "v0.13.0: CrazyNinjaOdds' +EV list (your Shared View link) in a CNO tab and the mini window, mixed with Vigilant's bets or alone; refreshes itself only while on screen, at most every 30 s; Refresh button in the mini window; EV rounded. 308 tests"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  84d75e5 ckpt 411: E2 done: CrazyNinjaOdds list in a CNO tab and the mini window (v0.13.0 code 17
  73e8961 ckpt 410: E2b app side built: CNO tab + sheet (Kelly stake, CNO game link, Open Novig), 
  9111748 ckpt 409: E2a: CNO data layer (CnoView/CnoPage/CnoClient/CnoFeed) + 26 tests green + liv
  3cdc233 ckpt 408: E1 done: RESEARCH.md §18 (how CNO's page/filters/table work, its terms forbid
  e1facc2 ckpt 407: Logged Tj's 2026-09-26T15:34Z request (CrazyNinjaOdds' positive-EV rows in the
  7104c43 ckpt 406: SHIPPED v0.12.0 (code 16): mini window over Novig; CI 36214953291 + release 36
  b58cbed ckpt 405: pre-release: v0.12.0: mini window over Novig (picture-in-picture): scan progre
  2288244 ckpt 404: D1+D2: picture-in-picture mini window over Novig (auto on leaving with a scan/
  1304a58 ckpt 403: Logged Tj's 2026-09-26 request (floating widget / picture-in-picture over Novi
  39a6b34 ckpt 402: SHIPPED v0.11.0 (code 15): CI 36211240206 green, release 36211424686 green, Re
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
