#!/usr/bin/env bash
# capture_inbox.sh — UserPromptSubmit hook. Appends the message Tj just sent
# to INBOX.md, verbatim, and commits+pushes it — BEFORE Claude does any work
# on it.
#
# WHY THIS EXISTS
# ----------------
# CLAUDE.md's rule is "write the request into TASKS.md before writing any
# code" — but that depends on a session remembering to do it, and a usage cap
# does not wait for a good moment. On this account's other repos (see
# fantasy-football's CLAUDE.md, 2026-09-15) a session spent its whole budget
# reading the codebase for a new feature, was cut off before ever writing the
# request down, and the PostToolUse autosave hook (which only fires on
# Edit|Write|NotebookEdit|Bash) never fired either, because a pure research
# stretch trips none of those. Nothing reached disk, anywhere, and the next
# session opened cold with no way to know the request had ever been made.
#
# This closes the gap at the one point that can never be skipped: the
# message arriving, before any tool call, before any judgment about whether
# it is "worth" saving yet. It does not replace TASKS.md — see INBOX.md's own
# header for that split.
#
# MUST NEVER BLOCK THE PROMPT. Whatever goes wrong here, the message still
# has to reach Claude. Always exit 0, and never emit a "decision":"block".
set -uo pipefail
D="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; cd "$D" || exit 0
[ -d .git ] || exit 0

# --text: this hook prints nothing of its own on the normal path (the
# message is written to disk, not echoed back); this only controls the
# shape of a warning relayed up from autosave.sh, for a human running this
# by hand.
TEXT_MODE=0
for a in "$@"; do [ "$a" = "--text" ] && TEXT_MODE=1; done

INPUT="$(cat)"
[ -z "$INPUT" ] && exit 0

if command -v python3 >/dev/null 2>&1; then
  PROMPT="$(printf '%s' "$INPUT" | python3 -c '
import json, sys
try:
    d = json.load(sys.stdin)
    p = d.get("prompt", "")
    p = p if isinstance(p, str) else ""
    # Never commit a key Tj pastes into chat: this repo is public (tools/redact_keys.py).
    try:
        sys.path.insert(0, "tools")
        from redact_keys import redact
        p = redact(p)
    except Exception:
        pass
    sys.stdout.write(p)
except Exception:
    pass
' 2>/dev/null)"
else
  PROMPT=""
fi
[ -z "$PROMPT" ] && exit 0

# The harness's own notices (a background command finishing) arrive as prompts too; they are
# not Tj's words, and logging them made INBOX.md look like he had sent them (2026-09-27).
case "$PROMPT" in
  "<task-notification>"*) exit 0 ;;
  # a scheduled rotation routine's own stored prompt is not Tj's words (and must never make its session look interactive)
  "You are a Vigilant rotation worker"*) exit 0 ;;
esac

# ONE ACCOUNT, THREE ACCOUNTS (Tj, 2026-10-15: "chat with any of the Claude accounts ... make changes from any account ... as though all three accounts are one account"):
# a message from Tj means a person is here, in whichever account he opened. Take the repo's lease for this session NOW, before anything is pushed: from a routine (it stops at its next push and
# parks its work on a routine-wip branch), from a stale holder, or from nobody. Another LIVE interactive session (Tj chatting in a second account) is not taken from: Claude is told to say so.
# Never blocks the prompt; any failure is silent (losing a message is worse than two sessions meeting).
# a message from Tj lifts the dead-man switch's STOP before anything else (TCL50)
[ -f tools/deadman.py ] && [ "${COORD_KIND:-interactive}" = "interactive" ] && timeout 40 python3 -I tools/deadman.py resume 2>&1 | head -2
# Chat owner (Tj, 2026-10-16): while ANOTHER account's slot owns the chat and it is running, a message typed here does not take the lease (the routine there is serving Tj); the session is told, and
# Tj's word "force" (this message containing it) still takes it.
CHAT_STAND=""
if [ -f tools/rotation.py ] && [ -z "${COORD_OFF:-}" ] && [ "${COORD_KIND:-interactive}" = "interactive" ]; then
  CHAT_LINE="$(timeout 60 python3 -I tools/rotation.py chat 2>/dev/null | head -1)"
  case "$CHAT_LINE" in
    "CHATSTATE STANDDOWN"*)
      if printf '%s' "$PROMPT" | grep -qi "force"; then :; else
        CHAT_STAND="$CHAT_LINE"
        printf '%s\n' "CHAT OWNER: $CHAT_LINE. Tell Tj plainly that this account is not scheduled right now and the chat belongs to that account; do NOT work, edit, push or take the lease. He can say 'force' to take it back."
      fi ;;
  esac
fi
if [ -z "$CHAT_STAND" ] && [ -f tools/coord.py ] && [ -z "${COORD_OFF:-}" ] && [ "${COORD_KIND:-interactive}" = "interactive" ]; then
  COORD_OUT="$(timeout 40 python3 -I tools/coord.py acquire --kind interactive 2>&1)"; COORD_RC=$?
  if [ "$COORD_RC" -eq 3 ]; then
    printf '%s\n' "COORD: ANOTHER LIVE CLAUDE SESSION HOLDS THIS REPO (see below). Do not edit, push or ship. Tell Tj plainly which session it is and ask whether to take over; only on his word run: bash tools/coord.py acquire --force"
    printf '%s\n' "$COORD_OUT" | head -3
  elif printf '%s' "$COORD_OUT" | grep -qi "took over\|TAKEN BY FORCE"; then
    printf '%s\n' "COORD: $(printf '%s' "$COORD_OUT" | head -1 | sed 's/^COORD  //')"
    printf '%s\n' "COORD: a paused routine is not lost: a rotation-paused note is on the board, the routine parks its work and waits for the lease, and resumes by itself when you have been idle 20 minutes (or the next run covers it). You need do nothing."
  fi
fi

# RELAY (Tj, 2026-10-18: "no matter which Claude account I am talking to, if I ask for modifications, the message is automatically relayed to the Claude account holding the lease and it should read
# the prompt and begin the answer as soon as possible"). A message typed here while this session does NOT hold the lease (the chat belongs to another account, or another live session holds
# the repo) goes into the app chat as Tj's message: the lease holder's chat_watch.sh prints it within seconds, and chat-wake.yml wakes the on-duty account if nobody is watching. Never blocks the prompt.
if [ -z "${COORD_OFF:-}" ] && [ "${COORD_KIND:-interactive}" = "interactive" ] && [ -f tools/tell_tj.py ] && { [ -n "$CHAT_STAND" ] || [ "${COORD_RC:-0}" -eq 3 ]; }; then
  RELAY_ID="$(timeout 40 python3 -I tools/tell_tj.py relay "$PROMPT" 2>/dev/null | awk '/^relayed/{print $2}')"
  if [ -n "$RELAY_ID" ]; then
    printf '%s\n' "RELAYED: this message was passed on to the account that holds the lease (chat id $RELAY_ID); it reads it within seconds and answers in the app chat. Tell Tj that, and do not act on the request yourself unless he says 'force'."
  fi
fi

{
  echo ""
  echo "## $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo '```'
  printf '%s\n' "$PROMPT"
  echo '```'
} >> INBOX.md

# Files Tj sent with this message (screenshots, diagnostics, research files) are saved into research/uploads/ now, so the autosave below pushes them with the message
# (tools/archive-uploads.sh; Tj, 2026-10-09: "save as much data and research as possible into the GitHub repo"). Never blocks the prompt.
bash tools/archive-uploads.sh >/dev/null 2>&1 || true

# Reuse the already-tested commit/secretscan/push path rather than a second
# copy of it — this file's only job is getting the message onto disk.
if [ "$TEXT_MODE" -eq 1 ]; then
  bash tools/autosave.sh --text
else
  bash tools/autosave.sh
fi
exit 0
