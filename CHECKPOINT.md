# CHECKPOINT 4527 — read me first, then TASKS.md

**Written:** 2026-11-16T08:17:24Z · **tests:** all 10 fast checks green
**Branch:** `claude/optimistic-curie-gq85mu` · **builds on:** `82c7224f9` (this checkpoint is the commit after it)

## Just done
pre-release: The app opens faster: Novig's wallet history is read in the background instead of while the first screen is built (versionCode 462, v1.0.122)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.122), then run: bash tools/record-release.sh v1.0.122 462 "The app opens faster: Novig's wallet history is read in the background instead of while the first screen is built"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  82c7224f9 ckpt 4526: pre-ship: The app opens faster: Novig's wallet history is read in the backg
  f2403d2fa ckpt 4525: pre-release: The Tracker no longer re-reads Novig's wallet in the backgroun
  60f8743ee ckpt 4524: pre-ship: The Tracker no longer re-reads Novig's wallet in the background e
  3e8fcd776 ckpt 4523: pre-release: Daily profit and loss counts lost bets Novig no longer holds, 
  4841d2f84 ckpt 4522: pre-ship: Daily profit and loss counts lost bets Novig no longer holds, so 
  7d8d07e21 ckpt 4521: pre-ship: Daily profit and loss counts lost bets Novig no longer holds, so 
  d2174abee ckpt 4520: pre-ship: Daily profit and loss counts lost bets Novig no longer holds, so 
  7a7ebd063 ckpt 4519: answered Tj numbers vs wallet; logged LD4
  064c4f76b ckpt 4518: v1.0.119 release.yml triggered; answered chat; logged BP1
  6ffb64685 ckpt 4517: pre-release: Daily profit/loss, charts and all-time come from Novig's own w
```
