# CHECKPOINT 2355 — read me first, then TASKS.md

**Written:** 2026-10-02T17:53:57Z · **tests:** all 3 fast checks green
**Branch:** `ccr-38dc4f3b-3f0cld` · **builds on:** `12628f3a` (this checkpoint is the commit after it)

## Just done
pre-release: v0.45.0: presets (Volume + safe CLV, Strict CLV, your own), sharp veto by kind of bet (Kalshi/ProphetX for props, Pinnacle/Circa for game lines), every bet's record as placed in Diagnostics with CLV splits, Diagnostics file saved to Downloads/Vigilant (versionCode 83, v0.45.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.45.0), then run: bash tools/record-release.sh v0.45.0 83 "v0.45.0: presets (Volume + safe CLV, Strict CLV, your own), sharp veto by kind of bet (Kalshi/ProphetX for props, Pinnacle/Circa for game lines), every bet's record as placed in Diagnostics with CLV splits, Diagnostics file saved to Downloads/Vigilant"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  95b9097d ckpt 2354: full floor green on v0.45.0 (1,695: 1,672 passed, 23 skipped)
  9429058e ckpt 2353: AW7 done (BetKind fallback by words + whole-match sets, page-less dissent, p
  e52f5a9b ckpt 2352: floor green (1,693: 1,670 passed, 23 skipped); sweep found AW7 (BetKind OTHE
  a5733e96 ckpt 2351: app tests green (40): PresetsUiTest, Downloads save (DiagnosticsShareTest), 
  9b214aef ckpt 2350: tests: SharpVetoTest, PresetsTest, AtBetTest, BetLedgerTest green (33); reco
  3bc48fc2 ckpt 2349: old tests moved to SharpMode (CONFIRM/OFF/VETO), UI test for the mode chips
  c0226e81 ckpt 2348: AW5 code: BetLedger (JSON line per bet + 13 splits), Diagnostics splits + EV
  a9c8bd2a ckpt 2347: AW4 code: AtBet (as placed) on TrackedBet + BetTarget; recorded by auto-bet,
  040f47d7 ckpt 2346: AW3 UI: Presets tab (built-ins Volume + safe CLV / Strict CLV, save/apply/de
  9529aaa4 ckpt 2345: AW2 code: SharpVeto (ranking by bet kind and sport), SharpMode OFF/VETO/CONF
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
