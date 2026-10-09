# Research lab: one steward, everything archived (Tj, 2026-10-09, PERMANENT)

Tj runs Claude on several accounts (three) against this one repo. Only ONE session at a time manages the GitHub research lab (`.github/workflows/lab-record.yml`, every 6 h, 5.5 h a run).
Everyone else leaves it alone and READS: `research/lab/NOTES.md` (the running log of findings), the `lab-data` branch (`latest.txt`, a 7-day snapshot) and the `lab-archive-YYYY-MM-DD` Releases (every run's raw journals, never pruned).

## The rules (every session, every account)
1. **No data is ever lost.** The `lab-data` branch is force-pushed and keeps only 7 days: it is a snapshot, not storage. Storage is the `lab-archive-*` Releases (the workflow's "Archive" step uploads every run's gzipped journals and report; releases are free for a public repo and have no total size limit). Never delete a `lab-archive-*` release or asset. Never shorten what a run records.
2. **One steward.** `research/lab/STEWARD.json` names it (`claimId`, `lastCheckinAt`, `leaseHours`). You are the steward only if a routine/prompt you were handed carries the current `claimId`. If you are not: do NOT trigger, cancel, disable or edit the lab workflow, do NOT create a lab routine; you may read everything and append findings to NOTES.md.
3. **Takeover.** If `lastCheckinAt` is more than `leaseHours` (36) old, the steward has gone quiet (usage cap, account out): any session may take over: set a NEW `claimId`, `stewardSession`, `claimedAt`, `lastCheckinAt` in STEWARD.json, push it, then create the daily routine below with the new claimId in its prompt. An old steward's routine that fires later reads STEWARD.json, sees a different claimId, and exits without doing anything: that is how two accounts share one lab without seeing each other's routines.
4. **The daily check-in** (08:47 America/New_York, a fresh session per fire; the steward's routine prompt is the text under "Check-in prompt" below):
   a. Read STEWARD.json: claimId matches? If not, stop silently.
   b. `actions_list` the `lab-record.yml` runs: one should be in progress or queued. None for 7 h, or the last failed? Trigger one (`workflow_dispatch`, defaults) or read `get_job_logs` and fix.
   c. Read `latest.txt` on `lab-data` (git fetch origin lab-data; `git show origin/lab-data:latest.txt`): note STATUS numbers (runs, scanner passes, paper bids posted/fills, SGO ON/off, problems) as a dated entry in NOTES.md.
   d. Confirm today's `lab-archive-YYYY-MM-DD` release exists with this day's assets (`list_releases` / `get_release_by_tag`). Missing = the archive step failed: say so in NOTES.md and fix the workflow.
   e. Set `lastCheckinAt` in STEWARD.json, commit and push (`bash tools/ckpt.sh "lab check-in" "next daily check-in"`).
   f. Stop rule: see `stopRule` in STEWARD.json (default: stop on 2026-10-19 after the archive is complete; Tj can move it).
5. **Analysis** is separate from the check-in: when NOTES.md says READY FOR ANALYSIS (about 7 days, 30+ fills per recipe) or Tj asks, a session reads the lab-data snapshot and the archive releases (assets: `edge-*`, `sgo-tick-*`, `sgo-close-*`, `bidlab-*`, `bidlab-event-*`, `lab-*`, `report-*`) with the phone's research file, ranks the recipes, and writes the verdict into NOTES.md. Never loosen a limit or turn on real money from lab data without Tj's word.
6. Never put a key in NOTES.md or any lab file. The repo is public.

## Check-in prompt (what the steward's daily routine says; replace CLAIM with the current claimId)
"You are the Novig research-lab steward, claim CLAIM. Do this and nothing else. 1) Read research/lab/STEWARD.md and research/lab/STEWARD.json in github.com/tjshea90/novig (main). If STEWARD.json's claimId is not CLAIM, stop: you are not the steward. 2) Follow STEWARD.md section 4 (a to f). Use the GitHub MCP tools only (no gh CLI). Never connect to pinnodds.com or CrazyNinjaOdds from this session. 3) Reply with 5 lines: runs, fills, SGO on/off, archive ok/missing, anything wrong."
