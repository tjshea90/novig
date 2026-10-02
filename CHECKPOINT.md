# CHECKPOINT 2363 — read me first, then TASKS.md

**Written:** 2026-10-02T18:38:15Z · **tests:** all 3 fast checks green
**Branch:** `ccr-38dc4f3b-3f0cld` · **builds on:** `6514a7e9` (this checkpoint is the commit after it)

## Just done
pre-release: v0.46.0: Settings reorganized (a home list with search and plain-English pages), Auto-bet is its own tab with presets, contradicting settings fixed ($1 bet amount, ✓ switches, background scan, shadowed limits), superfluous setting removed (versionCode 84, v0.46.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.46.0), then run: bash tools/record-release.sh v0.46.0 84 "v0.46.0: Settings reorganized (a home list with search and plain-English pages), Auto-bet is its own tab with presets, contradicting settings fixed ($1 bet amount, ✓ switches, background scan, shadowed limits), superfluous setting removed"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  6514a7e9 ckpt 2362: AX6 prep: v0.46.0 code 84; floor 1,710 had 3 data tests on old menu paths, f
  b94ccfe6 ckpt 2361: AX polish: home summaries (keys count, 1%+), CLV explained in presets intro,
  38b97032 ckpt 2360: AX3-AX5 code + existing tests moved: Settings home/pages/search, Auto-bet ta
  02e24ceb ckpt 2359: AX3/AX4 code in progress: SettingsPage home+pages+search (SettingsIndex), Au
  268658fe ckpt 2358: AW6 done: v0.45.0 released and verified (APK versionCode 83, cert AB:22:07:A
  5060403e ckpt 2357: AX1 inventory + AX2 design written into TASKS.md (contradictions a-g, apiMin
  6696ee82 ckpt 2356: AX: Tj's 17:55Z settings reorganization request written into TASKS.md (AX1-A
  6f1e681e ckpt 2355: pre-release: v0.45.0: presets (Volume + safe CLV, Strict CLV, your own), sha
  95b9097d ckpt 2354: full floor green on v0.45.0 (1,695: 1,672 passed, 23 skipped)
  9429058e ckpt 2353: AW7 done (BetKind fallback by words + whole-match sets, page-less dissent, p
```
