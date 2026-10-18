# Three accounts, one team — the one-page index (2026-10-18)

Accounts: **acct-5d1e2d (mirage)**, **acct-7542bf (tj outlook)**, **acct-27a37e (tj Gmail)**. The tag is the key; the name is added where a person reads.

| Question | Where the answer is |
| :- | :- |
| Who may work right now? | `bash tools/coord.py status` (lease on `claude-coord`; 20 min without a heartbeat = anyone may take it; Tj's word "force" = `acquire --force`) |
| Who answers Tj's chat? | `python3 -I tools/rotation.py chat` (CHAT_OWNER.md). A message to a non-holder is relayed to the lease holder. |
| What was done / what is next? | `CHECKPOINT.md` (last deliberate step) then `TASKS.md` (first `[ ]` of the last section) |
| What did Tj ask, raw? | `INBOX.md` tail (hook-captured, verbatim) |
| Messages between accounts | `bash tools/coord.py board` / `post` |
| Change any setting or switch (except STOP ALL, dead-man) | `python3 tools/engine_set.py path=value --why "..."` |
| Which account is out of usage? | `research/coordination/away.json` (empty = all available) |
| Tell Tj something / answer chat | `python3 tools/tell_tj.py say|chat-reply` |

Rules that never change: no key in a file, class C list in `research/review/ROUTINE.md`, STOP ALL and the dead-man switch are never bypassed, one decision = one order.

## Branches
`main` is the source of truth; every session's `ccr-*` branch is fast-forwarded onto it by `push.sh`. The many old `ccr-*` and `claude/*` branches are NOT deleted by sessions (deleting is irreversible and their histories are mostly autosave noise); see `BRANCH_AUDIT_2026-10-18.md` for the list with ages. Tj can delete the old ones in GitHub's branch list in one pass.
