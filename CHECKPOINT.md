# CHECKPOINT 3517 — read me first, then TASKS.md

**Written:** 2026-10-17T03:07:24Z · **tests:** all 5 fast checks green
**Branch:** `ccr-b02146f9-09npa9` · **builds on:** `65a94fe29` (this checkpoint is the commit after it)

## Just done
TCL70: chat_wake.py + chat-wake.yml installed on cloud-config, CHAT_WAKE.md, CLAUDE.md rule, routine prompt

## Do this next
ask Tj for the 3 routine API triggers+secrets; check engine bolt fetch; lease: stale interactive lease fix; ship

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M CLAUDE.md
     M research/coordination/ROUTINE_PROMPT.txt
    ?? research/coordination/CHAT_WAKE.md

## Last ten checkpoints
```
  1ce63dca6 ckpt 3516: TCL71a/b code edits (bolt socket forced on engine, PropLine shares), TASKS 
  8f2161d07 ckpt 3515: WATCH 19:20 slot: engine healthy, no changes
  73c9c0e49 ckpt 3514: TCL69 written (origin column)
  efc7bb2b7 ckpt 3513: TCL68 handoff state written
  cd9abe048 ckpt 3512: TCL68: timing study script + propline.yml timing task
  9a62895d3 ckpt 3511: engine restarted onto partial-pass re-read; CHANGES entry
  b668a4384 ckpt 3510: engine: mid-scan trading passes re-read stale Novig books (13,039 'no Novig
  03deb4185 ckpt 3509: v0.114.13 shipped
  b8beaf872 ckpt 3508: pre-release: PropLine is now a grading source (box scores, scores) in the a
  70b472c6f ckpt 3507: TCL67b: PropLineScores verified on real shapes
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
