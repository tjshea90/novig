# CHECKPOINT 2960 — read me first, then TASKS.md

**Written:** 2026-10-11T14:13:00Z · **tests:** all 4 fast checks green
**Branch:** `claude/vigilant-scanner-enhancements-q97yhz` · **builds on:** `3bdaccaab` (this checkpoint is the commit after it)

## Just done
pre-release: v0.89.17: 1-5 star bet ratings on both scanners with an auto-bet minimum rating; wallet under 20 cents puts the whole app to sleep (versionCode 202, v0.89.17)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.89.17), then run: bash tools/record-release.sh v0.89.17 202 "v0.89.17: 1-5 star bet ratings on both scanners with an auto-bet minimum rating; wallet under 20 cents puts the whole app to sleep"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  3bdaccaab ckpt 2959: pre-ship: v0.89.17: 1-5 star bet ratings on both scanners with an auto-bet 
  3064b5783 ckpt 2958: wrote TI tasks (star ratings)
  dedfb335f ckpt 2957: v0.89.16 shipped (BookCompany researched, RESEARCH §130); release.yml trig
  00be0f13a ckpt 2956: pre-release: v0.89.16: parent-company table researched from SGO and ParlayA
  3d5152d4e ckpt 2955: pre-ship: v0.89.16: parent-company table researched from SGO and ParlayAPI 
  bf0a24e57 ckpt 2954: v0.89.15 released and recorded; TH1-TH8 done
  f3cb85db8 ckpt 2953: TH done & shipped v0.89.15 (code 200); release.yml triggered on main
  3444ca043 ckpt 2952: pre-release: v0.89.15: Vigilant scanner: N-of-M books agree, sharp-book sta
  b5d7378fc ckpt 2951: pre-ship: v0.89.15: Vigilant scanner: N-of-M books agree, sharp-book star, 
  bb03e81bd ckpt 2950: TH1-7 code written: BookCompany+BadOdds in FairValue, Agreement sharp star,
```
