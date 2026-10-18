# CHECKPOINT 3635 — read me first, then TASKS.md

**Written:** 2026-10-18T07:13:11Z · **tests:** all 5 fast checks green
**Branch:** `ccr-5a7eb2e9-bn8drh` · **builds on:** `a23bd93f7` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.117.0: Claude sessions may change any setting/switch except STOP ALL and dead-man; the phone adopts settings a session writes to GitHub

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  fdb52658c ckpt 3634: TCL113 built: compile + unit test green
  c1d4a7bf7 ckpt 3633: TCL113: engine_set.py, CloudClaudeConfig + phone adopt, rules written; test
  bbcde0fd1 ckpt 3632: chat: TCL112 recorded + relayed
  43fa9cb30 ckpt 3631: chat: diagnosed bids not filling wallet (per-game 5, kelly ceiling 13); clo
  c4732629b ckpt 3630: TCL111f1 step 1: SX live frame recorder tap on engine
  04291e4c2 ckpt 3629: TCL111f1: confirmed SX pregame works on engine; live-lane plan written
  6869e93c3 ckpt 3628: SX Bet key accepted, engine has sxbet source
  db2985375 ckpt 3627: pre-release: v0.116.0: SX Bet: sharp exchange odds source (REST + WebSocket
  a7612715d ckpt 3626: TCL111e: SX Bet websocket (SxBetStream), socket switch, taker-frame convers
  a089a15ab ckpt 3625: TCL111b: SX Bet wired in (key slot, switch, engine secret, scan order, shar
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
