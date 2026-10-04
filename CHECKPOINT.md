# CHECKPOINT 2514 — read me first, then TASKS.md

**Written:** 2026-10-04T03:48:35Z · **tests:** all 3 fast checks green
**Branch:** `ccr-55a7238c-jfgers` · **builds on:** `8446c8bd` (this checkpoint is the commit after it)

## Just done
v0.58.4 released + recorded (release.yml run 37174473407 green, tag v0.58.4, BUILDLOG line written, pushed). Then answered Tj's question (no code change): does large Novig liquidity on a CNO +EV bet mean a sharp is on the other side? Answer = no per RESEARCH.md §62 (big resting size is LP quoting, symmetric, and loses to the close); the real protections are the sharp-book veto, Pinnacle confirm and trap guard; his own export can test it via the 'Novig dollars at the price' split

## Do this next
Open job is unchanged: BX5 (ask Tj whether to read a book page for every prop >= 1.5% EV, needed for the sharp-prop-book question), then BW1-BW5 (per-game exposure safeguard). Optional: when Tj's next scan-study file arrives, read CLV by the available-dollars bucket (under $25, $25-100, $100-500, $500+) to confirm the liquidity answer on his own data.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  9b32300f ckpt 2513: pre-release: v0.58.4: a close from another game can no longer count (a Washi
  2273ba90 ckpt 2512: BX3 log-only fixes done in data: rules line now says CNO's edge/odds/books, 
  3486e5f2 ckpt 2511: BX2 done in data: WSU close bug confirmed (0.5 matcher bar + latest row not 
  1a67a1d6 ckpt 2510: BX: Tj sent an outside analysis of the first scan-study export (v0.58.3, 622
  f39e0e5a ckpt 2509: BW: wrote Tj's per-game exposure request into TASKS.md (BW1-BW5)
  df2c9ade ckpt 2508: v0.58.3 released + recorded (release.yml run 37170946701 green, tag v0.58.3)
  d0334f7d ckpt 2507: pre-release: v0.58.3: bids are kept within the wallet and the day's limit (a
  c80df9fc ckpt 2506: pre-ship: v0.58.3: bids are kept within the wallet and the day's limit (a be
  baa4035c ckpt 2505: BV built: RateGate.takeLowRate, ReadPace on BookBatch (public/key start/low/
  d37ac654 ckpt 2504: BU app tests green (MakerAppTest + WalletStripTest 23 passed). Tj sent diagn
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
