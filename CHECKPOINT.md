# CHECKPOINT 4534 — read me first, then TASKS.md

**Written:** 2026-11-16T16:55:47Z · **tests:** all 10 fast checks green
**Branch:** `claude/optimistic-curie-1qw51k` · **builds on:** `8514e029a` (this checkpoint is the commit after it)

## Just done
pre-release: Settled bets older than 3 days lose their heavy snapshot once on GitHub, so the bets file stays small and the app starts faster (versionCode 463, v1.0.123)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.123), then run: bash tools/record-release.sh v1.0.123 463 "Settled bets older than 3 days lose their heavy snapshot once on GitHub, so the bets file stays small and the app starts faster"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  8514e029a ckpt 4533: pre-ship: Settled bets older than 3 days lose their heavy snapshot once on 
  61d9516a5 ckpt 4532: DG5 slimArchived built+tested
  6a0b92608 ckpt 4531: chat: answered Tj 16:43 '?' (DG5 status); DG5 not built: bets store prune n
  9f63ad7ea ckpt 4530: answered Tj diagnostics report; logged DG5
  e5718515a ckpt 4529: WATCH 11-16 slot1: engine healthy, no change
  482dcb682 ckpt 4528: answered Tj; shipped v1.0.120-122 (LD5 open-markets loss fix, wallet poll 1
  332c7809b ckpt 4527: pre-release: The app opens faster: Novig's wallet history is read in the ba
  82c7224f9 ckpt 4526: pre-ship: The app opens faster: Novig's wallet history is read in the backg
  f2403d2fa ckpt 4525: pre-release: The Tracker no longer re-reads Novig's wallet in the backgroun
  60f8743ee ckpt 4524: pre-ship: The Tracker no longer re-reads Novig's wallet in the background e
```
