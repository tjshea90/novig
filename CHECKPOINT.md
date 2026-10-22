# CHECKPOINT 3943 — read me first, then TASKS.md

**Written:** 2026-10-22T00:25:02Z · **tests:** all 9 fast checks green
**Branch:** `claude/optimistic-curie-wju7hd` · **builds on:** `d8affb98c` (this checkpoint is the commit after it)

## Just done
pre-release: v1.0.44: SharpAPI shows on Engine > APIs (engine's own key check makes the card) and has its socket switch on the Live tab (versionCode 384, v1.0.44)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.44), then run: bash tools/record-release.sh v1.0.44 384 "v1.0.44: SharpAPI shows on Engine > APIs (engine's own key check makes the card) and has its socket switch on the Live tab"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  d8affb98c ckpt 3942: answered Tj on Novig socket use; NS1 task written
  7695f9d68 ckpt 3941: pre-ship: v1.0.44: SharpAPI shows on Engine > APIs (engine's own key check 
  617145d3b ckpt 3940: SharpAPI card on Engine>APIs without a phone key; SharpAPI switch on Live s
  f8a27fbfa ckpt 3939: chat: SharpAPI key verified live on engine after reset
  f1f887602 ckpt 3938: GS: game state was in v1.0.43; added the engine's sharpapi-finals journal (
  5b5e1e302 ckpt 3937: SS done: SharpAPI WebSocket stream + game state built, v1.0.43 shipped (rel
  99d105a3d ckpt 3936: pre-release: v1.0.43: SharpAPI WebSocket stream (odds and game state pushed
  cc2080cb6 ckpt 3935: SA done: v1.0.42 shipped (release building); SharpAPI on engine (store + de
  01bc3ee0f ckpt 3934: pre-release: v1.0.42: SharpAPI Sharp plan: the engine keeps a copy of 25 bo
  ab65f67cf ckpt 3933: SA4a: SharpApiLiveFeed (Pinnacle live from the store as Pinnodds frames, 1 
```
