# CHECKPOINT 2210 — read me first, then TASKS.md

**Written:** 2026-09-30T20:42:28Z · **tests:** all 3 fast checks green
**Branch:** `ccr-01f0c1f9-xy73ag` · **builds on:** `535507e5` (this checkpoint is the commit after it)

## Just done
Fixed a real race in ApiBettingController.placer() (two plans on two threads could see a half-made placer and plan nothing), found by the ship gate; schema asserts 11

## Do this next
V5: ship.sh again, CI green on main, trigger release.yml, confirm, record-release, send link

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/src/test/kotlin/com/tjshea/vigilant/app/ApiBettingControllerTest.kt

## Last ten checkpoints
```
  47c2a353 ckpt 2209: V3 done: Diagnostics health checks (FAIL/WARN/OK with evidence and code), ac
  5743ac38 ckpt 2208: V1 done: CNO has no book choice; ParlayAPI has no more sharp books; PropLine
  0b96ecae ckpt 2207: V2 done: Bet sheet Amount field (any amount up to the limit), opens at the w
  e914c36a ckpt 2206: V4 done: live games out of the Check odds now counter; closes dated by their
  1397fbd7 ckpt 2205: V4 audit: CLV correct (pregame close only); CheckOddsStats counts live bets 
  5a1de17f ckpt 2204: Logged Tj's 19:30Z request as TASKS.md V1-V5 (more books, custom bet amount 
  8d5c8324 ckpt 2203: U1 answered: the Apify bet-clv-tracker adds nothing (a CLV calculator needin
  5045e52e ckpt 2202: Logged Tj's question on the Apify bet-clv-tracker actor as TASKS.md U1
  843a5b65 ckpt 2201: T4: NovigPublicClientTest refused-wave test hardened (one wave until the fir
  145ce11e ckpt 2200: v0.34.0 (code 62) released and recorded: Check odds now refreshes every open
```

(12 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
