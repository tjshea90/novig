# CHECKPOINT 2919 — read me first, then TASKS.md

**Written:** 2026-10-11T02:52:15Z · **tests:** all 4 fast checks green
**Branch:** `claude/unabated-oddsapi-integration-1u6kdc` · **builds on:** `466b2af7` (this checkpoint is the commit after it)

## Just done
pre-release: v0.89.0: Unabated (free) and OddsPapi Free (v4) added; Settings > APIs page with a switch, Test button and usage per API; keys rotate per key (versionCode 185, v0.89.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.89.0), then run: bash tools/record-release.sh v0.89.0 185 "v0.89.0: Unabated (free) and OddsPapi Free (v4) added; Settings > APIs page with a switch, Test button and usage per API; keys rotate per key"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  466b2af7 ckpt 2918: pre-ship: v0.89.0: Unabated (free) and OddsPapi Free (v4) added; Settings > 
  1f3fc96d ckpt 2917: UA2-UA4 built: per-API switches (usePinnwire/usePinnapi/usePinnodds + gating
  9636ce1d ckpt 2916: UA1 done: Unabated per-key 5s gate + key rotation (refused/429 -> next key),
  57e2e312 ckpt 2915: UN3 wired: VigilantApp (Unabated last-in-line sources, OddsPapi Free closes 
  cf5a29a1 ckpt 2914: UN3: Unabated + OddsPapi v4 (Op4 client/budget/store/Op4Closes) done in data
  4a8f4dbf ckpt 2913: UN3 part 1: Unabated client/parser/books/convert/sources compile (data modul
  071cc44d ckpt 2912: UN1 done: raw Unabated/OddsPapi(v4) docs saved to research/unabated_oddspapi
  639e07b2 ckpt 2911: v0.88.11 released+recorded (tennis: Tennis switch, auto-bet from Vigilant sc
  5d8c65b9 ckpt 2910: pre-release: v0.88.11: tennis in the auto-bet (from Vigilant's scan) and the
  93119d04 ckpt 2909: pre-ship: v0.88.11: tennis in the auto-bet (from Vigilant's scan) and the bi
```
