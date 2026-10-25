# CHECKPOINT 4161 — read me first, then TASKS.md

**Written:** 2026-10-25T01:32:03Z · **tests:** all 9 fast checks green
**Branch:** `ccr-70c81990-qm4nt2` · **builds on:** `556fdda82` (this checkpoint is the commit after it)

## Just done
pre-release: SG: one money limit on similar bets (same game total at any line, a team's side, a player's over/under), counting your bets, the engine's bets and every bid together; no bids on what is already bet, the engine never bets what you bet by hand, red X on cards and the Bet sheet (versionCode 420, v1.0.80)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.80), then run: bash tools/record-release.sh v1.0.80 420 "SG: one money limit on similar bets (same game total at any line, a team's side, a player's over/under), counting your bets, the engine's bets and every bid together; no bids on what is already bet, the engine never bets what you bet by hand, red X on cards and the Bet sheet"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  4b597670e ckpt 4160: SG1-SG4 code: SimilarBets key, RepeatGuard who/cap/room/heldShort, engine t
  f8f246246 ckpt 4159: SG: Tj's similar-bets cap request written into TASKS.md (root cause found: 
  8d80889b4 ckpt 4158: pre-release: All-time profit = winnings on final bets and bids minus their 
  df409822f ckpt 4157: pre-ship: All-time profit = winnings on final bets and bids minus their cos
  9d94fe4b8 ckpt 4156: pre-ship: All-time profit = winnings on final bets and bids minus their cos
  fecb13665 ckpt 4155: chat: answered ledger-profit method
  affbeeada ckpt 4154: chat: replied to profit screenshots
  d80033b3b ckpt 4153: chat: answered propline/chat-lag/profit (CH87)
  26a74408c ckpt 4152: ENGINEER 2026-10-24 done
  97468674a ckpt 4151: ENGINEER: CH86 traced; extras are likely unfilled IOC retries (research/ch8
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
