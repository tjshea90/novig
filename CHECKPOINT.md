# CHECKPOINT 3624 — read me first, then TASKS.md

**Written:** 2026-10-18T03:36:17Z · **tests:** all 5 fast checks green
**Branch:** `claude/optimistic-curie-b86oug` · **builds on:** `402737f9b` (this checkpoint is the commit after it)

## Just done
TCL111: SX Bet docs saved (research/sxbet), SXBET_API.md, SxBetConvert+SxBetClient+SxBetTest green, League.sxLeagueIds, QuotaPolicy.SXBET

## Do this next
wire SX Bet into app+engine: ApiProvider SXBET, ApiEntry, ScanSettings switch, ApiToggles, CloudSources/CloudKeys/cloud-scan.yml secret SXBET_API_KEY, SOURCE_ORDER/ApiChain, ApiTogglesTest; then Centrifugo socket

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
    A  SXBET_API.md

## Last ten checkpoints
```
  3f4f4435c ckpt 3623: TCL110: no-lease push policy + relay of typed messages to the lease holder
  8fe87c69a ckpt 3622: added tj outlook name for acct-7542bf (coord.py, tell_tj.py, CLAUDE.md)
  75ec3d20d ckpt 3621: TCL110: acct-5d1e2d shown as (mirage) in coord, chat sender, CLAUDE.md
  8c9989252 ckpt 3620: TCL104d: fixed hidden-bets note height jump in Feed
  2aa6cf8ab ckpt 3619: chat session done
  2fbb0d950 ckpt 3618: v0.115.0 released (TCL109)
  87d310316 ckpt 3617: merged main into TCL109 branch
  e569b8c94 ckpt 3616: pre-release: v0.115.0: live betting on GitHub: tail bets, Live light bids, 
  6647b4604 ckpt 3615: pre-ship: v0.115.0: live betting on GitHub: tail bets, Live light bids, liv
  870082cf6 ckpt 3614: TCL109 phase 2 built (live taker+bids on engine), phone stand-down, tests
```

(7 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
