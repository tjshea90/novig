#!/usr/bin/env bash
# save_agent.sh <label> [note] — bank ONE finished agent's result on GitHub, right now.
#
# Tj, 2026-10-06 14:14Z: "save all progress to GitHub when each agent has finished its work, so no progress
# is lost and another session can resume". The usage cap / container loss that kills a session also kills the
# agents still running, so the only protection is to commit and push each result the moment it exists, not
# after the whole batch.
#
#   bash tools/save_agent.sh study-traps                 # research/v0701_partial/study-traps.json
#   bash tools/save_agent.sh verify-3-luck "note"
#
# It stages ONLY research/v0701_partial (never the app's code), refuses a result that is missing / not JSON /
# shaped like a credential (the repo is public: numbers and findings only, no bet rows, wallet or keys), commits
# and pushes with tools/push.sh. Exit 1 = nothing was saved (read why); 0 = saved and pushed.
set -uo pipefail
D="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; cd "$D" || exit 1
LABEL="${1:-}"; NOTE="${2:-}"
[ -n "$LABEL" ] || { echo "usage: bash tools/save_agent.sh <label> [note]" >&2; exit 1; }
F="research/v0701_partial/$LABEL.json"
[ -s "$F" ] || { echo "save_agent: $F is missing or empty: that agent has not saved its result (rerun just that label)" >&2; exit 1; }
python3 -I -c 'import json,sys; json.load(open(sys.argv[1]))' "$F" 2>/dev/null \
  || { echo "save_agent: $F is not valid JSON (a torn write): rerun just that label" >&2; exit 1; }
git add -- research/v0701_partial
if ! bash tools/secretscan.sh >/dev/null 2>&1; then
  git reset -q -- research/v0701_partial
  echo "save_agent: the secret scan tripped on $F: remove the credential-shaped text, do not bypass it" >&2; exit 1
fi
if git diff --cached --quiet -- research/v0701_partial; then
  echo "save_agent: $LABEL was already saved (nothing new to commit)"
else
  git commit -q -m "agent done: $LABEL${NOTE:+ — $NOTE}" -- research/v0701_partial || { echo "save_agent: commit failed" >&2; exit 1; }
  echo "save_agent: committed $LABEL"
fi
bash tools/push.sh >/dev/null 2>&1
if git rev-parse --verify -q "origin/$(git branch --show-current)" >/dev/null && \
   [ "$(git rev-list --count "origin/$(git branch --show-current)"..HEAD 2>/dev/null || echo 1)" -eq 0 ]; then
  echo "save_agent: $LABEL is on GitHub (origin/$(git branch --show-current))"
else
  echo "save_agent: pushed, but could not confirm origin has it: run 'git status -sb' and 'bash tools/push.sh'" >&2
fi
exit 0
