# CHECKPOINT 4166 — read me first, then TASKS.md

**Written:** 2026-10-25T06:34:27Z · **tests:** 1 RED: test_research_index.sh (8 green)
**Branch:** `ccr-cfd54eb0-3h0vnn` · **builds on:** `a1c717749` (this checkpoint is the commit after it)

## Just done
PSL2-5 engine side: Streaming Lite pacing (25/s, 10 wide), 4 sharp books added (SBOBET, BookMaker, Betfair Ex, theScore), outcome ids, PropLineLive overlay + PropLineStream (2 sockets) + freshness reader, status fields, scratch guard; PropLineLiveTest

## Do this next
app: socket switch for PropLine (ApiEntry, LiveScreen, ApiSocketTogglesTest), freshness card; then NR1/PR1/FR1 reviews

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  f44501da6 ckpt 4165: PSL1 docs saved + findings file research/PROPLINE_STREAMING_2026-10-25.md
  9de3cf282 ckpt 4164: PSL: Tj's PropLine Streaming Lite + API re-review request written into TASK
  5a31ee026 ckpt 4163: SG done: v1.0.80 released and recorded; cap $3 live; engine reset
  82290926f ckpt 4162: SG shipped as v1.0.80 (ship.sh green, on main); release.yml triggered; live
  ccc6f8047 ckpt 4161: pre-release: SG: one money limit on similar bets (same game total at any li
  4b597670e ckpt 4160: SG1-SG4 code: SimilarBets key, RepeatGuard who/cap/room/heldShort, engine t
  f8f246246 ckpt 4159: SG: Tj's similar-bets cap request written into TASKS.md (root cause found: 
  8d80889b4 ckpt 4158: pre-release: All-time profit = winnings on final bets and bids minus their 
  df409822f ckpt 4157: pre-ship: All-time profit = winnings on final bets and bids minus their cos
  9d94fe4b8 ckpt 4156: pre-ship: All-time profit = winnings on final bets and bids minus their cos
```

(45 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
