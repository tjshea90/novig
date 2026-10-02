# CHECKPOINT 2359 — read me first, then TASKS.md

**Written:** 2026-10-02T18:12:48Z · **tests:** all 3 fast checks green
**Branch:** `ccr-38dc4f3b-3f0cld` · **builds on:** `73ce4d3e` (this checkpoint is the commit after it)

## Just done
AX3/AX4 code in progress: SettingsPage home+pages+search (SettingsIndex), AutoBetScreen tab (ic_autobet), BackgroundScan on/off model, Shadowed warnings, StakeText ( sheet fix), SharpVetoSection split, ✓/only-agreed nesting, stale Settings paths updated; compiles

## Do this next
add apiMinEv removal (AX5), then fix tests (SettingsTabsTest→pages, ScreenshotTest, SharpConfirmUiTest, AutoBetUiTest, MgmAppTest), new tests (SettingsIndexTest, BackgroundScan, StakeText, Shadowed, AutoBetScreen fixes)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/src/main/kotlin/com/tjshea/vigilant/app/AutoBettor.kt
     M app/src/main/kotlin/com/tjshea/vigilant/app/HealthChecks.kt
     M app/src/main/kotlin/com/tjshea/vigilant/app/MainViewModel.kt

## Last ten checkpoints
```
  268658fe ckpt 2358: AW6 done: v0.45.0 released and verified (APK versionCode 83, cert AB:22:07:A
  5060403e ckpt 2357: AX1 inventory + AX2 design written into TASKS.md (contradictions a-g, apiMin
  6696ee82 ckpt 2356: AX: Tj's 17:55Z settings reorganization request written into TASKS.md (AX1-A
  6f1e681e ckpt 2355: pre-release: v0.45.0: presets (Volume + safe CLV, Strict CLV, your own), sha
  95b9097d ckpt 2354: full floor green on v0.45.0 (1,695: 1,672 passed, 23 skipped)
  9429058e ckpt 2353: AW7 done (BetKind fallback by words + whole-match sets, page-less dissent, p
  e52f5a9b ckpt 2352: floor green (1,693: 1,670 passed, 23 skipped); sweep found AW7 (BetKind OTHE
  a5733e96 ckpt 2351: app tests green (40): PresetsUiTest, Downloads save (DiagnosticsShareTest), 
  9b214aef ckpt 2350: tests: SharpVetoTest, PresetsTest, AtBetTest, BetLedgerTest green (33); reco
  3bc48fc2 ckpt 2349: old tests moved to SharpMode (CONFIRM/OFF/VETO), UI test for the mode chips
```

(21 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
