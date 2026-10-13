# CHECKPOINT 3190 — read me first, then TASKS.md

**Written:** 2026-10-13T22:57:33Z · **tests:** all 4 fast checks green
**Branch:** `ccr-b57fa2a2-ftkkrm` · **builds on:** `470cb4c06` (this checkpoint is the commit after it)

## Just done
pre-release: v0.97.2: Kalshi socket now counts every subscribed market (ok acknowledgements), ended unfilled bids kept 6 h not 2 days and the OddsPapi Free cache pruned (memory and lag) (versionCode 254, v0.97.2)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.97.2), then run: bash tools/record-release.sh v0.97.2 254 "v0.97.2: Kalshi socket now counts every subscribed market (ok acknowledgements), ended unfilled bids kept 6 h not 2 days and the OddsPapi Free cache pruned (memory and lag)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  470cb4c06 ckpt 3189: pre-ship: v0.97.2: Kalshi socket now counts every subscribed market (ok ack
  1d4bf6991 ckpt 3188: TDG: diagnostics analysed, Kalshi ok-ack fix
  6ea5f446f ckpt 3187: v0.97.0 recorded; v0.97.1 (PropLine first) shipped, release triggered
  4c61d009f ckpt 3186: pre-release: v0.97.1: PropLine leads the API order (lines and props) while 
  fa5604717 ckpt 3185: pre-ship: v0.97.1: PropLine leads the API order (lines and props) while its
  1bde8d023 ckpt 3184: pre-release: v0.97.0: Kalshi API key (Ed25519/RSA, sealed in the Keystore) 
  e0e81901f ckpt 3183: pre-ship: v0.97.0: Kalshi API key (Ed25519/RSA, sealed in the Keystore) wit
  0255b794e ckpt 3182: Kalshi PEM sealed by Keystore + excluded from backup; docs; CLAUDE.md rule
  6c37e2e00 ckpt 3181: Kalshi: receipt-ordered overlay, tests green; fixes for settings tests
  fe81cad63 ckpt 3180: TKS2-4: Kalshi key store, Test key, Settings page, socket switches (Novig/B
```
