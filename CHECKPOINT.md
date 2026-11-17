# CHECKPOINT 4606 — read me first, then TASKS.md

**Written:** 2026-11-17T19:12:46Z · **tests:** all 10 fast checks green
**Branch:** `claude/optimistic-curie-ewoq4d` · **builds on:** `d35d16356` (this checkpoint is the commit after it)

## Just done
pre-release: HB1 hard block (NCAAB exempt), NCAAB scroll memory, bids deploy (versionCode 476, v1.0.136)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.136), then run: bash tools/record-release.sh v1.0.136 476 "HB1 hard block (NCAAB exempt), NCAAB scroll memory, bids deploy"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  d35d16356 ckpt 4605: pre-ship: HB1 hard block (NCAAB exempt), NCAAB scroll memory, bids deploy
  621e790cd ckpt 4604: answered chat: wallet is $67, bid churn found
  726199880 ckpt 4603: ANALYST 11-17 slot1 (acct-27a37e): digest unchanged, skip rule, no change
  f88059253 ckpt 4602: chat: restored bid/bet settings overwritten by 2nd device; engine_set json:
  690cc28d0 ckpt 4601: chat run: v1.0.135 shipped (TR2 stable wallet read, PF11 feed memo)
  31ee61d62 ckpt 4600: pre-release: TR2 stable all-time profit reading, PF11 +EV tab no double reb
  e9cabde5b ckpt 4599: pre-ship: TR2 stable all-time profit reading, PF11 +EV tab no double rebuil
  05bb211bb ckpt 4598: TR2 wallet stable read, PF11 feed memo; running ship
  36e17a379 ckpt 4597: AU1 v1.0.134 shipped
  bc13c1406 ckpt 4596: pre-release: AU1: faster start (no MB parses on main thread), no large-file
```
