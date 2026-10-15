# Three accounts in rotation (Tj, 2026-10-15, TASKS.md TCL20f)

Tj: "each Claude account should be used in rotation so that the Claude usage is split evenly. Each Claude account should have scheduled check-ins on a rotating schedule so as not to interfere with each other. Each Claude account should work on this repo fully autonomously, without my input."

| slot | time (America/New_York, every day) | account |
| :- | :- | :- |
| 0 | 07:20 | the first account to run `python3 -I tools/rotation.py register` |
| 1 | 13:20 | the second |
| 2 | 19:20 | the third |

- **Slots never overlap** (six hours apart) and the lease (`tools/coord.py`) is the second guard: a run that finds a live session holding the repo stops quietly.
- **The duty rotates by the date** (`tools/rotation.py`, tested by `selftest`): `DUTIES[(day number + slot) mod 3]` with DUTIES = ANALYST, ENGINEER, WATCH. Over three days each account does each duty once, so the usage is split evenly even though ANALYST and ENGINEER cost more than WATCH. With fewer than three accounts registered the duty is ALL (WATCH, then ANALYST if there is new data, then ENGINEER if there is a backlog).
- **The daily digest** (`review-digest.yml`) is built at 10:20 UTC (before the 07:20 ET slot in summer and winter alike), after the previous evening's games were graded.
- **The engine** runs 08:00-22:00 ET; the three slots cover the morning before it starts (07:20), the middle of the day (13:20) and the evening before it stops (19:20).
- The roster lives in `roster.json` on the `claude-coord` branch; `python3 -I tools/rotation.py roster` prints it.
- One routine per account, created in THAT account (a scheduled task fires a session in the account that made it). The standard prompt is `research/coordination/ROUTINE_PROMPT.txt`; the setup message Tj pastes is `research/coordination/SETUP_FOR_TJ.md`.

## When an account does not show up (added 2026-10-15)
- **Every routine run leaves a trace first**: `rotation.py checkin` (a board note) before it even tries the lease. No trace within 5 h of the slot = the account is dead for that slot (usage cap, no repo attached, routine deleted).
- **The next run covers it, once, cheaply**: `rotation.py missed` lists such slots from the last 30 h; the run does that slot's WATCH checks and posts `rotation-cover`, so a third account never repeats it. A whole missed ANALYST/ENGINEER day is not made up (the digest and backlog wait).
- **The lease is the other half**: a session cut off mid-task stops renewing; after 20 minutes anyone takes over, and CHECKPOINT.md (written after every step) says what was done and what is next. Half-done changes show as "INTERRUPTED MID-CHANGE" in the briefing.
- **Routines get their own repo**: a scheduled session starts empty. Every routine prompt begins with `add_repo` and a clone (the first Tj check-in lacked it and did nothing). Tests: `python3 -I tools/rotation.py selftest`.
