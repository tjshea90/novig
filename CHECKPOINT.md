# CHECKPOINT 198 — read me first, then TASKS.md

**Written:** 2026-09-20T20:17:54Z · **tests:** all 1 fast checks green
**Branch:** `claude/positive-ev-sports-research-n6jum5` · **builds on:** `acf6e43` (this checkpoint is the commit after it)

## Just done
Wrote Tj's 'make the app functional' request into TASKS.md with the architecture plan: SharpAPI free tier supplies the Novig leg (uniquely includes Novig among its ~40 books), The Odds API supplies the reference leg (Pinnacle/consensus). Researched real API shapes for both before writing clients (SharpAPI's actual /odds endpoint schema, The Odds API's documented rate-limit headers) rather than guessing.

## Do this next
Build KeyRotator (pure multi-key rotation logic) in data module first with tests, then SharpApiClient, then rewire TheOddsApiClient onto it, then the app-module Settings UI + encrypted key storage + ScannerViewModel wiring. Checkpoint in stages.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  54208cf ckpt 195: Updated RESEARCH.md §4.1.1 with the real API-access process Tj found (email d
  b3b36e3 ckpt 191: Wrote Tj's request into TASKS.md: he found the real API-access process (email 
  44e6c69 ckpt 188: Second research pass on Novig's NBX API pricing/access: found strong convergin
  73d4460 ckpt 183: Wrote Tj's Novig-trading-API-pricing research request into TASKS.md (raw messa
  35a13a2 ckpt 179: Release v0.1.0 published: build, signature verification, server-side tag creat
  4cf8327 ckpt 168: First release.yml run: assembleRelease itself succeeded (proves the app builds
  afbb1cf ckpt 155: Tj switched the repo's default branch to main. Confirmed via the API, confirme
  013f762 ckpt 145: Tried to trigger release.yml — got a 404, workflow not found. Diagnosed why:
  a50ac5e ckpt 143: Switched Vigilant's release signing to match fantasy-football's precedent: gen
  200a6bd ckpt 137: Logged Tj's course-correction on release signing: checked Portfolio and fantas
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
