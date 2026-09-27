# CHECKPOINT 451 — read me first, then TASKS.md

**Written:** 2026-09-27T00:58:59Z · **tests:** all 1 fast checks green
**Branch:** `claude/cno-scanner-enhancements-tj52i1` · **builds on:** `7431be3` (this checkpoint is the commit after it)

## Just done
pre-release: v0.15.3: full test: CNO pages parsed off the main thread (smoother widget); a busy/refused CNO answer pauses every lane and isn't wiped out; the bet sheet and widget Books view judge the same (newer) price as the green check; Open in Novig shows Opening...; 'only agreed' says what's held back; INBOX ignores harness notices. 446 tests (versionCode 23, v0.15.3)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.15.3), then run: bash tools/record-release.sh v0.15.3 23 "v0.15.3: full test: CNO pages parsed off the main thread (smoother widget); a busy/refused CNO answer pauses every lane and isn't wiped out; the bet sheet and widget Books view judge the same (newer) price as the green check; Open in Novig shows Opening...; 'only agreed' says what's held back; INBOX ignores harness notices. 446 tests"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  7431be3 ckpt 450: K10b/c done; v0.15.3 code 23 bumped; forced floor 446 green; v0.15.2 released 
  732628d ckpt 449: K10a: parse off main (CnoClient/PlayerTeams), pause fixes (links, mid-read), v
  b39913d ckpt 448: CI fix: CnoNetworkTest dead-connection retry pinned to 127.0.0.1 (CI runners r
  cb2fe83 ckpt 447: pre-release: v0.15.2: taps open Novig's bet slip every time (links read ahead,
  8c4d920 ckpt 446: K1-K9 done, v0.15.2 code 22 bumped, TASKS ticked, forced floor 438 green (engi
  1ef848e ckpt 445: K1 (links lane + TapLink: cache -> CNO 5s -> Novig catalog -> game, toast), K2
  d17f056 ckpt 444: K1/K2/K6/K9 data layer: CnoNetwork (DoH+remembered DNS, 20s keep-alive, 12s re
  1d2da36 ckpt 443: Logged K9 (reliable CNO refresh research); CnoFeed link lane + lighter books l
  3f2758e ckpt 442: Logged K8 (only-agreed-bets option); CnoNetwork (RememberingDns, keep-alive 20
  17ba6f1 ckpt 441: v0.15.1 released + recorded; logged K6 (DNS/timeouts) and K7 (✕ hide button)
```
