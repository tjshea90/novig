# CHECKPOINT 4478 — read me first, then TASKS.md

**Written:** 2026-11-15T22:45:55Z · **tests:** all 10 fast checks green
**Branch:** `claude/optimistic-curie-0oyh4z` · **builds on:** `fe1f9276a` (this checkpoint is the commit after it)

## Just done
pre-release: Phone reads no Novig order books for bids while the engine posts them (CPU/RAM/throttle) (versionCode 452, v1.0.112)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.112), then run: bash tools/record-release.sh v1.0.112 452 "Phone reads no Novig order books for bids while the engine posts them (CPU/RAM/throttle)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  fe1f9276a ckpt 4477: pre-ship: Phone reads no Novig order books for bids while the engine posts 
  7097f9504 ckpt 4476: CloudLink loop 4 stops reading Novig books while engine posts bids
  872e45d41 ckpt 4475: chat: v1.0.111 shipped, runner reset; live real, pregame kinds pruned
  5b0f80357 ckpt 4474: pre-release: Engine: keeps ordering after an unreadable fill; pregame auto-
  4b08a21e5 ckpt 4473: pre-ship: Engine: keeps ordering after an unreadable fill; pregame auto-bet
  288eb3317 ckpt 4472: chat: v1.0.110 shipped (LG3c); answered live results + volume question, awa
  f63f94471 ckpt 4471: pre-release: LG3c: phone bid guard rests while the engine's desk is live (n
  8d37c4296 ckpt 4470: pre-ship: LG3c: phone bid guard rests while the engine's desk is live (no 2
  ab51f9a24 ckpt 4469: LG3c: BidGuard rests while engine desk live; LG4 logged
  265cd6fe0 ckpt 4468: LG3 shipped v1.0.108+109 (paced reads on low-RAM, phone study off while clo
```
