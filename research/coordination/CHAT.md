# The app chat: rules for the session that answers it (CR, Tj 2026-10-19)

Tj: "Assign just one account to the chat which never changes. That account will always watch and answer the chat. The other Claude accounts will not see the chat at all ...
Figure out how to make the chat function stable and work like a real chat". Why it was rebuilt, with the evidence: `CHAT_REBUILD_2026-10-19.md`.

## Who

- **Only acct-5d1e2d (mirage)** answers the chat (`chat.json`). On tj outlook and tj Gmail, `tools/chat.py` says so and does nothing, and their briefing never mentions the chat. Change the account only on Tj's word, in `chat.json`.
- **One session of that account at a time:** the one whose `chat.py watch` holds the seat. A second session's watcher stays on standby and takes over by itself 2.5 minutes after the first one stops. `chat.py reply` refuses while another session holds the seat.

## How (the chat account's session)

1. Start the watcher under Monitor, persistent: `python3 -I -u tools/chat.py watch` (`bash tools/chat_watch.sh` does the same). It costs no tokens while Tj is quiet.
2. Each batch it prints is Tj's unanswered messages (`CHAT [<id>] <time> (<age>): <text>`); attachments are already downloaded, open the printed path with Read. The receipt (Seen) is written by the watcher; you write nothing for it.
3. Answer every batch with ONE reply naming the ids: `python3 -I tools/chat.py reply <id> [<id> ...] "text"`. A message that needs no words ("ok", "thanks"): `chat.py done <id>` (a thumbs-up, no bubble). A follow-up later ("done, v1.0.6 is out: <link>") is a new reply to the same id or `chat.py say "..."`.
4. Starting a long job: `python3 -I tools/chat.py status working "building v1.0.6"`; done: `chat.py status idle`. The app's header shows it.
5. Requests go into TASKS.md first (CLAUDE.md "When Tj asks for something new"), then the answer, then the work; claim the TASKS item (`bash tools/coord.sh claim <ID>`) so another session does not start it too.

## Never

- Never answer from another account or while another session holds the seat (the tool refuses; do not work around it).
- Never send almost the same message twice (the tool refuses a 90%-same text within an hour; `--again` only when repeating is the point).
- Never post GitHub reports, bets or wake notices into the chat: they go to Messages (`tools/tell_tj.py say`). The chat is a conversation.
- Never ignore a message because it is old: a message without an answer is answered, late ones with a short "sorry for the wait" after checking TASKS.md/INBOX.md that it was not already handled elsewhere.
- Never put a key or token in the chat (the repo is public; the tool refuses key-shaped text).

## When nobody is watching

`chat-wake.yml` (on the `chat` branch) runs on every message: 25 s later, if a message of Tj's has no answer and no session holds the seat (seen or not: a seen message with no answer and no seat means the session that saw it was cut off), it fires the chat account's routine (`CLAUDE_FIRE_5D1E2D_*`), once per 3 minutes, backing off to one an hour after 3 fires that brought nobody (the account may be out of usage). A sweep every 5 minutes (engine-watchdog) catches a dropped push event. The wake is shown in the app's header, never as a chat message. A woken session follows the steps above.

## The app (v1.0.5)

The phone keeps its copy of the thread and a send queue (`chat_v2.json`, `ChatSync`): Tj's message shows the moment he taps Send and says where it is: **Sending**, **Not sent yet, trying again** (a lost connection; it goes by itself), **Not sent** with Retry / Delete (the token was refused or is missing), **✓ Sent** (on GitHub), **✓✓ Seen** (this watcher printed it), **✓✓ Answered**; a `done` is a 👍 on his bubble. A reply that answers a message which is not right above it quotes it. The header says online / replying / working: <note> / waking up / offline (your message wakes it) / could not be woken / not answering 3 wakes (out of usage?), from `seat.json`. The red number on the Claude tab counts only chat replies (Tj, CH16) and goes when the chat is looked at; each reply is one notification that opens the chat. Links in a reply are tappable: put release links in as plain URLs.

## The store

Branch `chat`, never rewritten: `msgs/<UTC day>/<id>.json` (one file per message; Tj's `t<ms>-<hex>`, Claude's `c<ms>-<hex>` with `replyTo` = the list of ids it answers), `msgs/<day>/<id>.seen.json` (receipt), `files/<id>/<n>-<name>` (attachments, uploaded before the message). Branch `chat-seat`: `seat.json` (who answers, heartbeat, working note, the last wake), one commit replaced in place. The old chat (`chat/` on `cloud-config`) stays readable; until 2026-10-26 the watcher also answers new messages from the old app there (`chat.json` legacySince/legacyUntil), so nothing is missed while Tj updates.
