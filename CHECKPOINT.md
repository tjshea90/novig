# CHECKPOINT 3183 — read me first, then TASKS.md

**Written:** 2026-10-13T22:26:39Z · **tests:** all 4 fast checks green
**Branch:** `ccr-b57fa2a2-ftkkrm` · **builds on:** `0255b794e` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.97.0: Kalshi API key (Ed25519/RSA, sealed in the Keystore) with signed reads and a real-time WebSocket; a socket on/off switch for every API with a socket (Novig, BoltOdds, Pinnodds, Kalshi) in Settings > APIs

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/build.gradle.kts

## Last ten checkpoints
```
  0255b794e ckpt 3182: Kalshi PEM sealed by Keystore + excluded from backup; docs; CLAUDE.md rule
  6c37e2e00 ckpt 3181: Kalshi: receipt-ordered overlay, tests green; fixes for settings tests
  fe81cad63 ckpt 3180: TKS2-4: Kalshi key store, Test key, Settings page, socket switches (Novig/B
  f6dd455ed ckpt 3179: Kalshi data layer + tests green (38)
  84c6a6b0e ckpt 3178: TKS2/3: KalshiAuth, KalshiStream, KalshiClient signed+socket overlay (data 
  c7c0ed3f6 ckpt 3177: TKS1: Kalshi docs researched into KALSHI_API.md
  8c3a1e988 ckpt 3174: TKS tasks written
  a7675650c ckpt 3173: v0.96.4 released and recorded
  63bd2385c ckpt 3172: pre-release: v0.96.4: fixes the laggy, glitching feed: the adjusted-stars n
  9c5d7c7d0 ckpt 3171: pre-ship: v0.96.4: fixes the laggy, glitching feed: the adjusted-stars note
```
