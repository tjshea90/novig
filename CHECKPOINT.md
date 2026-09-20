# CHECKPOINT 208 — read me first, then TASKS.md

**Written:** 2026-09-20T20:22:23Z · **tests:** all 1 fast checks green
**Branch:** `claude/positive-ev-sports-research-n6jum5` · **builds on:** `642a684` (this checkpoint is the commit after it)

## Just done
Rewired TheOddsApiClient onto KeyRotator (was a single apiKey string) — automatic rotation on 401 (quota exhausted/bad key) and 429 (rate limited), same pattern as SharpApiClient. Updated its test suite to match (added rotation/exhaustion tests, fixed the old 401-throws test since 401 now rotates instead of throwing directly). 75 tests total across engine+data, all green.

## Do this next
Move to the app module: EncryptedApiKeyStore (Android-side persistence for Tj's real credentials), a Settings screen to add/view/remove multiple keys per provider, and wire ScannerViewModel to build real SharpAPI/TheOddsAPI repositories from stored keys, falling back to sample per-leg when a provider has no keys yet.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  8e70e49 ckpt 205: Built KeyRotator (data module) — provider-agnostic multi-key rotation with r
  b663452 ckpt 198: Wrote Tj's 'make the app functional' request into TASKS.md with the architectu
  54208cf ckpt 195: Updated RESEARCH.md §4.1.1 with the real API-access process Tj found (email d
  b3b36e3 ckpt 191: Wrote Tj's request into TASKS.md: he found the real API-access process (email 
  44e6c69 ckpt 188: Second research pass on Novig's NBX API pricing/access: found strong convergin
  73d4460 ckpt 183: Wrote Tj's Novig-trading-API-pricing research request into TASKS.md (raw messa
  35a13a2 ckpt 179: Release v0.1.0 published: build, signature verification, server-side tag creat
  4cf8327 ckpt 168: First release.yml run: assembleRelease itself succeeded (proves the app builds
  afbb1cf ckpt 155: Tj switched the repo's default branch to main. Confirmed via the API, confirme
  013f762 ckpt 145: Tried to trigger release.yml — got a 404, workflow not found. Diagnosed why:
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
