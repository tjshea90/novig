# Making the three Claude accounts work by themselves (rewritten 2026-10-15 after the permission probe)

**What was wrong:** the old "Vigilant rotation" routines were created from inside a Claude chat (a command, `create_trigger`). A routine made that way starts EMPTY: no repository, no GitHub tools, and it cannot clone or push (the probe of 2026-10-15 failed steps 2-7 and was refused at step 4). So nothing it was told to do could happen, and the board shows no routine run ever checked in.
**The fix:** each account's routine is created on claude.ai's Routines page with the repository attached. Then the session starts with `tjshea90/novig` cloned, this repo's `.claude/settings.json` (Bash allowed, hooks) loaded, and the GitHub tools present.

**Second failure seen (2026-10-15, the 13:20 account's run, from Tj's screenshot):** with no repo attached the session cloned the repo itself with plain git, and the permission classifier then refused `tools/install-hooks.sh` and `tools/coord.sh` as "external code from a freshly cloned repo" (it never got the lease, wrote nothing, sent a push notification). A repo the routine is CREATED WITH (the Repository field) is the session's own project: its scripts are trusted and `.claude/settings.json` (Bash allowed) is read. So the Repository field is the fix for both failures; if a run still says "external code", the repository was not attached.

## Do this once in EACH of the three Claude accounts (about 5 minutes each)
1. Sign in to claude.ai as that account. Open **claude.ai/code/routines** (Claude Code on the web, then Routines). If you do not see Routines, open Claude Code on the web and look for "Routines" or "Scheduled" in the menu.
2. Find the old routine named **Vigilant rotation** (if there is one) and **delete** it.
3. Tap **New routine** and fill it in:
   - **Name:** Vigilant rotation
   - **Repository:** tjshea90/novig (allow it to push; branch main). This is the step the old routines missed.
   - **Connectors:** turn on **GitHub** (and nothing else).
   - **Permission mode:** **Auto** (so it never stops to ask you).
   - **Schedule:** daily at the slot of THIS account, Eastern time: first account 07:20, second account 13:20, third account 19:20. Never give two accounts the same time.
   - **Prompt:** paste the whole text of `research/coordination/ROUTINE_PROMPT.txt` (open it on GitHub: github.com/tjshea90/novig/blob/main/research/coordination/ROUTINE_PROMPT.txt, tap Raw, select all, copy).
   - **Notifications:** push on.
4. Save, then tap **Run now** once. Wait about 5 minutes.
5. Check it worked: open github.com/tjshea90/novig/blob/claude-coord/ and look for a new note "rotation-checkin" from that account (or just tell the next Claude session "check the routines": it runs `python3 -I tools/coord.py board`).

If the run fails, open its transcript (Routines, tap the run) and send me the last lines.

## Keeping it running
- Each routine repairs its own cron and prompt from this repo every run (ROUTINE.md "Keep your own routine correct"), so once the three exist you never touch them again.
- If an account is out of usage its run just does not happen; the next account's run covers it (ROUTINE.md "A missed slot is covered").
- To pause everything: turn each routine off on the Routines page, or switch the cloud scan off in the app (the engine stops by itself).
