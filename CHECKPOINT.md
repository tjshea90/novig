# CHECKPOINT 405 — read me first, then TASKS.md

**Written:** 2026-09-26T03:33:36Z · **tests:** all 1 fast checks green
**Branch:** `claude/positive-ev-betting-research-dkp593` · **builds on:** `2288244` (this checkpoint is the commit after it)

## Just done
pre-release: v0.12.0: mini window over Novig (picture-in-picture): scan progress and the top +EV bets float over Novig, opens when you leave Vigilant with a scan or bets (Settings switch), from the button next to Scan, and from Open Novig (now opens Novig's app); Scan/Recheck/Next buttons; whole settings rows toggle (versionCode 16, v0.12.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.12.0), then run: bash tools/record-release.sh v0.12.0 16 "v0.12.0: mini window over Novig (picture-in-picture): scan progress and the top +EV bets float over Novig, opens when you leave Vigilant with a scan or bets (Settings switch), from the button next to Scan, and from Open Novig (now opens Novig's app); Scan/Recheck/Next buttons; whole settings rows toggle"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  2288244 ckpt 404: D1+D2: picture-in-picture mini window over Novig (auto on leaving with a scan/
  1304a58 ckpt 403: Logged Tj's 2026-09-26 request (floating widget / picture-in-picture over Novi
  39a6b34 ckpt 402: SHIPPED v0.11.0 (code 15): CI 36211240206 green, release 36211424686 green, Re
  a5f567c ckpt 401: pre-release: v0.11.0: full-test fixes (stale fair odds never price the feed; v
  420fc11 ckpt 400: C1-C5 done: RESEARCH.md §16 (CNO real Novig +EV at $5-15 depth; OddsAssist he
  efee7d7 ckpt 399: C2 app layer: recheck (feed 'Recheck prices' + sheet 'Recheck price' + old-pri
  1e2cb8e ckpt 398: C2 data layer: ScanSettings.outlierGuard (default on) + maxOdds (default +1000
  4284bdd ckpt 397: C1/C2: fixed stale-fair pricing in Scanner (final result + reprice only from y
  b35fb87 ckpt 396: C1 started: local SDK + Maven/Robolectric mirror set up (build trap 6); full f
  449efd7 ckpt 395: Logged Tj's 2026-09-26 request (full tests, feature/scan improvements, OddsAss
```
