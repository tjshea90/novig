# CHECKPOINT 4517 — read me first, then TASKS.md

**Written:** 2026-11-16T07:03:46Z · **tests:** all 10 fast checks green
**Branch:** `claude/optimistic-curie-2gqjsf` · **builds on:** `55ec49fff` (this checkpoint is the commit after it)

## Just done
pre-release: Daily profit/loss, charts and all-time come from Novig's own wallet by game day; pull to refresh re-reads the wallet (LD1-LD3) (versionCode 459, v1.0.119)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.119), then run: bash tools/record-release.sh v1.0.119 459 "Daily profit/loss, charts and all-time come from Novig's own wallet by game day; pull to refresh re-reads the wallet (LD1-LD3)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  55ec49fff ckpt 4516: pre-ship: Daily profit/loss, charts and all-time come from Novig's own wall
  f0704cd3a ckpt 4515: pre-ship: Daily profit/loss, charts and all-time come from Novig's own wall
  338d25680 ckpt 4514: LD1-3 built: LedgerDays (daily P/L from Novig wallet), pull reads wallet; L
  c2ea74c93 ckpt 4513: logged LD3
  24d6ab3a7 ckpt 4512: logged LD2 (daily P/L from Novig ledger)
  071a7b7a4 ckpt 4511: answered Tj on ledger vs tracker gap; logged LD1
  15a6cafe8 ckpt 4510: v1.0.118 shipped (DG6)
  fc835bc6c ckpt 4509: pre-release: The phone downloads Novig's wallet history once and then only 
  3bb4b18ff ckpt 4508: DG6: wallet files: full once, then 3-day delta merged into the phone's cach
  c749536c5 ckpt 4507: pre-release: Lighter and permanent: old days kept as daily numbers from Nov
```
