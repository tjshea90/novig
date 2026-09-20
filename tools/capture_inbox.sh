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
# message is written to disk, not echoed back), but a warning relayed up
# from autosave.sh must stay PLAIN TEXT so tools/hooks/inbox.sh can combine
# several repos into ONE JSON object — see tools/hooks/emit.py for why two
# JSON objects on hook stdout is a silent total loss, not just noise.
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
    sys.stdout.write(p if isinstance(p, str) else "")
except Exception:
    pass
' 2>/dev/null)"
else
  PROMPT=""
fi
[ -z "$PROMPT" ] && exit 0

{
  echo ""
  echo "## $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo '```'
  printf '%s\n' "$PROMPT"
  echo '```'
} >> INBOX.md

# Reuse the already-tested commit/secretscan/push path rather than a second
# copy of it — this file's only job is getting the message onto disk. Pass
# --text through so a blocked-secret or failed-push warning stays plain text
# for the aggregator rather than pre-wrapped JSON nested inside more JSON.
if [ "$TEXT_MODE" -eq 1 ]; then
  bash tools/autosave.sh --text
else
  bash tools/autosave.sh
fi
exit 0
