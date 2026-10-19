# CHECKPOINT 3740 — read me first, then TASKS.md

**Written:** 2026-10-19T07:49:38Z · **tests:** all 6 fast checks green
**Branch:** `ccr-2df6ce48-pkrh5v` · **builds on:** `5639fc1db` (this checkpoint is the commit after it)

## Just done
CR5 code done + reviewed; landing gate fixed (marks the tree taken BEFORE a run, filtered runs mark nothing); grep -q/pipefail SIGPIPE race fixed (test_coord 1-in-3 red, push.sh marker guard, resume.sh, ship.sh); docs CR7 (CLAUDE.md, CHAT.md, CHAT_REBUILD §4); TASKS CR4/CR7/CH16/CH18 ticked; version 1.0.5 code 345

## Do this next
full suite running (scratchpad full.log); when green: bash ship.sh, release.yml, record-release, chat.py say with the link, board note, unclaim CR

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  108d9f54e ckpt 3739: CR5 app chat written: data CloudChat v2 (model/thread/presence) + ChatSync 
  f0c34da40 ckpt 3738: CR tools live on main (504eafa56); chat + chat-seat branches created by cha
  504eafa56 ckpt 3737: CR3+CR6: new chat tools (chat.py, chat_wake.py v2) and coordination without
  130e92ba9 ckpt 3736: CR1+CR2: diagnosis and design written (research/coordination/CHAT_REBUILD_2
  8606d13d1 ckpt 3735: CR written into TASKS.md (Tj: chat rebuild, one chat account, no lease)
  6679d493a ckpt 3734: merged with session B; tennis+P/L on staked ready
  1ee438c0a ckpt 3730: tennis live ON, P/L on staked, chat one-reply guard
  1f9d326b9 ckpt 3733: fix merged conflict in tell_tj
  675a814e6 ckpt 3732: CH20 tennis live default on, CH18c one-answer guard
  eb56179ba ckpt 3731: tell_tj selftest fixed for stale guard
```

(8 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
