# CHECKPOINT 2862 — read me first, then TASKS.md

**Written:** 2026-10-10T14:35:30Z · **tests:** all 4 fast checks green
**Branch:** `ccr-3fba2f23-lerg26` · **builds on:** `1513237f` (this checkpoint is the commit after it)

## Just done
pre-release: v0.86.0: cleanup. Diagnostics reset button, automatic 2-day retention and a 350 KB file cap, bid store pruned (was 15 MB rewritten whole), Games tab and Locked in card removed, auto-bet finds bets through CNO's own link, scan study clear button, settings tidied (versionCode 169, v0.86.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.86.0), then run: bash tools/record-release.sh v0.86.0 169 "v0.86.0: cleanup. Diagnostics reset button, automatic 2-day retention and a 350 KB file cap, bid store pruned (was 15 MB rewritten whole), Games tab and Locked in card removed, auto-bet finds bets through CNO's own link, scan study clear button, settings tidied"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  ff036059 ckpt 2861: TI3: auto-bet/Bet sheet find the bet through CNO's own link outcome (NovigBe
  b5805899 ckpt 2860: TI: Games tab + Locked in card removed, Bids rules text fixed, edges-real re
  5674414e ckpt 2859: TI request written into TASKS.md
  1c48380e ckpt 2858: steward check-in 2026-10-10 13:35Z done by hand; the routine session has no 
  241eda8b ckpt 2857: v0.85.8 released and recorded
  f018273d ckpt 2856: pre-release: v0.85.8: the diagnostics file reads each source with its own de
  0f9f6c05 ckpt 2855: pre-ship: v0.85.8: the diagnostics file reads each source with its own deadl
  5f5e2fdc ckpt 2854: pre-ship: v0.85.8: the diagnostics file reads each source with its own deadl
  df3915a5 ckpt 2853: v0.85.7 released and recorded
  951b7eae ckpt 2852: TG1: tail 18-0 is 3 games of one repeated bet under the explore rules
```

(9 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
