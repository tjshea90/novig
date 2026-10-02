# CHECKPOINT 2322 — read me first, then TASKS.md

**Written:** 2026-10-02T14:55:42Z · **tests:** all 3 fast checks green
**Branch:** `ccr-ea5bf769-s5xd3t` · **builds on:** `2ffb429b` (this checkpoint is the commit after it)

## Just done
AS1 done: Check odds now and pull to refresh resume a paused scanner (the other session's half-change reviewed, finished, AutoResumeAppTest + 4 mutants killed)

## Do this next
AS2: auto-bet survives an app switch (LaunchReset only on a fresh launch / process restart)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M app/src/main/kotlin/com/tjshea/vigilant/app/ui/SettingsScreen.kt

## Last ten checkpoints
```
  831f36c4 ckpt 2321: AQ2 ticked: notification off main + FoundCount, frame meter in Diagnostics; 
  be4d5c86 ckpt 2320: AS written to TASKS.md (Tj 06:14Z: auto-resume scanner on Check odds now/pul
  158094f6 ckpt 2319: AQ2 part 2: frame meter (FrameStats + FrameMeter) in Diagnostics' Performanc
  05349b30 ckpt 2318: AQ2 part 1: scan notification built off the main thread, its found count onc
  743a98e6 ckpt 2317: AQ1/AQ3/AQ4/AR1/AR2 ticked; app-level manual-bet test added (ApiBettingContr
  11e9ba2b ckpt 2316: AR1/AR2: Check odds now and Price now price Vigilant's bets whatever the sca
  9c48a379 ckpt 2315: AR written to TASKS.md (Check odds now must refresh every open bet, Vigilant
  fb432429 ckpt 2314: AQ3/AQ4: manual Bet sheet has no min EV / fair-age / pause block (BetLimits.
  88d819f2 ckpt 2313: v0.44.1 released and recorded (AP1): refusal under the minimum names the min
  eb5c7763 ckpt 2312: pre-release: v0.44.1: a bet under your minimum edge says so and where to cha
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
