# CHECKPOINT 2173 — read me first, then TASKS.md

**Written:** 2026-09-30T06:03:45Z · **tests:** all 3 fast checks green
**Branch:** `ccr-01f0c1f9-xy73ag` · **builds on:** `9fa19aff` (this checkpoint is the commit after it)

## Just done
M5 data: ParlayBestBets (/best-bets?books=novig 10 cr/league, tap only, bet text parsed, edge alerts' fair from their pp edge), ParlayPlay.row() CNO-shaped for NovigBetFinder/NovigLive re-pricing, ParlayPick.priced, NovigBetFinder.event(row); ParlayBestBetsTest 4 green

## Do this next
M5 app: SOURCE_PARLAY + logCno(source), container parlayBestBets, UiState.parlayPicks, VM scanParlayPicks (read boards -> rows -> betFinder.event starts -> live.readNow -> priced) + recheck (Novig only), +EV tab section with ParlayPickCard (EV at Novig now, listed vs now, verify-first alerts, ✓/✕/Open), Tracker ScannerFilter.PARLAY + labels; UI test

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  4906819b ckpt 2172: M4 done: Second opinion (ParlayAPI /verdict, 5 cr, tap only) in +EV, CNO and
  52770233 ckpt 2171: M3 done: Pinnacle line moves (ParlayMovers public/free, Games-tab card, towa
  5a128378 ckpt 2170: M2 done: ParlayAPI credits-a-day chart + top endpoints under its meter (/v1/
  7330ce20 ckpt 2169: M1 done: injury tags (InjuryIndex from /props free + /injuries 1 cr/10 min f
  73e819e2 ckpt 2168: M1 data: Injury/InjuryIndex/ParlayInjuries (/props rows' injury free, /injur
  7742a805 ckpt 2167: Handoff for Tj's next session: PARLAY_API.md (permanent ParlayAPI memory + �
  36843185 ckpt 2166: v0.29.0 (code 57) released and recorded; I3/J3/K3 ticked
  597ed3f4 ckpt 2165: v0.29.0 gated and pushed (1117 tests green); I1,I2,I4-I6 ticked
  1c2128d8 ckpt 2164: pre-release: v0.29.0: ParlayAPI matched to its docs and Tj's real key: the m
  64fb7853 ckpt 2163: pre-ship: v0.29.0: ParlayAPI matched to its docs and Tj's real key: the mete
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
