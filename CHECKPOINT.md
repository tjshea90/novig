# CHECKPOINT 3915 — read me first, then TASKS.md

**Written:** 2026-10-21T18:53:32Z · **tests:** all 9 fast checks green
**Branch:** `claude/optimistic-curie-s9zcjn` · **builds on:** `520b89fb9` (this checkpoint is the commit after it)

## Just done
ApiState: a fresh accepted key check outranks a ledger 'every key refused' (BoltOdds card red beside green check); ApiStateTest updated, green

## Do this next
ship in the next release; check why BoltOdds ledger marks refused (socket 1008 too-many-connections?) and keep it out of key state

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  4c0826410 ckpt 3914: pre-release: v1.0.37: sharp-confirmed bids step their margin down to 1.5% u
  cb7b3025c ckpt 3913: pre-ship: v1.0.37: sharp-confirmed bids step their margin down to 1.5% unde
  edf8cc5e2 ckpt 3913: ANALYST 2026-10-21 skipped: under 100 new settled; engine healthy
  70c07be9a ckpt 3912: CH59 sharp bid margin step-down merged onto main line, tests green (2863), 
  f9837152a ckpt 3901: CH59 sharp-confirmed bid margin step-down to 1.5% (tests green, not release
  590ef9bb4 ckpt 3911: Research index landed
  13541563e ckpt 3910: Research library: tools/research_index.py + research/INDEX.md (104 studies,
  20f4af5d8 ckpt 3909: pre-release: v1.0.36: feed-level API problems on the API card, order gradin
  a0eaf1a28 ckpt 3908: Merged cut-off branch ccr-cb0c319e (feed-level API problems, order grading 
  181fe07e0 ckpt 3907: XO1 feed-level problems (API manager note+message, card), XO2 orders withou
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
