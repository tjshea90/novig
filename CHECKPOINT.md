# CHECKPOINT 4172 — read me first, then TASKS.md

**Written:** 2026-10-25T06:54:23Z · **tests:** all 9 fast checks green
**Branch:** `ccr-cfd54eb0-3h0vnn` · **builds on:** `8fe49550b` (this checkpoint is the commit after it)

## Just done
pre-release: PropLine Streaming Lite: engine WebSockets (moves, pulls, scratches, heartbeat freshness), 4 sharp books, per-book freshness on Engine > APIs, Novig marked PnL, Polymarket V2 id fix (versionCode 421, v1.0.81)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.81), then run: bash tools/record-release.sh v1.0.81 421 "PropLine Streaming Lite: engine WebSockets (moves, pulls, scratches, heartbeat freshness), 4 sharp books, per-book freshness on Engine > APIs, Novig marked PnL, Polymarket V2 id fix"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  8fe49550b ckpt 4171: pre-ship: PropLine Streaming Lite: engine WebSockets (moves, pulls, scratch
  7e05ea878 ckpt 4170: NR1/PR1/FR1 done (api docs mirror run, Polymarket version fix, Novig per-su
  9333357ba ckpt 4169: NR1 part: Novig /v3/portfolio/pnl read by engine every 5 min (status wallet
  b377194be ckpt 4168: PSL docs + TASKS ticks; PSL7 accuracy update written
  256ec116f ckpt 4167: PSL app: PropLine socket switch (ApiEntry, Live tab), Engine > APIs freshne
  e47ea5ebe ckpt 4166: PSL2-5 engine side: Streaming Lite pacing (25/s, 10 wide), 4 sharp books ad
  f44501da6 ckpt 4165: PSL1 docs saved + findings file research/PROPLINE_STREAMING_2026-10-25.md
  9de3cf282 ckpt 4164: PSL: Tj's PropLine Streaming Lite + API re-review request written into TASK
  5a31ee026 ckpt 4163: SG done: v1.0.80 released and recorded; cap $3 live; engine reset
  82290926f ckpt 4162: SG shipped as v1.0.80 (ship.sh green, on main); release.yml triggered; live
```
