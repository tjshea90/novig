# CHECKPOINT 2945 — read me first, then TASKS.md

**Written:** 2026-10-11T06:33:59Z · **tests:** all 4 fast checks green
**Branch:** `claude/unabated-oddsapi-integration-1u6kdc` · **builds on:** `8eb3a4aa` (this checkpoint is the commit after it)

## Just done
SGO sample: tennis block first, NFL cut to 300 KB (the 600 KB file cap cut the tennis off); SGO lists tennis leagues ATP,WTA with 50 markets for 2 matches

## Do this next
ship v0.89.13; Tj re-sends the SGO sample; wire SGO tennis from it

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/build.gradle.kts
     M data/src/main/kotlin/com/tjshea/vigilant/data/reference/SgoKeyTest.kt

## Last ten checkpoints
```
  662a4b23 ckpt 2944: pre-release: v0.89.12: SGO Test sample also reads SGO's tennis leagues and t
  70328333 ckpt 2943: SGO Test sample now probes tennis leagues + 2 matches (TG3 prep); v0.89.12
  029b4106 ckpt 2942: pre-release: v0.89.11: with tennis picked, every feed that carries it (Pinna
  6ecb95b8 ckpt 2941: TG: tennis picked -> Pinnacle/Kalshi/ParlayAPI asked for ATP/WTA even when t
  ad3b27e5 ckpt 2940: pre-release: v0.89.10: OddsPapi Free lists a league's games in one request i
  6eed1860 ckpt 2939: TE4: OddsPapi Free lists a league's games with one request (was one per 12 h
  b912b71c ckpt 2938: pre-release: v0.89.9: live sniper, one switch for taker, tail bettor, cover 
  2bed34f6 ckpt 2937: TF: Live sniper (LiveSniper = autopilot + cover taker + slider-level bids), 
  23d442f3 ckpt 2936: pre-release: v0.89.8: OddsPapi Free no longer errors on empty fixture window
  08a2cec0 ckpt 2935: TE/TD: OddsPapi Free 404 FIXTURE_NOT_FOUND = empty window/no odds (not a fai
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
