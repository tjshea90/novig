# CHECKPOINT 249 — read me first, then TASKS.md

**Written:** 2026-09-20T20:49:08Z · **tests:** all 1 fast checks green
**Branch:** `claude/positive-ev-sports-research-n6jum5` · **builds on:** `c762da5` (this checkpoint is the commit after it)

## Just done
Fixed a real CI failure caught on the first push of the sport picker (run 35536648655): 'import androidx.compose.foundation.layout.weight' in OpportunitiesScreen.kt resolved to an unrelated internal top-level symbol (RowColumnParentData?.weight) instead of the intended ColumnScope.weight member extension, breaking compileDebugKotlin/compileReleaseKotlin ('it is internal in file'). Modifier.weight() is a member extension on ColumnScope/RowScope, not a top-level function — it needs zero import when called inside a Column{}/Row{} lambda, which is exactly why the bogus import was harmful rather than merely redundant. Removed the import; Modifier.weight(1f) inside the Column body now resolves correctly via the implicit ColumnScope receiver. Also bumped versionCode 1->2 / versionName 0.1.0->0.2.0 in app/build.gradle.kts (BRIEF.md's rule — release.yml refuses to re-release an already-tagged version, and v0.1.0/code 1 is already shipped) ahead of triggering the release workflow next. engine+data tests still green.

## Do this next
Push this fix, re-confirm CI green for the app module for real this time, then trigger release.yml via workflow_dispatch, confirm it goes green, record it in BUILDLOG.md via tools/record-release.sh, and send Tj the new v0.2.0 Release link as plain tappable text.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  2c1f31c ckpt 241: Built the sport-selection picker per Tj's explicit instruction: no odds load f
  92edb91 ckpt 229: Wrote Tj's new sport-picker request into TASKS.md before writing any code, per
  1fe3119 ckpt 226: Confirmed CI green for the app module (run 35535815614, conclusion=success) �
  8be34bf ckpt 220: Wired Settings navigation into MainActivity (state-based, no nav library): Set
  aadab85 ckpt 208: Rewired TheOddsApiClient onto KeyRotator (was a single apiKey string) — auto
  8e70e49 ckpt 205: Built KeyRotator (data module) — provider-agnostic multi-key rotation with r
  b663452 ckpt 198: Wrote Tj's 'make the app functional' request into TASKS.md with the architectu
  54208cf ckpt 195: Updated RESEARCH.md §4.1.1 with the real API-access process Tj found (email d
  b3b36e3 ckpt 191: Wrote Tj's request into TASKS.md: he found the real API-access process (email 
  44e6c69 ckpt 188: Second research pass on Novig's NBX API pricing/access: found strong convergin
```

(7 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
