# CHECKPOINT 560 — read me first, then TASKS.md

**Written:** 2026-09-28T07:01:32Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-api-coverage-d7iwyq` · **builds on:** `4fca994` (this checkpoint is the commit after it)

## Just done
pre-release: v0.19.0: keyed scans load their prices through Novig's live feed (one websocket subscribe for the whole plan, ~8 s in, no request per price); scans fill the per-scan budget with every quoted line (alternate spreads/totals, more props); tennis (ATP/WTA) priced from Kalshi + Pinnacle: winner, games spread/total, games won, 1st set. 677 tests (versionCode 35, v0.19.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.19.0), then run: bash tools/record-release.sh v0.19.0 35 "v0.19.0: keyed scans load their prices through Novig's live feed (one websocket subscribe for the whole plan, ~8 s in, no request per price); scans fill the per-scan budget with every quoted line (alternate spreads/totals, more props); tennis (ATP/WTA) priced from Kalshi + Pinnacle: winner, games spread/total, games won, 1st set. 677 tests"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  4fca994 ckpt 559: pre-release v0.19.0 (code 35): floor 677 tests green (engine 39, data 434/9 li
  6a2f1a1 ckpt 558: light test: screenshots looked at (key section, settings fill switch); UI test
  44b45ee ckpt 557: K2 (websocket for keyed scans: NovigStream market subscriptions, pushed books 
  1975c04 ckpt 556: K3b: tennis (ATP/WTA) priced from Kalshi + Pinnacle: winner, games spread/tota
  498d761 ckpt 555: K3a: scans fill the per-scan budget with every other quoted line (fillBudget, 
  da4b847 ckpt 554: K1 diagnosed (thin slate + per-game caps + no tennis); K2-K5 planned in TASKS.
  d8969f8 ckpt 553: Recorded Tj's 7-games / Novig key / CNO-copy request as K1-K5 in TASKS.md
  8b3f7cd ckpt 552: Released Vigilant v0.18.0 (code 34): release.yml run 36376581625 green, tag v0
  1db8616 ckpt 551: pre-release: v0.18.0: background auto-scan (CNO or CNO + Vigilant, every 5-40 
  ee1cfa9 ckpt 550: P6: live Novig/finder/CNO/scores green; 110 screenshots green (5d, 5e looked a
```
