# CHECKPOINT 3192 — read me first, then TASKS.md

**Written:** 2026-10-13T23:09:41Z · **tests:** all 4 fast checks green
**Branch:** `ccr-b57fa2a2-ftkkrm` · **builds on:** `dc824e142` (this checkpoint is the commit after it)

## Just done
pre-release: v0.97.3: BoltOdds passes classify each market name once instead of every outcome every pass (about 70x less work; same answers) (versionCode 255, v0.97.3)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.97.3), then run: bash tools/record-release.sh v0.97.3 255 "v0.97.3: BoltOdds passes classify each market name once instead of every outcome every pass (about 70x less work; same answers)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  dc824e142 ckpt 3191: pre-ship: v0.97.3: BoltOdds passes classify each market name once instead o
  2d3b1c981 ckpt 3190: pre-release: v0.97.2: Kalshi socket now counts every subscribed market (ok 
  470cb4c06 ckpt 3189: pre-ship: v0.97.2: Kalshi socket now counts every subscribed market (ok ack
  1d4bf6991 ckpt 3188: TDG: diagnostics analysed, Kalshi ok-ack fix
  6ea5f446f ckpt 3187: v0.97.0 recorded; v0.97.1 (PropLine first) shipped, release triggered
  4c61d009f ckpt 3186: pre-release: v0.97.1: PropLine leads the API order (lines and props) while 
  fa5604717 ckpt 3185: pre-ship: v0.97.1: PropLine leads the API order (lines and props) while its
  1bde8d023 ckpt 3184: pre-release: v0.97.0: Kalshi API key (Ed25519/RSA, sealed in the Keystore) 
  e0e81901f ckpt 3183: pre-ship: v0.97.0: Kalshi API key (Ed25519/RSA, sealed in the Keystore) wit
  0255b794e ckpt 3182: Kalshi PEM sealed by Keystore + excluded from backup; docs; CLAUDE.md rule
```
