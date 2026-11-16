# CHECKPOINT 4507 — read me first, then TASKS.md

**Written:** 2026-11-16T05:47:36Z · **tests:** all 10 fast checks green
**Branch:** `claude/optimistic-curie-99twme` · **builds on:** `2ca5cfef7` (this checkpoint is the commit after it)

## Just done
pre-release: Lighter and permanent: old days kept as daily numbers from Novig's money, the phone downloads only the last 45 days of wallet rows, money overlay cached (versionCode 457, v1.0.117)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.117), then run: bash tools/record-release.sh v1.0.117 457 "Lighter and permanent: old days kept as daily numbers from Novig's money, the phone downloads only the last 45 days of wallet rows, money overlay cached"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  2ca5cfef7 ckpt 4506: DG6: day summaries cached permanently, bets dropped per whole day, phone do
  be575a34d ckpt 4505: pre-release: Lighter: the Tracker's Novig-money overlay is worked out once 
  4513b66d3 ckpt 4504: lightweight: money overlay cached per ledger file, wallet files polled once
  dc0c7734a ckpt 4503: DG5 shipped v1.0.116; wallet ledger (15,558 rows) and fills (10,579) comple
  5cc55fd5c ckpt 4502: DG5: ledger/fills full first walk (complete flag), 3 s paging
  7b0a8a318 ckpt 4501: pre-release: DG5: Tracker's money comes from Novig's own wallet records (fi
  0aa108310 ckpt 4500: fix source pin
  85f5c7a97 ckpt 4499: DG5: Tracker money from Novig ledger+fills (WalletBetMoney), engine publish
  4a51202fd ckpt 4498: DG5: ledger reader keeps pages as read, paced, 429 no longer loses progress
  632fc9f6c ckpt 4497: pre-release: DG5: engine keeps the wallet's whole Novig ledger on GitHub; t
```
