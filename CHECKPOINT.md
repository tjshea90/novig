# CHECKPOINT 4075 — read me first, then TASKS.md

**Written:** 2026-10-23T23:45:46Z · **tests:** all 9 fast checks green
**Branch:** `ccr-f7c47823-ez8xwu` · **builds on:** `e9e72fb9a` (this checkpoint is the commit after it)

## Just done
FX2 engine bugs from the live log: (1) Kalshi bid cancels sent by id alone went to shard 0, 404'd and fell to the retired route ('Please switch to the V2 endpoints'): cancel now carries the market ticker (remembered by KalshiAsNovig, looked up otherwise), no legacy fallback; Kalshi 429s no longer say 'Novig asked us to slow down'; (2) Kalshi lane re-reads +EV lines before judging (97% were 'no Kalshi price read in the last minute' after a 2-min scan). Tests fail on old code.

## Do this next
Pinnacle source silent 73 scans (error text says Pinnodds); BoltOdds Free key; bid churn; then land + engine reset

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  72184f30c ckpt 4074: FX2: StudyExport played-days-first (test fails on old code), Review day bud
  b8f282792 ckpt 4073: FX1 floor green (4,192 tests, exit 0), 25 py selftests ok, 134 screenshots 
  8c5eba4e6 ckpt 4072: FX: full-test request written into TASKS.md (FX1-FX5), claimed
  1bb4f757f ckpt 4071: ANALYST 2026-10-23: no rule change
  3e0dff430 ckpt 4070: pre-release: ST2: stars/soonest sort works on the merged Novig+Kalshi list 
  454574295 ckpt 4069: ST2: feed sort fix + CNO sort buttons
  08b8813b1 ckpt 4068: v1.0.64 released: single star rating; cross-check on propline-clv written
  ebfe78b7e ckpt 4067: pre-release: ST1: one combined star rating (type of bet, lead time, small-b
  1068f47a7 ckpt 4066: ST1: single star rating (type+lead points, small-book cap), study written, 
  98fa56d7f ckpt 4065: ST1: history folded into the one BetRating (typePoints), history line remov
```

(9 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
