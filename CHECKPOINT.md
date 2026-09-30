# CHECKPOINT 2190 — read me first, then TASKS.md

**Written:** 2026-09-30T11:19:55Z · **tests:** all 3 fast checks green
**Branch:** `ccr-01f0c1f9-xy73ag` · **builds on:** `fb7f0b0d` (this checkpoint is the commit after it)

## Just done
pre-release: v0.32.0: ParlayAPI's picks: in-app Bet through Novig's API, CNO's and Vigilant's own EV beside ParlayAPI's on every pick, tap a pick for every book's odds with Vigilant's worst-case check (CNO's page or ParlayAPI's books); full tests fixes: movers only on screen, no double Vigilant reads, no ghost sheet (versionCode 60, v0.32.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.32.0), then run: bash tools/record-release.sh v0.32.0 60 "v0.32.0: ParlayAPI's picks: in-app Bet through Novig's API, CNO's and Vigilant's own EV beside ParlayAPI's on every pick, tap a pick for every book's odds with Vigilant's worst-case check (CNO's page or ParlayAPI's books); full tests fixes: movers only on screen, no double Vigilant reads, no ghost sheet"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  fb7f0b0d ckpt 2189: Full tests done: regression 1221 green (exit 0, log clean); sweep fixes with
  591927fa ckpt 2188: Full tests: floor green (1217: 1196 passed, 21 live skipped), all 91 screens
  d47cc485 ckpt 2187: P1 Bet button on ParlayAPI picks, P2 CNO/Vigilant EV beside each pick (Parla
  b134cc4c ckpt 2186: Logged Tj's 07:13Z request as TASKS.md P4 (tap a ParlayAPI pick -> bet sheet
  6350a1ac ckpt 2185: v0.31.0 (code 59) released (CI + release.yml green, APK confirmed) and recor
  e03dbd31 ckpt 2184: Logged Tj's 07:25Z request as TASKS.md §P (P1 Bet button on ParlayAPI picks
  a229d5b5 ckpt 2183: pre-release: v0.31.0: Check odds now (and the closing-line capture) reads ev
  c424cce8 ckpt 2182: O1 done: Check odds now + closing capture read every open bet both ways (CNO
  1cb92e7a ckpt 2181: Wrote Tj's 06:50Z request into TASKS.md as O1-O3 (Check odds now always adds
  e8b4fe3b ckpt 2180: Corrected the probe tally to 15 credits (19,867 -> 19,852)
```
