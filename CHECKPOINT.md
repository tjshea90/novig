# CHECKPOINT 4169 — read me first, then TASKS.md

**Written:** 2026-10-25T06:45:03Z · **tests:** all 9 fast checks green
**Branch:** `ccr-cfd54eb0-3h0vnn` · **builds on:** `3de70165a` (this checkpoint is the commit after it)

## Just done
NR1 part: Novig /v3/portfolio/pnl read by engine every 5 min (status walletPositionsValue/Cost, Engine Money line; PortfolioPnlTest); PropLine hot only while its stream is live (ReferenceSource.hotNow; HotRefreshTest)

## Do this next
NOVIG_API.md §30 (per-subaccount throttles, pnl); then Kalshi/Polymarket/SX/ParlayAPI doc diffs

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  b377194be ckpt 4168: PSL docs + TASKS ticks; PSL7 accuracy update written
  256ec116f ckpt 4167: PSL app: PropLine socket switch (ApiEntry, Live tab), Engine > APIs freshne
  e47ea5ebe ckpt 4166: PSL2-5 engine side: Streaming Lite pacing (25/s, 10 wide), 4 sharp books ad
  f44501da6 ckpt 4165: PSL1 docs saved + findings file research/PROPLINE_STREAMING_2026-10-25.md
  9de3cf282 ckpt 4164: PSL: Tj's PropLine Streaming Lite + API re-review request written into TASK
  5a31ee026 ckpt 4163: SG done: v1.0.80 released and recorded; cap $3 live; engine reset
  82290926f ckpt 4162: SG shipped as v1.0.80 (ship.sh green, on main); release.yml triggered; live
  ccc6f8047 ckpt 4161: pre-release: SG: one money limit on similar bets (same game total at any li
  4b597670e ckpt 4160: SG1-SG4 code: SimilarBets key, RepeatGuard who/cap/room/heldShort, engine t
  f8f246246 ckpt 4159: SG: Tj's similar-bets cap request written into TASKS.md (root cause found: 
```

(9 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
