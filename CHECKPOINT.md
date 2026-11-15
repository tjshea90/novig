# CHECKPOINT 4474 — read me first, then TASKS.md

**Written:** 2026-11-15T22:14:56Z · **tests:** all 10 fast checks green
**Branch:** `claude/optimistic-curie-tpnud5` · **builds on:** `4b08a21e5` (this checkpoint is the commit after it)

## Just done
pre-release: Engine: keeps ordering after an unreadable fill; pregame auto-bet only profitable kinds; live lane real (versionCode 451, v1.0.111)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.111), then run: bash tools/record-release.sh v1.0.111 451 "Engine: keeps ordering after an unreadable fill; pregame auto-bet only profitable kinds; live lane real"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  4b08a21e5 ckpt 4473: pre-ship: Engine: keeps ordering after an unreadable fill; pregame auto-bet
  288eb3317 ckpt 4472: chat: v1.0.110 shipped (LG3c); answered live results + volume question, awa
  f63f94471 ckpt 4471: pre-release: LG3c: phone bid guard rests while the engine's desk is live (n
  8d37c4296 ckpt 4470: pre-ship: LG3c: phone bid guard rests while the engine's desk is live (no 2
  ab51f9a24 ckpt 4469: LG3c: BidGuard rests while engine desk live; LG4 logged
  265cd6fe0 ckpt 4468: LG3 shipped v1.0.108+109 (paced reads on low-RAM, phone study off while clo
  e347ee8e7 ckpt 4467: pre-release: LG3b: with the GitHub engine driving, the phone no longer logs
  598aefc00 ckpt 4466: pre-ship: LG3b: with the GitHub engine driving, the phone no longer logs, g
  9661216ae ckpt 4465: pre-release: LG3: low-RAM devices (Android Go tablets) read the engine 2.5x
  8051a60b7 ckpt 4464: pre-ship: LG3: low-RAM devices (Android Go tablets) read the engine 2.5x le
```
