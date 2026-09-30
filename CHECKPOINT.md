# CHECKPOINT 2194 — read me first, then TASKS.md

**Written:** 2026-09-30T14:55:25Z · **tests:** all 3 fast checks green
**Branch:** `ccr-01f0c1f9-xy73ag` · **builds on:** `0a7fabb2` (this checkpoint is the commit after it)

## Just done
pre-release: v0.33.0: a ParlayAPI pick's sheet shows every other sportsbook's odds: ParlayAPI at every real book with one-sided lines kept (home runs and anytime TDs are one-sided at most books, which is why it said none) plus PropLine side by side, The Odds API when neither has one; older prices listed apart, never counted (versionCode 61, v0.33.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.33.0), then run: bash tools/record-release.sh v0.33.0 61 "v0.33.0: a ParlayAPI pick's sheet shows every other sportsbook's odds: ParlayAPI at every real book with one-sided lines kept (home runs and anytime TDs are one-sided at most books, which is why it said none) plus PropLine side by side, The Odds API when neither has one; older prices listed apart, never counted"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  cf57ddea ckpt 2193: Q1+Q2: pick sheet books from every source (OtherBooks: ParlayAPI all books o
  ed445bff ckpt 2192: Logged Tj's 14:35Z request as TASKS.md Q1-Q3 (pick sheet books: find why Par
  0414dcba ckpt 2191: v0.32.0 (code 60) released (CI + release.yml green, APK confirmed) and recor
  4fa00ab8 ckpt 2190: pre-release: v0.32.0: ParlayAPI's picks: in-app Bet through Novig's API, CNO
  fb7f0b0d ckpt 2189: Full tests done: regression 1221 green (exit 0, log clean); sweep fixes with
  591927fa ckpt 2188: Full tests: floor green (1217: 1196 passed, 21 live skipped), all 91 screens
  d47cc485 ckpt 2187: P1 Bet button on ParlayAPI picks, P2 CNO/Vigilant EV beside each pick (Parla
  b134cc4c ckpt 2186: Logged Tj's 07:13Z request as TASKS.md P4 (tap a ParlayAPI pick -> bet sheet
  6350a1ac ckpt 2185: v0.31.0 (code 59) released (CI + release.yml green, APK confirmed) and recor
  e03dbd31 ckpt 2184: Logged Tj's 07:25Z request as TASKS.md §P (P1 Bet button on ParlayAPI picks
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
