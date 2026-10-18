# CHECKPOINT 3627 — read me first, then TASKS.md

**Written:** 2026-10-18T03:58:00Z · **tests:** all 5 fast checks green
**Branch:** `claude/optimistic-curie-b86oug` · **builds on:** `0299e6b38` (this checkpoint is the commit after it)

## Just done
pre-release: v0.116.0: SX Bet: sharp exchange odds source (REST + WebSocket), Settings > SX Bet, engine secret SXBET_API_KEY (versionCode 332, v0.116.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.116.0), then run: bash tools/record-release.sh v0.116.0 332 "v0.116.0: SX Bet: sharp exchange odds source (REST + WebSocket), Settings > SX Bet, engine secret SXBET_API_KEY"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  a7612715d ckpt 3626: TCL111e: SX Bet websocket (SxBetStream), socket switch, taker-frame convers
  a089a15ab ckpt 3625: TCL111b: SX Bet wired in (key slot, switch, engine secret, scan order, shar
  b207f892c ckpt 3624: TCL111: SX Bet docs saved (research/sxbet), SXBET_API.md, SxBetConvert+SxBe
  3f4f4435c ckpt 3623: TCL110: no-lease push policy + relay of typed messages to the lease holder
  8fe87c69a ckpt 3622: added tj outlook name for acct-7542bf (coord.py, tell_tj.py, CLAUDE.md)
  75ec3d20d ckpt 3621: TCL110: acct-5d1e2d shown as (mirage) in coord, chat sender, CLAUDE.md
  8c9989252 ckpt 3620: TCL104d: fixed hidden-bets note height jump in Feed
  2aa6cf8ab ckpt 3619: chat session done
  2fbb0d950 ckpt 3618: v0.115.0 released (TCL109)
  87d310316 ckpt 3617: merged main into TCL109 branch
```
