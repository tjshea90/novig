# CHECKPOINT 2172 — read me first, then TASKS.md

**Written:** 2026-09-30T05:59:21Z · **tests:** all 3 fast checks green
**Branch:** `ccr-01f0c1f9-xy73ag` · **builds on:** `7fbe6314` (this checkpoint is the commit after it)

## Just done
M4 done: Second opinion (ParlayAPI /verdict, 5 cr, tap only) in +EV, CNO and Tracker sheets. ParlayVerdictTest 4 + SecondOpinionTest 6 green; PARLAY_API.md §6.4 updated (canonical keys, free /try/verdict noted)

## Do this next
M5: /best-bets?books=novig (10 cr/league, props only) as a third scanner, tap/pull only; parse bet text; re-price each play from Novig's own book before showing; edge_alerts 'verify first'; ✓/✕/Open/Tracker like CNO. Tests on parlay-best-bets-*.json

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M PARLAY_API.md
     M TASKS.md
     M app/src/test/kotlin/com/tjshea/vigilant/app/SecondOpinionTest.kt

## Last ten checkpoints
```
  52770233 ckpt 2171: M3 done: Pinnacle line moves (ParlayMovers public/free, Games-tab card, towa
  5a128378 ckpt 2170: M2 done: ParlayAPI credits-a-day chart + top endpoints under its meter (/v1/
  7330ce20 ckpt 2169: M1 done: injury tags (InjuryIndex from /props free + /injuries 1 cr/10 min f
  73e819e2 ckpt 2168: M1 data: Injury/InjuryIndex/ParlayInjuries (/props rows' injury free, /injur
  7742a805 ckpt 2167: Handoff for Tj's next session: PARLAY_API.md (permanent ParlayAPI memory + �
  36843185 ckpt 2166: v0.29.0 (code 57) released and recorded; I3/J3/K3 ticked
  597ed3f4 ckpt 2165: v0.29.0 gated and pushed (1117 tests green); I1,I2,I4-I6 ticked
  1c2128d8 ckpt 2164: pre-release: v0.29.0: ParlayAPI matched to its docs and Tj's real key: the m
  64fb7853 ckpt 2163: pre-ship: v0.29.0: ParlayAPI matched to its docs and Tj's real key: the mete
  8d5bb257 ckpt 2162: RESEARCH §45 written (real-key findings, docs pass, backup, features to off
```

(10 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
