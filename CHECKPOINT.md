# CHECKPOINT 3282 — read me first, then TASKS.md

**Written:** 2026-10-14T22:31:37Z · **tests:** all 4 fast checks green
**Branch:** `ccr-e8041e9d-7grwre` · **builds on:** `6cc83b134` (this checkpoint is the commit after it)

## Just done
pre-release: v0.100.0: Cloud scan - the scans run on GitHub (engine, cloud-scan.yml), the phone sets settings and shows the list; bets, bids and history saved to GitHub automatically (versionCode 276, v0.100.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.100.0), then run: bash tools/record-release.sh v0.100.0 276 "v0.100.0: Cloud scan - the scans run on GitHub (engine, cloud-scan.yml), the phone sets settings and shows the list; bets, bids and history saved to GitHub automatically"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  6cc83b134 ckpt 3281: TCL: cloudScan/cloudSave moved into gates (ScanSettings slot limit), off-ow
  47ec326c1 ckpt 3280: TCL3/TCL4: phone CloudLink (poll, settings push, GitHub history saving), Cl
  650b7053e ckpt 3279: TCL2: CloudEngine (+tests), :cloud module (CloudMain publisher), cloud-scan
  0e840422d ckpt 3278: TCL1: CloudCodec (+test pass), CloudSources, CloudFiles, CloudClient writte
  26b2e2a3d ckpt 3277: TCL tasks written after Tj agreed (cloud engine + permanent GitHub history)
  fe91cc153 ckpt 3276: wrote cloud-scan design (research/cloud_scan_2026-10-14.md) and TASKS.md TC
  46c994200 ckpt 3275: wrote handoff-novig.md (amhandoff #1)
  97bc219d3 ckpt 3274: v0.99.16 released and recorded (TVM5b done)
  c212ff27b ckpt 3273: v0.99.15 recorded; v0.99.16 shipped (Volume mode stake scale-up), release.y
  9d007daa3 ckpt 3272: pre-release: v0.99.16: Volume mode scales the stakes of new bids up toward 
```
