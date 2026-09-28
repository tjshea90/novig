# CHECKPOINT 579 — read me first, then TASKS.md

**Written:** 2026-09-28T15:37:20Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-api-coverage-d7iwyq` · **builds on:** `e10cc0c` (this checkpoint is the commit after it)

## Just done
S1: Novig 451 codes read correctly (ANONYMIZED_NETWORK = Novig's verdict on the address, not the phone); Test key names the connection, real VPN check, tries the other connection; tests green

## Do this next
S2: analyse drift.jsonl (Kalshi+Novig price movement over 5/10/15/30 min) when the recorder finishes (~16:10Z), research line-movement speed, decide the 5-min rule

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M app/src/main/kotlin/com/tjshea/vigilant/app/ui/NovigKeySection.kt
     M app/src/test/kotlin/com/tjshea/vigilant/app/ScreenshotTest.kt

## Last ten checkpoints
```
  bbf4475 ckpt 578: S3: CNO fewest books 1-4 (default 4, saved 5+ -> 4, app's choice always posted
  ce24b8e ckpt 577: Recorded Tj's VPN/proxy false alarm, 5-minute cutoff review, min-books 1-4 as 
  20f906b ckpt 576: R2e: docs (RESEARCH §29 with live Kalshi lines-first timings, NOVIG_API §11.
  0a9e9f7 ckpt 575: R2c Kalshi game lines first (lines pass, hold-back lets lines through) + R2d S
  d2cabfe ckpt 574: R2a: key reads were capped at 4 in flight (OkHttp's 5/host, websocket holds on
  62ba92d ckpt 573: R1 done: causes found (v0.19.2 hold-back waits for Kalshi 8-28s; v0.19.1 7 day
  dbbe366 ckpt 572: R1: measured board (7d = 8,319 markets, 0.67MB, 2 pages) and free sources live
  facb691 ckpt 571: Recorded Tj's 'API reading very slow since latest version' as R1-R2
  8e5378c ckpt 570: Released Vigilant v0.19.2 (code 37): release.yml green, tag v0.19.2 has vigila
  a34fcd4 ckpt 569: pre-release: v0.19.2: bets shown mid-scan stay: a league's bets wait until all
```

(8 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
