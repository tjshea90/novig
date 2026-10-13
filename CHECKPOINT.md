# CHECKPOINT 3186 — read me first, then TASKS.md

**Written:** 2026-10-13T22:38:57Z · **tests:** all 4 fast checks green
**Branch:** `ccr-b57fa2a2-ftkkrm` · **builds on:** `fa5604717` (this checkpoint is the commit after it)

## Just done
pre-release: v0.97.1: PropLine leads the API order (lines and props) while its measured quote age is as fresh as the others; falls back by measurement (versionCode 253, v0.97.1)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.97.1), then run: bash tools/record-release.sh v0.97.1 253 "v0.97.1: PropLine leads the API order (lines and props) while its measured quote age is as fresh as the others; falls back by measurement"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  fa5604717 ckpt 3185: pre-ship: v0.97.1: PropLine leads the API order (lines and props) while its
  1bde8d023 ckpt 3184: pre-release: v0.97.0: Kalshi API key (Ed25519/RSA, sealed in the Keystore) 
  e0e81901f ckpt 3183: pre-ship: v0.97.0: Kalshi API key (Ed25519/RSA, sealed in the Keystore) wit
  0255b794e ckpt 3182: Kalshi PEM sealed by Keystore + excluded from backup; docs; CLAUDE.md rule
  6c37e2e00 ckpt 3181: Kalshi: receipt-ordered overlay, tests green; fixes for settings tests
  fe81cad63 ckpt 3180: TKS2-4: Kalshi key store, Test key, Settings page, socket switches (Novig/B
  f6dd455ed ckpt 3179: Kalshi data layer + tests green (38)
  84c6a6b0e ckpt 3178: TKS2/3: KalshiAuth, KalshiStream, KalshiClient signed+socket overlay (data 
  c7c0ed3f6 ckpt 3177: TKS1: Kalshi docs researched into KALSHI_API.md
  8c3a1e988 ckpt 3174: TKS tasks written
```
