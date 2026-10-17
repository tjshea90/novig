# CHECKPOINT 3601 — read me first, then TASKS.md

**Written:** 2026-10-17T21:00:53Z · **tests:** all 5 fast checks green
**Branch:** `claude/wizardly-meitner-h2g4pr` · **builds on:** `ef290d521` (this checkpoint is the commit after it)

## Just done
pre-release: v0.114.32: the bid desk no longer stops a whole pass when Novig locks one game (it posted 3 of 250 wanted bids while cash sat idle); the Feed list no longer jumps when the progress bar comes and goes (versionCode 328, v0.114.32)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.114.32), then run: bash tools/record-release.sh v0.114.32 328 "v0.114.32: the bid desk no longer stops a whole pass when Novig locks one game (it posted 3 of 250 wanted bids while cash sat idle); the Feed list no longer jumps when the progress bar comes and goes"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  ef290d521 ckpt 3600: TCL105 bug-hunt rule written
  b0ee40c7c ckpt 3599: pre-ship: v0.114.32: the bid desk no longer stops a whole pass when Novig l
  7b1e5d9ac ckpt 3598: TCL103: bid desk no longer stops the pass on EVENT_LOCKED
  d37d321e7 ckpt 3597: chat: TCL104 fixed-height progress slot in Feed top bar
  a5ea93910 ckpt 3596: chat: TCL103 bid wallet deployment written
  c7f9ba6da ckpt 3595: chat: TCL102 written; v0.114.31 gating
  03bab4d98 ckpt 3594: pre-release: v0.114.31: the Live engine-trading button lights up whenever t
  080a7c8c9 ckpt 3593: pre-ship: v0.114.31: the Live engine-trading button lights up whenever the 
  52852c20a ckpt 3592: pre-release: v0.114.30: the Live engine-trading button lights up whenever t
  f86c8350a ckpt 3591: pre-ship: v0.114.30: the Live engine-trading button lights up whenever the 
```
