# CHECKPOINT 2511 — read me first, then TASKS.md

**Written:** 2026-10-04T03:19:17Z · **tests:** all 3 fast checks green
**Branch:** `ccr-e34b35d5-c6t9c8` · **builds on:** `97b45020` (this checkpoint is the commit after it)

## Just done
BX2 done in data: WSU close bug confirmed (0.5 matcher bar + latest row not best game; reproduced 0.258 failing-first, same as file's 0.269): TeamMatcher.gameScore/whichOf, ParlayCloses best-game+side assignment, ParlayBooks/OtherBooks same bar, ClosePlausibility backstop in CloseBackfill (bars from Tj's 230 closes); data 1,133 green. Arkansas State close looks legit (3.3 pts = Pinnacle vs CNO conservative devig), only WSU was wrong

## Do this next
BX3: study export log-only fixes (READ ME: CNO filters next to rules line, multi-devig close, close source, per-look fields, MISMATCH rows), BX4 independent closes, then mutants + floor + ship; BW workflow results pending

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  1a67a1d6 ckpt 2510: BX: Tj sent an outside analysis of the first scan-study export (v0.58.3, 622
  f39e0e5a ckpt 2509: BW: wrote Tj's per-game exposure request into TASKS.md (BW1-BW5)
  df2c9ade ckpt 2508: v0.58.3 released + recorded (release.yml run 37170946701 green, tag v0.58.3)
  d0334f7d ckpt 2507: pre-release: v0.58.3: bids are kept within the wallet and the day's limit (a
  c80df9fc ckpt 2506: pre-ship: v0.58.3: bids are kept within the wallet and the day's limit (a be
  baa4035c ckpt 2505: BV built: RateGate.takeLowRate, ReadPace on BookBatch (public/key start/low/
  d37ac654 ckpt 2504: BU app tests green (MakerAppTest + WalletStripTest 23 passed). Tj sent diagn
  34f91bf4 ckpt 2503: BU mutants 11/11 killed (M4/M6 needed stronger tests); app layer written: Ma
  ca3a709e ckpt 2502: BU2 done in data: MakerPlan trim (budget<0 takes least valuable bids down: h
  a9cbe2b6 ckpt 2501: BU: wrote Tj's wallet-vs-open-bids request into TASKS.md; cause confirmed in
```

(9 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
