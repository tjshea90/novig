# CHECKPOINT 4211 — read me first, then TASKS.md

**Written:** 2026-10-25T16:07:27Z · **tests:** all 9 fast checks green
**Branch:** `ccr-be05497a-18skm3` · **builds on:** `7b474939f` (this checkpoint is the commit after it)

## Just done
propline_grade root cause: 6+ bets per request outlast PropLine's 30 s gateway (1 bet = 3.5 s, 200); now 1 bet/request, 8 in flight, final grades reused; run dispatched

## Do this next
16:33 check-in: read SUMMARY.md, add numbers to research/propline_parlay_audit_2026-10-25.md point 3, tell Tj; then PB8, PB12

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  5bd951e41 ckpt 4210: v1.0.83 released + recorded; engine reset: ParlayAPI overlay now live (501 
  3a0b2a12b ckpt 4209: pre-release: v1.0.83 PS: PropLine + ParlayAPI audit: ParlayAPI stream overl
  c353f654d ckpt 4208: pre-ship: v1.0.83 PS: PropLine + ParlayAPI audit: ParlayAPI stream overlay 
  e02194fe6 ckpt 4207: PB17b ParlayLiveFeed (paper-gated) + PinnFrames shared; tests green; TASKS/
  e0a1e8c59 ckpt 4206: PS1-PS12 written up (research/propline_parlay_audit_2026-10-25.md, CHANGES.
  73f465f45 ckpt 4205: PB23 root cause fixed: ParlayAPI quotes tagged at parse so the stream overl
  5a82f1a63 ckpt 4204: PS9a PropLine 1st-half source propline_1h (PropLinePeriodTest); PB21b cross
  686633040 ckpt 4203: PS1 hot streams + PB21b cross-check + PS4 PropLine re-read 90s; PS5/PS6 ver
  452e77ed0 ckpt 4202: PS2 answered + fixed (13 PropLine books, same-desk groups, research/proplin
  f860b9505 ckpt 4201: PS2 coverage probe (native keys) dispatched; PS3 tools/propline_grade.py wr
```
