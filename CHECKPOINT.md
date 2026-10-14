# CHECKPOINT 3287 — read me first, then TASKS.md

**Written:** 2026-10-14T22:50:48Z · **tests:** all 4 fast checks green
**Branch:** `ccr-e8041e9d-7grwre` · **builds on:** `948771d04` (this checkpoint is the commit after it)

## Just done
pre-release: v0.100.1: the app sends your API keys to GitHub secrets for the cloud engine (sealed on the phone, write-only; Pinnodds, BoltOdds and Kalshi never sent) (versionCode 277, v0.100.1)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.100.1), then run: bash tools/record-release.sh v0.100.1 277 "v0.100.1: the app sends your API keys to GitHub secrets for the cloud engine (sealed on the phone, write-only; Pinnodds, BoltOdds and Kalshi never sent)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  948771d04 ckpt 3286: TCL7: app sends API keys to GitHub secrets (SealedBox, sendKeys, button), d
  05f8377e1 ckpt 3285: v0.100.0 released and recorded (TCL5 done)
  9f974b523 ckpt 3284: research doc: built + first live run; Diagnostics CLOUD section; release.ym
  aa9eb8fc2 ckpt 3283: pre-release: v0.100.0: Cloud scan - the scans run on GitHub (engine, cloud-
  76db0ed58 ckpt 3282: pre-release: v0.100.0: Cloud scan - the scans run on GitHub (engine, cloud-
  6cc83b134 ckpt 3281: TCL: cloudScan/cloudSave moved into gates (ScanSettings slot limit), off-ow
  47ec326c1 ckpt 3280: TCL3/TCL4: phone CloudLink (poll, settings push, GitHub history saving), Cl
  650b7053e ckpt 3279: TCL2: CloudEngine (+tests), :cloud module (CloudMain publisher), cloud-scan
  0e840422d ckpt 3278: TCL1: CloudCodec (+test pass), CloudSources, CloudFiles, CloudClient writte
  26b2e2a3d ckpt 3277: TCL tasks written after Tj agreed (cloud engine + permanent GitHub history)
```
