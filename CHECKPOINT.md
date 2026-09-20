# CHECKPOINT 229 — read me first, then TASKS.md

**Written:** 2026-09-20T20:43:22Z · **tests:** all 1 fast checks green
**Branch:** `claude/positive-ev-sports-research-n6jum5` · **builds on:** `a7705c1` (this checkpoint is the commit after it)

## Just done
Wrote Tj's new sport-picker request into TASKS.md before writing any code, per the working agreement.

## Do this next
Implement: data-module Sport/SportsCatalog, EvScanner multi-sport support, ScannerViewModel idle-until-refresh + sport selection state, OpportunitiesScreen sport-picker chips + PullToRefreshBox. Then verify engine/data tests, push, confirm CI, trigger the release workflow, send Tj the link.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  1fe3119 ckpt 226: Confirmed CI green for the app module (run 35535815614, conclusion=success) �
  8be34bf ckpt 220: Wired Settings navigation into MainActivity (state-based, no nav library): Set
  aadab85 ckpt 208: Rewired TheOddsApiClient onto KeyRotator (was a single apiKey string) — auto
  8e70e49 ckpt 205: Built KeyRotator (data module) — provider-agnostic multi-key rotation with r
  b663452 ckpt 198: Wrote Tj's 'make the app functional' request into TASKS.md with the architectu
  54208cf ckpt 195: Updated RESEARCH.md §4.1.1 with the real API-access process Tj found (email d
  b3b36e3 ckpt 191: Wrote Tj's request into TASKS.md: he found the real API-access process (email 
  44e6c69 ckpt 188: Second research pass on Novig's NBX API pricing/access: found strong convergin
  73d4460 ckpt 183: Wrote Tj's Novig-trading-API-pricing research request into TASKS.md (raw messa
  35a13a2 ckpt 179: Release v0.1.0 published: build, signature verification, server-side tag creat
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
