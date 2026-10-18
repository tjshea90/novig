# Full administration of the GitHub engine, and the runner replacement rule (Tj, 2026-10-18, TCL114, permanent)

**Tj, 2026-10-18 (verbatim, shortened):** "Do the runner replacement rule and set it up so Claude always has full permissions to change settings and full administration of GitHub engine" (after: "any Claude session ... change and toggle on and off any setting and any switch anywhere in the app and on GitHub except the stop all master switch ... The only exception is the stop all kill switch and the dead-man rules").

## The runner replacement rule

A bad runner is replaced, at once, by whichever session or watchdog notices, without asking anyone:
1. **When:** Novig refuses the runner's ADDRESS (`ANONYMIZED_NETWORK`, `RESTRICTED_NETWORK_REGION`: `status.novigRoute`, the engine log), the engine job hangs (no heartbeat 12+ min, scan progress frozen 10+ min), or the job ended and no replacement started while the switch is on and inside the hours.
2. **How:** cancel the run (`mcp__github__actions_run_trigger` `cancel_workflow_run`, or the workflow's own hand-over) and dispatch a new `cloud-scan.yml` on `main` (`run_workflow`). A new job draws a new runner, usually a new address. The free automatic version is `engine-watchdog.yml` (`tools/engine_watchdog.py`, every 5 minutes, backup for the in-job hand-over: an address refusal is replaced after 30 s with NO swap cap, Tj 2026-10-18 TCL115, "immediately ... until there is a runner with an accepted address"; each new run re-reads the fair-odds feeds and costs API requests, which he accepted); a session replaces by hand when it sees one of the causes sooner, and may also reset through `CloudConfig.resetAtMs` (the app's Reset engine).
3. **Never:** hide the runner's location (no proxy, VPN or relay: that evades Novig's location check), touch the OTHER Novig check (the key holder's device: the Novig app opened on Tj's phone within 3 days, `GEOLOCATION_*`; say so in the chat and stop ordering), start an engine over STOP ALL or the dead-man STOP, or send an order twice.
4. Log a swap in the chat (one line: which run, why, the new run id).

## Full administration, every session on every account

Every session may do all of these without asking (Tj's word; the exceptions are STOP ALL and the dead-man rules, and keys are never read, printed or committed):
| What | How |
| :- | :- |
| Change any app/engine setting or switch (rules, filters, bet and bid kinds, sizes, Kelly, caps, wallet use, API and socket switches, engine hours, trading off/paper/live, Run/Stop) | `python3 tools/engine_set.py path=value ... [--on\|--off] --why "..."` (writes `cloud-config` as `claude:<account>`; the engine reads it at once, the phone adopts it, `CloudClaudeConfig`) |
| Start the engine on schedule even when the app's toggles are off | `engine_set.py --on` (sets `on`, `gates.cloudScan`, un-pauses; refuses to start over STOP ALL) and `run_workflow cloud-scan.yml` |
| Stop the engine (not STOP ALL) | `engine_set.py --off` |
| Start, cancel, re-run any workflow; replace a runner | `mcp__github__actions_run_trigger`, `mcp__github__actions_list`, `get_job_logs` |
| Edit workflows, tools, code, docs, ship a release | edit, `bash ship.sh`, `release.yml`, `record-release.sh` (the lease rule in PROTOCOL.md; code and workflows need the lease or `NOLEASE_VERIFIED=1`) |
| Tell Tj, or ask the phone to switch an API | `tools/tell_tj.py say`, `set-api` |
| Read everything the engine saved | `cloud-feed` (status, log), `cloud-archive-*` Releases, `engine-dataset`, `phone-data` |

Needed once, by Tj, on each of the three Claude accounts: allow Bash and the `cloud-config` write in that account's Claude Code permissions (the session safety check is outside the repo, and an unattended routine can not show a prompt, so allow it in the routine's settings too). Tested on acct-7542bf (tj outlook) and acct-27a37e (tj Gmail); mirage (acct-5d1e2d) still to confirm.
