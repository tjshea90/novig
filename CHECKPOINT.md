# CHECKPOINT 2177 — read me first, then TASKS.md

**Written:** 2026-09-30T06:22:29Z · **tests:** all 3 fast checks green
**Branch:** `ccr-01f0c1f9-xy73ag` · **builds on:** `0d90eeca` (this checkpoint is the commit after it)

## Just done
v0.30.0 (code 58) gated by ship.sh (1168 tests green) and pushed; M1-M5, M7 built; Diagnostics line for ParlayAPI extras; M9 added (key-gated probes: line-movement, MLB/NHL 1H, verdict canonical key)

## Do this next
Wait for CI run 36677835675 on main green, trigger release.yml on main, confirm v0.30.0 Release, bash tools/record-release.sh v0.30.0 58 '<note>', tick M8, send Tj the Release link and the plain answer; M6/M9 need Tj's key

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  0d90eeca ckpt 2176: pre-release: v0.30.0: ParlayAPI features: injury tags on prop bets (ESPN via
  bfec9e2b ckpt 2175: M7 done (football+basketball): ParlayAPI 1st-half lines as source parlay_1h 
  1ecf0021 ckpt 2174: M5 done: ParlayAPI's picks at Novig on the +EV tab (tap-only best-bets, re-p
  ec0c2790 ckpt 2173: M5 data: ParlayBestBets (/best-bets?books=novig 10 cr/league, tap only, bet 
  4906819b ckpt 2172: M4 done: Second opinion (ParlayAPI /verdict, 5 cr, tap only) in +EV, CNO and
  52770233 ckpt 2171: M3 done: Pinnacle line moves (ParlayMovers public/free, Games-tab card, towa
  5a128378 ckpt 2170: M2 done: ParlayAPI credits-a-day chart + top endpoints under its meter (/v1/
  7330ce20 ckpt 2169: M1 done: injury tags (InjuryIndex from /props free + /injuries 1 cr/10 min f
  73e819e2 ckpt 2168: M1 data: Injury/InjuryIndex/ParlayInjuries (/props rows' injury free, /injur
  7742a805 ckpt 2167: Handoff for Tj's next session: PARLAY_API.md (permanent ParlayAPI memory + �
```
