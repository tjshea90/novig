# CHECKPOINT 404 — read me first, then TASKS.md

**Written:** 2026-09-26T03:29:20Z · **tests:** all 1 fast checks green
**Branch:** `claude/positive-ev-betting-research-dkp593` · **builds on:** `897c6ea` (this checkpoint is the commit after it)

## Just done
D1+D2: picture-in-picture mini window over Novig (auto on leaving with a scan/bets, feed button, Open Novig opens us.novig.app + floats Vigilant; Scan/Recheck/Next buttons; Settings switch); RESEARCH §17, BRIEF, CLAUDE; v0.12.0 code 16. Local 267 tests green (exit 0, 0 failures); screenshots 7*_mini_window checked.

## Do this next
D3: CI on this commit, ship.sh, release.yml, confirm, record-release, send Tj the link + how to use it (and that split screen works too)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M BRIEF.md
     M CHECKPOINT.md
     M CLAUDE.md
     M RESEARCH.md
     M TASKS.md
     M app/build.gradle.kts

## Last ten checkpoints
```
  1304a58 ckpt 403: Logged Tj's 2026-09-26 request (floating widget / picture-in-picture over Novi
  39a6b34 ckpt 402: SHIPPED v0.11.0 (code 15): CI 36211240206 green, release 36211424686 green, Re
  a5f567c ckpt 401: pre-release: v0.11.0: full-test fixes (stale fair odds never price the feed; v
  420fc11 ckpt 400: C1-C5 done: RESEARCH.md §16 (CNO real Novig +EV at $5-15 depth; OddsAssist he
  efee7d7 ckpt 399: C2 app layer: recheck (feed 'Recheck prices' + sheet 'Recheck price' + old-pri
  1e2cb8e ckpt 398: C2 data layer: ScanSettings.outlierGuard (default on) + maxOdds (default +1000
  4284bdd ckpt 397: C1/C2: fixed stale-fair pricing in Scanner (final result + reprice only from y
  b35fb87 ckpt 396: C1 started: local SDK + Maven/Robolectric mirror set up (build trap 6); full f
  449efd7 ckpt 395: Logged Tj's 2026-09-26 request (full tests, feature/scan improvements, OddsAss
  aa9d099 ckpt 394: SHIPPED v0.10.0 (code 14): release 36174969512 green, Release confirmed with v
```

(10 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
