# CHECKPOINT 2922 — read me first, then TASKS.md

**Written:** 2026-10-11T03:12:44Z · **tests:** all 4 fast checks green
**Branch:** `claude/unabated-oddsapi-integration-1u6kdc` · **builds on:** `e542d897` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.89.2: Unabated fixed from the real sample (bet-type filtered boards, CFB/CBB, real line times, no failed scans); Test active APIs shows progress and times out

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M app/build.gradle.kts
     M data/src/main/kotlin/com/tjshea/vigilant/data/reference/UnabatedKeyTest.kt

## Last ten checkpoints
```
  8edfb143 ckpt 2921: pre-release: v0.89.1: usage bars back on Settings > APIs (a green-amber-red 
  e319ba3a ckpt 2920: pre-ship: v0.89.1: usage bars back on Settings > APIs (a green-amber-red bar
  3d1e0a9c ckpt 2919: pre-release: v0.89.0: Unabated (free) and OddsPapi Free (v4) added; Settings
  466b2af7 ckpt 2918: pre-ship: v0.89.0: Unabated (free) and OddsPapi Free (v4) added; Settings > 
  1f3fc96d ckpt 2917: UA2-UA4 built: per-API switches (usePinnwire/usePinnapi/usePinnodds + gating
  9636ce1d ckpt 2916: UA1 done: Unabated per-key 5s gate + key rotation (refused/429 -> next key),
  57e2e312 ckpt 2915: UN3 wired: VigilantApp (Unabated last-in-line sources, OddsPapi Free closes 
  cf5a29a1 ckpt 2914: UN3: Unabated + OddsPapi v4 (Op4 client/budget/store/Op4Closes) done in data
  4a8f4dbf ckpt 2913: UN3 part 1: Unabated client/parser/books/convert/sources compile (data modul
  071cc44d ckpt 2912: UN1 done: raw Unabated/OddsPapi(v4) docs saved to research/unabated_oddspapi
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
