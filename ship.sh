#!/usr/bin/env bash
# ship.sh — cut a release. Once there is an app, GITHUB BUILDS AND SIGNS IT;
# this prepares and triggers that. See CLAUDE.md's "Releasing" section for
# the full plan, ported from Portfolio's ship.sh/android.yml.
#
#   bash ship.sh "what changed this release"
#
# WHY THIS DOES SO LITTLE RIGHT NOW
# ------------------------------------
# There is no Android project scaffold yet — no app/build.gradle.kts, no
# signing keystore, no .github/workflows/*.yml for this repo. A ship.sh that
# pretended to gate a build against files that don't exist would either
# silently no-op (looks like success, isn't) or crash confusingly. Neither
# is acceptable for the one command CLAUDE.md calls "the milestone gate", so
# this refuses honestly and says exactly what to build first, the same way
# every other gate below refuses honestly when a real condition fails.
#
# THE REAL GATE TO WRITE, THE DAY THERE IS SOMETHING TO SHIP (mirror
# Portfolio's ship.sh, which this account has already proven works):
#   1. Run the fast checks (same discovery ckpt.sh uses).
#   2. Run the FULL test suite (whatever the toolchain's real suite is —
#      Gradle's testDebugUnitTest, or equivalent), not just the fast ones.
#   3. Read versionCode/versionName from the real build file, and refuse
#      unless versionCode is strictly higher than every code already in
#      BUILDLOG.md (this file exists, empty, ready for the first entry —
#      tools/record-release.sh already writes that entry once a run is
#      green; see its own header for why it takes versionCode explicitly).
#   4. Push (never a tag — confirmed elsewhere in this account that a Claude
#      container gets HTTP 403 on refs/tags/* pushes; GitHub creates the tag
#      itself from inside the Actions run instead).
#   5. Write the "trigger the build, then record it" next step into
#      CHECKPOINT.md via tools/ckpt.sh BEFORE ending, so an interruption
#      between the push and the GitHub API call isn't a lost instruction.
set -uo pipefail
D="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; cd "$D" || exit 1

NOTE="${1:-}"; [ -z "$NOTE" ] && { echo "FAIL: a one-line change note is required."; exit 1; }

# COMMIT BEFORE GATING. A gate run against a dirty tree proves nothing about
# what is actually committed, and leaves the tree looking like the last
# session was interrupted when it wasn't (tools/resume.sh's mid-change
# warning must not cry wolf on every ship attempt).
if [ -d .git ] && [ -n "$(git status --porcelain 2>/dev/null)" ]; then
  bash tools/ckpt.sh "pre-ship: $NOTE" "ship.sh gates and releases this" >/dev/null 2>&1
  echo "  OK    committed the working tree before gating"
fi

echo "== ship gates =="

# ---- whatever fast checks exist, same discovery ckpt.sh uses -----------------
PASS=0; FAIL=0; REDS=""; RAN_ANY=0
if [ -f package.json ] && command -v npm >/dev/null 2>&1 && command -v node >/dev/null 2>&1 \
   && node -e "const p=require('./package.json'); process.exit(p.scripts && p.scripts.test ? 0 : 1)" 2>/dev/null; then
  RAN_ANY=1
  if npm test --silent >/dev/null 2>&1; then PASS=$((PASS+1)); else FAIL=$((FAIL+1)); REDS="$REDS npm-test"; fi
fi
for T in tools/test_*.js tools/test_*.sh tools/test_*.py; do
  [ -f "$T" ] || continue
  RAN_ANY=1
  case "$T" in *.sh) RUNNER="bash" ;; *.py) RUNNER="python3" ;; *) RUNNER="node" ;; esac
  if "$RUNNER" "$T" >/dev/null 2>&1; then PASS=$((PASS+1)); else FAIL=$((FAIL+1)); REDS="$REDS $(basename "$T")"; fi
done
if [ "$RAN_ANY" -gt 0 ]; then
  if [ "$FAIL" -gt 0 ]; then
    echo "  FAIL  $FAIL fast check(s) red:$REDS — not shipping this."
    exit 1
  fi
  echo "  OK    all $PASS fast checks green"
else
  echo "  note  no fast checks exist yet"
fi

# ---- is there anything to actually build and release? -------------------------
if [ -f app/build.gradle.kts ] || [ -f build.gradle.kts ] || [ -f package.json ] && [ -d app ]; then
  echo "  FAIL  a build system now exists, but ship.sh has not been updated to gate"
  echo "        it yet (full suite, versionCode check, GitHub Actions trigger)."
  echo "        Write that gate now — see this file's own header for the shape to"
  echo "        follow (Portfolio's ship.sh) — then re-run."
  exit 1
fi

echo "  FAIL  no Android project scaffold exists yet (no app/build.gradle.kts,"
echo "        no build.gradle.kts) — there is nothing to build or release."
echo "        Stand up the project first (see BRIEF.md's open TBDs and"
echo "        CLAUDE.md's \"Releasing\" section for the order to do it in)."
echo "        The checkpoint itself is still safe: 'bash tools/ckpt.sh \"$NOTE\" \"next\"'"
echo "        records this work without claiming a release that doesn't exist."
exit 1
