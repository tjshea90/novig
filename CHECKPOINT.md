# CHECKPOINT 591 — read me first, then TASKS.md

**Written:** 2026-09-28T19:20:19Z · **tests:** all 1 fast checks green
**Branch:** `ccr-ed1962c6-kshrww` · **builds on:** `216f4ba` (this checkpoint is the commit after it)

## Just done
pre-release: v0.19.4: Novig prices per scan up to 2,000 (long scans skip lines whose odds would be too old), lines/props per game up to 10/48, props credits up to 192; optional stake in Novig's bet slip (Off/$1/Kelly/my amount); one-tap Open in Novig on every +EV card; any PinnWire failure falls back to pinnapi (versionCode 39, v0.19.4)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.19.4), then run: bash tools/record-release.sh v0.19.4 39 "v0.19.4: Novig prices per scan up to 2,000 (long scans skip lines whose odds would be too old), lines/props per game up to 10/48, props credits up to 192; optional stake in Novig's bet slip (Off/$1/Kelly/my amount); one-tap Open in Novig on every +EV card; any PinnWire failure falls back to pinnapi"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  216f4ba ckpt 590: T7 pre-ship: fixed flaky NovigPublicClientTest 'a refused wave is waited out o
  43f033e ckpt 589: T1-T3: budget up to 2,000 + too-late read guard (no 'No limit', reasons in RES
  0a4c486 ckpt 588: T4 + T6: bet-slip amount setting (Off/$1/Kelly/My amount) on every Novig link 
  d27d061 ckpt 587: T5: PinnWire->pinnapi was built (daily limit); pinned the reset cycle with a t
  7e8359f ckpt 586: T1 measured live: 7-day board 8,620 Novig markets (4,819 spreads/totals, ~2,70
  482486c ckpt 585: Recorded Tj's request (scan budget/no limit, lines+props per game, props credi
  3238762 ckpt 584: Released Vigilant v0.19.3 (code 38): release.yml green, vigilant-v0.19.3.apk o
  7d41bab ckpt 583: pre-release: v0.19.3: key reads 10 at a time (were capped at 4), one Novig ref
  94339f0 ckpt 582: S2/S2a: quote-age limit 10 min on games >3h off, 5 within 3h/live (Freshness.m
  c58c2c8 ckpt 581: S2 measured: 30-min live recording (Kalshi fair 62k pairs, Novig 2.5k): >=1pt 
```
