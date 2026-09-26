# CHECKPOINT 410 — read me first, then TASKS.md

**Written:** 2026-09-26T17:15:05Z · **tests:** all 1 fast checks green
**Branch:** `claude/odds-display-integration-8qnsaq` · **builds on:** `18a6456` (this checkpoint is the commit after it)

## Just done
E2b app side built: CNO tab + sheet (Kelly stake, CNO game link, Open Novig), mini window mixes Vigilant + CNO rows (tagged, $ available, rounded EV), Refresh button when CNO-only, watch only while started (incl. PiP), Settings (switch, Shared View link, refresh interval, mini window source); v0.13.0 code 17; MiniWindowTest 8 + ScreenshotTest 43 green

## Do this next
E2c docs (BRIEF.md CNO exception to manual-only, RESEARCH.md §18.5 built, CLAUDE.md surface list), then full floor :engine:test :data:test :app:testDebugUnitTest, then ship v0.13.0

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/build.gradle.kts

## Last ten checkpoints
```
  9111748 ckpt 409: E2a: CNO data layer (CnoView/CnoPage/CnoClient/CnoFeed) + 26 tests green + liv
  3cdc233 ckpt 408: E1 done: RESEARCH.md §18 (how CNO's page/filters/table work, its terms forbid
  e1facc2 ckpt 407: Logged Tj's 2026-09-26T15:34Z request (CrazyNinjaOdds' positive-EV rows in the
  7104c43 ckpt 406: SHIPPED v0.12.0 (code 16): mini window over Novig; CI 36214953291 + release 36
  b58cbed ckpt 405: pre-release: v0.12.0: mini window over Novig (picture-in-picture): scan progre
  2288244 ckpt 404: D1+D2: picture-in-picture mini window over Novig (auto on leaving with a scan/
  1304a58 ckpt 403: Logged Tj's 2026-09-26 request (floating widget / picture-in-picture over Novi
  39a6b34 ckpt 402: SHIPPED v0.11.0 (code 15): CI 36211240206 green, release 36211424686 green, Re
  a5f567c ckpt 401: pre-release: v0.11.0: full-test fixes (stale fair odds never price the feed; v
  420fc11 ckpt 400: C1-C5 done: RESEARCH.md §16 (CNO real Novig +EV at $5-15 depth; OddsAssist he
```

(21 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
