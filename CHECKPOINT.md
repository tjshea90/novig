# CHECKPOINT 2175 — read me first, then TASKS.md

**Written:** 2026-09-30T06:15:30Z · **tests:** all 3 fast checks green
**Branch:** `ccr-01f0c1f9-xy73ag` · **builds on:** `e3fed2cd` (this checkpoint is the commit after it)

## Just done
M7 done (football+basketball): ParlayAPI 1st-half lines as source parlay_1h (2 cr, only where Novig lists 1H), registered in SOURCE_ORDER/enabledSources; ParlayPeriodsTest 3 green. MLB/NHL + M6 need Tj's key (sandbox is fake)

## Do this next
M8: sweep (bugs/UI/efficiency) over M1-M7 diff, Diagnostics lines for the new ParlayAPI parts, full floor (bash tools/test.sh), then ship v0.30.0 (bash ship.sh), release.yml, record-release, Release link; M6 + MLB/NHL 1H stay open pending a key

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M PARLAY_API.md
     M TASKS.md

## Last ten checkpoints
```
  1ecf0021 ckpt 2174: M5 done: ParlayAPI's picks at Novig on the +EV tab (tap-only best-bets, re-p
  ec0c2790 ckpt 2173: M5 data: ParlayBestBets (/best-bets?books=novig 10 cr/league, tap only, bet 
  4906819b ckpt 2172: M4 done: Second opinion (ParlayAPI /verdict, 5 cr, tap only) in +EV, CNO and
  52770233 ckpt 2171: M3 done: Pinnacle line moves (ParlayMovers public/free, Games-tab card, towa
  5a128378 ckpt 2170: M2 done: ParlayAPI credits-a-day chart + top endpoints under its meter (/v1/
  7330ce20 ckpt 2169: M1 done: injury tags (InjuryIndex from /props free + /injuries 1 cr/10 min f
  73e819e2 ckpt 2168: M1 data: Injury/InjuryIndex/ParlayInjuries (/props rows' injury free, /injur
  7742a805 ckpt 2167: Handoff for Tj's next session: PARLAY_API.md (permanent ParlayAPI memory + �
  36843185 ckpt 2166: v0.29.0 (code 57) released and recorded; I3/J3/K3 ticked
  597ed3f4 ckpt 2165: v0.29.0 gated and pushed (1117 tests green); I1,I2,I4-I6 ticked
```

(7 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
