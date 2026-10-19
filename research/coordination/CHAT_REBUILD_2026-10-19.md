# Chat rebuild and coordination without the lease (CR, Tj 2026-10-19)

Tj (06:20Z, typed to acct-5d1e2d (mirage)): the app chat "misses some of my messages or ignores them, it sends me stale messages, sometimes it sends me back to back messages that are almost identical, sometimes it doesn't get my attachments. It must be totally rebuilt ... Assign just one account to the chat which never changes ... The other Claude accounts will not see the chat at all. Get rid of the lease system ... I should never see a lease error again. Any claude account can make changes to this repo, as long as they coordinate with each other and don't double work or break anything ... a smart way to have the three Claude accounts coordinate and not get jumbled up with different branches".

## 1. What was wrong (evidence: the 922 files in `chat/` on `cloud-config`, read 06:30Z, and the code)

| Symptom | Cause found | Evidence |
| :- | :- | :- |
| Messages missed or ignored | Up to three accounts and several sessions per account watched the chat; the "chat owner" rotation told some sessions to STAND DOWN, and the stale filters added on 10-19 (15 min, then 30 min, then 3 h, flip-flopping between two sessions) dropped any message older than the window. | 34 of Tj's 263 messages never got a reply. |
| | A broken merge put conflict markers inside `chat_watch.sh`'s Python on `main` for some minutes; any watcher started then printed nothing. | `<<<<<<< HEAD` in tools/chat_watch.sh at 06:20Z (fixed on main by "resolve merge markers"). |
| | Every tool shared git's `FETCH_HEAD`: the watcher's `git fetch cloud-config` and any other fetch (autosave, tell_tj, ckpt) at the same moment overwrote each other, so a watcher could read `main`'s tree (no `chat/`) and see nothing. | `chat_watch.sh`, `tell_tj.py` and `chat_wake.py` all read `FETCH_HEAD`. |
| | The app lists `chat/` with the Contents API, which returns at most 1,000 entries; acks, wake notices and relays all live in that folder, and at 922 files the newest messages were about to fall off the end. | `CloudInbox.pollChat` -> `CloudClient.listDir`. |
| Stale messages | Each wake started a NEW session (the routine has `persist_session: false`) with an empty /tmp, so it re-printed and re-answered old messages; thumbs-up receipts arrived under messages hours old. | ack of relay r1792361315 (22:08Z) written at 06:21Z. |
| | Relays: a message Tj typed into a Claude Code session was copied into the app chat as if he had written it there, then answered there. | 19 relay messages. |
| | The app read at most 20 new files per poll, newest first, so older replies trickled in later and appeared as new. | `MAX_PER_ROUND = 20` + `takeLast`. |
| Near-identical back-to-back messages | Two accounts (or two sessions of one account) answered the same message; the wake workflow posted a "Waking acct-X's Claude now..." bubble, then another for the next account in the chain. | 23 messages answered by two different accounts; 17 acked twice; 27 "Waking..." bubbles; 180 replies signed `claude:session` (whoami failed). |
| Attachments not received | The hourly cleanup deleted attachments after 24 h (27 of 48 are already gone), and the watcher printed only a branch path the session had to fetch by hand. | `chat-cleanup.yml`, `tell_tj.py chat-file`. |
| "Lease" errors | The lease was ONE lock for the whole repo; any second session was told "ANOTHER CLAUDE SESSION HOLDS THIS REPO ... do not edit or push", and Tj was asked to say "force". | capture_inbox.sh, coord.py status/check, push.sh. |

## 2. The new chat

**One chat account, fixed:** `research/coordination/chat.json` names it: acct-5d1e2d (mirage). Only that account's sessions run the chat; the tools refuse on the other two (they never print, read or answer the chat), the wake fires only that account, and nothing is relayed into the chat any more. To change the account (only if Tj asks), edit that one file.

**One answerer at a time (the seat):** inside the chat account, the session that runs the chat daemon holds the *seat* (`seat.json` on the `chat-seat` branch: holder, heartbeat every 30 s). A second session's daemon waits quietly on standby and takes over only if the holder stops for 2 minutes. `tools/chat.py reply` refuses while another live session holds the seat, so two sessions can never both answer.

**The store (`chat` branch, new, never rewritten, never cleaned):**
- `msgs/<UTC day>/<id>.json`: one immutable file per message (Tj's: `t<ms>-<hex>`, Claude's: `c<ms>-<hex>` with `replyTo` = the list of Tj's message ids it answers).
- `msgs/<UTC day>/<id>.seen.json`: the read receipt, written once by the daemon when it shows the message to the session (one name per message, so a second receipt is impossible).
- `files/<message id>/<n>-<name>`: attachments, uploaded BEFORE the message that names them; kept for good (Tj's save-everything rule). The daemon downloads them and prints the local path, so the session opens them with Read.
- Day folders keep every listing far under GitHub's 1,000-entry cap; wake state and the seat are not messages (no "Waking..." bubbles).

**The daemon (`python3 -I tools/chat.py watch`, run under Monitor, persistent):** every 4 s one `git ls-remote` (no model tokens); on a change it fetches into its own ref (never `FETCH_HEAD`), collects every message of Tj's that has no reply yet and was not shown in this seat tenure, waits 3 s so messages sent together are shown together, downloads attachments, prints the batch, writes the receipts. No age cut-off: an unanswered message is answered, late ones with a short apology; answered ones are never shown again, whichever session or container looks.

**Replies:** `python3 -I tools/chat.py reply <id> [<id> ...] "text"` (one reply can answer several messages), `chat.py say "text"` (a message of Claude's own, e.g. a release link), `chat.py status working "building v1.0.6"` (the app shows "Claude is working: ..."). Refused: another session holds the seat; an unknown id; a text 90%+ the same as one sent in the last hour; a key-shaped string.

**Wake:** a push of a message to `chat` runs `chat-wake.yml` (lives on the `chat` branch, runs `tools/chat_wake.py` from main): 25 s after Tj's newest message, if it has no receipt and the seat is stale, fire the chat account's routine (`CLAUDE_FIRE_5D1E2D_*`) once, and record the wake in `seat.json` (the app shows "Waking Claude..."). At most one fire per 3 minutes and 6 an hour; a sweep every 5 minutes (engine-watchdog) catches a missed push event.

**The app:** a new client (send queue with retries that survives restarts, attachments first, one listing per day folder with ETags, local store, notifications once per reply, unread count = new Claude chat replies only) and a messenger screen: bubbles by day, Tj's messages marked Sending / Sent / Seen / Answered, "Not sent, tap to retry", a reply quoting the message it answers when it is not the one above, a header that says Claude is online / working / offline (your message wakes it) / waking / not answering for N minutes.

**Moving over:** the old chat (`chat/` on `cloud-config`) stays readable on GitHub. Until Tj installs the new app the daemon also answers new messages there (old format, only while no old-style watcher is alive), so nothing is missed during the switch.

## 3. Coordination without the lease

- **No lease.** `tools/coord.py acquire` now checks the session in and always says yes (exit 5 only for the dead-man STOP and a routine of an account that is away, exactly as before). Nothing tells Tj "another session holds this repo"; `push.sh` never holds a push back.
- **Who is working** (`sessions/<account>-<session>.json` on `claude-coord`): account, session, kind, task, branch, last checkpoint ("just done / next"), heartbeat (renewed by every push). Every briefing lists the live sessions and any branch with work not yet on main, with the command to pick it up.
- **Claims, to avoid double work:** `bash tools/coord.sh claim CR` before starting a TASKS.md item; a live claim by another session answers "taken by X, pick another item". Claims expire 30 min after their session's last heartbeat. `ship.sh` claims `RELEASE` itself and waits while another release is being cut, so two sessions never produce one versionCode.
- **Branches stay in step with main:** every push lands on main when it is a fast-forward and safe; when main moved, `ckpt.sh` merges main into the session's branch (only a clean merge; on a conflict it stops and names the files) and lands it. `merge=union` for INBOX.md, TASKS.md and BUILDLOG.md, `merge=ours` for CHECKPOINT.md, so the usual appends never conflict.
- **Nothing broken reaches main:** autosave never commits a half-finished merge or a file with conflict markers; code (`app/`, `data/`, `engine/`, `cloud/`, `lab/`, gradle files) moves to main only when `tools/test.sh` passed on exactly that code (it records what it tested), tools and workflows when the fast checks passed; the branch is always pushed, so nothing is lost while it waits.

## 4. What was built (CR3-CR6, 2026-10-19)

- **Tools** (CR3): `tools/chat.py` (watch / reply / done / say / status / seat / pending / file / init), `tools/chat_wake.py` v2, `chat-wake.yml` on `chat`, the 5-minute sweep in `engine-watchdog.yml`. Wake rule refined after review: a message counts as waiting when it has no answer, seen or not (a seen-but-unanswered message with a stale seat = the session was cut off before it answered). A reply to an old-app message is mirrored into the new store with `quote` = Tj's old words.
- **App** (CR5, v1.0.5): `CloudChat.kt` (model + thread + presence, pure), `ChatSync.kt` (send queue + reader), `CloudClient.headSha / listEntries / readText / createFile` (create never replaces and never creates the branch: `chat` is made by `chat.py init`, never from main), `ChatScreen.kt` (reverse list anchored at the newest message, so the keyboard never pushes it away; delivery marks; Retry / Delete; quotes; tappable links; "Claude is replying"; New chat / Clear chat / the old app's chat in the menu), `ChatAlerts.kt` (one notification per reply, a tap opens the chat). How each symptom of §1 is closed:

| Symptom (§1) | Closed by |
| :- | :- |
| missed or ignored messages | the watcher shows every message without an answer, whatever its age, once per seat, and the wake fires for any unanswered one while nobody holds the seat; the phone never drops a message it could not send (it stays in the queue, visibly) |
| stale messages | no relays, no posts from GitHub into the chat; replies refused for unknown ids or from a second session; the phone reads each file once, by day folder, so nothing old "arrives" again |
| near-identical back-to-back messages | one answering session (the seat); `chat.py` refuses a 90%-same text within an hour; one reply can answer several messages |
| lost attachments | uploaded before the message, never deleted (no 24 h cleanup), kept on the phone too (thumbnails), downloaded by the watcher with the path printed; a failed upload holds the message back instead of sending it without the file |

