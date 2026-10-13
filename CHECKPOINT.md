# CHECKPOINT 3189 — read me first, then TASKS.md

**Written:** 2026-10-13T22:54:05Z · **tests:** all 4 fast checks green
**Branch:** `ccr-b57fa2a2-ftkkrm` · **builds on:** `9274de00a` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.97.2: Kalshi socket now counts every subscribed market (ok acknowledgements), ended unfilled bids kept 6 h not 2 days and the OddsPapi Free cache pruned (memory and lag)

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/build.gradle.kts

## Last ten checkpoints
```
  1d4bf6991 ckpt 3188: TDG: diagnostics analysed, Kalshi ok-ack fix
  6ea5f446f ckpt 3187: v0.97.0 recorded; v0.97.1 (PropLine first) shipped, release triggered
  4c61d009f ckpt 3186: pre-release: v0.97.1: PropLine leads the API order (lines and props) while 
  fa5604717 ckpt 3185: pre-ship: v0.97.1: PropLine leads the API order (lines and props) while its
  1bde8d023 ckpt 3184: pre-release: v0.97.0: Kalshi API key (Ed25519/RSA, sealed in the Keystore) 
  e0e81901f ckpt 3183: pre-ship: v0.97.0: Kalshi API key (Ed25519/RSA, sealed in the Keystore) wit
  0255b794e ckpt 3182: Kalshi PEM sealed by Keystore + excluded from backup; docs; CLAUDE.md rule
  6c37e2e00 ckpt 3181: Kalshi: receipt-ordered overlay, tests green; fixes for settings tests
  fe81cad63 ckpt 3180: TKS2-4: Kalshi key store, Test key, Settings page, socket switches (Novig/B
  f6dd455ed ckpt 3179: Kalshi data layer + tests green (38)
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
