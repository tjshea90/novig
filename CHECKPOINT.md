# CHECKPOINT 2653 — read me first, then TASKS.md

**Written:** 2026-10-07T01:35:55Z · **tests:** all 4 fast checks green
**Branch:** `ccr-c79f7430-lq8xfl` · **builds on:** `ccdcdbcf` (this checkpoint is the commit after it)

## Just done
pre-release: v0.70.4: ParlayAPI keys rotate: a scan goes to the next key when the one before it is spent or at its day's share, free keys serve scans down to their last 100 credits (kept for closing lines), every key is tried from key 1 again on every call so each comes back at its own reset; no key left means the other feeds price; a 403 HISTORICAL_LIMIT no longer marks a key refused (versionCode 122, v0.70.4)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.70.4), then run: bash tools/record-release.sh v0.70.4 122 "v0.70.4: ParlayAPI keys rotate: a scan goes to the next key when the one before it is spent or at its day's share, free keys serve scans down to their last 100 credits (kept for closing lines), every key is tried from key 1 again on every call so each comes back at its own reset; no key left means the other feeds price; a 403 HISTORICAL_LIMIT no longer marks a key refused"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  ccdcdbcf ckpt 2652: v0.70.4 candidate: version bump 0.70.4/code 122, PARLAY_API.md section 4a (r
  54a3ff97 ckpt 2651: DB3 tests: client-level rotation to free keys (TheOddsApiClientTest), HISTOR
  5c08e9e4 ckpt 2650: DB1/DB2/DB4: free ParlayAPI keys now serve scans in Tj's key order after the
  b19f6a41 ckpt 2649: WRAP-UP (usage nearly out): 22/30 verifiers saved on GitHub plus 9/9 study, 
  6ee5134c ckpt 2648: 21/30 verifiers saved: rule 8 (R1: first look inside 6 h with EV>=2.5%) repr
  c391db77 ckpt 2647: 18/30 verifiers saved (rules 1-6 complete except rule 6 feasibility running;
  2c16c8de ckpt 2646: container restarted at ~00:35Z (new VM, data and prompts survived): verify-6
  dcf3d7a8 ckpt 2645: verify-4-feasibility, verify-5-reproduce, verify-5-luck saved (14/30 verifie
  1c6bf9ff ckpt 2644: RESUMED per Tj ('check the last status and checkpoint, then resume'): state 
  a955f73e ckpt 2643: wrote the standalone resume runbook research/RESUME_v0701_ANALYSIS.md (job, 
```
