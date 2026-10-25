# CHECKPOINT 4246 — read me first, then TASKS.md

**Written:** 2026-10-25T22:33:13Z · **tests:** all 9 fast checks green
**Branch:** `ccr-e58c5a58-3qyzld` · **builds on:** `f5c8e91a4` (this checkpoint is the commit after it)

## Just done
PX11a: novig-probe.yml + cloud/OrdersProbeMain.kt (Novig's record of rejected/canceled orders, read key, ids left out); NOFILL diagnostic reads the row after it was removed (all nulls): fix next

## Do this next
dispatch novig-probe.yml on main, read why the 4 hand orders at 21:40-41 UTC were rejected

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M cloud/src/main/kotlin/com/tjshea/vigilant/cloud/OrdersProbeMain.kt

## Last ten checkpoints
```
  3081884d7 ckpt 4245: PX11 written into TASKS.md (5 parts)
  4aa23e7c5 ckpt 4244: v1.0.84 released + recorded; phone adopted Pinnodds off (its own copy now s
  cddc690ee ckpt 4243: PX10 findings recorded (Pinnacle census numbers, migration gap); v1.0.84 re
  835ab9fd2 ckpt 4242: pre-release: v1.0.84 PX10: phone keeps a Claude session's settings change i
  a4c29748b ckpt 4241: pre-ship: v1.0.84 PX10: phone keeps a Claude session's settings change it h
  14947206f ckpt 4240: PX10d/e: Pinnacle census in the quote census; ParlayLive booksHeld fixed; p
  2fc4404b5 ckpt 4239: PX10a/c: Pinnodds free logged + switched off; Pinnacle hosts obey their own
  945ee7e29 ckpt 4238: PX10 written into TASKS.md (Tj's 4-part API migration request)
  80d060bf3 ckpt 4237: PX9 resumed and finished for today: PropLine 60-book fix live (run 40234294
  10d1333e9 ckpt 4236: PX9d reverted (thinner fairs on the listed bets); cloud tests green
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
