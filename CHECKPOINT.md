# CHECKPOINT 2956 — read me first, then TASKS.md

**Written:** 2026-10-11T13:54:15Z · **tests:** all 4 fast checks green
**Branch:** `claude/vigilant-scanner-enhancements-q97yhz` · **builds on:** `3d5152d4e` (this checkpoint is the commit after it)

## Just done
pre-release: v0.89.16: parent-company table researched from SGO and ParlayAPI book lists (Kambi desk, Mr Green, Eurobet, Betway; unsupported merges removed) (versionCode 201, v0.89.16)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.89.16), then run: bash tools/record-release.sh v0.89.16 201 "v0.89.16: parent-company table researched from SGO and ParlayAPI book lists (Kambi desk, Mr Green, Eurobet, Betway; unsupported merges removed)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  3d5152d4e ckpt 2955: pre-ship: v0.89.16: parent-company table researched from SGO and ParlayAPI 
  bf0a24e57 ckpt 2954: v0.89.15 released and recorded; TH1-TH8 done
  f3cb85db8 ckpt 2953: TH done & shipped v0.89.15 (code 200); release.yml triggered on main
  3444ca043 ckpt 2952: pre-release: v0.89.15: Vigilant scanner: N-of-M books agree, sharp-book sta
  b5d7378fc ckpt 2951: pre-ship: v0.89.15: Vigilant scanner: N-of-M books agree, sharp-book star, 
  bb03e81bd ckpt 2950: TH1-7 code written: BookCompany+BadOdds in FairValue, Agreement sharp star,
  1374a9883 ckpt 2949: wrote TH tasks (7 Vigilant scanner asks)
  1191ee05c ckpt 2948: pre-release: v0.89.14: SportsGameOdds prices tennis (ATP and WTA match winn
  ea2eb9bc4 ckpt 2947: TG3a: SGO tennis (ATP/WTA) read from the real sample: match winner = sets M
  7fca39921 ckpt 2946: pre-release: v0.89.13: SGO sample puts the tennis answers first so the file
```
