# CHECKPOINT 526 — read me first, then TASKS.md

**Written:** 2026-09-27T19:57:38Z · **tests:** all 1 fast checks green
**Branch:** `claude/betmgm-ev-scanner-h4yfqw` · **builds on:** `1b47e1d` (this checkpoint is the commit after it)

## Just done
SHIPPED v0.17.0 code 32 (V1-V5 ticked): Vigilant MGM + Vigilant both on https://github.com/tjshea90/novig/releases/tag/v0.17.0 (vigilant-v0.17.0.apk, vigilant-mgm-v0.17.0.apk); CI 36345562068 green; release.yml 36345843461 green; recorded in BUILDLOG

## Do this next
Nothing queued. Open, waits on Tj: first BetMGM scan with his PropLine key (verifies BetMGM's book_outcome_id shape and bet-slip links, RESEARCH §25.3), pick his BetMGM state in Vigilant MGM Settings

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  bbd955f ckpt 525: pre-release: v0.17.0: Vigilant MGM, the same +EV scanner for BetMGM as its own
  f68ecd4 ckpt 524: V1-V4 done + docs (RESEARCH §25, BRIEF decision, CLAUDE surface), version v0.
  55958db ckpt 523: V3 app: AppBook (BuildConfig.BOOK) + mgm module (com.tjshea.vigilant.betmgm, c
  56eb079 ckpt 522: V1 data layer + V2 links: data/book (Sportsbook, BookBoard, SportsbookScanner,
  f515f63 ckpt 521: Wrote Tj's BetMGM request into TASKS.md (V1-V5) with the design decision: sepa
  b663cd1 ckpt 520: SHIPPED v0.16.4 code 31 (T4+U4 ticked): floor engine 39 / data 385 (8 live ski
  7bd459b ckpt 519: pre-release: v0.16.4: no sportsbook odds over 5 minutes old ever price an EV (
  d81ae2a ckpt 518: Floor found PropLineClientTest last-seen test contradicting the withdrawn-outc
  ce2ec54 ckpt 517: U2/U3 ticked, RESEARCH §24.3, CLAUDE.md surface list, version v0.16.4 code 31
  6852c62 ckpt 516: U3 part 2: PropLine last_seen_at per quote; re-use capped at 2 min everywhere 
```
