#!/usr/bin/env bash
# push.sh — get commits to GitHub, and never decide "no" by accident.
#
# WHY THIS IS ITS OWN FILE
# ------------------------
# Both ckpt.sh and autosave.sh need to answer "is there anything to push?"
# cheaply, because autosave runs after every single tool call and a no-op
# network round trip on each one is latency paid hundreds of times a session
# for nothing.
#
# The obvious test — `git rev-list --count @{u}..HEAD` — has a failure mode
# worth guarding against. `@{u}` needs a remote-TRACKING ref
# (refs/remotes/origin/main). A checkout whose branch was created from
# FETCH_HEAD has branch.<name>.remote and branch.<name>.merge set, looks
# completely normal to `git remote show`, and still has no such ref — so
# `@{u}` errors, the `|| echo 0` fallback reads as "nothing to push", and the
# push is skipped in silence. The commit is made, the session reports
# success, and the work never leaves the container. On a usage cap that is
# total loss.
#
# So the rule here: when the count cannot be determined, PUSH. A wasted push
# costs a second; a skipped one can cost the whole session.
#
# THREE ACCOUNTS, NO LEASE (CR, Tj 2026-10-19: "Any claude account can make changes to this repo, as long as they coordinate with each other and don't double work or
# break anything ... not get jumbled up with different branches"). Nothing here holds a push back any more. Instead, after the session's own branch is pushed:
#   * main moves to the branch only as a FAST-FORWARD and only when tools/landing.py says everything the branch changes was checked (docs always; tools after the fast
#     checks; app/engine code after tools/test.sh), so main is never broken;
#   * when main has moved on (another session landed), main is merged into the branch by itself, but only a CLEAN merge on a clean tree; a conflict is aborted at once
#     (never committed with markers) and the session is told which files to merge by hand;
#   * a branch that is merely behind main is fast-forwarded to it, so a session never works on old code for long.
# What the session should know (main waits for a check, a conflict) is left in .git/vigilant-push-note; autosave.sh shows it once.
set -uo pipefail
D="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; cd "$D" || exit 0
[ -d .git ] || exit 0

BR="$(git branch --show-current 2>/dev/null || true)"
[ -z "$BR" ] && exit 0   # detached HEAD — no branch to push to
NOTE_FILE=".git/vigilant-push-note"
note() { printf '%s\n' "$1" > "$NOTE_FILE" 2>/dev/null; echo "PUSH  $1" >&2; }

# The session's heartbeat on claude-coord (who is working, on which branch). Never a reason not to push.
if [ -z "${COORD_OFF:-}" ] && [ -f tools/coord.py ]; then
  timeout 40 python3 -I tools/coord.py check >/dev/null 2>&1 || true
fi

UP="$(git rev-parse --abbrev-ref '@{u}' 2>/dev/null || true)"
[ -z "$UP" ] && UP="origin/$BR"
if git rev-parse --verify -q "$UP" >/dev/null 2>&1; then
  AHEAD="$(git rev-list --count "$UP"..HEAD 2>/dev/null || echo 1)"
else
  AHEAD=1
fi

clean_tree() { [ -z "$(git status --porcelain --untracked-files=no 2>/dev/null)" ] && [ ! -f .git/MERGE_HEAD ]; }

# Bring origin/main into this branch when that is safe (a fast-forward, or a clean merge on a clean tree). Echoes what happened.
take_main() {
  git config merge.ours.driver true >/dev/null 2>&1   # CHECKPOINT.md keeps the session's own (.gitattributes)
  local M=refs/remotes/origin/main
  git rev-parse -q --verify "$M" >/dev/null 2>&1 || return 0
  [ "$(git rev-parse HEAD)" = "$(git rev-parse $M)" ] && return 0
  git merge-base --is-ancestor "$M" HEAD 2>/dev/null && return 0          # main is behind: nothing to take
  clean_tree || { note "main has new work, but this tree has uncommitted changes: it is merged at the next checkpoint"; return 0; }
  if git merge-base --is-ancestor HEAD "$M" 2>/dev/null; then
    timeout 30 git merge -q --ff-only "$M" >/dev/null 2>&1 && echo "fast-forwarded to main"
    return 0
  fi
  if timeout 60 git -c user.name=Claude -c user.email=noreply@anthropic.com merge -q --no-edit -m "merge main into $BR (push.sh, CR)" "$M" >/dev/null 2>&1; then
    echo "merged main"
    return 0
  fi
  local FILES; FILES="$(git diff --name-only --diff-filter=U 2>/dev/null | head -8 | tr '\n' ' ')"
  git merge --abort >/dev/null 2>&1 || git reset -q --merge >/dev/null 2>&1
  note "MAIN CONFLICTS WITH THIS BRANCH in: ${FILES:-?}. Your work is safe on $BR (pushed) but cannot land until you merge: git merge origin/main, fix those files, commit (bash tools/ckpt.sh)."
  return 0
}

if [ "${AHEAD:-1}" -ne 0 ]; then
  # Explicit destination: works whether or not tracking is configured. `timeout` bounds this well under the caller's hook budget.
  if ! timeout 45 git push -q origin "HEAD:refs/heads/$BR" >/dev/null 2>&1; then
    # A checkout ON main (a routine) is refused when another session landed first: take main in (clean only) and try once more.
    if [ "$BR" = "main" ] && timeout 20 git fetch -q origin "+refs/heads/main:refs/remotes/origin/main" >/dev/null 2>&1; then
      take_main >/dev/null
      timeout 45 git push -q origin "HEAD:refs/heads/$BR" >/dev/null 2>&1 || exit 1
    else
      exit 1
    fi
  fi
  # Self-heal, so the cheap check above starts working and this stops paying for a network round trip on every tool call (an EMPTY remote.origin.fetch after a
  # shallow clone means refs/remotes/origin/* is never populated).
  git config --get-all remote.origin.fetch >/dev/null 2>&1 || \
    git config remote.origin.fetch '+refs/heads/*:refs/remotes/origin/*' 2>/dev/null || true
  timeout 10 git fetch -q origin "$BR:refs/remotes/origin/$BR" >/dev/null 2>&1 || true
  git branch --set-upstream-to="origin/$BR" "$BR" >/dev/null 2>&1 || true
fi
[ "$BR" = "main" ] && exit 0

# KEEP THIS BRANCH AND main IN STEP. At most every 30 s from the hooks (PUSH_SYNC_NOW=1 from ckpt.sh forces it): one fetch of main.
STAMP=".git/vigilant-main-sync"
NOWS="$(date +%s)"
LAST="$(cat "$STAMP" 2>/dev/null || echo 0)"
if [ -z "${PUSH_SYNC_NOW:-}" ] && [ $((NOWS - ${LAST:-0})) -lt 30 ] && [ "${AHEAD:-1}" -eq 0 ]; then
  exit 0
fi
echo "$NOWS" > "$STAMP" 2>/dev/null
timeout 20 git fetch -q origin "+refs/heads/main:refs/remotes/origin/main" >/dev/null 2>&1 || exit 0
WHAT="$(take_main)"
if [ -n "$WHAT" ]; then
  timeout 45 git push -q origin "HEAD:refs/heads/$BR" >/dev/null 2>&1 || true
fi
M=refs/remotes/origin/main
if git merge-base --is-ancestor "$M" HEAD 2>/dev/null && [ "$(git rev-parse HEAD)" != "$(git rev-parse $M)" ]; then
  # Nothing with conflict markers ever lands (a merge someone finished by hand too early).
  # grep reads to the end (no -q): under pipefail an early exit would SIGPIPE git and hide the markers.
  if git diff "$M"...HEAD 2>/dev/null | grep -E '^\+(<<<<<<< |>>>>>>> )' >/dev/null; then
    note "main NOT moved: conflict markers (<<<<<<< / >>>>>>>) are committed on $BR. Remove them, then bash tools/ckpt.sh."
    exit 0
  fi
  if VERDICT="$(python3 -I tools/landing.py decide 2>/dev/null)"; then
    if timeout 30 git push -q origin "HEAD:refs/heads/main" >/dev/null 2>&1; then
      rm -f "$NOTE_FILE" 2>/dev/null
    fi
  else
    note "main waits for this branch's checks (your work is safe on $BR): run ${VERDICT#WAIT }"
  fi
fi
exit 0
