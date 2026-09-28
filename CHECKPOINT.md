# CHECKPOINT 589 — read me first, then TASKS.md

**Written:** 2026-09-28T19:03:23Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-api-coverage-d7iwyq` · **builds on:** `68b5308` (this checkpoint is the commit after it)

## Just done
T1-T3: budget up to 2,000 + too-late read guard (no 'No limit', reasons in RESEARCH §31), lines/props per game up to 10/48, props credits up to 192 with worst-case hint; v0.19.4 (39); full floor 735/0 failures

## Do this next
T7: CI green on this commit, ship.sh, release.yml, confirm, record, link

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  0a4c486 ckpt 588: T4 + T6: bet-slip amount setting (Off/$1/Kelly/My amount) on every Novig link 
  d27d061 ckpt 587: T5: PinnWire->pinnapi was built (daily limit); pinned the reset cycle with a t
  7e8359f ckpt 586: T1 measured live: 7-day board 8,620 Novig markets (4,819 spreads/totals, ~2,70
  482486c ckpt 585: Recorded Tj's request (scan budget/no limit, lines+props per game, props credi
  3238762 ckpt 584: Released Vigilant v0.19.3 (code 38): release.yml green, vigilant-v0.19.3.apk o
  7d41bab ckpt 583: pre-release: v0.19.3: key reads 10 at a time (were capped at 4), one Novig ref
  94339f0 ckpt 582: S2/S2a: quote-age limit 10 min on games >3h off, 5 within 3h/live (Freshness.m
  c58c2c8 ckpt 581: S2 measured: 30-min live recording (Kalshi fair 62k pairs, Novig 2.5k): >=1pt 
  f744729 ckpt 580: Full floor after S1/S3: 2 old pins updated (schema 9, CNO chips 1+..4+); RESEA
  032a66e ckpt 579: S1: Novig 451 codes read correctly (ANONYMIZED_NETWORK = Novig's verdict on th
```

(7 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
