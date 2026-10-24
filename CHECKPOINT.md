# CHECKPOINT 4094 — read me first, then TASKS.md

**Written:** 2026-10-24T02:50:26Z · **tests:** 1 RED: test_research_index.sh (8 green)
**Branch:** `ccr-305d3f7a-i2z8ja` · **builds on:** `fcbf16b67` (this checkpoint is the commit after it)

## Just done
SB1-2: phone study (8,384 +EV lines) + engine lines re-rated: edge predicts sharp CLV monotone in both; S5 support-heavy stars do not order bets (-0.12/star); S6 design chosen (edge ladder, thin small edges no trusted edge, breadth caps); research/star_rating_sb_2026-10-24.md

## Do this next
implement S6 in BetRating (ladder, thin floor, breadth caps, Rating.support for CloudKelly), StarCheck variants, tests, full suite, v1.0.69; then SB3 workflow for API history

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
    ?? research/star_rating_sb_2026-10-24.md

## Last ten checkpoints
```
  230599af4 ckpt 4093: S5 done: v1.0.68 released and recorded (no-trusted-edge floor, 1 star under
  a79976d1e ckpt 4092: pre-release: S5: star scale fixed at the bottom: 0 = no trusted edge (EV un
  8b0c5e816 ckpt 4091: pre-ship: S5: star scale fixed at the bottom: 0 = no trusted edge (EV under
  8f5d90a9b ckpt 4090: S5a-c: star scale checked on the engine's 20,840 graded lines; 0 = no trust
  f0ac263e3 ckpt 4089: v1.0.67 gated (full suite 4,207 green) and on main; release.yml triggered
  4567b1e3b ckpt 4088: pre-release: SR: star rating re-weighted (sharp book 25->15, edge 25->30, a
  81e2270b7 ckpt 4087: pre-ship: SR: star rating re-weighted (sharp book 25->15, edge 25->30, agre
  6ba9c7de4 ckpt 4086: SR1-2: verified the cut-off session's SR weights against review-digest run 
  fa7239519 ckpt 4085: OK1-3: The Odds API credits corrected/explained (per-key daily pace, no cre
  9c7198ea1 ckpt 4084: Corrected The Odds API claim (Tj's screenshot): 5,082 of 10,500 credits lef
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
