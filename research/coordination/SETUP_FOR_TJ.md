# What Tj does in each Claude account (once per account)

Open a new Claude Code session on the Vigilant repo (github.com/tjshea90/novig) in that account and paste the message below. Do it on the second and third accounts; the first account (slot 0, 07:20 ET) is already set up. Nothing else is needed: the sessions then run by themselves every day at their slot.

---- paste from here ----
You are being set up as one of three Claude accounts that work on github.com/tjshea90/novig one at a time, without Tj's input. Do these steps, in order, then answer in five lines.
1. Get the repo: if /home/user/novig is missing, call mcp__claude-code-remote__add_repo (owner tjshea90, repo novig, access push), run the clone command, then mcp__claude-code-remote__register_repo_root. Use the main branch. Read CLAUDE.md's section "Three Claude accounts, one worker at a time" and research/coordination/ROTATION.md.
2. Run: bash tools/install-hooks.sh
3. Say who you are: bash tools/coord.sh whoami acct-$(printf %s "<the e-mail address of the Claude account you are logged in as>" | sha1sum | cut -c1-6)   (a short tag; never write the e-mail itself anywhere in the repo)
4. Join the rotation: python3 -I tools/rotation.py register   (it prints your slot and the cron line)
5. Create your daily routine with mcp__claude-code-remote__create_trigger: name "Vigilant rotation", cron_expression = the cron line step 4 printed, create_new_session_on_fire true, notifications {"push": true}, initiation human_request, prompt = the exact text of research/coordination/ROUTINE_PROMPT.txt. Then call list_triggers and confirm it is enabled with the right time.
6. Post a note: bash tools/coord.sh post "account joined the rotation" "<your tag>, slot <n>, routine <trigger id>"
Answer with: your tag, your slot and time, the trigger id, "routine enabled", and anything that failed.
---- to here ----

If a step fails the session says so; the usual causes are a missing GitHub connection in that account (connect it at claude.ai: Settings, Connectors) or an account without scheduled tasks.
