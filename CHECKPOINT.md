# CHECKPOINT 3738 — read me first, then TASKS.md

**Written:** 2026-10-19T07:14:16Z · **tests:** 1 RED: test_coord.sh (5 green)
**Branch:** `ccr-2df6ce48-pkrh5v` · **builds on:** `899fce0a2` (this checkpoint is the commit after it)

## Just done
CR tools live on main (504eafa56); chat + chat-seat branches created by chat.py init (chat-wake.yml deployed on chat); ROUTINE_PROMPT.txt updated; board note posted; CR claimed; chat watcher running and holding the seat

## Do this next
CR5 app chat client: data Chat2 model (store JSON of tools/chat.py) + ChatClient (outbox/retry, attachment-first, per-day poll with ETag, seat) + rebuilt ChatPage; then CR8 ship

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  504eafa56 ckpt 3737: CR3+CR6: new chat tools (chat.py, chat_wake.py v2) and coordination without
  130e92ba9 ckpt 3736: CR1+CR2: diagnosis and design written (research/coordination/CHAT_REBUILD_2
  8606d13d1 ckpt 3735: CR written into TASKS.md (Tj: chat rebuild, one chat account, no lease)
  6679d493a ckpt 3734: merged with session B; tennis+P/L on staked ready
  1ee438c0a ckpt 3730: tennis live ON, P/L on staked, chat one-reply guard
  1f9d326b9 ckpt 3733: fix merged conflict in tell_tj
  675a814e6 ckpt 3732: CH20 tennis live default on, CH18c one-answer guard
  eb56179ba ckpt 3731: tell_tj selftest fixed for stale guard
  5345f85a7 ckpt 3729: chat: 15 min window, old-reply guard
  c010057a5 ckpt 3730: CH18b stale-reply guard
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
