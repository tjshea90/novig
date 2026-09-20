# CHECKPOINT 220 — read me first, then TASKS.md

**Written:** 2026-09-20T20:31:18Z · **tests:** all 1 fast checks green
**Branch:** `claude/positive-ev-sports-research-n6jum5` · **builds on:** `288eb2d` (this checkpoint is the commit after it)

## Just done
Wired Settings navigation into MainActivity (state-based, no nav library): SettingsViewModel by viewModels(), rememberSaveable toggle, rescan() on returning from Settings so newly-added keys take effect. This was the last piece needed to match OpportunitiesScreen's new onOpenSettings signature. Re-ran :engine:test :data:test — all green (unchanged pass count, no regressions from the earlier ApiKeyStore.kt addition).

## Do this next
Push and confirm the app module actually compiles via CI (no local Android SDK). Then update BRIEF.md (KeyRotator design, SharpAPI/Odds-API leg split, DataStore+Keystore over deprecated EncryptedSharedPreferences, hardcoded-NFL limitation), RESEARCH.md (verified SharpAPI/Odds-API real endpoint shapes), and TASKS.md (tick completed items). Tell Tj the app is wired for real data pending his keys entered via Settings, and flag the hardcoded-NFL sport limitation.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  aadab85 ckpt 208: Rewired TheOddsApiClient onto KeyRotator (was a single apiKey string) — auto
  8e70e49 ckpt 205: Built KeyRotator (data module) — provider-agnostic multi-key rotation with r
  b663452 ckpt 198: Wrote Tj's 'make the app functional' request into TASKS.md with the architectu
  54208cf ckpt 195: Updated RESEARCH.md §4.1.1 with the real API-access process Tj found (email d
  b3b36e3 ckpt 191: Wrote Tj's request into TASKS.md: he found the real API-access process (email 
  44e6c69 ckpt 188: Second research pass on Novig's NBX API pricing/access: found strong convergin
  73d4460 ckpt 183: Wrote Tj's Novig-trading-API-pricing research request into TASKS.md (raw messa
  35a13a2 ckpt 179: Release v0.1.0 published: build, signature verification, server-side tag creat
  4cf8327 ckpt 168: First release.yml run: assembleRelease itself succeeded (proves the app builds
  afbb1cf ckpt 155: Tj switched the repo's default branch to main. Confirmed via the API, confirme
```

(11 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
