# CHECKPOINT 600 — read me first, then TASKS.md

**Written:** 2026-09-28T20:16:17Z · **tests:** all 1 fast checks green
**Branch:** `ccr-ed1962c6-kshrww` · **builds on:** `73e2359` (this checkpoint is the commit after it)

## Just done
V2-V5 data side: ScanSettings.NO_LIMIT on Novig prices/credits/lines/props/props-hours, propLineGamesPerScan (was fixed 12), scanWindowHours (Starts within bounds the scan, S1), bookPropWindowHours, leftLastScan rotation; tests BiggerScansTest (3 new + choices pin), OddsApiPropsTest, PropLineClientTest

## Do this next
UI: Settings chips 'No limit'/'All' + honest hints, PropLine games chips, Days ahead/Starts within copy, last-scanned-window banner; app tests; full floor

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  8a2d4bb ckpt 599: Released Vigilant v0.19.5 (code 40): release.yml green on 22e5276, APK on the 
  b65e2db ckpt 598: Recorded Tj's request (unlimited options for every scan cap, scan must stop wh
  22e5276 ckpt 597: pre-release: v0.19.5: pause all scanning (Settings switch, pause/resume on the
  2d7374f ckpt 596: U1 done + full floor 752/0 failures; v0.19.5 (40) ready
  bf7f8d1 ckpt 595: U1 built: pause all scanning (ScanSettings.paused, CnoWatch.hold, ScanRunner.s
  25f6d90 ckpt 594: U2: Open in Novig button on every CNO tab card (same path/stake as the widget 
  a021c70 ckpt 593: Recorded Tj's request (pause all scanning; Open in Novig buttons on CNO tab ca
  090d751 ckpt 592: Released Vigilant v0.19.4 (code 39): release.yml green on 23c4b10, vigilant-v0
  23c4b10 ckpt 591: pre-release: v0.19.4: Novig prices per scan up to 2,000 (long scans skip lines
  216f4ba ckpt 590: T7 pre-ship: fixed flaky NovigPublicClientTest 'a refused wave is waited out o
```

(7 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
