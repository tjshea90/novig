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
- **The engine** runs 24/7 (PB7, Tj 2026-10-25: "make the GitHub engine run 24/7 by default, with regular check-ins by Claude for maintenance and tuning"; the old 08:00-01:00 ET window is Settings › Cloud scan › hours, off). The three slots are the Claude check-ins: maintenance and tuning at 07:20 (after the night's games are graded), 13:20 and 19:20; overnight the engine is kept alive without Claude by `engine-alarm.yml` (the keeper, every 2 minutes) and `engine-watchdog.yml`, which replace a dead or refused runner.
- The roster lives in `roster.json` on the `claude-coord` branch; `python3 -I tools/rotation.py roster` prints it.
- One routine per account, created in THAT account (a scheduled task fires a session in the account that made it). The standard prompt is `research/coordination/ROUTINE_PROMPT.txt`; the setup message Tj pastes is `research/coordination/SETUP_FOR_TJ.md`.

## When an account does not show up or runs out of usage (updated 2026-10-15, Tj: "default to the next available account, or skip a session when it isn't available")
- **Every routine run leaves a trace first** (`rotation.py checkin`) and **a finished-duty note last** (`coord.sh post "<duty> <date>" ...`). `rotation.py missed` reads both: NO-SHOW = no trace within 5 h of the slot (usage cap, no repo attached, routine deleted/disabled); INTERRUPTED = a trace but no finished note and the duty was ANALYST or ENGINEER (cut off mid-task).
- **The next account that runs covers up to two such slots**, oldest first: the first with the slot's whole duty, the second with WATCH only (`rotation.py cover <slot> --date <d>` so nobody repeats it). An account without usage just does not run: that is the skip. With one account up, it covers two of the other two slots' work each day and the rest wait; nothing is lost because the data (digest, journals) and the backlog stay in the repo.
- **Each routine repairs its own schedule** (`rotation.py cron` + `list_triggers`/`update_trigger`, ROUTINE.md "Keep your own routine correct"), so a change to the cron line or the prompt in this repo reaches all three accounts without Tj.
- **The lease is the other half**: a session cut off mid-task stops renewing; after 20 minutes anyone takes over, and CHECKPOINT.md says what was done and what is next.
- **What no session can see**: another account's routine (a scheduled task lives in the account that made it) and that account's remaining usage. The only evidence is the board. If a slot shows NO-SHOW two days running, the cause is on that account (routine disabled, no usage, repo not attached): Tj looks at claude.ai/code/routines in that account.
- **Routines get their own repo**: a scheduled session starts empty. Every routine prompt begins with `add_repo` and a clone. Tests: `python3 -I tools/rotation.py selftest`.


## Away until (Tj says an account is out of usage)
`research/coordination/away.json` maps an account tag to the time (UTC) its usage returns. `rotation.py missed` then lists that account's slots before that time as **AWAY** right away (no 5-hour grace) so the next live session covers them (first with the slot's whole duty, the second with WATCH only, as usual). Remove the entry (or let it pass) when the account is back. 2026-10-15, Tj: acct-7542bf (slot 0, 07:20 ET) is out of weekly usage until Friday 2026-10-16 22:00 ET (= 2026-10-17T02:00Z).

Dormant account: its own routine stops at `rotation.py checkin` (exit 5, prints DORMANT) and `coord.py acquire --kind routine` refuses (exit 5) while now is before its away.json time, so it cannot take the lease from the accounts covering its slot. After that time both work normally again, with nothing to edit (an interactive session by Tj is never blocked). Tests: `rotation.py selftest`, `tools/test_coord.sh`.
