# CHECKPOINT 2188 — read me first, then TASKS.md

**Written:** 2026-09-30T11:15:33Z · **tests:** all 3 fast checks green
**Branch:** `ccr-01f0c1f9-xy73ag` · **builds on:** `787e9d49` (this checkpoint is the commit after it)

## Just done
Full tests: floor green (1217: 1196 passed, 21 live skipped), all 91 screenshots reviewed; fixes: ParlayAPI movers only on screen + at once on return (refreshWhileOnScreen, ScreenPacingTest), no Vigilant re-ask within 2 min (vigilantAsks), stale pick-sheet reopen, pick sheet books remembered, usage text, 1m screenshot name

## Do this next
Continue sweep (API usage, lifecycle, data retention), then full regression and ship v0.32.0

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  d47cc485 ckpt 2187: P1 Bet button on ParlayAPI picks, P2 CNO/Vigilant EV beside each pick (Parla
  b134cc4c ckpt 2186: Logged Tj's 07:13Z request as TASKS.md P4 (tap a ParlayAPI pick -> bet sheet
  6350a1ac ckpt 2185: v0.31.0 (code 59) released (CI + release.yml green, APK confirmed) and recor
  e03dbd31 ckpt 2184: Logged Tj's 07:25Z request as TASKS.md §P (P1 Bet button on ParlayAPI picks
  a229d5b5 ckpt 2183: pre-release: v0.31.0: Check odds now (and the closing-line capture) reads ev
  c424cce8 ckpt 2182: O1 done: Check odds now + closing capture read every open bet both ways (CNO
  1cb92e7a ckpt 2181: Wrote Tj's 06:50Z request into TASKS.md as O1-O3 (Check odds now always adds
  e8b4fe3b ckpt 2180: Corrected the probe tally to 15 credits (19,867 -> 19,852)
  c0af9949 ckpt 2179: v0.30.0 (code 58) released (release.yml green, Release + APK confirmed) and 
  a6208d0f ckpt 2178: pre-release: v0.30.0: ParlayAPI: injury tags on prop bets; credits-a-day cha
```

(9 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
