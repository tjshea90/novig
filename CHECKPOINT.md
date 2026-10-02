# CHECKPOINT 2360 — read me first, then TASKS.md

**Written:** 2026-10-02T18:21:37Z · **tests:** all 3 fast checks green
**Branch:** `ccr-38dc4f3b-3f0cld` · **builds on:** `f749bcde` (this checkpoint is the commit after it)

## Just done
AX3-AX5 code + existing tests moved: Settings home/pages/search, Auto-bet tab, contradictions a-g fixed, apiMinEv removed; app suite 680/680 green (SettingsPagesTest replaces SettingsTabsTest)

## Do this next
new unit/UI tests: SettingsFixesTest (BackgroundScan, StakeText + ApiBetting.base $1, Shadowed, AutoBetText.fixFor, AutoBetScreen fix buttons, SettingsSummary); mutation-check; look at screenshots; full floor; sweep; ship

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  02e24ceb ckpt 2359: AX3/AX4 code in progress: SettingsPage home+pages+search (SettingsIndex), Au
  268658fe ckpt 2358: AW6 done: v0.45.0 released and verified (APK versionCode 83, cert AB:22:07:A
  5060403e ckpt 2357: AX1 inventory + AX2 design written into TASKS.md (contradictions a-g, apiMin
  6696ee82 ckpt 2356: AX: Tj's 17:55Z settings reorganization request written into TASKS.md (AX1-A
  6f1e681e ckpt 2355: pre-release: v0.45.0: presets (Volume + safe CLV, Strict CLV, your own), sha
  95b9097d ckpt 2354: full floor green on v0.45.0 (1,695: 1,672 passed, 23 skipped)
  9429058e ckpt 2353: AW7 done (BetKind fallback by words + whole-match sets, page-less dissent, p
  e52f5a9b ckpt 2352: floor green (1,693: 1,670 passed, 23 skipped); sweep found AW7 (BetKind OTHE
  a5733e96 ckpt 2351: app tests green (40): PresetsUiTest, Downloads save (DiagnosticsShareTest), 
  9b214aef ckpt 2350: tests: SharpVetoTest, PresetsTest, AtBetTest, BetLedgerTest green (33); reco
```

(12 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
