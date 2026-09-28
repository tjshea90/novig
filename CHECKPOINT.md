# CHECKPOINT 582 — read me first, then TASKS.md

**Written:** 2026-09-28T16:12:05Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-api-coverage-d7iwyq` · **builds on:** `b16b5bb` (this checkpoint is the commit after it)

## Just done
S2/S2a: quote-age limit 10 min on games >3h off, 5 within 3h/live (Freshness.maxAgeMs), one rule for pricing/reprice/recheck/display; RESEARCH §30.2; full floor 724/0 failures

## Do this next
S4: ship.sh v0.19.3 (38), CI green, release.yml, confirm, record-release, send link

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  c58c2c8 ckpt 581: S2 measured: 30-min live recording (Kalshi fair 62k pairs, Novig 2.5k): >=1pt 
  f744729 ckpt 580: Full floor after S1/S3: 2 old pins updated (schema 9, CNO chips 1+..4+); RESEA
  032a66e ckpt 579: S1: Novig 451 codes read correctly (ANONYMIZED_NETWORK = Novig's verdict on th
  bbf4475 ckpt 578: S3: CNO fewest books 1-4 (default 4, saved 5+ -> 4, app's choice always posted
  ce24b8e ckpt 577: Recorded Tj's VPN/proxy false alarm, 5-minute cutoff review, min-books 1-4 as 
  20f906b ckpt 576: R2e: docs (RESEARCH §29 with live Kalshi lines-first timings, NOVIG_API §11.
  0a9e9f7 ckpt 575: R2c Kalshi game lines first (lines pass, hold-back lets lines through) + R2d S
  d2cabfe ckpt 574: R2a: key reads were capped at 4 in flight (OkHttp's 5/host, websocket holds on
  62ba92d ckpt 573: R1 done: causes found (v0.19.2 hold-back waits for Kalshi 8-28s; v0.19.1 7 day
  dbbe366 ckpt 572: R1: measured board (7d = 8,319 markets, 0.67MB, 2 pages) and free sources live
```

(11 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
