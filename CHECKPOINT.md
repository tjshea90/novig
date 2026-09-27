# CHECKPOINT 525 — read me first, then TASKS.md

**Written:** 2026-09-27T19:50:41Z · **tests:** all 1 fast checks green
**Branch:** `claude/betmgm-ev-scanner-h4yfqw` · **builds on:** `f68ecd4` (this checkpoint is the commit after it)

## Just done
pre-release: v0.17.0: Vigilant MGM, the same +EV scanner for BetMGM as its own app (com.tjshea.vigilant.betmgm) built from Vigilant's code; BetMGM's odds ride in the PropLine/The Odds API calls (no extra requests) and never price their own fair line; Vigilant unchanged. 611 tests (versionCode 32, v0.17.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.17.0), then run: bash tools/record-release.sh v0.17.0 32 "v0.17.0: Vigilant MGM, the same +EV scanner for BetMGM as its own app (com.tjshea.vigilant.betmgm) built from Vigilant's code; BetMGM's odds ride in the PropLine/The Odds API calls (no extra requests) and never price their own fair line; Vigilant unchanged. 611 tests"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  f68ecd4 ckpt 524: V1-V4 done + docs (RESEARCH §25, BRIEF decision, CLAUDE surface), version v0.
  55958db ckpt 523: V3 app: AppBook (BuildConfig.BOOK) + mgm module (com.tjshea.vigilant.betmgm, c
  56eb079 ckpt 522: V1 data layer + V2 links: data/book (Sportsbook, BookBoard, SportsbookScanner,
  f515f63 ckpt 521: Wrote Tj's BetMGM request into TASKS.md (V1-V5) with the design decision: sepa
  b663cd1 ckpt 520: SHIPPED v0.16.4 code 31 (T4+U4 ticked): floor engine 39 / data 385 (8 live ski
  7bd459b ckpt 519: pre-release: v0.16.4: no sportsbook odds over 5 minutes old ever price an EV (
  d81ae2a ckpt 518: Floor found PropLineClientTest last-seen test contradicting the withdrawn-outc
  ce2ec54 ckpt 517: U2/U3 ticked, RESEARCH §24.3, CLAUDE.md surface list, version v0.16.4 code 31
  6852c62 ckpt 516: U3 part 2: PropLine last_seen_at per quote; re-use capped at 2 min everywhere 
  0fec760 ckpt 515: U3 part 1: Freshness (5 min per quote, 2 min re-use), Scanner stamps each quot
```
