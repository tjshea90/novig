# Three accounts, one team — the one-page index (rebuilt 2026-10-19, CR)

Accounts: **acct-5d1e2d (mirage)**, **acct-7542bf (tj outlook)**, **acct-27a37e (tj Gmail)**. The tag is the key; the name is added where a person reads. There is **no lease**: every account may work at the same time (Tj, 2026-10-19).

| Question | Where the answer is |
| :- | :- |
| Who else is working right now, on what, on which branch? | the briefing's `WORKING` lines, or `bash tools/coord.sh sessions` |
| Is someone already doing this TASKS.md item? | `bash tools/coord.sh claims`; before starting an item: `bash tools/coord.sh claim <ID>` (exit 3 = taken: pick another, or post on the board) |
| Who answers Tj's app chat? | ONLY acct-5d1e2d (mirage), for good (`chat.json`, `CHAT.md`). The other two accounts never touch the chat. |
| What was done / what is next? | `CHECKPOINT.md` (the last deliberate step on main) then `TASKS.md` (first `[ ]` of the last section); other sessions' last step and next step are in the briefing (`WORKING`, `UNLANDED`) |
| A cut-off session's unfinished branch? | the briefing's `UNLANDED` line names it and the command to carry it on (`git merge origin/<branch>`) |
| What did Tj ask, raw? | `INBOX.md` tail (hook-captured, verbatim) |
| Messages between accounts | `bash tools/coord.sh board` / `post` |
| Change any setting or switch (except STOP ALL, dead-man) | `python3 tools/engine_set.py path=value --why "..."` |
| Which account is out of usage? | `research/coordination/away.json` (empty = all available) |
| Tell Tj something outside the chat | `python3 tools/tell_tj.py say "title" "body"` (Messages) |
| Cut a release | `bash ship.sh "note"` (it claims RELEASE: one release at a time), then release.yml, then `tools/record-release.sh` (gives it back) |

Rules that never change: no key in a file, the class C list in `research/review/ROUTINE.md`, STOP ALL and the dead-man switch are never bypassed, one decision = one order.

## Branches (how three accounts do not get jumbled)
`main` is the source of truth and what the engine runs and every release is built from. Each session works on its own branch, which `push.sh` pushes on every save and keeps in step with main by itself: main is merged into the branch whenever that is a clean merge (a conflict is aborted and named, never committed), and the branch lands on main as a fast-forward once what it changes is checked (docs at once; `tools/` and workflows after the fast checks of `ckpt.sh`; app and engine code after `tools/test.sh` passed on exactly that code: `tools/landing.py`). Shared logs (INBOX.md, TASKS.md, BUILDLOG.md, NOTES.md, CHANGES.md) merge by keeping both sides (`.gitattributes`). Full rules: `PROTOCOL.md`. Old `ccr-*` / `claude/*` branches are not deleted by sessions (see `BRANCH_AUDIT_2026-10-18.md`); Tj may delete them in GitHub's branch list.
