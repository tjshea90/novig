# CHECKPOINT 2952 — read me first, then TASKS.md

**Written:** 2026-10-11T13:38:40Z · **tests:** all 4 fast checks green
**Branch:** `claude/vigilant-scanner-enhancements-q97yhz` · **builds on:** `b5d7378fc` (this checkpoint is the commit after it)

## Just done
pre-release: v0.89.15: Vigilant scanner: N-of-M books agree, sharp-book star, one book per company, wrong-looking odds left out, liquidity/EV auto-removal, both sides in the book table, auto-bet also bets Vigilant's scans (versionCode 200, v0.89.15)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.89.15), then run: bash tools/record-release.sh v0.89.15 200 "v0.89.15: Vigilant scanner: N-of-M books agree, sharp-book star, one book per company, wrong-looking odds left out, liquidity/EV auto-removal, both sides in the book table, auto-bet also bets Vigilant's scans"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  b5d7378fc ckpt 2951: pre-ship: v0.89.15: Vigilant scanner: N-of-M books agree, sharp-book star, 
  bb03e81bd ckpt 2950: TH1-7 code written: BookCompany+BadOdds in FairValue, Agreement sharp star,
  1374a9883 ckpt 2949: wrote TH tasks (7 Vigilant scanner asks)
  1191ee05c ckpt 2948: pre-release: v0.89.14: SportsGameOdds prices tennis (ATP and WTA match winn
  ea2eb9bc4 ckpt 2947: TG3a: SGO tennis (ATP/WTA) read from the real sample: match winner = sets M
  7fca39921 ckpt 2946: pre-release: v0.89.13: SGO sample puts the tennis answers first so the file
  36bb4d575 ckpt 2945: SGO sample: tennis block first, NFL cut to 300 KB (the 600 KB file cap cut 
  662a4b23f ckpt 2944: pre-release: v0.89.12: SGO Test sample also reads SGO's tennis leagues and 
  70328333c ckpt 2943: SGO Test sample now probes tennis leagues + 2 matches (TG3 prep); v0.89.12
  029b4106b ckpt 2942: pre-release: v0.89.11: with tennis picked, every feed that carries it (Pinn
```
