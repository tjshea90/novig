# CHECKPOINT 2925 — read me first, then TASKS.md

**Written:** 2026-10-11T03:53:49Z · **tests:** all 4 fast checks green
**Branch:** `claude/unabated-oddsapi-integration-1u6kdc` · **builds on:** `212d81d0` (this checkpoint is the commit after it)

## Just done
pre-release: v0.89.3: OddsPapi (v5) switch to price the fair average with every book it carries (versionCode 188, v0.89.3)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.89.3), then run: bash tools/record-release.sh v0.89.3 188 "v0.89.3: OddsPapi (v5) switch to price the fair average with every book it carries"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  f118e846 ckpt 2924: pre-ship: v0.89.3: OddsPapi (v5) switch to price the fair average with every
  ba65a365 ckpt 2923: pre-release: v0.89.2: Unabated fixed from the real sample (bet-type filtered
  69bf0afd ckpt 2922: pre-ship: v0.89.2: Unabated fixed from the real sample (bet-type filtered bo
  8edfb143 ckpt 2921: pre-release: v0.89.1: usage bars back on Settings > APIs (a green-amber-red 
  e319ba3a ckpt 2920: pre-ship: v0.89.1: usage bars back on Settings > APIs (a green-amber-red bar
  3d1e0a9c ckpt 2919: pre-release: v0.89.0: Unabated (free) and OddsPapi Free (v4) added; Settings
  466b2af7 ckpt 2918: pre-ship: v0.89.0: Unabated (free) and OddsPapi Free (v4) added; Settings > 
  1f3fc96d ckpt 2917: UA2-UA4 built: per-API switches (usePinnwire/usePinnapi/usePinnodds + gating
  9636ce1d ckpt 2916: UA1 done: Unabated per-key 5s gate + key rotation (refused/429 -> next key),
  57e2e312 ckpt 2915: UN3 wired: VigilantApp (Unabated last-in-line sources, OddsPapi Free closes 
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
