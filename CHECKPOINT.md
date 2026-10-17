# CHECKPOINT 3563 — read me first, then TASKS.md

**Written:** 2026-10-17T17:49:53Z · **tests:** all 5 fast checks green
**Branch:** `claude/keen-keller-8fzr9r` · **builds on:** `6af8bdb88` (this checkpoint is the commit after it)

## Just done
pre-release: v0.114.21: PropLine +EV: the Bet button sits on every card (opens the quick bet slip), the engine's first sweep after a restart waits for its first scan so lines get Bet slips (versionCode 317, v0.114.21)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.114.21), then run: bash tools/record-release.sh v0.114.21 317 "v0.114.21: PropLine +EV: the Bet button sits on every card (opens the quick bet slip), the engine's first sweep after a restart waits for its first scan so lines get Bet slips"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  6af8bdb88 ckpt 3562: pre-ship: v0.114.21: PropLine +EV: the Bet button sits on every card (opens
  cbf33e26d ckpt 3561: v0.114.20 released and recorded; engine restarted with TCL89 engine side an
  e66cc9cf1 ckpt 3560: TCL91a: account-wide per-game exposure for the engine's game cap and same-t
  93fea8fc7 ckpt 3559: pre-release: v0.114.20: PropLine +EV: pull down runs a PropLine-only sweep 
  3b35ef708 ckpt 3558: pre-release: v0.114.20: pull down on the PropLine +EV screen runs a PropLin
  be3147158 ckpt 3557: pre-ship: v0.114.20: pull down on the PropLine +EV screen runs a PropLine-o
  2187ac2f6 ckpt 3556: v0.114.19 released; chat handover
  8510ce22c ckpt 3555: pre-release: v0.114.19: Bet button on every PropLine +EV row (lines Vigilan
  078e3da9d ckpt 3554: pre-ship: v0.114.19: Bet button on every PropLine +EV row (lines Vigilant n
  cec23e7a9 ckpt 3553: TCL88a: Bet button on PropLine +EV rows Vigilant never priced (PropLine fai
```
