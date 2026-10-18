# CHECKPOINT 3633 — read me first, then TASKS.md

**Written:** 2026-10-18T06:59:43Z · **tests:** all 5 fast checks green
**Branch:** `ccr-5a7eb2e9-bn8drh` · **builds on:** `304fefd10` (this checkpoint is the commit after it)

## Just done
TCL113: engine_set.py, CloudClaudeConfig + phone adopt, rules written; tests running

## Do this next
wait for :data:test result, run :app compile, ship.sh, release, then engine_set bidDeployKelly=20

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  bbcde0fd1 ckpt 3632: chat: TCL112 recorded + relayed
  43fa9cb30 ckpt 3631: chat: diagnosed bids not filling wallet (per-game 5, kelly ceiling 13); clo
  c4732629b ckpt 3630: TCL111f1 step 1: SX live frame recorder tap on engine
  04291e4c2 ckpt 3629: TCL111f1: confirmed SX pregame works on engine; live-lane plan written
  6869e93c3 ckpt 3628: SX Bet key accepted, engine has sxbet source
  db2985375 ckpt 3627: pre-release: v0.116.0: SX Bet: sharp exchange odds source (REST + WebSocket
  a7612715d ckpt 3626: TCL111e: SX Bet websocket (SxBetStream), socket switch, taker-frame convers
  a089a15ab ckpt 3625: TCL111b: SX Bet wired in (key slot, switch, engine secret, scan order, shar
  b207f892c ckpt 3624: TCL111: SX Bet docs saved (research/sxbet), SXBET_API.md, SxBetConvert+SxBe
  3f4f4435c ckpt 3623: TCL110: no-lease push policy + relay of typed messages to the lease holder
```

(8 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
