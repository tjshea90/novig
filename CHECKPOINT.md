# CHECKPOINT 4492 — read me first, then TASKS.md

**Written:** 2026-11-16T03:18:52Z · **tests:** 1 RED: test_research_index.sh (9 green)
**Branch:** `claude/optimistic-curie-1nn2x9` · **builds on:** `a263949a8` (this checkpoint is the commit after it)

## Just done
DG5 logged + answered Tj; found ledger readers NovigTradingClient.bettingRows/moneyMoves/fills/orders already exist

## Do this next
DG5a: find whether engine's read key can read /v3/account/subaccounts/<id>/transactions (phone reads it with mgmt key via WalletMoney); if not, phone uploads ledger pages to phone-data; then write tracker-ledger file on GitHub, then switch Tracker to read it

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  e51b1ce06 ckpt 4491: chat: DG4 research written, Tj asked go
  cc527242b ckpt 4490: chat: DG4 answered, logged
  32e0ca377 ckpt 4489: chat: v1.0.114 shipped (DG3)
  51f3f5ebc ckpt 4488: pre-release: DG3: phone drops settled bets older than 35 days once GitHub h
  519ef05c0 ckpt 4487: pre-ship: DG3: phone drops settled bets older than 35 days once GitHub hold
  5664b7752 ckpt 4486: chat: DG3 logged (phone keeps only open+recent bets)
  c50e3c71e ckpt 4485: chat: v1.0.113 shipped (DG1a,c; OddsPapi off+cache gone); told Tj live/bids
  d725dde5d ckpt 4484: pre-release: DG1: bets.json saved at most every 4 s, OddsPapi Free cache de
  c9882254b ckpt 4483: pre-ship: DG1: bets.json saved at most every 4 s, OddsPapi Free cache delet
  d4216bb86 ckpt 4482: pre-ship: DG1: bets.json saved at most every 4 s, OddsPapi Free cache delet
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
