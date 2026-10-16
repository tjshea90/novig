# Dead-man switch (TCL50; Tj, 2026-10-16, permanent)

Tj: "if I ever stop responding or chatting for 3 days, Claude sends me a prompt asking if I'm still here. if I don't respond within 24 hours to that prompt, all GitHub actions are stopped, the engine is shut down, all routines are stopped, Claude sessions are paused immediately and indefinitely with schedules turned off. basically if I go away, I want the entire system to stop. everything on GitHub and all 3 Claude accounts should stop"

**Presence** = a message from Tj: a chat message in the app, or a typed prompt in any Claude session (INBOX.md). NOT presence: the app syncing, phone-data pushes, bots, Claude's own messages, a routine's stored prompt.
**Machine:** `.github/workflows/deadman.yml` hourly -> `tools/deadman.py tick` (state in `deadman/state.json` on cloud-config).
1. 72 h without presence -> priority-1 phone notification + chat message "still there?".
2. Any message from Tj within 24 h -> back to normal.
3. No message in 24 h -> STOP: stage 1 `on:false` in cloud-config (the engine ends and takes its own bids down), `deadman/STOP.json`, every workflow except this one and the engine disabled; stage 2 (30 min later) the engine run is cancelled and cloud-scan.yml disabled.
4. While STOP.json exists `tools/coord.py acquire` refuses (exit 5) for everyone (routines, interactive, any account); `resume.sh` prints a DEADMAN line. A session that sees it does NO work: no edits, no pushes, no workflow triggers. Scheduled routines that fire anyway exit at once (acquire refuses before any model work).
5. A message from Tj lifts it: `capture_inbox.sh` runs `deadman.py resume` (STOP.json removed at once); the next hourly tick sees his message and re-enables exactly the workflows it disabled. The engine's `on` and trading switches are NOT turned back on: Tj does that in Settings > Cloud scan.
Limits: Claude's scheduled routines live in Anthropic's schedule, not on GitHub; this switch cannot delete them, but they cannot start work while STOP is set. A session with the claude-code-remote trigger tools may also disable them (`update_trigger enabled=false`) when it sees STOP, and re-enable them after Tj returns.
Test: `python3 -I tools/deadman.py selftest` (the workflow runs it every tick).
