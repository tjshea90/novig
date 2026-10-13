# AGENTS.md

<!-- handoff:learnings:begin -->
## Session Learnings (auto-updated by handoff)

### Implicit Rules
- Repo CLAUDE.md rules: run tools/install-hooks.sh first; ckpt.sh after each step; ship.sh then release.yml then record-release.sh; no Workflow/subagent fan-out; Vigilant Novig only, mgm frozen
- Never commit keys; Pinnodds/BoltOdds sockets never opened by Claude sessions

### Key Decisions
- Decision: refusal hold 2,4,8..min cap 1h, reset on any accepted call — Reason: old rule rested refused key until next UTC day; transient 1008 locked BoltOdds all day
- Decision: every API Test button lifts refusal via ApiProbe.policyOf — Reason: only BoltOdds Test did
- Decision: down() of SGO/OddsPapi/Unabated/BoltOdds REST lasts 60s after last failure (RETRY_AFTER_MS) — Reason: down source dropped from source list never called again, stayed down until app restart
- Decision: quota-depleted holds unchanged — Reason: legit until provider reset

<!-- handoff:learnings:end -->
