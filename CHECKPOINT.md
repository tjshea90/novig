# CHECKPOINT 2980 — read me first, then TASKS.md

**Written:** 2026-10-11T17:34:41Z · **tests:** all 4 fast checks green
**Branch:** `claude/vigilant-scanner-enhancements-q97yhz` · **builds on:** `edd72ae4b` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.89.20: bets by hand on live games (the Bet sheet no longer refuses a game in play: Novig's taker fee is in the price, a LIVE note says how old the fair is and that an order takes about 5 s); in-play refusals read as this-bet-only; auto-bet and bids stay pregame

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  bb685c195 ckpt 2979: TO2 done with tests; docs (NOVIG_API §24, RESEARCH 132.3, CLAUDE.md); TO3/
  bc1e90049 ckpt 2978: TO2: manual live bets (planner prices live fee, live note, betRefused codes
  d4d5a3f69 ckpt 2977: TO: wrote Tj's live-betting request into TASKS.md; answer = Vigilant rule n
  4c1454c14 ckpt 2976: v0.89.19 released (TL, TM, TN done and shipped)
  62fef5926 ckpt 2975: pre-release: v0.89.19: every API toggled on is used for any data and one to
  5a6ba8775 ckpt 2974: TL/TM/TN done, tests green (data, app); docs updated
  2b986156d ckpt 2973: TL2: removed OutsideSgo/OutsideOp/TennisOnly, every API on is asked, off ne
  f2ecdb21d ckpt 2972: TN code + tests green in data (real start sync, sharp-history close preferr
  bae53b9ee ckpt 2971: TM done in data: worst-case devig default + migration (schema 17) + CloseDe
  2fae65f9c ckpt 2970: wrote TM tasks (conservative devig); TL audit found: OutsideSgo/OutsideOp r
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
