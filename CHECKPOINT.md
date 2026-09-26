# CHECKPOINT 409 — read me first, then TASKS.md

**Written:** 2026-09-26T16:55:23Z · **tests:** all 1 fast checks green
**Branch:** `claude/odds-display-integration-8qnsaq` · **builds on:** `5a2903b` (this checkpoint is the commit after it)

## Just done
E2a: CNO data layer (CnoView/CnoPage/CnoClient/CnoFeed) + 26 tests green + live smoke green (100 Novig rows, refresh = 1 request)

## Do this next
E2b: app side: ScanSettings fields (cnoEnabled, cnoViewUrl, cnoRefreshSeconds, miniSource), AppContainer.cno, MainViewModel state + watch/refresh, CNO tab, MiniFeed merged rows, PiP Refresh action, Settings section, tests + screenshots

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  3cdc233 ckpt 408: E1 done: RESEARCH.md §18 (how CNO's page/filters/table work, its terms forbid
  e1facc2 ckpt 407: Logged Tj's 2026-09-26T15:34Z request (CrazyNinjaOdds' positive-EV rows in the
  7104c43 ckpt 406: SHIPPED v0.12.0 (code 16): mini window over Novig; CI 36214953291 + release 36
  b58cbed ckpt 405: pre-release: v0.12.0: mini window over Novig (picture-in-picture): scan progre
  2288244 ckpt 404: D1+D2: picture-in-picture mini window over Novig (auto on leaving with a scan/
  1304a58 ckpt 403: Logged Tj's 2026-09-26 request (floating widget / picture-in-picture over Novi
  39a6b34 ckpt 402: SHIPPED v0.11.0 (code 15): CI 36211240206 green, release 36211424686 green, Re
  a5f567c ckpt 401: pre-release: v0.11.0: full-test fixes (stale fair odds never price the feed; v
  420fc11 ckpt 400: C1-C5 done: RESEARCH.md §16 (CNO real Novig +EV at $5-15 depth; OddsAssist he
  efee7d7 ckpt 399: C2 app layer: recheck (feed 'Recheck prices' + sheet 'Recheck price' + old-pri
```

(17 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
