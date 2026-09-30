# CHECKPOINT 2199 — read me first, then TASKS.md

**Written:** 2026-09-30T15:32:13Z · **tests:** all 3 fast checks green
**Branch:** `ccr-01f0c1f9-xy73ag` · **builds on:** `12147735` (this checkpoint is the commit after it)

## Just done
pre-release: v0.34.0: Check odds now refreshes every open bet whatever found it: games under way re-priced, ParlayAPI asked for every real sportsbook (bet365, BetMGM, Fanatics, Hard Rock, Fliff, betPARX), bets with no CNO page get every book's read too, book lists refreshed; tapping any Vigilant notification opens Vigilant full screen (versionCode 62, v0.34.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.34.0), then run: bash tools/record-release.sh v0.34.0 62 "v0.34.0: Check odds now refreshes every open bet whatever found it: games under way re-priced, ParlayAPI asked for every real sportsbook (bet365, BetMGM, Fanatics, Hard Rock, Fliff, betPARX), bets with no CNO page get every book's read too, book lists refreshed; tapping any Vigilant notification opens Vigilant full screen"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  12147735 ckpt 2198: T1 alerts open Vigilant; T2 Check odds now refreshes every open bet (live ga
  f5b23db5 ckpt 2197: v0.33.0 (code 61) released and recorded: pick sheet books from every source 
  c605151e ckpt 2196: pre-release: v0.33.0: a ParlayAPI pick's sheet shows every other sportsbook'
  83be1779 ckpt 2195: pre-ship: v0.33.0: a ParlayAPI pick's sheet shows every other sportsbook's o
  e573f872 ckpt 2194: pre-release: v0.33.0: a ParlayAPI pick's sheet shows every other sportsbook'
  cf57ddea ckpt 2193: Q1+Q2: pick sheet books from every source (OtherBooks: ParlayAPI all books o
  ed445bff ckpt 2192: Logged Tj's 14:35Z request as TASKS.md Q1-Q3 (pick sheet books: find why Par
  0414dcba ckpt 2191: v0.32.0 (code 60) released (CI + release.yml green, APK confirmed) and recor
  4fa00ab8 ckpt 2190: pre-release: v0.32.0: ParlayAPI's picks: in-app Bet through Novig's API, CNO
  fb7f0b0d ckpt 2189: Full tests done: regression 1221 green (exit 0, log clean); sweep fixes with
```
