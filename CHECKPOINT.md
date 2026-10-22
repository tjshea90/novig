# CHECKPOINT 3945 — read me first, then TASKS.md

**Written:** 2026-10-22T00:35:23Z · **tests:** all 9 fast checks green
**Branch:** `claude/optimistic-curie-wju7hd` · **builds on:** `0fc71f282` (this checkpoint is the commit after it)

## Just done
pre-release: v1.0.45: the engine logs what the Novig stream holds and what SharpAPI returns per league after each scan; SharpAPI +EV cross-check stands down when Novig is not a picked book (versionCode 385, v1.0.45)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.45), then run: bash tools/record-release.sh v1.0.45 385 "v1.0.45: the engine logs what the Novig stream holds and what SharpAPI returns per league after each scan; SharpAPI +EV cross-check stands down when Novig is not a picked book"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  0fc71f282 ckpt 3944: pre-ship: v1.0.45: the engine logs what the Novig stream holds and what Sha
  515a5ea41 ckpt 3943: pre-release: v1.0.44: SharpAPI shows on Engine > APIs (engine's own key che
  d8affb98c ckpt 3942: answered Tj on Novig socket use; NS1 task written
  7695f9d68 ckpt 3941: pre-ship: v1.0.44: SharpAPI shows on Engine > APIs (engine's own key check 
  617145d3b ckpt 3940: SharpAPI card on Engine>APIs without a phone key; SharpAPI switch on Live s
  f8a27fbfa ckpt 3939: chat: SharpAPI key verified live on engine after reset
  f1f887602 ckpt 3938: GS: game state was in v1.0.43; added the engine's sharpapi-finals journal (
  5b5e1e302 ckpt 3937: SS done: SharpAPI WebSocket stream + game state built, v1.0.43 shipped (rel
  99d105a3d ckpt 3936: pre-release: v1.0.43: SharpAPI WebSocket stream (odds and game state pushed
  cc2080cb6 ckpt 3935: SA done: v1.0.42 shipped (release building); SharpAPI on engine (store + de
```
