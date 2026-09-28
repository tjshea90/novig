# CHECKPOINT 586 — read me first, then TASKS.md

**Written:** 2026-09-28T18:45:28Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-api-coverage-d7iwyq` · **builds on:** `5b07562` (this checkpoint is the commit after it)

## Just done
T1 measured live: 7-day board 8,620 Novig markets (4,819 spreads/totals, ~2,700 props); free sources price 1,168 (all of them fit in 1,200); Pinnacle/PropLine keys price more (can't measure here). Novig docs: web link novig.com/events/<outcome>/<partner>/<wager> pre-fills the wager (T4 feasible)

## Do this next
T5: read PinnapiClient key order; then T6+T4 (Open in Novig button + stake in links), T1-T3 settings + freshness time guard

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  482486c ckpt 585: Recorded Tj's request (scan budget/no limit, lines+props per game, props credi
  3238762 ckpt 584: Released Vigilant v0.19.3 (code 38): release.yml green, vigilant-v0.19.3.apk o
  7d41bab ckpt 583: pre-release: v0.19.3: key reads 10 at a time (were capped at 4), one Novig ref
  94339f0 ckpt 582: S2/S2a: quote-age limit 10 min on games >3h off, 5 within 3h/live (Freshness.m
  c58c2c8 ckpt 581: S2 measured: 30-min live recording (Kalshi fair 62k pairs, Novig 2.5k): >=1pt 
  f744729 ckpt 580: Full floor after S1/S3: 2 old pins updated (schema 9, CNO chips 1+..4+); RESEA
  032a66e ckpt 579: S1: Novig 451 codes read correctly (ANONYMIZED_NETWORK = Novig's verdict on th
  bbf4475 ckpt 578: S3: CNO fewest books 1-4 (default 4, saved 5+ -> 4, app's choice always posted
  ce24b8e ckpt 577: Recorded Tj's VPN/proxy false alarm, 5-minute cutoff review, min-books 1-4 as 
  20f906b ckpt 576: R2e: docs (RESEARCH §29 with live Kalshi lines-first timings, NOVIG_API §11.
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
