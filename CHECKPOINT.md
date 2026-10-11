# CHECKPOINT 2981 — read me first, then TASKS.md

**Written:** 2026-10-11T17:37:18Z · **tests:** all 4 fast checks green
**Branch:** `claude/vigilant-scanner-enhancements-q97yhz` · **builds on:** `30bddb6b4` (this checkpoint is the commit after it)

## Just done
pre-release: v0.89.20: bets by hand on live games (the Bet sheet no longer refuses a game in play: Novig's taker fee is in the price, a LIVE note says how old the fair is and that an order takes about 5 s); in-play refusals read as this-bet-only; auto-bet and bids stay pregame (versionCode 205, v0.89.20)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.89.20), then run: bash tools/record-release.sh v0.89.20 205 "v0.89.20: bets by hand on live games (the Bet sheet no longer refuses a game in play: Novig's taker fee is in the price, a LIVE note says how old the fair is and that an order takes about 5 s); in-play refusals read as this-bet-only; auto-bet and bids stay pregame"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  30bddb6b4 ckpt 2980: pre-ship: v0.89.20: bets by hand on live games (the Bet sheet no longer ref
  bb685c195 ckpt 2979: TO2 done with tests; docs (NOVIG_API §24, RESEARCH 132.3, CLAUDE.md); TO3/
  bc1e90049 ckpt 2978: TO2: manual live bets (planner prices live fee, live note, betRefused codes
  d4d5a3f69 ckpt 2977: TO: wrote Tj's live-betting request into TASKS.md; answer = Vigilant rule n
  4c1454c14 ckpt 2976: v0.89.19 released (TL, TM, TN done and shipped)
  62fef5926 ckpt 2975: pre-release: v0.89.19: every API toggled on is used for any data and one to
  5a6ba8775 ckpt 2974: TL/TM/TN done, tests green (data, app); docs updated
  2b986156d ckpt 2973: TL2: removed OutsideSgo/OutsideOp/TennisOnly, every API on is asked, off ne
  f2ecdb21d ckpt 2972: TN code + tests green in data (real start sync, sharp-history close preferr
  bae53b9ee ckpt 2971: TM done in data: worst-case devig default + migration (schema 17) + CloseDe
```
