# CHECKPOINT 3807 — read me first, then TASKS.md

**Written:** 2026-10-20T04:49:05Z · **tests:** all 6 fast checks green
**Branch:** `claude/kalshi-vigilant-integration-v5u4ho` · **builds on:** `cd79c234c` (this checkpoint is the commit after it)

## Just done
pre-release: v1.0.12: Kalshi beside Novig: a Kalshi tab with its own +EV list, bets, auto-bet, bids, sure-profit arbitrage between the two venues, venue switches (Novig / Kalshi / both), and a GitHub engine Kalshi lane (off by default) (versionCode 352, v1.0.12)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.12), then run: bash tools/record-release.sh v1.0.12 352 "v1.0.12: Kalshi beside Novig: a Kalshi tab with its own +EV list, bets, auto-bet, bids, sure-profit arbitrage between the two venues, venue switches (Novig / Kalshi / both), and a GitHub engine Kalshi lane (off by default)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  cd79c234c ckpt 3806: pre-ship: v1.0.12: Kalshi beside Novig: a Kalshi tab with its own +EV list,
  43f18fd32 ckpt 3805: KV9 phone side: GitHub engine card, kalshi.json push, status poll; lane hon
  ea4ae6d69 ckpt 3804: KV9 engine lane: CloudKalshiLane + models + CloudMain wiring (additive) + t
  4764eb008 ckpt 3797: AS tasks written: API status lights lie (pinnacle feed id shared by PinnWir
  0ec1b5fac ckpt 3803: KV10 docs: CLAUDE.md section, research note; KalshiMirror (APIs from Novig,
  8196c29fb ckpt 3802: Kalshi bids in the hub + Bids page
  d18e71c32 ckpt 3801: KV4: KalshiAsNovig adapter + KalshiBidRunner on the unchanged MakerDesk; te
  fd0eefdb9 ckpt 3800: KV6/7: Kalshi tab (EV, arb, bets, auto, setup), venue switches, bet sheet; 
  2852455ed ckpt 3799: KV6 KalshiHub (scan, bets, arb, venues) compiles
  390e15c1d ckpt 3798: KV8 settler + tests
```
