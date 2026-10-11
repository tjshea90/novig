# CHECKPOINT 2921 — read me first, then TASKS.md

**Written:** 2026-10-11T03:01:57Z · **tests:** all 4 fast checks green
**Branch:** `claude/unabated-oddsapi-integration-1u6kdc` · **builds on:** `e319ba3a` (this checkpoint is the commit after it)

## Just done
pre-release: v0.89.1: usage bars back on Settings > APIs (a green-amber-red bar under every key, plus a total per API) (versionCode 186, v0.89.1)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.89.1), then run: bash tools/record-release.sh v0.89.1 186 "v0.89.1: usage bars back on Settings > APIs (a green-amber-red bar under every key, plus a total per API)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  e319ba3a ckpt 2920: pre-ship: v0.89.1: usage bars back on Settings > APIs (a green-amber-red bar
  3d1e0a9c ckpt 2919: pre-release: v0.89.0: Unabated (free) and OddsPapi Free (v4) added; Settings
  466b2af7 ckpt 2918: pre-ship: v0.89.0: Unabated (free) and OddsPapi Free (v4) added; Settings > 
  1f3fc96d ckpt 2917: UA2-UA4 built: per-API switches (usePinnwire/usePinnapi/usePinnodds + gating
  9636ce1d ckpt 2916: UA1 done: Unabated per-key 5s gate + key rotation (refused/429 -> next key),
  57e2e312 ckpt 2915: UN3 wired: VigilantApp (Unabated last-in-line sources, OddsPapi Free closes 
  cf5a29a1 ckpt 2914: UN3: Unabated + OddsPapi v4 (Op4 client/budget/store/Op4Closes) done in data
  4a8f4dbf ckpt 2913: UN3 part 1: Unabated client/parser/books/convert/sources compile (data modul
  071cc44d ckpt 2912: UN1 done: raw Unabated/OddsPapi(v4) docs saved to research/unabated_oddspapi
  639e07b2 ckpt 2911: v0.88.11 released+recorded (tennis: Tennis switch, auto-bet from Vigilant sc
```
