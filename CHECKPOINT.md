# CHECKPOINT 3106 — read me first, then TASKS.md

**Written:** 2026-10-12T18:25:59Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `d6cd39547` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.94.1: streaming engine matches per line: a Novig push re-prices just that line against the fair already held and the auto-bet judges it at once; a BoltOdds change re-matches and re-prices all held books; BoltOdds push-lag/sharp-book report in Diagnostics for the live-fair decision

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M CLAUDE.md
     M TASKS.md
     M app/build.gradle.kts
     M app/src/main/kotlin/com/tjshea/vigilant/app/MainViewModel.kt
     M app/src/main/kotlin/com/tjshea/vigilant/app/ui/SettingsScreen.kt

## Last ten checkpoints
```
  ba5ec75ab ckpt 3105: pre-release: v0.94.0: streaming engine (Novig and BoltOdds pushes start a s
  3c50031f2 ckpt 3104: pre-ship: v0.94.0: streaming engine (Novig and BoltOdds pushes start a scan
  8a2711b77 ckpt 3103: pre-ship: v0.94.0: streaming engine (Novig and BoltOdds pushes start a scan
  9b66c0e71 ckpt 3102: pre-release: v0.93.4: repeat and hedge guard (a side is bet or bid at most 
  21712abeb ckpt 3101: pre-ship: v0.93.4: repeat and hedge guard (a side is bet or bid at most twi
  9c78c9d5f ckpt 3100: TST2 done: RepeatGuard (pure, RepeatGuardTest) wired into ApiBetPlacer (aut
  2771ed5aa ckpt 3099: Recorded Tj's streaming-engine request as TST1-5; v0.93.3 CI/release still 
  2eb959c28 ckpt 3098: pre-release: v0.93.3: Entain's bwin, Party Sports, Sports Interaction, Ladb
  6dab775a8 ckpt 3097: pre-ship: v0.93.3: Entain's bwin, Party Sports, Sports Interaction, Ladbrok
  7efdb28cc ckpt 3096: pre-release: v0.93.2: a resting bid priced at or over its fair comes down i
```

(12 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
