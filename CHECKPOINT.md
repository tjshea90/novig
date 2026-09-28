# CHECKPOINT 563 — read me first, then TASKS.md

**Written:** 2026-09-28T07:25:40Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-api-coverage-d7iwyq` · **builds on:** `aed8ef5` (this checkpoint is the commit after it)

## Just done
M1: Days ahead 7 default (+schema 8 moves a saved 3), all events read so later games are counted (stats.laterGames, feed text), DELAYED games scanned; M2 so far: strike guard (live: 6,541/6,541 agree), GET /v3/limits paces key + stream, board via the key's signed catalog w/ public fallback. Tests: ScanReachTest 5, NovigPublicClientTest +6

## Do this next
M2: finish doc review (fees, errors), app compile + UI text check, NOVIG_API.md, floor, ship v0.19.1

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  8f76985 ckpt 562: Recorded Tj's 'way more than 7 games' + Novig docs request as M1-M3
  ac363cb ckpt 561: Released Vigilant v0.19.0 (code 35): release.yml run 36389431089 green, tag v0
  627a6eb ckpt 560: pre-release: v0.19.0: keyed scans load their prices through Novig's live feed 
  4fca994 ckpt 559: pre-release v0.19.0 (code 35): floor 677 tests green (engine 39, data 434/9 li
  6a2f1a1 ckpt 558: light test: screenshots looked at (key section, settings fill switch); UI test
  44b45ee ckpt 557: K2 (websocket for keyed scans: NovigStream market subscriptions, pushed books 
  1975c04 ckpt 556: K3b: tennis (ATP/WTA) priced from Kalshi + Pinnacle: winner, games spread/tota
  498d761 ckpt 555: K3a: scans fill the per-scan budget with every other quoted line (fillBudget, 
  da4b847 ckpt 554: K1 diagnosed (thin slate + per-game caps + no tennis); K2-K5 planned in TASKS.
  d8969f8 ckpt 553: Recorded Tj's 7-games / Novig key / CNO-copy request as K1-K5 in TASKS.md
```

(21 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
