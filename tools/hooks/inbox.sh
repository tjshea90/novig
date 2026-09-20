#!/usr/bin/env bash
# inbox.sh — UserPromptSubmit. Capture the raw message into every
# checkpoint-managed repo that wants it (i.e. that ships tools/capture_inbox.sh),
# before Claude does any work on it, in one JSON object.
#
# WHY THIS IS ITS OWN ENTRY POINT, NOT FOLDED INTO save.sh
# ---------------------------------------------------------
# save.sh runs on PostToolUse/Stop and needs no input — it just autosaves
# whatever is on disk. This runs earlier, on UserPromptSubmit, and needs the
# actual message text, which arrives on stdin as JSON
# (`{"prompt": "...", ...}`). hook_collect passes stdin straight through to
# each repo's tools/capture_inbox.sh via a here-string, and the result is
# wrapped as a UserPromptSubmit hookSpecificOutput rather than a
# systemMessage — see tools/hooks/emit.py.
set -uo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
INPUT="$(cat)"
hook_collect "tools/capture_inbox.sh" "${CLAUDE_HOOK_BUDGET:-30}" "$INPUT" | hook_emit user-prompt-submit
exit 0
