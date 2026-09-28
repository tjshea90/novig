# CHECKPOINT 559 — read me first, then TASKS.md

**Written:** 2026-09-28T06:58:04Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-api-coverage-d7iwyq` · **builds on:** `48b0c65` (this checkpoint is the commit after it)

## Just done
pre-release v0.19.0 (code 35): floor 677 tests green (engine 39, data 434/9 live skipped, app 204), release APK built + cert verified; release.yml notes mention the live feed

## Do this next
wait for ci.yml green on this commit, then ship.sh, release.yml, record-release, link

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  6a2f1a1 ckpt 558: light test: screenshots looked at (key section, settings fill switch); UI test
  44b45ee ckpt 557: K2 (websocket for keyed scans: NovigStream market subscriptions, pushed books 
  1975c04 ckpt 556: K3b: tennis (ATP/WTA) priced from Kalshi + Pinnacle: winner, games spread/tota
  498d761 ckpt 555: K3a: scans fill the per-scan budget with every other quoted line (fillBudget, 
  da4b847 ckpt 554: K1 diagnosed (thin slate + per-game caps + no tennis); K2-K5 planned in TASKS.
  d8969f8 ckpt 553: Recorded Tj's 7-games / Novig key / CNO-copy request as K1-K5 in TASKS.md
  8b3f7cd ckpt 552: Released Vigilant v0.18.0 (code 34): release.yml run 36376581625 green, tag v0
  1db8616 ckpt 551: pre-release: v0.18.0: background auto-scan (CNO or CNO + Vigilant, every 5-40 
  ee1cfa9 ckpt 550: P6: live Novig/finder/CNO/scores green; 110 screenshots green (5d, 5e looked a
  6259bbe ckpt 549: P6 full tests: fix F1 duplicate alerts when two scans end together (send mutex
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
