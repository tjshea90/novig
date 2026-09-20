# CHECKPOINT 226 — read me first, then TASKS.md

**Written:** 2026-09-20T20:33:09Z · **tests:** all 1 fast checks green
**Branch:** `claude/positive-ev-sports-research-n6jum5` · **builds on:** `f1453da` (this checkpoint is the commit after it)

## Just done
Confirmed CI green for the app module (run 35535815614, conclusion=success) — the Settings/DataStore/Keystore/ViewModel/navigation code from Tj's 'make the app functional' request all compiles for real. Updated TASKS.md (ticked the completed data+app module work, left the hardcoded-NFL sport limitation as an explicit open item), BRIEF.md 'Locked architecture decisions' (SharpAPI/Odds-API leg split, KeyRotator design and why, DataStore+Keystore over deprecated EncryptedSharedPreferences, per-leg live/sample flags, hardcoded-NFL limitation), and RESEARCH.md new §4.2.1 (the verified real SharpAPI endpoint shape and The Odds API rate-limit header behavior this session's clients were actually built against).

## Do this next
This request is functionally done: SharpAPI + The Odds API are wired as real data sources, Settings screen lets Tj type in and manage multiple keys per provider, KeyRotator auto-switches on rate-limit/invalid-key. Tell Tj plainly: add his keys via the new Settings screen (⚙ icon), the app will use real data per-leg as soon as keys exist for that provider, sample data remains the fallback per-leg until then. Flag the hardcoded-NFL-only limitation. Nothing further queued unless Tj asks for something new.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  8be34bf ckpt 220: Wired Settings navigation into MainActivity (state-based, no nav library): Set
  aadab85 ckpt 208: Rewired TheOddsApiClient onto KeyRotator (was a single apiKey string) — auto
  8e70e49 ckpt 205: Built KeyRotator (data module) — provider-agnostic multi-key rotation with r
  b663452 ckpt 198: Wrote Tj's 'make the app functional' request into TASKS.md with the architectu
  54208cf ckpt 195: Updated RESEARCH.md §4.1.1 with the real API-access process Tj found (email d
  b3b36e3 ckpt 191: Wrote Tj's request into TASKS.md: he found the real API-access process (email 
  44e6c69 ckpt 188: Second research pass on Novig's NBX API pricing/access: found strong convergin
  73d4460 ckpt 183: Wrote Tj's Novig-trading-API-pricing research request into TASKS.md (raw messa
  35a13a2 ckpt 179: Release v0.1.0 published: build, signature verification, server-side tag creat
  4cf8327 ckpt 168: First release.yml run: assembleRelease itself succeeded (proves the app builds
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
