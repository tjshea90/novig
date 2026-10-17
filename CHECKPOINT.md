# CHECKPOINT 3559 — read me first, then TASKS.md

**Written:** 2026-10-17T17:30:47Z · **tests:** all 5 fast checks green
**Branch:** `claude/keen-keller-8fzr9r` · **builds on:** `3bf749d7f` (this checkpoint is the commit after it)

## Just done
pre-release: v0.114.20: PropLine +EV: pull down runs a PropLine-only sweep now; the Bet button opens the quick bet slip with the Kelly stake like the Vigilant feed (a tap on the result still opens the big data view) (versionCode 316, v0.114.20)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.114.20), then run: bash tools/record-release.sh v0.114.20 316 "v0.114.20: PropLine +EV: pull down runs a PropLine-only sweep now; the Bet button opens the quick bet slip with the Kelly stake like the Vigilant feed (a tap on the result still opens the big data view)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  3b35ef708 ckpt 3558: pre-release: v0.114.20: pull down on the PropLine +EV screen runs a PropLin
  be3147158 ckpt 3557: pre-ship: v0.114.20: pull down on the PropLine +EV screen runs a PropLine-o
  2187ac2f6 ckpt 3556: v0.114.19 released; chat handover
  8510ce22c ckpt 3555: pre-release: v0.114.19: Bet button on every PropLine +EV row (lines Vigilan
  078e3da9d ckpt 3554: pre-ship: v0.114.19: Bet button on every PropLine +EV row (lines Vigilant n
  cec23e7a9 ckpt 3553: TCL88a: Bet button on PropLine +EV rows Vigilant never priced (PropLine fai
  2a364e11c ckpt 3552: TCL87b decided: no trim while scans are ~4 min
  1fc8ed937 ckpt 3551: TCL87a verified: engine runs >15 min
  8b5a5d11d ckpt 3550: TCL87a2: mid-scan re-read before judging uses requoteListed (recheck waited
  d7eac8f5a ckpt 3549: TCL87a: watchdog false positive fixed (bounded watch steps 60s; log activit
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
