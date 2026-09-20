#!/usr/bin/env python3
"""emit.py — turn collected hook text into EXACTLY ONE JSON object.

WHY THIS EXISTS
---------------
Hook stdout is parsed as a single JSON document. Looping over every repo in
the container and letting each repo's script print its own JSON produces,
with two repos present, a stream like:

    {"hookSpecificOutput": ...}{"hookSpecificOutput": ...}

which is not JSON at all ("Extra data: line 2 column 1"). With a second repo
present the entire session briefing is silently lost — the worst possible
failure for a system whose only job is to brief a cold session. So
collection is kept separate from emission, and emission happens once, here.

    ... | python3 emit.py session-start
    ... | python3 emit.py user-prompt-submit
    ... | python3 emit.py system-message
"""
import json, sys

mode = sys.argv[1] if len(sys.argv) > 1 else "session-start"
text = sys.stdin.read().strip()
if not text:
    sys.exit(0)

if mode == "session-start":
    out = {"hookSpecificOutput": {"hookEventName": "SessionStart",
                                  "additionalContext": text}}
elif mode == "user-prompt-submit":
    out = {"hookSpecificOutput": {"hookEventName": "UserPromptSubmit",
                                  "additionalContext": text}}
else:
    out = {"systemMessage": text}

json.dump(out, sys.stdout)
sys.stdout.write("\n")
