# CHECKPOINT 2178 — read me first, then TASKS.md

**Written:** 2026-09-30T06:31:09Z · **tests:** all 3 fast checks green
**Branch:** `ccr-01f0c1f9-xy73ag` · **builds on:** `9148755c` (this checkpoint is the commit after it)

## Just done
pre-release: v0.30.0: ParlayAPI: injury tags on prop bets; credits-a-day chart; Pinnacle line moves (free) on Games + team-bet notes; Second opinion (/verdict, 5 cr, tap only) in bet sheets; ParlayAPI's picks at Novig re-priced at Novig's book (+EV tab, 10 cr/league, tap only); 1st-half lines (football/basketball) and MLB first-5 lines (2 cr, only where Novig lists them) (versionCode 58, v0.30.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.30.0), then run: bash tools/record-release.sh v0.30.0 58 "v0.30.0: ParlayAPI: injury tags on prop bets; credits-a-day chart; Pinnacle line moves (free) on Games + team-bet notes; Second opinion (/verdict, 5 cr, tap only) in bet sheets; ParlayAPI's picks at Novig re-priced at Novig's book (+EV tab, 10 cr/league, tap only); 1st-half lines (football/basketball) and MLB first-5 lines (2 cr, only where Novig lists them)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  a6e3eb00 ckpt 2177: v0.30.0 (code 58) gated by ship.sh (1168 tests green) and pushed; M1-M5, M7 
  0d90eeca ckpt 2176: pre-release: v0.30.0: ParlayAPI features: injury tags on prop bets (ESPN via
  bfec9e2b ckpt 2175: M7 done (football+basketball): ParlayAPI 1st-half lines as source parlay_1h 
  1ecf0021 ckpt 2174: M5 done: ParlayAPI's picks at Novig on the +EV tab (tap-only best-bets, re-p
  ec0c2790 ckpt 2173: M5 data: ParlayBestBets (/best-bets?books=novig 10 cr/league, tap only, bet 
  4906819b ckpt 2172: M4 done: Second opinion (ParlayAPI /verdict, 5 cr, tap only) in +EV, CNO and
  52770233 ckpt 2171: M3 done: Pinnacle line moves (ParlayMovers public/free, Games-tab card, towa
  5a128378 ckpt 2170: M2 done: ParlayAPI credits-a-day chart + top endpoints under its meter (/v1/
  7330ce20 ckpt 2169: M1 done: injury tags (InjuryIndex from /props free + /injuries 1 cr/10 min f
  73e819e2 ckpt 2168: M1 data: Injury/InjuryIndex/ParlayInjuries (/props rows' injury free, /injur
```

(16 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
