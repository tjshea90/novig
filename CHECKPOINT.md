# CHECKPOINT 524 — read me first, then TASKS.md

**Written:** 2026-09-27T19:46:05Z · **tests:** all 1 fast checks green
**Branch:** `claude/betmgm-ev-scanner-h4yfqw` · **builds on:** `9f36550` (this checkpoint is the commit after it)

## Just done
V1-V4 done + docs (RESEARCH §25, BRIEF decision, CLAUDE surface), version v0.17.0 code 32. Floor: engine 39 / data 400 (8 live skipped) / app 170 / mgm 2, exit 0 + XML counts; both release APKs built+verified locally; screenshots checked (Novig 2_detail unchanged, 20-23 MGM)

## Do this next
Wait for CI green on this commit, then ship.sh, trigger release.yml, confirm Release has both APKs, record-release, send Tj the link

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  55958db ckpt 523: V3 app: AppBook (BuildConfig.BOOK) + mgm module (com.tjshea.vigilant.betmgm, c
  56eb079 ckpt 522: V1 data layer + V2 links: data/book (Sportsbook, BookBoard, SportsbookScanner,
  f515f63 ckpt 521: Wrote Tj's BetMGM request into TASKS.md (V1-V5) with the design decision: sepa
  b663cd1 ckpt 520: SHIPPED v0.16.4 code 31 (T4+U4 ticked): floor engine 39 / data 385 (8 live ski
  7bd459b ckpt 519: pre-release: v0.16.4: no sportsbook odds over 5 minutes old ever price an EV (
  d81ae2a ckpt 518: Floor found PropLineClientTest last-seen test contradicting the withdrawn-outc
  ce2ec54 ckpt 517: U2/U3 ticked, RESEARCH §24.3, CLAUDE.md surface list, version v0.16.4 code 31
  6852c62 ckpt 516: U3 part 2: PropLine last_seen_at per quote; re-use capped at 2 min everywhere 
  0fec760 ckpt 515: U3 part 1: Freshness (5 min per quote, 2 min re-use), Scanner stamps each quot
  af6c68b ckpt 514: U1 audit done: RESEARCH.md §24.1 (re-use windows 15-60 min, stale-limit keep,
```

(7 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
