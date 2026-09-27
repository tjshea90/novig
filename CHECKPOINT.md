# CHECKPOINT 447 — read me first, then TASKS.md

**Written:** 2026-09-27T00:42:05Z · **tests:** all 1 fast checks green
**Branch:** `claude/cno-scanner-enhancements-tj52i1` · **builds on:** `8c4d920` (this checkpoint is the commit after it)

## Just done
pre-release: v0.15.2: taps open Novig's bet slip every time (links read ahead, Novig's own catalog when CNO can't answer); CNO reads survive DNS failures and dead connections (backup DNS, fresh connections, one retry) and say which it was; ✕ removes a bet without placing it (Undo, removed list); 'Only bets the books agree on' setting; bottom bar fits any width; stale 'scan done' notification cleared. 438 tests (versionCode 22, v0.15.2)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.15.2), then run: bash tools/record-release.sh v0.15.2 22 "v0.15.2: taps open Novig's bet slip every time (links read ahead, Novig's own catalog when CNO can't answer); CNO reads survive DNS failures and dead connections (backup DNS, fresh connections, one retry) and say which it was; ✕ removes a bet without placing it (Undo, removed list); 'Only bets the books agree on' setting; bottom bar fits any width; stale 'scan done' notification cleared. 438 tests"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  8c4d920 ckpt 446: K1-K9 done, v0.15.2 code 22 bumped, TASKS ticked, forced floor 438 green (engi
  1ef848e ckpt 445: K1 (links lane + TapLink: cache -> CNO 5s -> Novig catalog -> game, toast), K2
  d17f056 ckpt 444: K1/K2/K6/K9 data layer: CnoNetwork (DoH+remembered DNS, 20s keep-alive, 12s re
  1d2da36 ckpt 443: Logged K9 (reliable CNO refresh research); CnoFeed link lane + lighter books l
  3f2758e ckpt 442: Logged K8 (only-agreed-bets option); CnoNetwork (RememberingDns, keep-alive 20
  17ba6f1 ckpt 441: v0.15.1 released + recorded; logged K6 (DNS/timeouts) and K7 (✕ hide button)
  5c9fe1c ckpt 440: Logged Tj's K1-K5 (bet slip taps, CNO unreachable, Milwaukee notification, nar
  3b2a13c ckpt 439: pre-release: v0.15.1: floating widget resizes with two fingers (spread/pinch, 
  3cf811b ckpt 438: J5 tests green: 409 tests 0 failed; FloatingWidget handles touches in dispatch
  a79a462 ckpt 437: J1-J4, J6 done: framed widget with corner handles, pinch/spread + two-finger m
```
